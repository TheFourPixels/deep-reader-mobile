package com.ergin.deepreader.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class SentenceLayoutInfo(
    val sentence: SentenceSpan,
    val topY: Float,
    val bottomY: Float
)

@Immutable
data class ParagraphLayoutInfo(
    val paragraphIndex: Int,
    val text: String,
    val topY: Float,
    val bottomY: Float,
    val sentences: List<SentenceLayoutInfo>
)

@Immutable
data class ViewportLayoutSnapshot(
    val viewportTopY: Float = 0f,
    val viewportBottomY: Float,
    val chapterTitle: String?,
    val paragraphs: List<ParagraphLayoutInfo>
)
