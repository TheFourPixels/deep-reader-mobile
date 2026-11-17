package com.ergin.deepreader.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class SentenceSpan(
    val sentenceIndex: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val epubCfi: String? = null
)
