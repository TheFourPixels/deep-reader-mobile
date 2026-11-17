package com.ergin.deepreader.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class SavedQuote(
    val id: Long = 0L,
    val bookSha256: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val paragraphIndex: Int,
    val charOffset: Int,
    val quoteText: String,
    val createdAt: Long = 0L
)
