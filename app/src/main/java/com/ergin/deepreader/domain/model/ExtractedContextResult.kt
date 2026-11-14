package com.ergin.deepreader.domain.model

sealed interface ExtractedContextResult {
    data class Success(
        val selectedText: String,
        val currentSentence: String,
        val sentencesBefore: List<String>,
        val sentencesAfter: List<String>,
        val paragraph: String?,
        val chapterContext: String?
    ) : ExtractedContextResult

    data class SelectionTooLongForAi(val selectedLength: Int) : ExtractedContextResult
    data class SelectionTooLongForBoth(val selectedLength: Int) : ExtractedContextResult
    object EmptySelection : ExtractedContextResult
}