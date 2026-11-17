package com.ergin.deepreader.feature.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ParagraphBlock

@Immutable
data class ReaderPageItem(
    val paragraphIndex: Int,
    val text: String,
    val isHeader: Boolean,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val originalParagraph: ParagraphBlock
)

@Immutable
data class ReaderPage(
    val pageIndex: Int,
    val showChapterHeader: Boolean,
    val showEndOfChapter: Boolean,
    val items: List<ReaderPageItem>
)

fun readerChapterTitleTextStyle(): TextStyle = TextStyle(
    fontSize = 24.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 30.sp,
    letterSpacing = 0.sp,
    textAlign = TextAlign.Center
)

fun readerParagraphHeaderTextStyle(
    fontSp: Int,
    fontFamily: FontFamily = FontFamily.Serif
): TextStyle = TextStyle(
    fontSize = (fontSp * 1.35).sp,
    lineHeight = (fontSp * 1.8).sp,
    fontWeight = FontWeight.Bold,
    fontFamily = fontFamily,
    letterSpacing = 0.sp
)

fun readerBodyTextStyle(
    fontSp: Int,
    fontFamily: FontFamily = FontFamily.Serif
): TextStyle = TextStyle(
    fontSize = fontSp.sp,
    lineHeight = (fontSp * 1.68).sp,
    fontFamily = fontFamily,
    letterSpacing = 0.sp,
    textAlign = TextAlign.Justify
)

fun paginateChapter(
    chapter: ChapterContent,
    fontSp: Int,
    textMeasurer: TextMeasurer,
    density: Density,
    pageWidthPx: Int,
    pageHeightPx: Int,
    fontFamily: FontFamily = FontFamily.Serif
): List<ReaderPage> {
    if (pageWidthPx <= 0 || pageHeightPx <= 0) return emptyList()

    val paragraphSpacingPx = with(density) { 18.dp.roundToPx() }
    val headerExtraHeightPx = with(density) { (12.dp + 16.dp + 24.dp).roundToPx() }
    val endOfChapterHeightPx = with(density) { 48.dp.roundToPx() }
    val headerPaddingPx = with(density) { 8.dp.roundToPx() }

    val chapterTitleStyle = readerChapterTitleTextStyle()
    val paragraphHeaderStyle = readerParagraphHeaderTextStyle(fontSp, fontFamily)
    val bodyStyle = readerBodyTextStyle(fontSp, fontFamily)

    val pages = mutableListOf<ReaderPage>()
    var currentItems = mutableListOf<ReaderPageItem>()
    var showHeaderOnCurrentPage = true

    val titleLayout = textMeasurer.measure(
        text = AnnotatedString(chapter.title),
        style = chapterTitleStyle,
        constraints = Constraints(maxWidth = pageWidthPx)
    )
    var remainingHeightPx = (pageHeightPx - titleLayout.size.height - headerExtraHeightPx)
        .coerceAtLeast(1)

    fun flushPage(showEndOfChapter: Boolean = false) {
        pages.add(
            ReaderPage(
                pageIndex = pages.size,
                showChapterHeader = showHeaderOnCurrentPage,
                showEndOfChapter = showEndOfChapter,
                items = currentItems.toList()
            )
        )
        currentItems = mutableListOf()
        showHeaderOnCurrentPage = false
        remainingHeightPx = pageHeightPx
    }

    for (paragraph in chapter.paragraphs) {
        if (paragraph.text.isBlank()) continue

        val style = if (paragraph.isHeader) paragraphHeaderStyle else bodyStyle
        val extraVerticalPad = if (paragraph.isHeader) headerPaddingPx else 0
        var sliceStart = 0

        while (sliceStart < paragraph.text.length) {
            val remainingText = paragraph.text.substring(sliceStart)
            val layout = textMeasurer.measure(
                text = AnnotatedString(remainingText),
                style = style,
                constraints = Constraints(maxWidth = pageWidthPx)
            )

            val neededHeight = layout.size.height + extraVerticalPad
            if (neededHeight <= remainingHeightPx) {
                currentItems.add(
                    ReaderPageItem(
                        paragraphIndex = paragraph.paragraphIndex,
                        text = remainingText,
                        isHeader = paragraph.isHeader,
                        startCharOffset = sliceStart,
                        endCharOffset = paragraph.text.length,
                        originalParagraph = paragraph
                    )
                )
                remainingHeightPx -= (neededHeight + paragraphSpacingPx)
                break
            } else {
                val availableForLines = (remainingHeightPx - extraVerticalPad).coerceAtLeast(0)
                var fittingLines = 0
                for (line in 0 until layout.lineCount) {
                    if (layout.getLineBottom(line) <= availableForLines) {
                        fittingLines = line + 1
                    } else {
                        break
                    }
                }

                if (fittingLines == 0) {
                    if (currentItems.isNotEmpty() || showHeaderOnCurrentPage) {
                        flushPage(showEndOfChapter = false)
                        continue
                    } else {
                        fittingLines = 1
                    }
                }

                val rawEndInSlice = layout.getLineEnd(fittingLines - 1, visibleEnd = false)
                val safeEndInSlice = rawEndInSlice.coerceIn(1, remainingText.length)
                val sliceText = remainingText.substring(0, safeEndInSlice).trimEnd()
                if (sliceText.isNotEmpty()) {
                    currentItems.add(
                        ReaderPageItem(
                            paragraphIndex = paragraph.paragraphIndex,
                            text = sliceText,
                            isHeader = paragraph.isHeader,
                            startCharOffset = sliceStart,
                            endCharOffset = sliceStart + safeEndInSlice,
                            originalParagraph = paragraph
                        )
                    )
                }
                sliceStart += safeEndInSlice
                while (sliceStart < paragraph.text.length && paragraph.text[sliceStart] == '\n') {
                    sliceStart++
                }
                flushPage(showEndOfChapter = false)
            }
        }
    }

    if (pages.isEmpty() && currentItems.isEmpty()) {
        flushPage(showEndOfChapter = true)
    } else if (currentItems.isNotEmpty() || showHeaderOnCurrentPage) {
        if (remainingHeightPx >= endOfChapterHeightPx) {
            flushPage(showEndOfChapter = true)
        } else {
            flushPage(showEndOfChapter = false)
            flushPage(showEndOfChapter = true)
        }
    } else {
        val last = pages.last()
        pages[pages.lastIndex] = last.copy(showEndOfChapter = true)
    }

    return pages
}

fun findPageIndexForPosition(
    pages: List<ReaderPage>,
    paragraphIndex: Int,
    charOffset: Int
): Int {
    if (pages.isEmpty()) return 0

    val exactSliceIndex = pages.indexOfFirst { page ->
        page.items.any { item ->
            item.paragraphIndex == paragraphIndex &&
                charOffset >= item.startCharOffset &&
                charOffset < item.endCharOffset.coerceAtLeast(item.startCharOffset + 1)
        }
    }
    if (exactSliceIndex != -1) return exactSliceIndex

    val paragraphPageIndex = pages.indexOfFirst { page ->
        page.items.any { it.paragraphIndex == paragraphIndex }
    }
    if (paragraphPageIndex != -1) return paragraphPageIndex

    val nextParagraphPageIndex = pages.indexOfFirst { page ->
        page.items.any { it.paragraphIndex >= paragraphIndex }
    }
    if (nextParagraphPageIndex != -1) return nextParagraphPageIndex

    return pages.lastIndex
}

fun findQuoteLocationInPage(
    quoteText: String,
    page: ReaderPage,
    preferredParagraphIndex: Int
): Pair<Int, Int> {
    val trimmed = quoteText.trim()
    val firstSegment = trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val searchToken = firstSegment.ifEmpty { trimmed }

    if (searchToken.isNotEmpty()) {
        val preferredItem = page.items.firstOrNull { it.paragraphIndex == preferredParagraphIndex }
        if (preferredItem != null) {
            val matchIdx = preferredItem.text.indexOf(searchToken, ignoreCase = true)
            if (matchIdx != -1) {
                return preferredItem.paragraphIndex to (preferredItem.startCharOffset + matchIdx)
            }
        }

        for (item in page.items) {
            val matchIdx = item.text.indexOf(searchToken, ignoreCase = true)
            if (matchIdx != -1) {
                return item.paragraphIndex to (item.startCharOffset + matchIdx)
            }
        }
    }

    val fallbackItem = page.items.firstOrNull { it.paragraphIndex == preferredParagraphIndex }
        ?: page.items.firstOrNull()
    return if (fallbackItem != null) {
        fallbackItem.paragraphIndex to fallbackItem.startCharOffset
    } else {
        preferredParagraphIndex to 0
    }
}
