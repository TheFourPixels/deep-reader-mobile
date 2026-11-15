package com.ergin.deepreader.domain.model

data class Book(
    val sha256: String,
    val title: String,
    val author: String?,
    val filePath: String,
    val coverPath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null
)