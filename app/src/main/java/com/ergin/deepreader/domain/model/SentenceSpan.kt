package com.ergin.deepreader.domain.model

data class SentenceSpan(
    val sentenceIndex: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val epubCfi: String? = null
)