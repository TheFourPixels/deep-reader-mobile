package com.ergin.deepreader.domain.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface ExtractedContextResult {
    @Immutable
    data class Success(
        val selectedText: String,
        val currentSentence: String,
        val sentencesBefore: List<String>,
        val sentencesAfter: List<String>,
        val paragraph: String?,
        val chapterContext: String?,
        val isViewportTruncated: Boolean = false
    ) : ExtractedContextResult

    data class SelectionTooLongForAi(val selectedLength: Int) : ExtractedContextResult
    data class SelectionTooLongForBoth(val selectedLength: Int) : ExtractedContextResult
    object EmptySelection : ExtractedContextResult
}
