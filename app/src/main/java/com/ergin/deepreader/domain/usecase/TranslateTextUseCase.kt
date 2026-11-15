package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.repository.AnalysisRepository
import javax.inject.Inject

class TranslateTextUseCase @Inject constructor(
    private val repository: AnalysisRepository
) {
    suspend operator fun invoke(request: TranslateRequestDto, isOnline: Boolean): AnalysisResult {
        return repository.translateText(request, isOnline)
    }
}