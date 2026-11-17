package com.ergin.deepreader.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ParagraphBlock(
    val paragraphIndex: Int,
    val xhtmlId: String?,
    val text: String,
    val sentences: List<SentenceSpan>,
    val isHeader: Boolean = false
)
