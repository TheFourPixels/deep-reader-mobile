package com.ergin.deepreader.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergin.deepreader.core.network.NetworkConnectivityObserver
import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.model.DomainError
import com.ergin.deepreader.domain.model.ExplainRequest
import com.ergin.deepreader.domain.model.TranslateRequest
import com.ergin.deepreader.domain.usecase.ExplainFragmentUseCase
import com.ergin.deepreader.domain.usecase.TranslateTextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val explainFragmentUseCase: ExplainFragmentUseCase,
    private val translateTextUseCase: TranslateTextUseCase,
    private val networkObserver: NetworkConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var lastSuccessfulSheetState: AnalysisSheetState? = null
    private var lastExplainRequest: ExplainRequest? = null
    private var lastTranslateRequest: TranslateRequest? = null
    private var lastRequestType: String? = null
    private var isLoading = false

    fun explain(request: ExplainRequest, isOnline: Boolean = networkObserver.isCurrentlyOnline()) {
        if (isLoading) return
        isLoading = true
        lastExplainRequest = request
        lastRequestType = REQUEST_EXPLAIN
        _uiState.update { it.copy(sheetState = AnalysisSheetState.Loading) }

        viewModelScope.launch {
            try {
                when (val result = explainFragmentUseCase(request, isOnline)) {
                    is AnalysisResult.ExplainSuccess -> {
                        val sheet = AnalysisSheetState.SuccessExplain(result.data)
                        lastSuccessfulSheetState = sheet
                        _uiState.update { it.copy(sheetState = sheet, hasLastSuccessfulResult = true) }
                    }
                    is AnalysisResult.Failure -> {
                        _uiState.update { it.copy(sheetState = mapErrorToSheetState(result.error)) }
                    }
                    else -> {
                        _uiState.update { it.copy(sheetState = AnalysisSheetState.Error(DomainError.Unknown())) }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(sheetState = AnalysisSheetState.Error(DomainError.Unknown())) }
            } finally {
                isLoading = false
            }
        }
    }

    fun translate(request: TranslateRequest, isOnline: Boolean = networkObserver.isCurrentlyOnline()) {
        if (isLoading) return
        isLoading = true
        lastTranslateRequest = request
        lastRequestType = REQUEST_TRANSLATE
        _uiState.update { it.copy(sheetState = AnalysisSheetState.Loading) }

        viewModelScope.launch {
            try {
                when (val result = translateTextUseCase(request, isOnline)) {
                    is AnalysisResult.TranslateSuccess -> {
                        val sheet = AnalysisSheetState.SuccessTranslate(result.data)
                        lastSuccessfulSheetState = sheet
                        _uiState.update { it.copy(sheetState = sheet, hasLastSuccessfulResult = true) }
                    }
                    is AnalysisResult.Failure -> {
                        _uiState.update { it.copy(sheetState = mapErrorToSheetState(result.error)) }
                    }
                    else -> {
                        _uiState.update { it.copy(sheetState = AnalysisSheetState.Error(DomainError.Unknown())) }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(sheetState = AnalysisSheetState.Error(DomainError.Unknown())) }
            } finally {
                isLoading = false
            }
        }
    }

    fun retryLastRequest() {
        val online = networkObserver.isCurrentlyOnline()
        when (lastRequestType) {
            REQUEST_EXPLAIN -> lastExplainRequest?.let { explain(it, online) }
            REQUEST_TRANSLATE -> lastTranslateRequest?.let { translate(it, online) }
        }
    }

    fun fallbackToTranslate() {
        val explainRequest = lastExplainRequest ?: return
        translate(
            request = TranslateRequest(
                text = explainRequest.selectedText,
                targetLanguage = explainRequest.targetLanguage
            )
        )
    }

    fun onDismissSheet() {
        _uiState.update { it.copy(sheetState = AnalysisSheetState.Idle) }
    }

    fun onReopenLastResult() {
        lastSuccessfulSheetState?.let { cachedSheet ->
            _uiState.update { it.copy(sheetState = cachedSheet) }
        }
    }

    private fun mapErrorToSheetState(error: DomainError): AnalysisSheetState {
        return when (error) {
            is DomainError.Offline -> AnalysisSheetState.Offline(error.action)
            else -> AnalysisSheetState.Error(error)
        }
    }

    companion object {
        private const val REQUEST_EXPLAIN = "EXPLAIN"
        private const val REQUEST_TRANSLATE = "TRANSLATE"
    }
}
