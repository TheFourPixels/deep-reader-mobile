package com.ergin.deepreader.feature.reader

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergin.deepreader.R
import com.ergin.deepreader.di.IoDispatcher
import com.ergin.deepreader.domain.model.ExplainRequest
import com.ergin.deepreader.domain.model.ExtractedContextResult
import com.ergin.deepreader.domain.model.ReadingPosition
import com.ergin.deepreader.domain.model.SavedQuote
import com.ergin.deepreader.domain.model.SurroundingContext
import com.ergin.deepreader.domain.model.ViewportLayoutSnapshot
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.repository.QuoteRepository
import com.ergin.deepreader.domain.usecase.EpubContextExtractorUseCase
import com.ergin.deepreader.domain.usecase.RestoreReadingPositionUseCase
import com.ergin.deepreader.domain.usecase.SaveReadingPositionUseCase
import com.ergin.deepreader.ui.theme.ReaderThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface ReaderUiEvent {
    data class ShowMessage(@param:StringRes val messageResId: Int) : ReaderUiEvent
    data class RequestTranslation(val text: String) : ReaderUiEvent
    data class RequestAiExplain(val request: ExplainRequest) : ReaderUiEvent
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val quoteRepository: QuoteRepository,
    private val restoreReadingPositionUseCase: RestoreReadingPositionUseCase,
    private val saveReadingPositionUseCase: SaveReadingPositionUseCase,
    private val epubContextExtractorUseCase: EpubContextExtractorUseCase,
    private val epubParserEngine: EpubParserEngine,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _events = Channel<ReaderUiEvent>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.receiveAsFlow()

    private var currentBookSha256: String? = null
    private var totalChaptersCount: Int = 1
    private var saveJob: Job? = null
    private var quotesJob: Job? = null
    private var latestQuotes: List<SavedQuote> = emptyList()
    private val requestIdCounter = AtomicLong(1L)

    private fun nextRequestId(): Long = requestIdCounter.getAndIncrement()

    private fun observeQuotesIfNeeded(bookSha256: String) {
        if (currentBookSha256 == bookSha256 && quotesJob?.isActive == true) return
        quotesJob?.cancel()
        latestQuotes = emptyList()
        quotesJob = viewModelScope.launch {
            quoteRepository.observeQuotesForBook(bookSha256).collect { quotes ->
                latestQuotes = quotes
                _uiState.update { state ->
                    if (state is ReaderUiState.Success && state.book.sha256 == bookSha256) {
                        state.copy(savedQuotes = quotes)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun loadBook(
        bookSha256: String,
        targetChapterIndex: Int? = null,
        totalChapters: Int? = null,
        openAtLastPage: Boolean = false,
        targetParagraphIndex: Int? = null,
        targetCharOffset: Int = 0,
        highlightText: String? = null
    ) {
        observeQuotesIfNeeded(bookSha256)
        currentBookSha256 = bookSha256
        val previousSuccess = _uiState.value as? ReaderUiState.Success
        _uiState.value = ReaderUiState.Loading

        viewModelScope.launch {
            try {
                val book = bookRepository.getBookBySha256(bookSha256)
                if (book == null) {
                    _uiState.value = ReaderUiState.Error("Не удалось открыть книгу. Файл недоступен на устройстве.")
                    return@launch
                }

                val epubFile = File(book.filePath)
                val isReadable = withContext(ioDispatcher) {
                    epubFile.exists() && epubFile.canRead()
                }
                if (!isReadable) {
                    _uiState.value = ReaderUiState.Error("Не удалось открыть книгу. Файл недоступен на устройстве.")
                    return@launch
                }

                val realTotal = withContext(ioDispatcher) {
                    totalChapters ?: epubParserEngine.getChapterCount(epubFile).coerceAtLeast(1)
                }
                totalChaptersCount = realTotal

                val resolvedChapterIndex: Int
                val initialPosition: ReadingPosition?

                if (targetChapterIndex == null) {
                    val savedPos = restoreReadingPositionUseCase(bookSha256, emptyList())
                    resolvedChapterIndex = savedPos.chapterIndex.coerceIn(0, realTotal - 1)
                    initialPosition = savedPos
                } else {
                    resolvedChapterIndex = targetChapterIndex.coerceIn(0, realTotal - 1)
                    initialPosition = null
                }

                val chapter = withContext(ioDispatcher) {
                    epubParserEngine.readChapter(epubFile, resolvedChapterIndex)
                }

                val finalPosition: ReadingPosition
                val pageTarget: PageNavigationTarget

                if (initialPosition != null) {
                    finalPosition = restoreReadingPositionUseCase(bookSha256, listOf(chapter))
                    pageTarget = PageNavigationTarget.Position(
                        paragraphIndex = finalPosition.paragraphIndex,
                        charOffset = finalPosition.charOffset,
                        requestId = nextRequestId()
                    )
                } else {
                    val resolvedParagraph = targetParagraphIndex
                        ?: if (openAtLastPage) (chapter.paragraphs.lastOrNull()?.paragraphIndex ?: 0) else 0
                    val totalParagraphs = chapter.paragraphs.size.coerceAtLeast(1)
                    val paragraphFraction = (resolvedParagraph.toFloat() / totalParagraphs).coerceIn(0f, 1f)
                    val cfi = "/6/${(resolvedChapterIndex + 1) * 2}!/4/${(resolvedParagraph + 1) * 2}/1:$targetCharOffset"
                    val progress = (((resolvedChapterIndex + paragraphFraction) / realTotal) * 100f).coerceIn(0f, 100f)

                    finalPosition = ReadingPosition(
                        bookSha256 = bookSha256,
                        chapterIndex = resolvedChapterIndex,
                        epubCfi = cfi,
                        paragraphIndex = resolvedParagraph,
                        charOffset = targetCharOffset,
                        scrollProgressPercentage = progress
                    ).also { pos ->
                        saveReadingPositionUseCase(pos)
                    }

                    pageTarget = if (openAtLastPage) {
                        PageNavigationTarget.LastPageOfChapter(requestId = nextRequestId())
                    } else {
                        PageNavigationTarget.Position(
                            paragraphIndex = resolvedParagraph,
                            charOffset = targetCharOffset,
                            highlightText = highlightText,
                            requestId = nextRequestId()
                        )
                    }
                }

                val isSameBook = previousSuccess?.book?.sha256 == bookSha256
                val quotesForBook = if (latestQuotes.isNotEmpty() || !isSameBook) {
                    latestQuotes
                } else {
                    previousSuccess.savedQuotes
                }

                _uiState.value = ReaderUiState.Success(
                    book = book,
                    chapter = chapter,
                    currentPosition = finalPosition,
                    totalChapters = totalChaptersCount,
                    fontSp = previousSuccess?.fontSp ?: 16,
                    readerThemeMode = previousSuccess?.readerThemeMode ?: ReaderThemeMode.LIGHT,
                    isFirstChapter = resolvedChapterIndex == 0,
                    isLastChapter = resolvedChapterIndex >= totalChaptersCount - 1,
                    savedQuotes = quotesForBook,
                    pendingPageTarget = pageTarget
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = ReaderUiState.Error("Не удалось открыть книгу. Файл недоступен на устройстве.")
            }
        }
    }

    fun onScrollPositionChanged(
        paragraphIndex: Int,
        charOffset: Int,
        chapterFractionOverride: Float? = null
    ) {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val chapterIndex = currentState.chapter.chapterIndex
            val totalParagraphs = currentState.chapter.paragraphs.size.coerceAtLeast(1)
            val totalChapters = currentState.totalChapters.coerceAtLeast(1)

            val chapterFraction = chapterFractionOverride?.coerceIn(0f, 1f)
                ?: (paragraphIndex.toFloat() / totalParagraphs).coerceIn(0f, 1f)
            val bookProgress = (((chapterIndex + chapterFraction) / totalChapters) * 100f)
                .coerceIn(0f, 100f)

            val cfi = "/6/${(chapterIndex + 1) * 2}!/4/${(paragraphIndex + 1) * 2}/1:$charOffset"

            val updatedPosition = currentState.currentPosition.copy(
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
                charOffset = charOffset,
                epubCfi = cfi,
                scrollProgressPercentage = bookProgress
            )
            _uiState.value = currentState.copy(currentPosition = updatedPosition)

            scheduleSaveReadingPosition(updatedPosition)
        }
    }

    private fun scheduleSaveReadingPosition(position: ReadingPosition) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            saveReadingPositionUseCase(position)
        }
    }

    fun flushReadingPositionImmediately() {
        saveJob?.cancel()
        saveJob = null
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val position = currentState.currentPosition
            viewModelScope.launch(ioDispatcher) {
                saveReadingPositionUseCase(position)
            }
        }
    }

    fun onFontSizeChanged(newFontSp: Int) {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val clampedFontSp = newFontSp.coerceIn(12, 32)
            _uiState.value = currentState.copy(
                fontSp = clampedFontSp,
                pendingPageTarget = PageNavigationTarget.Position(
                    paragraphIndex = currentState.currentPosition.paragraphIndex,
                    charOffset = currentState.currentPosition.charOffset,
                    requestId = nextRequestId()
                )
            )
        }
    }

    fun onThemeModeChanged(newThemeMode: ReaderThemeMode) {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            _uiState.value = currentState.copy(readerThemeMode = newThemeMode)
        }
    }

    fun onNextChapter() {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success && !currentState.isLastChapter) {
            loadBook(
                bookSha256 = currentState.book.sha256,
                targetChapterIndex = currentState.chapter.chapterIndex + 1,
                totalChapters = totalChaptersCount
            )
        }
    }

    fun onPreviousChapter(openAtEnd: Boolean = false) {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success && !currentState.isFirstChapter) {
            loadBook(
                bookSha256 = currentState.book.sha256,
                targetChapterIndex = currentState.chapter.chapterIndex - 1,
                totalChapters = totalChaptersCount,
                openAtLastPage = openAtEnd
            )
        }
    }

    fun onTranslateSelection(selectedText: String) {
        val trimmed = selectedText.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.length > MAX_SELECTION_CHARS) {
            _events.trySend(ReaderUiEvent.ShowMessage(R.string.reader_selection_too_long))
            return
        }
        _events.trySend(ReaderUiEvent.RequestTranslation(trimmed))
    }

    fun onAiExplainSelection(
        selectedText: String,
        targetParagraphIndex: Int,
        viewportSnapshot: ViewportLayoutSnapshot
    ) {
        val currentState = _uiState.value as? ReaderUiState.Success ?: return
        val trimmed = selectedText.trim()
        if (trimmed.isEmpty()) return

        when (
            val extractionResult = epubContextExtractorUseCase.extractContext(
                selectedText = trimmed,
                targetParagraphIndex = targetParagraphIndex,
                targetSentenceIndex = 0,
                viewportSnapshot = viewportSnapshot
            )
        ) {
            is ExtractedContextResult.Success -> {
                val request = ExplainRequest(
                    bookId = currentState.book.sha256,
                    selectedText = extractionResult.selectedText,
                    currentSentence = extractionResult.currentSentence,
                    surroundingContext = SurroundingContext(
                        sentencesBefore = extractionResult.sentencesBefore,
                        sentencesAfter = extractionResult.sentencesAfter,
                        paragraph = extractionResult.paragraph,
                        chapterContext = extractionResult.chapterContext
                    ),
                    targetLanguage = "ru"
                )
                _events.trySend(ReaderUiEvent.RequestAiExplain(request))
            }
            is ExtractedContextResult.SelectionTooLongForAi,
            is ExtractedContextResult.SelectionTooLongForBoth -> {
                _events.trySend(ReaderUiEvent.ShowMessage(R.string.reader_selection_too_long))
            }
            is ExtractedContextResult.EmptySelection -> Unit
        }
    }

    fun onBookmarksSheetToggled(visible: Boolean) {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            _uiState.value = currentState.copy(isBookmarksSheetVisible = visible)
        }
    }

    fun onSaveQuote(
        quoteText: String,
        paragraphIndex: Int? = null,
        charOffset: Int = 0
    ) {
        val currentState = _uiState.value as? ReaderUiState.Success ?: return
        val trimmed = quoteText.trim()
        if (trimmed.isEmpty()) return

        val resolvedParagraph = paragraphIndex ?: currentState.currentPosition.paragraphIndex
        val quote = SavedQuote(
            bookSha256 = currentState.book.sha256,
            chapterIndex = currentState.chapter.chapterIndex,
            chapterTitle = currentState.chapter.title,
            paragraphIndex = resolvedParagraph,
            charOffset = charOffset,
            quoteText = trimmed,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            quoteRepository.saveQuote(quote)
            _events.trySend(ReaderUiEvent.ShowMessage(R.string.reader_quote_saved))
        }
    }

    fun onDeleteQuote(quoteId: Long) {
        viewModelScope.launch {
            quoteRepository.deleteQuote(quoteId)
        }
    }

    fun onJumpToQuote(quote: SavedQuote) {
        val currentState = _uiState.value as? ReaderUiState.Success ?: return
        if (quote.chapterIndex == currentState.chapter.chapterIndex) {
            val totalParagraphs = currentState.chapter.paragraphs.size.coerceAtLeast(1)
            val totalChapters = currentState.totalChapters.coerceAtLeast(1)
            val paragraphFraction = (quote.paragraphIndex.toFloat() / totalParagraphs).coerceIn(0f, 1f)
            val bookProgress = (((quote.chapterIndex + paragraphFraction) / totalChapters) * 100f)
                .coerceIn(0f, 100f)
            val cfi = "/6/${(quote.chapterIndex + 1) * 2}!/4/${(quote.paragraphIndex + 1) * 2}/1:${quote.charOffset}"

            val updatedPosition = currentState.currentPosition.copy(
                chapterIndex = quote.chapterIndex,
                paragraphIndex = quote.paragraphIndex,
                charOffset = quote.charOffset,
                epubCfi = cfi,
                scrollProgressPercentage = bookProgress
            )
            _uiState.value = currentState.copy(
                currentPosition = updatedPosition,
                isBookmarksSheetVisible = false,
                pendingPageTarget = PageNavigationTarget.Position(
                    paragraphIndex = quote.paragraphIndex,
                    charOffset = quote.charOffset,
                    highlightText = quote.quoteText,
                    requestId = nextRequestId()
                )
            )
            scheduleSaveReadingPosition(updatedPosition)
        } else {
            loadBook(
                bookSha256 = currentState.book.sha256,
                targetChapterIndex = quote.chapterIndex,
                totalChapters = totalChaptersCount,
                targetParagraphIndex = quote.paragraphIndex,
                targetCharOffset = quote.charOffset,
                highlightText = quote.quoteText
            )
        }
    }

    fun onPageTargetConsumed() {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success && currentState.pendingPageTarget != null) {
            _uiState.value = currentState.copy(pendingPageTarget = null)
        }
    }

    companion object {
        const val MAX_SELECTION_CHARS = 1000
    }
}
