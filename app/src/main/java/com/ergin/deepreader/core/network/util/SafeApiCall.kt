package com.ergin.deepreader.core.network.util

import com.ergin.deepreader.core.network.model.ErrorResponseDto
import com.ergin.deepreader.domain.model.DomainError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.Response

sealed interface NetworkResult<out T> {
    data class Success<T>(val body: T) : NetworkResult<T>
    data class Error(val error: DomainError) : NetworkResult<Nothing>
}

suspend fun <T> safeApiCall(
    action: String,
    json: Json,
    apiCall: suspend () -> Response<T>
): NetworkResult<T> {
    return try {
        val response = apiCall()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            NetworkResult.Success(body)
        } else {
            NetworkResult.Error(parseServerError(response.errorBody()?.string(), json))
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        NetworkResult.Error(DomainError.Network(action))
    }
}

private fun parseServerError(errorBodyString: String?, json: Json): DomainError {
    if (errorBodyString.isNullOrBlank()) {
        return DomainError.Unknown("INTERNAL_ERROR")
    }
    return try {
        val dto = json.decodeFromString<ErrorResponseDto>(errorBodyString)
        DomainError.ServerError(
            errorCode = dto.errorCode,
            serverMessage = dto.message,
            traceId = dto.traceId
        )
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        DomainError.Unknown("INTERNAL_ERROR")
    }
}
