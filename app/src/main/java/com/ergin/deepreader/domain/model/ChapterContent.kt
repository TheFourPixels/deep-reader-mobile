package com.ergin.deepreader.domain.model

data class ChapterContent(
    val chapterIndex: Int,
    val title: String,
    val paragraphs: List<ParagraphBlock>
)