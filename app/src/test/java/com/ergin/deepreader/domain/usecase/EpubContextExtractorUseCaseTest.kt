package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.domain.model.ExtractedContextResult
import com.ergin.deepreader.domain.model.ParagraphLayoutInfo
import com.ergin.deepreader.domain.model.SentenceLayoutInfo
import com.ergin.deepreader.domain.model.SentenceSpan
import com.ergin.deepreader.domain.model.ViewportLayoutSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EpubContextExtractorUseCaseTest {

    private lateinit var extractor: EpubContextExtractorUseCase

    @Before
    fun setUp() {
        extractor = EpubContextExtractorUseCase()
    }

    @Test
    fun extract_midParagraphAllVisible_returnsUpTo2BeforeAndUpTo2After() {
        val viewport = createSampleViewport(viewportBottomY = 1000f)

        val result = extractor.extractContext(
            selectedText = "clocks were striking thirteen",
            targetParagraphIndex = 1,
            targetSentenceIndex = 2, // Sentence 2 in Paragraph 1
            viewportSnapshot = viewport
        )

        assertTrue(result is ExtractedContextResult.Success)
        val success = result as ExtractedContextResult.Success
        assertEquals("clocks were striking thirteen", success.selectedText)
        assertEquals(2, success.sentencesBefore.size)
        assertEquals(2, success.sentencesAfter.size)
    }

    @Test
    fun extract_selectionNearBottomOfScreen_excludesSentencesWhereBottomYExceedsViewportBottomY() {
        // Target sentence p1s2 (Y=201f..300f) is at bottom of visible screen.
        // Sentences after p1s3 (Y=520f..650f) and p1s4 (Y=660f..750f) exceed viewportBottomY=500f.
        val viewport = createSampleViewportWithOffscreenAfterSentences(viewportBottomY = 500f)

        val result = extractor.extractContext(
            selectedText = "clocks were striking thirteen",
            targetParagraphIndex = 1,
            targetSentenceIndex = 2,
            viewportSnapshot = viewport
        )

        assertTrue(result is ExtractedContextResult.Success)
        val success = result as ExtractedContextResult.Success
        // sentencesAfter must be empty because all subsequent sentences exceed viewportBottomY=500f (Spoiler Guardrail!)
        assertTrue(success.sentencesAfter.isEmpty())
    }

    @Test
    fun extract_atStartOfChapter_returnsEmptySentencesBeforeWithoutPlaceholders() {
        val viewport = createSampleViewport(viewportBottomY = 1000f)

        val result = extractor.extractContext(
            selectedText = "First sentence",
            targetParagraphIndex = 0,
            targetSentenceIndex = 0,
            viewportSnapshot = viewport
        )

        assertTrue(result is ExtractedContextResult.Success)
        val success = result as ExtractedContextResult.Success
        assertTrue("sentencesBefore must be empty at start of chapter", success.sentencesBefore.isEmpty())
    }

    @Test
    fun extract_atEndOfChapter_returnsEmptySentencesAfterAndNeverReadsNextChapter() {
        val viewport = createSampleViewport(viewportBottomY = 1000f)

        val result = extractor.extractContext(
            selectedText = "Last sentence",
            targetParagraphIndex = 1,
            targetSentenceIndex = 4, // Last sentence in chapter
            viewportSnapshot = viewport
        )

        assertTrue(result is ExtractedContextResult.Success)
        val success = result as ExtractedContextResult.Success
        assertTrue("sentencesAfter must be empty at end of chapter", success.sentencesAfter.isEmpty())
    }

    @Test
    fun extract_selectedTextOver500Chars_returnsSelectionTooLongForAi() {
        val longText = "a".repeat(501)
        val viewport = createSampleViewport(viewportBottomY = 1000f)

        val result = extractor.extractContext(
            selectedText = longText,
            targetParagraphIndex = 0,
            targetSentenceIndex = 0,
            viewportSnapshot = viewport
        )

        assertTrue(result is ExtractedContextResult.SelectionTooLongForAi)
        assertEquals(501, (result as ExtractedContextResult.SelectionTooLongForAi).selectedLength)
    }

    @Test
    fun extract_selectedTextOver5000Chars_returnsSelectionTooLongForBoth() {
        val hugeText = "a".repeat(5001)
        val viewport = createSampleViewport(viewportBottomY = 1000f)

        val result = extractor.extractContext(
            selectedText = hugeText,
            targetParagraphIndex = 0,
            targetSentenceIndex = 0,
            viewportSnapshot = viewport
        )

        assertTrue(result is ExtractedContextResult.SelectionTooLongForBoth)
        assertEquals(5001, (result as ExtractedContextResult.SelectionTooLongForBoth).selectedLength)
    }

    @Test
    fun extract_longSurroundingFields_truncatesParagraphTo4000AndChapterContextTo2000WithoutTouchingCore() {
        val longChapterTitle = "Chapter 1: " + "A".repeat(2500)
        val longParagraphText = "Paragraph: " + "B".repeat(4500)

        val sentence = SentenceSpan(0, "Short sentence", 0, 14)
        val sentenceLayout = SentenceLayoutInfo(sentence, topY = 10f, bottomY = 30f)
        val paragraphLayout = ParagraphLayoutInfo(
            paragraphIndex = 0,
            text = longParagraphText,
            topY = 0f,
            bottomY = 100f,
            sentences = listOf(sentenceLayout)
        )
        val viewport = ViewportLayoutSnapshot(
            viewportBottomY = 1000f,
            chapterTitle = longChapterTitle,
            paragraphs = listOf(paragraphLayout)
        )

        val result = extractor.extractContext(
            selectedText = "Short sentence",
            targetParagraphIndex = 0,
            targetSentenceIndex = 0,
            viewportSnapshot = viewport
        )

        assertTrue(result is ExtractedContextResult.Success)
        val success = result as ExtractedContextResult.Success
        assertTrue(success.paragraph!!.length <= 4000)
        assertTrue(success.chapterContext!!.length <= 2000)
    }

    private fun createSampleViewport(viewportBottomY: Float): ViewportLayoutSnapshot {
        val p0s0 = SentenceSpan(0, "Sentence 0 in P0.", 0, 18)
        val p0s1 = SentenceSpan(1, "Sentence 1 in P0.", 19, 37)
        val p0 = ParagraphLayoutInfo(
            paragraphIndex = 0,
            text = "Sentence 0 in P0. Sentence 1 in P0.",
            topY = 0f,
            bottomY = 100f,
            sentences = listOf(
                SentenceLayoutInfo(p0s0, 0f, 40f),
                SentenceLayoutInfo(p0s1, 41f, 90f)
            )
        )

        val p1s0 = SentenceSpan(0, "Sentence 0 in P1.", 0, 18)
        val p1s1 = SentenceSpan(1, "Sentence 1 in P1.", 19, 37)
        val p1s2 = SentenceSpan(2, "It was a bright cold day in April, and the clocks were striking thirteen.", 38, 111)
        val p1s3 = SentenceSpan(3, "Sentence 3 in P1.", 112, 130)
        val p1s4 = SentenceSpan(4, "Sentence 4 in P1.", 131, 149)

        val p1 = ParagraphLayoutInfo(
            paragraphIndex = 1,
            text = "Sentence 0 in P1. Sentence 1 in P1. It was a bright cold day in April, and the clocks were striking thirteen. Sentence 3 in P1. Sentence 4 in P1.",
            topY = 101f,
            bottomY = 900f,
            sentences = listOf(
                SentenceLayoutInfo(p1s0, 101f, 150f),
                SentenceLayoutInfo(p1s1, 151f, 200f),
                SentenceLayoutInfo(p1s2, 201f, 300f),
                SentenceLayoutInfo(p1s3, 301f, 450f),
                SentenceLayoutInfo(p1s4, 451f, 500f)
            )
        )

        return ViewportLayoutSnapshot(
            viewportBottomY = viewportBottomY,
            chapterTitle = "Chapter 1: London",
            paragraphs = listOf(p0, p1)
        )
    }

    private fun createSampleViewportWithOffscreenAfterSentences(viewportBottomY: Float): ViewportLayoutSnapshot {
        val p0s0 = SentenceSpan(0, "Sentence 0 in P0.", 0, 18)
        val p0s1 = SentenceSpan(1, "Sentence 1 in P0.", 19, 37)
        val p0 = ParagraphLayoutInfo(
            paragraphIndex = 0,
            text = "Sentence 0 in P0. Sentence 1 in P0.",
            topY = 0f,
            bottomY = 100f,
            sentences = listOf(
                SentenceLayoutInfo(p0s0, 0f, 40f),
                SentenceLayoutInfo(p0s1, 41f, 90f)
            )
        )

        val p1s0 = SentenceSpan(0, "Sentence 0 in P1.", 0, 18)
        val p1s1 = SentenceSpan(1, "Sentence 1 in P1.", 19, 37)
        val p1s2 = SentenceSpan(2, "It was a bright cold day in April, and the clocks were striking thirteen.", 38, 111)
        val p1s3 = SentenceSpan(3, "Sentence 3 in P1.", 112, 130)
        val p1s4 = SentenceSpan(4, "Sentence 4 in P1.", 131, 149)

        val p1 = ParagraphLayoutInfo(
            paragraphIndex = 1,
            text = "Sentence 0 in P1. Sentence 1 in P1. It was a bright cold day in April, and the clocks were striking thirteen. Sentence 3 in P1. Sentence 4 in P1.",
            topY = 101f,
            bottomY = 900f,
            sentences = listOf(
                SentenceLayoutInfo(p1s0, 101f, 150f),
                SentenceLayoutInfo(p1s1, 151f, 200f),
                SentenceLayoutInfo(p1s2, 201f, 300f), // Target sentence (Y=201..300)
                SentenceLayoutInfo(p1s3, 520f, 650f), // Y=520..650 > 500f (Offscreen!)
                SentenceLayoutInfo(p1s4, 660f, 750f)  // Y=660..750 > 500f (Offscreen!)
            )
        )

        return ViewportLayoutSnapshot(
            viewportBottomY = viewportBottomY,
            chapterTitle = "Chapter 1: London",
            paragraphs = listOf(p0, p1)
        )
    }
}