package com.ergin.deepreader.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ChapterContent(
    val chapterIndex: Int,
    val title: String,
    val paragraphs: List<ParagraphBlock>
)
