package com.ergin.deepreader.core.network.mapper

import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.SurroundingContextDto
import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto
import com.ergin.deepreader.domain.model.ExplainAnalysis
import com.ergin.deepreader.domain.model.ExplainRequest
import com.ergin.deepreader.domain.model.SurroundingContext
import com.ergin.deepreader.domain.model.TranslateRequest
import com.ergin.deepreader.domain.model.TranslationResult

fun SurroundingContext.toDto(): SurroundingContextDto = SurroundingContextDto(
    sentencesBefore = sentencesBefore,
    sentencesAfter = sentencesAfter,
    paragraph = paragraph,
    chapterContext = chapterContext
)

fun ExplainRequest.toDto(): ExplainRequestDto = ExplainRequestDto(
    bookId = bookId,
    selectedText = selectedText,
    currentSentence = currentSentence,
    surroundingContext = surroundingContext.toDto(),
    targetLanguage = targetLanguage
)

fun ExplainResponseDto.toDomain(cachedOverride: Boolean = cached): ExplainAnalysis = ExplainAnalysis(
    selectedText = selectedText,
    contextualMeaning = contextualMeaning,
    phenomenonType = phenomenonType,
    explanation = explanation,
    stylisticColoring = stylisticColoring,
    spoilerAlert = spoilerAlert,
    cached = cachedOverride,
    processingTimeMs = processingTimeMs
)

fun TranslateRequest.toDto(): TranslateRequestDto = TranslateRequestDto(
    text = text,
    sourceLanguage = sourceLanguage,
    targetLanguage = targetLanguage
)

fun TranslateResponseDto.toDomain(cachedOverride: Boolean = cached): TranslationResult = TranslationResult(
    translatedText = translatedText,
    sourceLanguage = sourceLanguage,
    targetLanguage = targetLanguage,
    cached = cachedOverride
)
