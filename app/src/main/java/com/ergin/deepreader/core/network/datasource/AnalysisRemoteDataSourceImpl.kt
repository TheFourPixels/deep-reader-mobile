package com.ergin.deepreader.core.network.datasource

import com.ergin.deepreader.core.network.DeepReaderApi
import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto
import com.ergin.deepreader.core.network.util.NetworkResult
import com.ergin.deepreader.core.network.util.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class AnalysisRemoteDataSourceImpl @Inject constructor(
    private val api: DeepReaderApi,
    private val json: Json
) : AnalysisRemoteDataSource {

    override suspend fun explainFragment(request: ExplainRequestDto): NetworkResult<ExplainResponseDto> {
        return safeApiCall(action = "EXPLAIN", json = json) {
            api.explainFragment(request)
        }
    }

    override suspend fun translateText(request: TranslateRequestDto): NetworkResult<TranslateResponseDto> {
        return safeApiCall(action = "TRANSLATE", json = json) {
            api.translateText(request)
        }
    }
}
