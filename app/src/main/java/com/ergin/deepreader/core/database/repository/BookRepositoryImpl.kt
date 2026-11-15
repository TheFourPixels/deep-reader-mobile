package com.ergin.deepreader.core.database.repository

import com.ergin.deepreader.core.database.dao.BookDao
import com.ergin.deepreader.core.database.entity.BookEntity
import com.ergin.deepreader.core.storage.EpubFileImporter
import com.ergin.deepreader.core.storage.EpubImportException
import com.ergin.deepreader.core.storage.FileStorageGateway
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.repository.ImportBookResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val epubFileImporter: EpubFileImporter,
    private val storageGateway: FileStorageGateway
) : BookRepository {

    override fun getBooks(): Flow<List<Book>> {
        return bookDao.getBooksOrderedByLastOpened().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getBookBySha256(sha256: String): Book? {
        return bookDao.getBookBySha256(sha256)?.toDomain()
    }

    override suspend fun importBook(inputStream: InputStream, fallbackFileName: String): ImportBookResult {
        return try {
            val importResult = epubFileImporter.copyAndHash(inputStream, fallbackFileName)
            val stagingFile = File(importResult.stagingPath)

            // 1. Exact SHA-256 duplicate check
            val existingBook = bookDao.getBookBySha256(importResult.sha256)
            if (existingBook != null) {
                storageGateway.deleteFile(stagingFile)
                return ImportBookResult.ExactDuplicate(existingBook.toDomain())
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
            ImportBookResult.Error(e.message ?: "Failed to import EPUB file")
        } catch (e: Exception) {
            ImportBookResult.Error("Unexpected import error: ${e.message}")
        }
    }

    override suspend fun updateLastOpened(sha256: String) {
        bookDao.updateLastOpened(sha256, System.currentTimeMillis())
    }

    override suspend fun deleteBook(sha256: String) {
        val entity = bookDao.getBookBySha256(sha256)
        if (entity != null) {
            storageGateway.deleteFile(File(entity.filePath))
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