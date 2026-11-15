package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.repository.AnalysisRepository
import javax.inject.Inject

class ExplainFragmentUseCase @Inject constructor(
    private val repository: AnalysisRepository
) {
    suspend operator fun invoke(request: ExplainRequestDto, isOnline: Boolean): AnalysisResult {
        return repository.explainFragment(request, isOnline)
    }
}