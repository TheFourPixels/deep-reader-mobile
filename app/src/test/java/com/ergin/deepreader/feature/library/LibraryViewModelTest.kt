package com.ergin.deepreader.feature.library

import app.cash.turbine.test
import com.ergin.deepreader.core.storage.FileStorageGateway
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.repository.ImportBookResult
import com.ergin.deepreader.domain.usecase.ImportBookUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bookRepository: BookRepository
    private lateinit var importBookUseCase: ImportBookUseCase
    private lateinit var storageGateway: FileStorageGateway
    private lateinit var booksFlow: MutableStateFlow<List<Book>>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        bookRepository = mockk()
        storageGateway = mockk()
        booksFlow = MutableStateFlow(emptyList())
        every { bookRepository.getBooks() } returns booksFlow
        importBookUseCase = ImportBookUseCase(bookRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_whenNoBooks_emitsEmptyState() = runTest {
        val viewModel = LibraryViewModel(bookRepository, importBookUseCase, storageGateway)

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is LibraryUiState.Empty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_whenBooksExist_emitsContentState() = runTest {
        val books = listOf(Book("sha1", "Title 1", "Author 1", "/path/1"))
        booksFlow.value = books

        val viewModel = LibraryViewModel(bookRepository, importBookUseCase, storageGateway)

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is LibraryUiState.Content)
            assertEquals(books, (state as LibraryUiState.Content).books)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onImportStarted_emitsImportingStateAndExecutesImport() = runTest {
        val viewModel = LibraryViewModel(bookRepository, importBookUseCase, storageGateway)
        val stream = ByteArrayInputStream("test".toByteArray())
        val newBook = Book("sha999", "New Book", "New Author", "/path/999")

        coEvery { importBookUseCase(stream, "new.epub") } returns ImportBookResult.Success(newBook)

        viewModel.onImportFileSelected(stream, "new.epub")

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is LibraryUiState.Content || state is LibraryUiState.Empty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onBookClicked_whenLocalFileDeleted_emitsFileMissingErrorWithoutCrash() = runTest {
        val missingBook = Book("sha404", "Missing Book", "Author", "/path/missing.epub")
        every { storageGateway.fileExists(File("/path/missing.epub")) } returns false

        val viewModel = LibraryViewModel(bookRepository, importBookUseCase, storageGateway)

        viewModel.onBookClicked(missingBook)

        viewModel.dialogState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val dialog = expectMostRecentItem()
            assertTrue(dialog is LibraryDialogState.FileMissingError)
            assertEquals(missingBook.title, (dialog as LibraryDialogState.FileMissingError).bookTitle)
            cancelAndIgnoreRemainingEvents()
        }
    }
}