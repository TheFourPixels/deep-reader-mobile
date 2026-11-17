package com.ergin.deepreader.feature.reader

import androidx.compose.runtime.Immutable
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ReadingPosition
import com.ergin.deepreader.domain.model.SavedQuote
import com.ergin.deepreader.ui.theme.ReaderThemeMode

@Immutable
sealed interface PageNavigationTarget {
    val requestId: Long

    @Immutable
    data class Position(
        val paragraphIndex: Int,
        val charOffset: Int = 0,
        val highlightText: String? = null,
        override val requestId: Long = 0L
    ) : PageNavigationTarget

    @Immutable
    data class LastPageOfChapter(
        override val requestId: Long = 0L
    ) : PageNavigationTarget
}

@Immutable
sealed interface ReaderUiState {
    object Loading : ReaderUiState

    @Immutable
    data class Success(
        val book: Book,
        val chapter: ChapterContent,
        val currentPosition: ReadingPosition,
        val totalChapters: Int = 1,
        val fontSp: Int = 16,
        val readerThemeMode: ReaderThemeMode = ReaderThemeMode.LIGHT,
        val isFirstChapter: Boolean = false,
        val isLastChapter: Boolean = false,
        val savedQuotes: List<SavedQuote> = emptyList(),
        val isBookmarksSheetVisible: Boolean = false,
        val pendingPageTarget: PageNavigationTarget? = null
    ) : ReaderUiState

    data class Error(val message: String) : ReaderUiState
}
