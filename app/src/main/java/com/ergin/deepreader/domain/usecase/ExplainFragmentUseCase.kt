package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.model.ExplainRequest
import com.ergin.deepreader.domain.repository.AnalysisRepository
import javax.inject.Inject

class ExplainFragmentUseCase @Inject constructor(
    private val repository: AnalysisRepository
) {
    suspend operator fun invoke(request: ExplainRequest, isOnline: Boolean): AnalysisResult {
        return repository.explainFragment(request, isOnline)
    }
}
