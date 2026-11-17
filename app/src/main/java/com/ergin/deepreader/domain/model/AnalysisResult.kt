package com.ergin.deepreader.domain.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface AnalysisResult {
    data class ExplainSuccess(val data: ExplainAnalysis) : AnalysisResult
    data class TranslateSuccess(val data: TranslationResult) : AnalysisResult
    data class Failure(val error: DomainError) : AnalysisResult
}
