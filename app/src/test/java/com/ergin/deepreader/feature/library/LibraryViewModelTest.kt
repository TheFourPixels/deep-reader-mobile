package com.ergin.deepreader.feature.library

import app.cash.turbine.test
import com.ergin.deepreader.core.storage.FileStorageGateway
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.repository.ImportBookResult
import com.ergin.deepreader.domain.usecase.ImportBookUseCase
import com.ergin.deepreader.domain.usecase.UpdateLastOpenedUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private val bookRepository: BookRepository = mockk(relaxed = true)
    private val storageGateway: FileStorageGateway = mockk(relaxed = true)
    private val booksFlow = MutableStateFlow<List<Book>>(emptyList())

    private lateinit var importBookUseCase: ImportBookUseCase
    private lateinit var updateLastOpenedUseCase: UpdateLastOpenedUseCase
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { bookRepository.getBooks() } returns booksFlow
        importBookUseCase = ImportBookUseCase(bookRepository)
        updateLastOpenedUseCase = UpdateLastOpenedUseCase(bookRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(
            bookRepository = bookRepository,
            importBookUseCase = importBookUseCase,
            updateLastOpenedUseCase = updateLastOpenedUseCase,
            storageGateway = storageGateway,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun observeBooks_emptyList_emitsEmptyState() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LibraryContentState.Empty, viewModel.uiState.value.content)
    }

    @Test
    fun observeBooks_withBooks_emitsContentStateAndFiltersByQuery() = runTest(testDispatcher) {
        val books = listOf(
            Book("sha1", "The Great Gatsby", "F. Scott Fitzgerald", "/path/1.epub", null, 100L, null),
            Book("sha2", "1984", "George Orwell", "/path/2.epub", null, 200L, null)
        )
        booksFlow.value = books

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as LibraryContentState.Content
        assertEquals(books, content.books)
        assertEquals(books, content.filteredBooks)

        viewModel.onSearchQueryChanged("Orwell")
        val filteredContent = viewModel.uiState.value.content as LibraryContentState.Content
        assertEquals(1, filteredContent.filteredBooks.size)
        assertEquals("1984", filteredContent.filteredBooks.first().title)

        viewModel.onSearchQueryChanged("")
        viewModel.onSortOrderChanged(LibrarySortOrder.TITLE_ASC)
        val sortedContent = viewModel.uiState.value.content as LibraryContentState.Content
        assertEquals(listOf("1984", "The Great Gatsby"), sortedContent.filteredBooks.map { it.title })
    }

    @Test
    fun onImportFileSelected_exactDuplicate_showsDuplicateNotice() = runTest(testDispatcher) {
        val existingBook = Book("sha1", "Title 1", "Author 1", "/path/1.epub", null, 100L, null)
        coEvery { bookRepository.importBook(any(), any()) } returns ImportBookResult.ExactDuplicate(existingBook)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onImportFileSelected(ByteArrayInputStream(ByteArray(0)), "test.epub")
        testDispatcher.scheduler.advanceUntilIdle()

        val dialog = viewModel.uiState.value.dialog
        assertTrue(dialog is LibraryDialogState.DuplicateNotice)
        assertEquals(existingBook, (dialog as LibraryDialogState.DuplicateNotice).book)
    }

    @Test
    fun onImportFileSelected_error_showsErrorState() = runTest(testDispatcher) {
        coEvery { bookRepository.importBook(any(), any()) } returns ImportBookResult.Error("Invalid EPUB format")

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onImportFileSelected(ByteArrayInputStream(ByteArray(0)), "test.epub")
        testDispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertTrue(content is LibraryContentState.Error)
        assertEquals("Invalid EPUB format", (content as LibraryContentState.Error).message)
    }

    @Test
    fun onBookClicked_fileDoesNotExist_showsFileMissingError() = runTest(testDispatcher) {
        val book = Book("sha1", "Title 1", "Author 1", "/path/nonexistent.epub", null, 100L, null)
        every { storageGateway.fileExists(any()) } returns false

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onBookClicked(book)
        testDispatcher.scheduler.advanceUntilIdle()

        val dialog = viewModel.uiState.value.dialog
        assertTrue(dialog is LibraryDialogState.FileMissingError)
        assertEquals("Title 1", (dialog as LibraryDialogState.FileMissingError).bookTitle)
    }

    @Test
    fun onBookClicked_fileExists_updatesLastOpenedAndEmitsOpenReader() = runTest(testDispatcher) {
        val book = Book("sha1", "Title 1", "Author 1", "/path/existing.epub", null, 100L, null)
        every { storageGateway.fileExists(any()) } returns true

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            viewModel.onBookClicked(book)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LibraryNavigationEvent.OpenReader("sha1"), awaitItem())
            coVerify { bookRepository.updateLastOpened("sha1") }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
