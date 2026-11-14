package com.ergin.deepreader.core.network.repository

import com.ergin.deepreader.core.database.dao.LocalAiCacheDao
import com.ergin.deepreader.core.database.entity.AiCacheEntity
import com.ergin.deepreader.core.network.DeepReaderApi
import com.ergin.deepreader.core.network.model.ErrorResponseDto
import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto
import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.repository.AnalysisRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject

class AnalysisRepositoryImpl @Inject constructor(
    private val api: DeepReaderApi,
    private val localAiCacheDao: LocalAiCacheDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AnalysisRepository {

    override suspend fun explainFragment(request: ExplainRequestDto, isOnline: Boolean): AnalysisResult {
        val cacheKey = calculateExplainCacheKey(request)

        // 1. Check Local Cache
        val cachedEntity = try {
            localAiCacheDao.getCache(cacheKey)
        } catch (e: Exception) {
            null
        }

        if (cachedEntity != null) {
            return try {
                val cachedDto = json.decodeFromString<ExplainResponseDto>(cachedEntity.responseJson)
                AnalysisResult.ExplainSuccess(cachedDto.copy(cached = true))
            } catch (e: Exception) {
                fetchExplainFromNetwork(request, isOnline, cacheKey)
            }
        }

        return fetchExplainFromNetwork(request, isOnline, cacheKey)
    }

    private suspend fun fetchExplainFromNetwork(request: ExplainRequestDto, isOnline: Boolean, cacheKey: String): AnalysisResult {
        if (!isOnline) {
            return AnalysisResult.Offline("EXPLAIN", "Нет подключения к интернету. AI-разбор сейчас недоступен.")
        }

        return try {
            val response = api.explainFragment(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val responseJson = json.encodeToString(body)
                localAiCacheDao.saveCache(
                    AiCacheEntity(
                        cacheKey = cacheKey,
                        cacheType = "EXPLAIN",
                        responseJson = responseJson
                    )
                )
                AnalysisResult.ExplainSuccess(body)
            } else {
                parseErrorResponse(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            AnalysisResult.Error("NETWORK_ERROR", e.message ?: "Сбой соединения с сервером", null)
        }
    }

    override suspend fun translateText(request: TranslateRequestDto, isOnline: Boolean): AnalysisResult {
        val cacheKey = calculateTranslateCacheKey(request)

        val cachedEntity = try {
            localAiCacheDao.getCache(cacheKey)
        } catch (e: Exception) {
            null
        }

        if (cachedEntity != null) {
            return try {
                val cachedDto = json.decodeFromString<TranslateResponseDto>(cachedEntity.responseJson)
                AnalysisResult.TranslateSuccess(cachedDto.copy(cached = true))
            } catch (e: Exception) {
                fetchTranslateFromNetwork(request, isOnline, cacheKey)
            }
        }

        return fetchTranslateFromNetwork(request, isOnline, cacheKey)
    }

    private suspend fun fetchTranslateFromNetwork(request: TranslateRequestDto, isOnline: Boolean, cacheKey: String): AnalysisResult {
        if (!isOnline) {
            return AnalysisResult.Offline("TRANSLATE", "Нет подключения к интернету. Перевод сейчас недоступен.")
        }

        return try {
            val response = api.translateText(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val responseJson = json.encodeToString(body)
                localAiCacheDao.saveCache(
                    AiCacheEntity(
                        cacheKey = cacheKey,
                        cacheType = "TRANSLATE",
                        responseJson = responseJson
                    )
                )
                AnalysisResult.TranslateSuccess(body)
            } else {
                parseErrorResponse(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            AnalysisResult.Error("NETWORK_ERROR", e.message ?: "Сбой соединения с сервером", null)
        }
    }

    private fun parseErrorResponse(errorBodyString: String?): AnalysisResult.Error {
        if (errorBodyString == null || errorBodyString.isBlank()) {
            return AnalysisResult.Error("INTERNAL_ERROR", "Произошла ошибка при обработке запроса", null)
        }

        return try {
            val errorDto = json.decodeFromString<ErrorResponseDto>(errorBodyString)
            AnalysisResult.Error(
                errorCode = errorDto.errorCode,
                message = errorDto.message,
                traceId = errorDto.traceId
            )
        } catch (e: Exception) {
            AnalysisResult.Error("INTERNAL_ERROR", "Неизвестная ошибка сервера", null)
        }
    }

    private fun calculateExplainCacheKey(request: ExplainRequestDto): String {
        val raw = "v1.0:${request.bookId}:${request.selectedText.trim()}:${request.currentSentence.trim()}:${request.targetLanguage.lowercase()}"
        return sha256(raw)
    }

    private fun calculateTranslateCacheKey(request: TranslateRequestDto): String {
        val raw = "translate:v1:${request.text.trim()}:${request.sourceLanguage?.lowercase() ?: "auto"}:${request.targetLanguage.lowercase()}"
        return sha256(raw)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}