package com.ergin.deepreader.core.network.repository

import com.ergin.deepreader.core.database.dao.LocalAiCacheDao
import com.ergin.deepreader.core.database.entity.AiCacheEntity
import com.ergin.deepreader.core.network.DeepReaderApi
import com.ergin.deepreader.core.network.datasource.AnalysisRemoteDataSourceImpl
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto
import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.model.DomainError
import com.ergin.deepreader.domain.model.ExplainRequest
import com.ergin.deepreader.domain.model.SurroundingContext
import com.ergin.deepreader.domain.model.TranslateRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisRepositoryImplTest {

    private val api: DeepReaderApi = mockk(relaxed = true)
    private val localAiCacheDao: LocalAiCacheDao = mockk(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: AnalysisRepositoryImpl

    private val explainRequest = ExplainRequest(
        bookId = "sha123",
        selectedText = "selected",
        currentSentence = "Target sentence with selected text.",
        surroundingContext = SurroundingContext(paragraph = "Paragraph text"),
        targetLanguage = "ru"
    )

    private val translateRequest = TranslateRequest(
        text = "Hello world",
        targetLanguage = "ru"
    )

    @Before
    fun setUp() {
        val remoteDataSource = AnalysisRemoteDataSourceImpl(api, json)
        repository = AnalysisRepositoryImpl(remoteDataSource, localAiCacheDao, testDispatcher, json)
    }

    @Test
    fun explainFragment_cacheHit_returnsCachedResultInstantly() = runTest(testDispatcher) {
        val cachedJson = """{"selected_text":"selected","contextual_meaning":"Meaning","phenomenon_type":"metaphor","explanation":"Explanation","stylistic_coloring":"literary","spoiler_alert":false,"cached":true,"processing_time_ms":5}"""
        coEvery { localAiCacheDao.getCache(any()) } returns AiCacheEntity("key", "EXPLAIN", cachedJson)

        val result = repository.explainFragment(explainRequest, isOnline = false)

        assertTrue(result is AnalysisResult.ExplainSuccess)
        val success = result as AnalysisResult.ExplainSuccess
        assertTrue(success.data.cached)
        assertEquals("Meaning", success.data.contextualMeaning)
        coVerify(exactly = 0) { api.explainFragment(any()) }
    }

    @Test
    fun explainFragment_offlineAndCacheMiss_returnsOfflineDomainError() = runTest(testDispatcher) {
        coEvery { localAiCacheDao.getCache(any()) } returns null

        val result = repository.explainFragment(explainRequest, isOnline = false)

        assertTrue(result is AnalysisResult.Failure)
        val error = (result as AnalysisResult.Failure).error
        assertTrue(error is DomainError.Offline)
        assertEquals("EXPLAIN", (error as DomainError.Offline).action)
        coVerify(exactly = 0) { api.explainFragment(any()) }
    }

    @Test
    fun explainFragment_emptyResponseBody_returnsEmptyResponseErrorWithoutCaching() = runTest(testDispatcher) {
        coEvery { localAiCacheDao.getCache(any()) } returns null
        val emptyDto = ExplainResponseDto(
            selectedText = "selected",
            contextualMeaning = "",
            phenomenonType = "metaphor",
            explanation = "",
            stylisticColoring = "literary",
            spoilerAlert = false,
            cached = false,
            processingTimeMs = 10L
        )
        coEvery { api.explainFragment(any()) } returns Response.success(emptyDto)

        val result = repository.explainFragment(explainRequest, isOnline = true)

        assertTrue(result is AnalysisResult.Failure)
        val error = (result as AnalysisResult.Failure).error
        assertTrue(error is DomainError.EmptyResponse)
        assertEquals("EXPLAIN", (error as DomainError.EmptyResponse).action)
        coVerify(exactly = 0) { localAiCacheDao.saveCache(any()) }
    }

    @Test
    fun explainFragment_networkException_returnsNetworkDomainError() = runTest(testDispatcher) {
        coEvery { localAiCacheDao.getCache(any()) } returns null
        coEvery { api.explainFragment(any()) } throws IOException("Failed to connect to http://10.0.2.2:8080")

        val result = repository.explainFragment(explainRequest, isOnline = true)

        assertTrue(result is AnalysisResult.Failure)
        val error = (result as AnalysisResult.Failure).error
        assertTrue(error is DomainError.Network)
        assertEquals("EXPLAIN", (error as DomainError.Network).action)
    }

    @Test
    fun translateText_emptyResponseBody_returnsEmptyResponseErrorWithoutCaching() = runTest(testDispatcher) {
        coEvery { localAiCacheDao.getCache(any()) } returns null
        val emptyTranslateDto = TranslateResponseDto(
            translatedText = "",
            sourceLanguage = "en",
            targetLanguage = "ru",
            cached = false
        )
        coEvery { api.translateText(any()) } returns Response.success(emptyTranslateDto)

        val result = repository.translateText(translateRequest, isOnline = true)

        assertTrue(result is AnalysisResult.Failure)
        val error = (result as AnalysisResult.Failure).error
        assertTrue(error is DomainError.EmptyResponse)
        assertEquals("TRANSLATE", (error as DomainError.EmptyResponse).action)
        coVerify(exactly = 0) { localAiCacheDao.saveCache(any()) }
    }
}
