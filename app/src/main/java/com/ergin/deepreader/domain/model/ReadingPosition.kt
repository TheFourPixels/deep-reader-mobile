package com.ergin.deepreader.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ReadingPosition(
    val bookSha256: String,
    val chapterIndex: Int,
    val epubCfi: String?,
    val paragraphIndex: Int,
    val charOffset: Int,
    val scrollProgressPercentage: Float = 0f
)
