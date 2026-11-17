package com.ergin.deepreader.core.network

import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.HealthResponseDto
import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface DeepReaderApi {

    @POST("/api/v1/explain")
    suspend fun explainFragment(
        @Body request: ExplainRequestDto
    ): Response<ExplainResponseDto>

    @POST("/api/v1/translate")
    suspend fun translateText(
        @Body request: TranslateRequestDto
    ): Response<TranslateResponseDto>

    @GET("/api/v1/health")
    suspend fun checkHealth(): Response<HealthResponseDto>
}