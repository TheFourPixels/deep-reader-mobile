package com.ergin.deepreader.domain.model

import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto

sealed interface AnalysisResult {
    data class ExplainSuccess(val response: ExplainResponseDto) : AnalysisResult
    data class TranslateSuccess(val response: TranslateResponseDto) : AnalysisResult
    data class Error(val errorCode: String, val message: String, val traceId: String?) : AnalysisResult
    data class Offline(val action: String, val message: String) : AnalysisResult
}