package com.ergin.deepreader.feature.analysis

import androidx.compose.runtime.Immutable
import com.ergin.deepreader.domain.model.DomainError
import com.ergin.deepreader.domain.model.ExplainAnalysis
import com.ergin.deepreader.domain.model.TranslationResult

@Immutable
data class AnalysisUiState(
    val sheetState: AnalysisSheetState = AnalysisSheetState.Idle,
    val hasLastSuccessfulResult: Boolean = false
)

@Immutable
sealed interface AnalysisSheetState {
    object Idle : AnalysisSheetState
    object Loading : AnalysisSheetState
    data class SuccessExplain(val analysis: ExplainAnalysis) : AnalysisSheetState
    data class SuccessTranslate(val translation: TranslationResult) : AnalysisSheetState
    data class Error(val error: DomainError) : AnalysisSheetState
    data class Offline(val action: String) : AnalysisSheetState
}
