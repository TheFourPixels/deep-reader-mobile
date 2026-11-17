package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.domain.model.ExtractedContextResult
import com.ergin.deepreader.domain.model.ParagraphLayoutInfo
import com.ergin.deepreader.domain.model.SentenceLayoutInfo
import com.ergin.deepreader.domain.model.SentenceSpan
import com.ergin.deepreader.domain.model.ViewportLayoutSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EpubContextExtractorUseCaseTest {

    private lateinit var useCase: EpubContextExtractorUseCase

    @Before
    fun setUp() {
        useCase = EpubContextExtractorUseCase()
    }

    @Test
    fun extractContext_singleWordSelection_returnsFullEnclosingSentence() {
        val sentence1 = SentenceSpan(0, "First sentence before.", 0, 22)
        val sentence2 = SentenceSpan(1, "Target sentence with selected word.", 23, 58)
        val sentence3 = SentenceSpan(2, "Third sentence after.", 59, 80)

        val snapshot = ViewportLayoutSnapshot(
            viewportTopY = 0f,
            viewportBottomY = 1000f,
            chapterTitle = "Chapter 1",
            paragraphs = listOf(
                ParagraphLayoutInfo(
                    paragraphIndex = 0,
                    text = "First sentence before. Target sentence with selected word. Third sentence after.",
                    topY = 0f,
                    bottomY = 200f,
                    sentences = listOf(
                        SentenceLayoutInfo(sentence1, 0f, 50f),
                        SentenceLayoutInfo(sentence2, 51f, 100f),
                        SentenceLayoutInfo(sentence3, 101f, 150f)
                    )
                )
            )
        )

        val result = useCase.extractContext(
            selectedText = "selected",
            targetParagraphIndex = 0,
            targetSentenceIndex = 1,
            viewportSnapshot = snapshot
        )

        assertTrue(result is ExtractedContextResult.Success)
        val success = result as ExtractedContextResult.Success
        assertEquals("selected", success.selectedText)
        assertEquals("Target sentence with selected word.", success.currentSentence)
        assertEquals(listOf("First sentence before."), success.sentencesBefore)
        assertEquals(listOf("Third sentence after."), success.sentencesAfter)
        assertFalse(success.isViewportTruncated)
    }

    @Test
    fun extractContext_sentenceBelowViewportBottom_excludesFromSentencesAfterAndSetsTruncatedFlag() {
        val sentence1 = SentenceSpan(0, "Target sentence.", 0, 16)
        val sentence2 = SentenceSpan(1, "Offscreen spoiler sentence.", 17, 44)

        val snapshot = ViewportLayoutSnapshot(
            viewportTopY = 0f,
            viewportBottomY = 100f, // Screen cuts off at 100f
            chapterTitle = "Chapter 1",
            paragraphs = listOf(
                ParagraphLayoutInfo(
                    paragraphIndex = 0,
                    text = "Target sentence. Offscreen spoiler sentence.",
                    topY = 0f,
                    bottomY = 200f,
                    sentences = listOf(
                        SentenceLayoutInfo(sentence1, 10f, 50f),
                        SentenceLayoutInfo(sentence2, 110f, 160f) // Starts & ends below 100f
                    )
                )
            )
        )

        val result = useCase.extractContext(
            selectedText = "Target",
            targetParagraphIndex = 0,
            targetSentenceIndex = 0,
            viewportSnapshot = snapshot
        )

        assertTrue(result is ExtractedContextResult.Success)
        val success = result as ExtractedContextResult.Success
        assertTrue(success.sentencesAfter.isEmpty()) // Spoiler excluded!
        assertTrue(success.isViewportTruncated)
    }

    @Test
    fun extractContext_unpunctuatedPoeticText_usesTokenizerFallback() {
        val poeticParagraph = "O wild West Wind thou breath of Autumns being Thou from whose unseen presence the leaves dead Are driven"

        val snapshot = ViewportLayoutSnapshot(
            viewportTopY = 0f,
            viewportBottomY = 1000f,
            chapterTitle = "Ode to the West Wind",
            paragraphs = listOf(
                ParagraphLayoutInfo(
                    paragraphIndex = 0,
                    text = poeticParagraph,
                    topY = 0f,
                    bottomY = 300f,
                    sentences = emptyList() // No standard sentence breaks
                )
            )
        )

        val result = useCase.extractContext(
            selectedText = "West Wind",
            targetParagraphIndex = 0,
            targetSentenceIndex = 0,
            viewportSnapshot = snapshot
        )

        assertTrue(result is ExtractedContextResult.Success)
        val success = result as ExtractedContextResult.Success
        assertTrue(success.currentSentence.contains("West Wind"))
    }

    @Test
    fun extractContext_selectionOver1000Chars_returnsSelectionTooLongForAi() {
        val longText = "a".repeat(1005)
        val snapshot = ViewportLayoutSnapshot(viewportBottomY = 1000f, chapterTitle = null, paragraphs = emptyList())

        val result = useCase.extractContext(longText, 0, 0, snapshot)

        assertTrue(result is ExtractedContextResult.SelectionTooLongForAi)
    }

    @Test
    fun extractContext_budgetOver3000Chars_prunesByPriority() {
        val hugeChapterTitle = "Title " + "x".repeat(2000)
        val targetSentence = SentenceSpan(0, "Target sentence here.", 0, 21)

        val snapshot = ViewportLayoutSnapshot(
            viewportTopY = 0f,
            viewportBottomY = 1000f,
            chapterTitle = hugeChapterTitle,
            paragraphs = listOf(
                ParagraphLayoutInfo(
                    paragraphIndex = 0,
                    text = "Target sentence here. " + "y".repeat(2500),
                    topY = 0f,
                    bottomY = 500f,
                    sentences = listOf(SentenceLayoutInfo(targetSentence, 10f, 50f))
                )
            )
        )

        val result = useCase.extractContext(
            selectedText = "Target",
            targetParagraphIndex = 0,
            targetSentenceIndex = 0,
            viewportSnapshot = snapshot
        )

        assertTrue(result is ExtractedContextResult.Success)
        val success = result as ExtractedContextResult.Success
        val totalLength = success.selectedText.length +
                success.currentSentence.length +
                (success.paragraph?.length ?: 0) +
                (success.chapterContext?.length ?: 0)

        assertTrue(totalLength <= 3000)
        assertEquals("Target sentence here.", success.currentSentence) // Never truncated!
    }
}