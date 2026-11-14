package com.ergin.deepreader.domain.model

data class SentenceLayoutInfo(
    val sentence: SentenceSpan,
    val topY: Float,
    val bottomY: Float
)

data class ParagraphLayoutInfo(
    val paragraphIndex: Int,
    val text: String,
    val topY: Float,
    val bottomY: Float,
    val sentences: List<SentenceLayoutInfo>
)

data class ViewportLayoutSnapshot(
    val viewportTopY: Float = 0f,
    val viewportBottomY: Float,
    val chapterTitle: String?,
    val paragraphs: List<ParagraphLayoutInfo>
)