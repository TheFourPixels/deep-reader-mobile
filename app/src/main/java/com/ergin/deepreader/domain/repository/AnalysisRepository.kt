package com.ergin.deepreader.domain.repository

import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.model.ExplainRequest
import com.ergin.deepreader.domain.model.TranslateRequest

interface AnalysisRepository {
    suspend fun explainFragment(request: ExplainRequest, isOnline: Boolean): AnalysisResult
    suspend fun translateText(request: TranslateRequest, isOnline: Boolean): AnalysisResult
}
