package com.ergin.deepreader.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class SurroundingContext(
    val sentencesBefore: List<String> = emptyList(),
    val sentencesAfter: List<String> = emptyList(),
    val paragraph: String? = null,
    val chapterContext: String? = null
)

@Immutable
data class ExplainRequest(
    val bookId: String,
    val selectedText: String,
    val currentSentence: String,
    val surroundingContext: SurroundingContext,
    val targetLanguage: String = "ru"
)

@Immutable
data class ExplainAnalysis(
    val selectedText: String,
    val contextualMeaning: String,
    val phenomenonType: String,
    val explanation: String,
    val stylisticColoring: String,
    val spoilerAlert: Boolean,
    val cached: Boolean,
    val processingTimeMs: Long
)

@Immutable
data class TranslateRequest(
    val text: String,
    val sourceLanguage: String? = null,
    val targetLanguage: String = "ru"
)

@Immutable
data class TranslationResult(
    val translatedText: String,
    val sourceLanguage: String? = null,
    val targetLanguage: String,
    val cached: Boolean
)
