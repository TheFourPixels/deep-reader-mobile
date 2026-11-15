package com.ergin.deepreader.domain.repository

import com.ergin.deepreader.domain.model.Book
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface BookRepository {
    fun getBooks(): Flow<List<Book>>
    suspend fun getBookBySha256(sha256: String): Book?
    suspend fun importBook(inputStream: InputStream, fallbackFileName: String): ImportBookResult
    suspend fun updateLastOpened(sha256: String)
    suspend fun deleteBook(sha256: String)
}

sealed class ImportBookResult {
    data class Success(val book: Book) : ImportBookResult()
    data class ExactDuplicate(val existingBook: Book) : ImportBookResult()
    data class MetadataCollisionWarning(val importedBook: Book, val existingMatchingMetadataBook: Book) : ImportBookResult()
    data class Error(val message: String) : ImportBookResult()
}