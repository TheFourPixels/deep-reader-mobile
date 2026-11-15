package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.repository.ImportBookResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class ImportBookUseCaseTest {

    private lateinit var bookRepository: BookRepository
    private lateinit var importBookUseCase: ImportBookUseCase

    @Before
    fun setUp() {
        bookRepository = mockk()
        importBookUseCase = ImportBookUseCase(bookRepository)
    }

    @Test
    fun invoke_newValidEpub_callsRepositoryImportAndReturnsSuccess() = runTest {
        val stream = ByteArrayInputStream("dummy content".toByteArray())
        val newBook = Book(
            sha256 = "abc123sha256",
            title = "1984",
            author = "George Orwell",
            filePath = "/storage/books/abc123sha256.epub"
        )
        coEvery { bookRepository.importBook(stream, "1984.epub") } returns ImportBookResult.Success(newBook)

        val result = importBookUseCase(stream, "1984.epub")

        assertTrue(result is ImportBookResult.Success)
        assertEquals(newBook, (result as ImportBookResult.Success).book)
        coVerify(exactly = 1) { bookRepository.importBook(stream, "1984.epub") }
    }

    @Test
    fun invoke_duplicateSha256_returnsExactDuplicateResult() = runTest {
        val stream = ByteArrayInputStream("duplicate".toByteArray())
        val existingBook = Book(
            sha256 = "abc123sha256",
            title = "1984",
            author = "George Orwell",
            filePath = "/storage/books/abc123sha256.epub"
        )
        coEvery { bookRepository.importBook(stream, "1984.epub") } returns ImportBookResult.ExactDuplicate(existingBook)

        val result = importBookUseCase(stream, "1984.epub")

        assertTrue(result is ImportBookResult.ExactDuplicate)
        assertEquals(existingBook, (result as ImportBookResult.ExactDuplicate).existingBook)
    }

    @Test
    fun invoke_differentSha256SameTitleAndAuthor_returnsMetadataCollisionWarning() = runTest {
        val stream = ByteArrayInputStream("edition2".toByteArray())
        val importedBook = Book("sha222", "1984", "George Orwell", "/storage/books/sha222.epub")
        val existingBook = Book("sha111", "1984", "George Orwell", "/storage/books/sha111.epub")

        coEvery { bookRepository.importBook(stream, "1984.epub") } returns ImportBookResult.MetadataCollisionWarning(importedBook, existingBook)

        val result = importBookUseCase(stream, "1984.epub")

        assertTrue(result is ImportBookResult.MetadataCollisionWarning)
        val warning = result as ImportBookResult.MetadataCollisionWarning
        assertEquals(importedBook, warning.importedBook)
        assertEquals(existingBook, warning.existingMatchingMetadataBook)
    }
}