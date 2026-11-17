package com.ergin.deepreader.domain.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface DomainError {
    data class Offline(val action: String) : DomainError
    data class Network(val action: String) : DomainError
    data class EmptyResponse(val action: String) : DomainError
    data class ServerError(
        val errorCode: String,
        val serverMessage: String?,
        val traceId: String?
    ) : DomainError
    data class Unknown(val errorCode: String = "INTERNAL_ERROR") : DomainError
}
