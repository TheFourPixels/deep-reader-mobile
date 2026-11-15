package com.ergin.deepreader.domain.model

data class ParagraphBlock(
    val paragraphIndex: Int,
    val xhtmlId: String?,
    val text: String,
    val sentences: List<SentenceSpan>
)