package com.ergin.deepreader.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SurroundingContextDto(
    @SerialName("sentences_before") val sentencesBefore: List<String> = emptyList(),
    @SerialName("sentences_after") val sentencesAfter: List<String> = emptyList(),
    @SerialName("paragraph") val paragraph: String? = null,
    @SerialName("chapter_context") val chapterContext: String? = null
)

@Serializable
data class ExplainRequestDto(
    @SerialName("book_id") val bookId: String,
    @SerialName("selected_text") val selectedText: String,
    @SerialName("current_sentence") val currentSentence: String,
    @SerialName("surrounding_context") val surroundingContext: SurroundingContextDto,
    @SerialName("target_language") val targetLanguage: String = "ru"
)

@Serializable
data class ExplainResponseDto(
    @SerialName("selected_text") val selectedText: String,
    @SerialName("contextual_meaning") val contextualMeaning: String,
    @SerialName("phenomenon_type") val phenomenonType: String,
    @SerialName("explanation") val explanation: String,
    @SerialName("stylistic_coloring") val stylisticColoring: String,
    @SerialName("spoiler_alert") val spoilerAlert: Boolean,
    @SerialName("cached") val cached: Boolean,
    @SerialName("processing_time_ms") val processingTimeMs: Long
)

@Serializable
data class TranslateRequestDto(
    @SerialName("text") val text: String,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String = "ru"
)

@Serializable
data class TranslateResponseDto(
    @SerialName("translated_text") val translatedText: String,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String,
    @SerialName("cached") val cached: Boolean
)

@Serializable
data class ErrorDetailDto(
    @SerialName("field") val field: String,
    @SerialName("issue") val issue: String
)

@Serializable
data class ErrorResponseDto(
    @SerialName("error_code") val errorCode: String,
    @SerialName("message") val message: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("trace_id") val traceId: String,
    @SerialName("details") val details: List<ErrorDetailDto>? = null
)

@Serializable
data class HealthResponseDto(
    @SerialName("status") val status: String,
    @SerialName("service") val service: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("cache") val cache: String? = null,
    @SerialName("active_llm_provider") val activeLlmProvider: String? = null
)