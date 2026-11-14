package com.ergin.deepreader.feature.analysis

import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto

sealed interface AnalysisUiState {
    object Idle : AnalysisUiState
    object Loading : AnalysisUiState
    data class SuccessExplain(val response: ExplainResponseDto) : AnalysisUiState
    data class SuccessTranslate(val response: TranslateResponseDto) : AnalysisUiState
    data class Error(val errorCode: String, val message: String, val traceId: String?) : AnalysisUiState
    data class Offline(val action: String, val message: String) : AnalysisUiState
}