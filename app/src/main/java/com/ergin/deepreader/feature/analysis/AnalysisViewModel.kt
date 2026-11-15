package com.ergin.deepreader.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.usecase.ExplainFragmentUseCase
import com.ergin.deepreader.domain.usecase.TranslateTextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val explainFragmentUseCase: ExplainFragmentUseCase,
    private val translateTextUseCase: TranslateTextUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var lastSuccessfulState: AnalysisUiState? = null
    private var isLoading = false

    fun explain(request: ExplainRequestDto, isOnline: Boolean) {
        if (isLoading) return
        isLoading = true
        _uiState.value = AnalysisUiState.Loading

        viewModelScope.launch {
            try {
                val result = explainFragmentUseCase(request, isOnline)
                val newState = when (result) {
                    is AnalysisResult.ExplainSuccess -> {
                        val state = AnalysisUiState.SuccessExplain(result.response)
                        lastSuccessfulState = state
                        state
                    }
                    is AnalysisResult.Error -> AnalysisUiState.Error(result.errorCode, result.message, result.traceId)
                    is AnalysisResult.Offline -> AnalysisUiState.Offline(result.action, result.message)
                    else -> AnalysisUiState.Error("INTERNAL_ERROR", "Неизвестная ошибка", null)
                }
                _uiState.value = newState
            } finally {
                isLoading = false
            }
        }
    }

    fun translate(request: TranslateRequestDto, isOnline: Boolean) {
        if (isLoading) return
        isLoading = true
        _uiState.value = AnalysisUiState.Loading

        viewModelScope.launch {
            try {
                val result = translateTextUseCase(request, isOnline)
                val newState = when (result) {
                    is AnalysisResult.TranslateSuccess -> {
                        val state = AnalysisUiState.SuccessTranslate(result.response)
                        lastSuccessfulState = state
                        state
                    }
                    is AnalysisResult.Error -> AnalysisUiState.Error(result.errorCode, result.message, result.traceId)
                    is AnalysisResult.Offline -> AnalysisUiState.Offline(result.action, result.message)
                    else -> AnalysisUiState.Error("INTERNAL_ERROR", "Неизвестная ошибка", null)
                }
                _uiState.value = newState
            } finally {
                isLoading = false
            }
        }
    }

    fun onDismissSheet() {
        _uiState.value = AnalysisUiState.Idle
    }

    fun onReopenLastResult() {
        lastSuccessfulState?.let {
            _uiState.value = it
        }
    }
}