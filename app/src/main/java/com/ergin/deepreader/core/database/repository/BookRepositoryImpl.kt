package com.ergin.deepreader.core.database.repository

import com.ergin.deepreader.core.database.dao.BookDao
import com.ergin.deepreader.core.database.entity.BookEntity
import com.ergin.deepreader.core.storage.EpubFileImporter
import com.ergin.deepreader.core.storage.EpubImportException
import com.ergin.deepreader.core.storage.FileStorageGateway
import com.ergin.deepreader.di.IoDispatcher
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.repository.ImportBookResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val epubFileImporter: EpubFileImporter,
    private val storageGateway: FileStorageGateway,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BookRepository {

    override fun getBooks(): Flow<List<Book>> {
        return bookDao.getBooksOrderedByLastOpened()
            .onStart { backfillMissingCovers() }
            .map { list -> list.map { it.toDomain() } }
    }

    /** Extracts covers for books imported before cover support. Failures are ignored: covers are optional. */
    private suspend fun backfillMissingCovers() = withContext(ioDispatcher) {
        try {
            bookDao.getBooksWithoutCover().forEach { entity ->
                val epubFile = File(entity.filePath)
                if (!storageGateway.fileExists(epubFile)) return@forEach
                val coverPath = epubFileImporter.extractCover(epubFile, entity.sha256)
                if (coverPath != null) {
                    bookDao.updateCoverPath(entity.sha256, coverPath)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    override suspend fun getBookBySha256(sha256: String): Book? = withContext(ioDispatcher) {
        bookDao.getBookBySha256(sha256)?.toDomain()
    }

    override suspend fun importBook(inputStream: InputStream, fallbackFileName: String): ImportBookResult =
        withContext(ioDispatcher) {
            try {
                val importResult = epubFileImporter.copyAndHash(inputStream, fallbackFileName)
                val stagingFile = File(importResult.stagingPath)

                // 1. Exact SHA-256 duplicate check
                val existingBook = bookDao.getBookBySha256(importResult.sha256)
                if (existingBook != null) {
                    storageGateway.deleteFile(stagingFile)
                    return@withContext ImportBookResult.ExactDuplicate(existingBook.toDomain())
                }

                // 2. Move staging file to permanent books directory
                val permanentFile = storageGateway.moveToBooksDirectory(stagingFile, "${importResult.sha256}.epub")

                // 3. Metadata collision warning check
                val title = importResult.title ?: fallbackFileName.substringBeforeLast(".")
                val author = importResult.author
                val metadataMatches = bookDao.findBooksByTitleAndAuthor(title, author)

                val newEntity = BookEntity(
                    sha256 = importResult.sha256,
                    title = title,
                    author = author,
                    filePath = permanentFile.absolutePath,
                    coverPath = importResult.coverPath,
                    addedAt = System.currentTimeMillis(),
                    lastOpenedAt = null
                )
                bookDao.insertBook(newEntity)

                if (metadataMatches.isNotEmpty()) {
                    ImportBookResult.MetadataCollisionWarning(
                        importedBook = newEntity.toDomain(),
                        existingMatchingMetadataBook = metadataMatches.first().toDomain()
                    )
                } else {
                    ImportBookResult.Success(newEntity.toDomain())
                }
            } catch (e: EpubImportException) {
                ImportBookResult.Error(e.message ?: "Не удалось открыть книгу. Неподдерживаемый или повреждённый формат.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ImportBookResult.Error("Не удалось открыть книгу: ${e.message}")
            }
        }

    override suspend fun updateLastOpened(sha256: String) = withContext(ioDispatcher) {
        bookDao.updateLastOpened(sha256, System.currentTimeMillis())
    }

    override suspend fun deleteBook(sha256: String) = withContext(ioDispatcher) {
        val entity = bookDao.getBookBySha256(sha256)
        if (entity != null) {
            storageGateway.deleteFile(File(entity.filePath))
            entity.coverPath?.takeIf { it.isNotBlank() }?.let { storageGateway.deleteFile(File(it)) }
            bookDao.deleteBook(sha256)
        }
    }

    private fun BookEntity.toDomain() = Book(
        sha256 = sha256,
        title = title,
        author = author,
        filePath = filePath,
        coverPath = coverPath,
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt
    )
}
