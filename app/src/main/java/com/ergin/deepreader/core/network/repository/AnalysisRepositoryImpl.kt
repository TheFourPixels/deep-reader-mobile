package com.ergin.deepreader.core.network.repository

import com.ergin.deepreader.core.database.dao.LocalAiCacheDao
import com.ergin.deepreader.core.database.entity.AiCacheEntity
import com.ergin.deepreader.core.network.datasource.AnalysisRemoteDataSource
import com.ergin.deepreader.core.network.mapper.toDomain
import com.ergin.deepreader.core.network.mapper.toDto
import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto
import com.ergin.deepreader.core.network.util.NetworkResult
import com.ergin.deepreader.di.IoDispatcher
import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.model.DomainError
import com.ergin.deepreader.domain.model.ExplainRequest
import com.ergin.deepreader.domain.model.TranslateRequest
import com.ergin.deepreader.domain.repository.AnalysisRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject

class AnalysisRepositoryImpl @Inject constructor(
    private val remoteDataSource: AnalysisRemoteDataSource,
    private val localAiCacheDao: LocalAiCacheDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AnalysisRepository {

    override suspend fun explainFragment(request: ExplainRequest, isOnline: Boolean): AnalysisResult =
        withContext(ioDispatcher) {
            val requestDto = request.toDto()
            val cacheKey = calculateExplainCacheKey(requestDto)

            val cachedEntity = try {
                localAiCacheDao.getCache(cacheKey)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }

            if (cachedEntity != null) {
                return@withContext try {
                    val cachedDto = json.decodeFromString<ExplainResponseDto>(cachedEntity.responseJson)
                    AnalysisResult.ExplainSuccess(cachedDto.toDomain(cachedOverride = true))
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    fetchExplainFromNetwork(requestDto, isOnline, cacheKey)
                }
            }

            fetchExplainFromNetwork(requestDto, isOnline, cacheKey)
        }

    private suspend fun fetchExplainFromNetwork(
        requestDto: ExplainRequestDto,
        isOnline: Boolean,
        cacheKey: String
    ): AnalysisResult {
        if (!isOnline) {
            return AnalysisResult.Failure(DomainError.Offline("EXPLAIN"))
        }

        return when (val result = remoteDataSource.explainFragment(requestDto)) {
            is NetworkResult.Success -> {
                val body = result.body
                if (body.contextualMeaning.isBlank() || body.explanation.isBlank()) {
                    AnalysisResult.Failure(DomainError.EmptyResponse("EXPLAIN"))
                } else {
                    try {
                        val responseJson = json.encodeToString(body)
                        localAiCacheDao.saveCache(
                            AiCacheEntity(
                                cacheKey = cacheKey,
                                cacheType = "EXPLAIN",
                                responseJson = responseJson
                            )
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                    AnalysisResult.ExplainSuccess(body.toDomain())
                }
            }
            is NetworkResult.Error -> AnalysisResult.Failure(result.error)
        }
    }

    override suspend fun translateText(request: TranslateRequest, isOnline: Boolean): AnalysisResult =
        withContext(ioDispatcher) {
            val requestDto = request.toDto()
            val cacheKey = calculateTranslateCacheKey(requestDto)

            val cachedEntity = try {
                localAiCacheDao.getCache(cacheKey)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }

            if (cachedEntity != null) {
                return@withContext try {
                    val cachedDto = json.decodeFromString<TranslateResponseDto>(cachedEntity.responseJson)
                    AnalysisResult.TranslateSuccess(cachedDto.toDomain(cachedOverride = true))
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    fetchTranslateFromNetwork(requestDto, isOnline, cacheKey)
                }
            }

            fetchTranslateFromNetwork(requestDto, isOnline, cacheKey)
        }

    private suspend fun fetchTranslateFromNetwork(
        requestDto: TranslateRequestDto,
        isOnline: Boolean,
        cacheKey: String
    ): AnalysisResult {
        if (!isOnline) {
            return AnalysisResult.Failure(DomainError.Offline("TRANSLATE"))
        }

        return when (val result = remoteDataSource.translateText(requestDto)) {
            is NetworkResult.Success -> {
                val body = result.body
                if (body.translatedText.isBlank()) {
                    AnalysisResult.Failure(DomainError.EmptyResponse("TRANSLATE"))
                } else {
                    try {
                        val responseJson = json.encodeToString(body)
                        localAiCacheDao.saveCache(
                            AiCacheEntity(
                                cacheKey = cacheKey,
                                cacheType = "TRANSLATE",
                                responseJson = responseJson
                            )
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                    AnalysisResult.TranslateSuccess(body.toDomain())
                }
            }
            is NetworkResult.Error -> AnalysisResult.Failure(result.error)
        }
    }

    private fun calculateExplainCacheKey(request: ExplainRequestDto): String {
        val beforeJoined = request.surroundingContext.sentencesBefore.joinToString("|") { it.trim() }
        val afterJoined = request.surroundingContext.sentencesAfter.joinToString("|") { it.trim() }
        val paragraph = request.surroundingContext.paragraph?.trim().orEmpty()
        val raw = "v1.1:${request.bookId}:${request.selectedText.trim()}:${request.currentSentence.trim()}:$beforeJoined:$afterJoined:$paragraph:${request.targetLanguage.lowercase()}"
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
