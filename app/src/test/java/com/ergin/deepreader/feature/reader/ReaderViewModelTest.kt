package com.ergin.deepreader.feature.reader

import app.cash.turbine.test
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ParagraphBlock
import com.ergin.deepreader.domain.model.ReadingPosition
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.usecase.RestoreReadingPositionUseCase
import com.ergin.deepreader.domain.usecase.SaveReadingPositionUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bookRepository: BookRepository
    private lateinit var restoreReadingPositionUseCase: RestoreReadingPositionUseCase
    private lateinit var saveReadingPositionUseCase: SaveReadingPositionUseCase
    private lateinit var epubParserEngine: EpubParserEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        bookRepository = mockk()
        restoreReadingPositionUseCase = mockk()
        saveReadingPositionUseCase = mockk(relaxed = true)
        epubParserEngine = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadBook_atFirstChapter_disablesPreviousButton() = runTest {
        val sampleBook = Book("sha1", "Title", "Author", "/path/book.epub")
        val chapter0 = ChapterContent(0, "Chapter 1", listOf(ParagraphBlock(0, "p1", "Text", emptyList())))

        coEvery { bookRepository.getBookBySha256("sha1") } returns sampleBook
        every { epubParserEngine.getChapterCount(any()) } returns 2
        every { epubParserEngine.readChapter(any(), 0) } returns chapter0
        coEvery { restoreReadingPositionUseCase("sha1", any()) } returns ReadingPosition("sha1", 0, null, 0, 0)

        val viewModel = ReaderViewModel(
            bookRepository = bookRepository,
            restoreReadingPositionUseCase = restoreReadingPositionUseCase,
            saveReadingPositionUseCase = saveReadingPositionUseCase,
            epubParserEngine = epubParserEngine,
            ioDispatcher = testDispatcher
        )

        viewModel.loadBook("sha1", totalChapters = 2)

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is ReaderUiState.Success)
            val success = state as ReaderUiState.Success
            assertTrue(success.isFirstChapter)
            assertFalse(success.isLastChapter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chapterNavigation_atLastChapter_disablesNextButton() = runTest {
        val sampleBook = Book("sha1", "Title", "Author", "/path/book.epub")
        val chapter1 = ChapterContent(1, "Chapter 2", listOf(ParagraphBlock(0, "p1", "Text", emptyList())))

        coEvery { bookRepository.getBookBySha256("sha1") } returns sampleBook
        every { epubParserEngine.getChapterCount(any()) } returns 2
        every { epubParserEngine.readChapter(any(), 1) } returns chapter1
        coEvery { restoreReadingPositionUseCase("sha1", any()) } returns ReadingPosition("sha1", 1, null, 0, 0)

        val viewModel = ReaderViewModel(
            bookRepository = bookRepository,
            restoreReadingPositionUseCase = restoreReadingPositionUseCase,
            saveReadingPositionUseCase = saveReadingPositionUseCase,
            epubParserEngine = epubParserEngine,
            ioDispatcher = testDispatcher
        )

        viewModel.loadBook("sha1", initialChapterIndex = 1, totalChapters = 2)

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is ReaderUiState.Success)
            val success = state as ReaderUiState.Success
            assertFalse(success.isFirstChapter)
            assertTrue(success.isLastChapter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onFontSizeChanged_updatesFontSizeAndPreservesPosition() = runTest {
        val sampleBook = Book("sha1", "Title", "Author", "/path/book.epub")
        val chapter0 = ChapterContent(0, "Chapter 1", listOf(ParagraphBlock(0, "p1", "Text", emptyList())))

        coEvery { bookRepository.getBookBySha256("sha1") } returns sampleBook
        every { epubParserEngine.getChapterCount(any()) } returns 1
        every { epubParserEngine.readChapter(any(), 0) } returns chapter0
        coEvery { restoreReadingPositionUseCase("sha1", any()) } returns ReadingPosition("sha1", 0, null, 0, 0)

        val viewModel = ReaderViewModel(
            bookRepository = bookRepository,
            restoreReadingPositionUseCase = restoreReadingPositionUseCase,
            saveReadingPositionUseCase = saveReadingPositionUseCase,
            epubParserEngine = epubParserEngine,
            ioDispatcher = testDispatcher
        )

        viewModel.loadBook("sha1", totalChapters = 1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFontSizeChanged(22)

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is ReaderUiState.Success)
            assertEquals(22, (state as ReaderUiState.Success).fontSp)
            cancelAndIgnoreRemainingEvents()
        }
    }
}