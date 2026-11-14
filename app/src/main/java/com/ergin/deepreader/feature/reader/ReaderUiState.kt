package com.ergin.deepreader.feature.reader

import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ReadingPosition
import com.ergin.deepreader.ui.theme.ReaderThemeMode

sealed interface ReaderUiState {
    object Loading : ReaderUiState
    data class Success(
        val book: Book,
        val chapter: ChapterContent,
        val currentPosition: ReadingPosition,
        val fontSp: Int = 16,
        val readerThemeMode: ReaderThemeMode = ReaderThemeMode.LIGHT,
        val isFirstChapter: Boolean = false,
        val isLastChapter: Boolean = false
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}