package com.ergin.deepreader.feature.reader

import com.ergin.deepreader.domain.model.ParagraphBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPaginatorTest {

    private fun sampleParagraph(index: Int, text: String): ParagraphBlock = ParagraphBlock(
        paragraphIndex = index,
        xhtmlId = "p-$index",
        text = text,
        sentences = emptyList()
    )

    @Test
    fun findPageIndexForPosition_resolvesExactParagraphAndCharSlice() {
        val longPara = sampleParagraph(1, "A".repeat(200))
        val pages = listOf(
            ReaderPage(
                pageIndex = 0,
                showChapterHeader = true,
                showEndOfChapter = false,
                items = listOf(
                    ReaderPageItem(0, "Intro", false, 0, 5, sampleParagraph(0, "Intro")),
                    ReaderPageItem(1, "A".repeat(80), false, 0, 80, longPara)
                )
            ),
            ReaderPage(
                pageIndex = 1,
                showChapterHeader = false,
                showEndOfChapter = true,
                items = listOf(
                    ReaderPageItem(1, "A".repeat(120), false, 80, 200, longPara)
                )
            )
        )

        assertEquals(0, findPageIndexForPosition(pages, paragraphIndex = 0, charOffset = 0))
        assertEquals(0, findPageIndexForPosition(pages, paragraphIndex = 1, charOffset = 40))
        assertEquals(1, findPageIndexForPosition(pages, paragraphIndex = 1, charOffset = 120))
    }

    @Test
    fun findQuoteLocationInPage_findsParagraphAndOffsetOfQuote() {
        val para0 = sampleParagraph(0, "First paragraph without match.")
        val para1 = sampleParagraph(1, "Harry pushed the door ajar and peered inside.")
        val page = ReaderPage(
            pageIndex = 0,
            showChapterHeader = false,
            showEndOfChapter = false,
            items = listOf(
                ReaderPageItem(0, para0.text, false, 0, para0.text.length, para0),
                ReaderPageItem(1, para1.text, false, 0, para1.text.length, para1)
            )
        )

        val (paragraphIdx, charOffset) = findQuoteLocationInPage(
            quoteText = "door ajar",
            page = page,
            preferredParagraphIndex = 0
        )

        assertEquals(1, paragraphIdx)
        assertEquals(para1.text.indexOf("door ajar"), charOffset)
    }
}
