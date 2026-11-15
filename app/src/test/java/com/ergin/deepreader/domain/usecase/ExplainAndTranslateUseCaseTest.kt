package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.SurroundingContextDto
import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto
import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.repository.AnalysisRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExplainAndTranslateUseCaseTest {

    private lateinit var analysisRepository: AnalysisRepository
    private lateinit var explainFragmentUseCase: ExplainFragmentUseCase
    private lateinit var translateTextUseCase: TranslateTextUseCase

    @Before
    fun setUp() {
        analysisRepository = mockk()
        explainFragmentUseCase = ExplainFragmentUseCase(analysisRepository)
        translateTextUseCase = TranslateTextUseCase(analysisRepository)
    }

    @Test
    fun explain_whenOfflineAndPresentInRoomCache_returnsCachedSuccessImmediately() = runTest {
        val request = createSampleExplainRequest()
        val cachedResponse = ExplainResponseDto(
            selectedText = request.selectedText,
            contextualMeaning = "Meaning",
            phenomenonType = "metaphor",
            explanation = "Explanation",
            stylisticColoring = "literary",
            spoilerAlert = false,
            cached = true,
            processingTimeMs = 5
        )
        coEvery { analysisRepository.explainFragment(request, isOnline = false) } returns AnalysisResult.ExplainSuccess(cachedResponse)

        val result = explainFragmentUseCase(request, isOnline = false)

        assertTrue(result is AnalysisResult.ExplainSuccess)
        val success = result as AnalysisResult.ExplainSuccess
        assertTrue(success.response.cached)
    }

    @Test
    fun explain_whenOfflineAndNotInCache_returnsOfflineStateWithActionSpecificMessage() = runTest {
        val request = createSampleExplainRequest()
        val offlineResult = AnalysisResult.Offline("EXPLAIN", "Нет подключения к интернету. AI-разбор сейчас недоступен.")
        coEvery { analysisRepository.explainFragment(request, isOnline = false) } returns offlineResult

        val result = explainFragmentUseCase(request, isOnline = false)

        assertTrue(result is AnalysisResult.Offline)
        assertEquals("EXPLAIN", (result as AnalysisResult.Offline).action)
        assertTrue((result as AnalysisResult.Offline).message.contains("AI-разбор сейчас недоступен"))
    }

    @Test
    fun explain_whenOnlineAnd200Ok_returnsSuccess() = runTest {
        val request = createSampleExplainRequest()
        val freshResponse = ExplainResponseDto(
            selectedText = request.selectedText,
            contextualMeaning = "Fresh Meaning",
            phenomenonType = "idiom",
            explanation = "Fresh Explanation",
            stylisticColoring = "informal",
            spoilerAlert = false,
            cached = false,
            processingTimeMs = 1200
        )
        coEvery { analysisRepository.explainFragment(request, isOnline = true) } returns AnalysisResult.ExplainSuccess(freshResponse)

        val result = explainFragmentUseCase(request, isOnline = true)

        assertTrue(result is AnalysisResult.ExplainSuccess)
        assertEquals("Fresh Meaning", (result as AnalysisResult.ExplainSuccess).response.contextualMeaning)
    }

    @Test
    fun translate_whenOfflineAndNotInCache_returnsOfflineStateWithTranslationSpecificMessage() = runTest {
        val request = TranslateRequestDto(text = "hello", targetLanguage = "ru")
        val offlineResult = AnalysisResult.Offline("TRANSLATE", "Нет подключения к интернету. Перевод сейчас недоступен.")
        coEvery { analysisRepository.translateText(request, isOnline = false) } returns offlineResult

        val result = translateTextUseCase(request, isOnline = false)

        assertTrue(result is AnalysisResult.Offline)
        assertEquals("TRANSLATE", (result as AnalysisResult.Offline).action)
        assertTrue((result as AnalysisResult.Offline).message.contains("Перевод сейчас недоступен"))
    }

    private fun createSampleExplainRequest() = ExplainRequestDto(
        bookId = "sha123",
        selectedText = "clocks were striking thirteen",
        currentSentence = "It was a bright cold day in April, and the clocks were striking thirteen.",
        surroundingContext = SurroundingContextDto(
            sentencesBefore = listOf("Sentence before."),
            sentencesAfter = listOf("Sentence after.")
        )
    )
}