package com.ergin.deepreader.core.network.datasource

import com.ergin.deepreader.core.network.DeepReaderApi
import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.SurroundingContextDto
import com.ergin.deepreader.core.network.util.NetworkResult
import com.ergin.deepreader.domain.model.DomainError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class AnalysisRemoteDataSourceImplTest {

    private val api: DeepReaderApi = mockk()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var dataSource: AnalysisRemoteDataSourceImpl

    private val request = ExplainRequestDto(
        bookId = "sha1",
        selectedText = "word",
        currentSentence = "A word here.",
        surroundingContext = SurroundingContextDto()
    )

    @Before
    fun setUp() {
        dataSource = AnalysisRemoteDataSourceImpl(api, json)
    }

    @Test
    fun explainFragment_httpErrorWithJsonBody_parsesServerError() = runTest {
        val errorJson = """{"error_code":"LLM_PROVIDER_ERROR","message":"Upstream busy","timestamp":"2026-01-01T00:00:00Z","trace_id":"trace-42"}"""
        val body = errorJson.toResponseBody("application/json".toMediaType())
        coEvery { api.explainFragment(request) } returns Response.error(502, body)

        val result = dataSource.explainFragment(request)

        assertTrue(result is NetworkResult.Error)
        val error = (result as NetworkResult.Error).error
        assertTrue(error is DomainError.ServerError)
        val serverError = error as DomainError.ServerError
        assertEquals("LLM_PROVIDER_ERROR", serverError.errorCode)
        assertEquals("Upstream busy", serverError.serverMessage)
        assertEquals("trace-42", serverError.traceId)
    }

    @Test
    fun explainFragment_httpErrorWithoutOptionalTimestamp_stillParsesServerError() = runTest {
        val errorJson = """{"error_code":"RATE_LIMIT","message":"Too many requests"}"""
        val body = errorJson.toResponseBody("application/json".toMediaType())
        coEvery { api.explainFragment(request) } returns Response.error(429, body)

        val result = dataSource.explainFragment(request)

        assertTrue(result is NetworkResult.Error)
        val error = (result as NetworkResult.Error).error
        assertTrue(error is DomainError.ServerError)
        val serverError = error as DomainError.ServerError
        assertEquals("RATE_LIMIT", serverError.errorCode)
        assertEquals("Too many requests", serverError.serverMessage)
        assertEquals(null, serverError.traceId)
    }

    @Test
    fun explainFragment_ioException_returnsNetworkDomainError() = runTest {
        coEvery { api.explainFragment(request) } throws IOException("timeout")

        val result = dataSource.explainFragment(request)

        assertTrue(result is NetworkResult.Error)
        val error = (result as NetworkResult.Error).error
        assertEquals(DomainError.Network("EXPLAIN"), error)
    }
}
