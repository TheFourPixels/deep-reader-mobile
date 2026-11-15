package com.ergin.deepreader.domain.repository

import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.domain.model.AnalysisResult

interface AnalysisRepository {
    suspend fun explainFragment(request: ExplainRequestDto, isOnline: Boolean): AnalysisResult
    suspend fun translateText(request: TranslateRequestDto, isOnline: Boolean): AnalysisResult
}