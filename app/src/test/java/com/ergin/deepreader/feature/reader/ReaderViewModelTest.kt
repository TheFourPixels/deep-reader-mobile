package com.ergin.deepreader.feature.reader

import app.cash.turbine.test
import com.ergin.deepreader.R
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ParagraphBlock
import com.ergin.deepreader.domain.model.ParagraphLayoutInfo
import com.ergin.deepreader.domain.model.ReadingPosition
import com.ergin.deepreader.domain.model.SavedQuote
import com.ergin.deepreader.domain.model.SentenceLayoutInfo
import com.ergin.deepreader.domain.model.SentenceSpan
import com.ergin.deepreader.domain.model.ViewportLayoutSnapshot
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.repository.QuoteRepository
import com.ergin.deepreader.domain.usecase.EpubContextExtractorUseCase
import com.ergin.deepreader.domain.usecase.RestoreReadingPositionUseCase
import com.ergin.deepreader.domain.usecase.SaveReadingPositionUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private val bookRepository: BookRepository = mockk(relaxed = true)
    private val quoteRepository: QuoteRepository = mockk(relaxed = true)
    private val quotesFlow = MutableStateFlow<List<SavedQuote>>(emptyList())
    private val restoreReadingPositionUseCase: RestoreReadingPositionUseCase = mockk(relaxed = true)
    private val saveReadingPositionUseCase: SaveReadingPositionUseCase = mockk(relaxed = true)
    private val epubContextExtractorUseCase = EpubContextExtractorUseCase()
    private val epubParserEngine: EpubParserEngine = mockk(relaxed = true)

    private lateinit var viewModel: ReaderViewModel

    private lateinit var tempFile: File
    private lateinit var sampleBook: Book

    private val sampleChapter0 = ChapterContent(
        chapterIndex = 0,
        title = "Chapter 1",
        paragraphs = listOf(
            ParagraphBlock(0, null, "Paragraph 1", emptyList()),
            ParagraphBlock(1, null, "Paragraph 2", emptyList())
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tempFile = File.createTempFile("test_sample_", ".epub").apply {
            writeText("dummy content")
        }

        sampleBook = Book(
            sha256 = "book_sha1",
            title = "Sample Book",
            author = "Sample Author",
            filePath = tempFile.absolutePath,
            coverPath = null,
            addedAt = 100L,
            lastOpenedAt = null
        )

        coEvery { bookRepository.getBookBySha256("book_sha1") } answers { sampleBook }
        every { epubParserEngine.getChapterCount(any()) } returns 3
        every { epubParserEngine.readChapter(any(), any()) } answers {
            val index = secondArg<Int>()
            sampleChapter0.copy(chapterIndex = index, title = "Chapter ${index + 1}")
        }
        coEvery { restoreReadingPositionUseCase("book_sha1", emptyList()) } returns ReadingPosition("book_sha1", 0, null, 0, 0, 0f)
        coEvery { restoreReadingPositionUseCase("book_sha1", match { it.isNotEmpty() && it.first().chapterIndex == 0 }) } returns ReadingPosition("book_sha1", 0, null, 0, 0, 0f)
        coEvery { restoreReadingPositionUseCase("book_sha1", match { it.isNotEmpty() && it.first().chapterIndex == 1 }) } returns ReadingPosition("book_sha1", 1, null, 1, 0, 50f)

        every { quoteRepository.observeQuotesForBook("book_sha1") } returns quotesFlow
        coEvery { quoteRepository.saveQuote(any()) } answers {
            val quote = firstArg<SavedQuote>()
            val newId = (quotesFlow.value.size + 1).toLong()
            quotesFlow.value = quotesFlow.value + quote.copy(id = newId)
        }
        coEvery { quoteRepository.deleteQuote(any()) } answers {
            val id = firstArg<Long>()
            quotesFlow.value = quotesFlow.value.filterNot { it.id == id }
        }

        viewModel = ReaderViewModel(
            bookRepository = bookRepository,
            quoteRepository = quoteRepository,
            restoreReadingPositionUseCase = restoreReadingPositionUseCase,
            saveReadingPositionUseCase = saveReadingPositionUseCase,
            epubContextExtractorUseCase = epubContextExtractorUseCase,
            epubParserEngine = epubParserEngine,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::tempFile.isInitialized && tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun loadBook_initialOpen_restoresSavedChapterAndParagraph() = runTest(testDispatcher) {
        val savedPos = ReadingPosition("book_sha1", 1, null, 1, 0, 50f)
        coEvery { restoreReadingPositionUseCase("book_sha1", emptyList()) } returns savedPos

        viewModel.loadBook("book_sha1")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ReaderUiState.Success)
        val success = state as ReaderUiState.Success
        assertEquals(1, success.chapter.chapterIndex)
        assertEquals(1, success.currentPosition.paragraphIndex)
    }

    @Test
    fun flushReadingPositionImmediately_executesImmediateSave() = runTest(testDispatcher) {
        viewModel.loadBook("book_sha1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onScrollPositionChanged(1, 0)
        viewModel.flushReadingPositionImmediately()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { saveReadingPositionUseCase(match { it.paragraphIndex == 1 }) }
    }

    @Test
    fun onFontSizeChanged_clampsValueBetween12And32() = runTest(testDispatcher) {
        viewModel.loadBook("book_sha1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFontSizeChanged(8)
        var state = viewModel.uiState.value as ReaderUiState.Success
        assertEquals(12, state.fontSp)

        viewModel.onFontSizeChanged(40)
        state = viewModel.uiState.value as ReaderUiState.Success
        assertEquals(32, state.fontSp)
    }

    @Test
    fun onNextAndPreviousChapter_navigatesAndUpdatesBoundaryFlags() = runTest(testDispatcher) {
        viewModel.loadBook("book_sha1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNextChapter()
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Success
        assertEquals(1, state.chapter.chapterIndex)
        assertFalse(state.isFirstChapter)
        assertFalse(state.isLastChapter)

        viewModel.onNextChapter()
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value as ReaderUiState.Success
        assertEquals(2, state.chapter.chapterIndex)
        assertFalse(state.isFirstChapter)
        assertTrue(state.isLastChapter)

        viewModel.onPreviousChapter()
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value as ReaderUiState.Success
        assertEquals(1, state.chapter.chapterIndex)
    }

    @Test
    fun onTranslateSelection_emitsRequestOrTooLongEvent() = runTest(testDispatcher) {
        viewModel.events.test {
            viewModel.onTranslateSelection("hello world")
            assertEquals(ReaderUiEvent.RequestTranslation("hello world"), awaitItem())

            viewModel.onTranslateSelection("a".repeat(1001))
            assertEquals(ReaderUiEvent.ShowMessage(R.string.reader_selection_too_long), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onAiExplainSelection_extractsContextAndEmitsExplainRequest() = runTest(testDispatcher) {
        viewModel.loadBook("book_sha1")
        testDispatcher.scheduler.advanceUntilIdle()

        val sentence = SentenceSpan(0, "He felt under the weather.", 0, 26)
        val snapshot = ViewportLayoutSnapshot(
            viewportTopY = 0f,
            viewportBottomY = 500f,
            chapterTitle = "Chapter 1",
            paragraphs = listOf(
                ParagraphLayoutInfo(
                    paragraphIndex = 0,
                    text = sentence.text,
                    topY = 10f,
                    bottomY = 50f,
                    sentences = listOf(SentenceLayoutInfo(sentence, 10f, 50f))
                )
            )
        )

        viewModel.events.test {
            viewModel.onAiExplainSelection("under the weather", 0, snapshot)
            val event = awaitItem()
            assertTrue(event is ReaderUiEvent.RequestAiExplain)
            val explainEvent = event as ReaderUiEvent.RequestAiExplain
            assertEquals("book_sha1", explainEvent.request.bookId)
            assertEquals("under the weather", explainEvent.request.selectedText)
            assertEquals(sentence.text, explainEvent.request.currentSentence)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onSaveQuoteAndBookmarksSheet_updatesStateAndEmitsMessage() = runTest(testDispatcher) {
        viewModel.loadBook("book_sha1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onBookmarksSheetToggled(true)
        assertTrue((viewModel.uiState.value as ReaderUiState.Success).isBookmarksSheetVisible)

        viewModel.events.test {
            viewModel.onSaveQuote("Notable quote", paragraphIndex = 1, charOffset = 5)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(ReaderUiEvent.ShowMessage(R.string.reader_quote_saved), awaitItem())
            val state = viewModel.uiState.value as ReaderUiState.Success
            assertEquals(1, state.savedQuotes.size)
            assertEquals("Notable quote", state.savedQuotes.first().quoteText)
            assertEquals(1, state.savedQuotes.first().paragraphIndex)
            assertEquals(5, state.savedQuotes.first().charOffset)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onJumpToQuote_sameChapterAndCrossChapter_setsPageNavigationTarget() = runTest(testDispatcher) {
        viewModel.loadBook("book_sha1")
        testDispatcher.scheduler.advanceUntilIdle()

        val sameChapterQuote = SavedQuote(
            id = 1L,
            bookSha256 = "book_sha1",
            chapterIndex = 0,
            chapterTitle = "Chapter 1",
            paragraphIndex = 1,
            charOffset = 12,
            quoteText = "Paragraph 2"
        )
        viewModel.onJumpToQuote(sameChapterQuote)
        val sameState = viewModel.uiState.value as ReaderUiState.Success
        assertFalse(sameState.isBookmarksSheetVisible)
        assertEquals(1, sameState.currentPosition.paragraphIndex)
        assertEquals(12, sameState.currentPosition.charOffset)
        val sameTarget = sameState.pendingPageTarget as PageNavigationTarget.Position
        assertEquals(1, sameTarget.paragraphIndex)
        assertEquals(12, sameTarget.charOffset)
        assertEquals("Paragraph 2", sameTarget.highlightText)

        val otherChapterQuote = SavedQuote(
            id = 2L,
            bookSha256 = "book_sha1",
            chapterIndex = 2,
            chapterTitle = "Chapter 3",
            paragraphIndex = 1,
            charOffset = 4,
            quoteText = "Target text"
        )
        viewModel.onJumpToQuote(otherChapterQuote)
        testDispatcher.scheduler.advanceUntilIdle()

        val crossState = viewModel.uiState.value as ReaderUiState.Success
        assertEquals(2, crossState.chapter.chapterIndex)
        assertEquals(1, crossState.currentPosition.paragraphIndex)
        assertEquals(4, crossState.currentPosition.charOffset)
        val crossTarget = crossState.pendingPageTarget as PageNavigationTarget.Position
        assertEquals("Target text", crossTarget.highlightText)
    }

    @Test
    fun onPreviousChapterOpenAtEnd_setsLastPageOfChapterTarget() = runTest(testDispatcher) {
        viewModel.loadBook("book_sha1", targetChapterIndex = 1, totalChapters = 3)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPreviousChapter(openAtEnd = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Success
        assertEquals(0, state.chapter.chapterIndex)
        assertTrue(state.pendingPageTarget is PageNavigationTarget.LastPageOfChapter)
    }
}
