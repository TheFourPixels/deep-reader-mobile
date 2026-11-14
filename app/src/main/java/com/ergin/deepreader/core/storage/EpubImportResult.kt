package com.ergin.deepreader.core.storage

data class EpubImportResult(
    val sha256: String,
    val stagingPath: String,
    val title: String?,
    val author: String?,
    val coverPath: String? = null
)

sealed class EpubImportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidFormat(message: String) : EpubImportException(message)
    class IoError(message: String, cause: Throwable? = null) : EpubImportException(message, cause)
}