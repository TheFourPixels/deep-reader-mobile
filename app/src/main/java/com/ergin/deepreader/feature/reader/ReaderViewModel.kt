package com.ergin.deepreader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergin.deepreader.domain.model.ReadingPosition
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.usecase.RestoreReadingPositionUseCase
import com.ergin.deepreader.domain.usecase.SaveReadingPositionUseCase
import com.ergin.deepreader.ui.theme.ReaderThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val restoreReadingPositionUseCase: RestoreReadingPositionUseCase,
    private val saveReadingPositionUseCase: SaveReadingPositionUseCase,
    private val epubParserEngine: EpubParserEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBookSha256: String? = null
    private var totalChaptersCount: Int = 1

    fun loadBook(bookSha256: String, initialChapterIndex: Int = 0, totalChapters: Int = 1) {
        currentBookSha256 = bookSha256
        totalChaptersCount = totalChapters.coerceAtLeast(1)
        _uiState.value = ReaderUiState.Loading

        viewModelScope.launch {
            val book = bookRepository.getBookBySha256(bookSha256)
            if (book == null) {
                _uiState.value = ReaderUiState.Error("Книга не найдена")
                return@launch
            }

            val mockChapterXhtml = """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <body>
                    <h1>Chapter ${initialChapterIndex + 1}</h1>
                    <p id="p1">It was a bright cold day in April, and the clocks were striking thirteen.</p>
                    <p id="p2">Winston Smith, his chin nuzzled into his breast in an effort to escape the vile wind, slipped quickly through the glass doors of Victory Mansions.</p>
                  </body>
                </html>
            """.trimIndent()

            val chapter = epubParserEngine.parseChapterContent(mockChapterXhtml, initialChapterIndex)
            val restoredPosition = restoreReadingPositionUseCase(bookSha256, listOf(chapter))

            _uiState.value = ReaderUiState.Success(
                book = book,
                chapter = chapter,
                currentPosition = restoredPosition,
                fontSp = 16,
                readerThemeMode = ReaderThemeMode.LIGHT,
                isFirstChapter = initialChapterIndex == 0,
                isLastChapter = initialChapterIndex >= totalChaptersCount - 1
            )
        }
    }

    fun onScrollPositionChanged(paragraphIndex: Int, charOffset: Int) {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val updatedPosition = currentState.currentPosition.copy(
                paragraphIndex = paragraphIndex,
                charOffset = charOffset
            )
            _uiState.value = currentState.copy(currentPosition = updatedPosition)

            viewModelScope.launch {
                saveReadingPositionUseCase(updatedPosition)
            }
        }
    }

    fun onFontSizeChanged(newFontSp: Int) {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val clampedFontSp = newFontSp.coerceIn(12, 32)
            _uiState.value = currentState.copy(fontSp = clampedFontSp)
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
            loadBook(currentState.book.sha256, currentState.chapter.chapterIndex + 1, totalChaptersCount)
        }
    }

    fun onPreviousChapter() {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success && !currentState.isFirstChapter) {
            loadBook(currentState.book.sha256, currentState.chapter.chapterIndex - 1, totalChaptersCount)
        }
    }
}