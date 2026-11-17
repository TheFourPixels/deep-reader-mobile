package com.ergin.deepreader.core.network.datasource

import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto
import com.ergin.deepreader.core.network.util.NetworkResult

interface AnalysisRemoteDataSource {
    suspend fun explainFragment(request: ExplainRequestDto): NetworkResult<ExplainResponseDto>
    suspend fun translateText(request: TranslateRequestDto): NetworkResult<TranslateResponseDto>
}
