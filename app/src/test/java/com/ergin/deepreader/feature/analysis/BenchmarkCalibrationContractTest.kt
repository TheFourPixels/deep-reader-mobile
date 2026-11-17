package com.ergin.deepreader.feature.analysis

import com.ergin.deepreader.core.network.mapper.toDomain
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.domain.model.ExplainAnalysis
import com.ergin.deepreader.domain.model.ExtractedContextResult
import com.ergin.deepreader.domain.model.ParagraphLayoutInfo
import com.ergin.deepreader.domain.model.SentenceLayoutInfo
import com.ergin.deepreader.domain.model.SentenceSpan
import com.ergin.deepreader.domain.model.ViewportLayoutSnapshot
import com.ergin.deepreader.domain.usecase.EpubContextExtractorUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BenchmarkCalibrationContractTest {

    private lateinit var extractor: EpubContextExtractorUseCase

    private val validPhenomenonTypes = setOf(
        "idiom", "slang", "metaphor", "irony",
        "cultural_reference", "allusion", "archaic", "neologism", "literal"
    )

    private val validStylisticColorings = setOf(
        "neutral", "informal", "slang", "rude",
        "sarcastic", "ironic", "literary", "poetic", "dramatic"
    )

    @Before
    fun setUp() {
        extractor = EpubContextExtractorUseCase()
    }

    @Test
    fun benchmark_idiomSample_extractsContextAndValidatesRubric() {
        val sentence0 = SentenceSpan(0, "He was feeling under the weather.", 0, 33)
        val snapshot = createSingleSentenceSnapshot(sentence0, topY = 10f, bottomY = 50f, viewportBottom = 500f)

        val extraction = extractor.extractContext("under the weather", 0, 0, snapshot)
        assertTrue(extraction is ExtractedContextResult.Success)
        val success = extraction as ExtractedContextResult.Success

        assertEquals("under the weather", success.selectedText)
        assertEquals("He was feeling under the weather.", success.currentSentence)

        val mockAiResponse = ExplainResponseDto(
            selectedText = success.selectedText,
            contextualMeaning = "Чувствовать себя неважно или недомогать",
            phenomenonType = "idiom",
            explanation = "Идиома указывает на легкое недомогание или болезнь.",
            stylisticColoring = "informal",
            spoilerAlert = false,
            cached = false,
            processingTimeMs = 120L
        ).toDomain()

        assertValidRubricResponse(mockAiResponse)
    }

    @Test
    fun benchmark_slangSample_extractsContextAndValidatesRubric() {
        val sentence0 = SentenceSpan(0, "That new movie was totally rad.", 0, 31)
        val snapshot = createSingleSentenceSnapshot(sentence0, topY = 10f, bottomY = 50f, viewportBottom = 500f)

        val extraction = extractor.extractContext("rad", 0, 0, snapshot)
        assertTrue(extraction is ExtractedContextResult.Success)

        val mockAiResponse = ExplainResponseDto(
            selectedText = "rad",
            contextualMeaning = "Крутой, отличный, классный",
            phenomenonType = "slang",
            explanation = "Сленговое выражение 80-90х годов для описания чего-то впечатляющего.",
            stylisticColoring = "slang",
            spoilerAlert = false,
            cached = false,
            processingTimeMs = 90L
        ).toDomain()

        assertValidRubricResponse(mockAiResponse)
    }

    @Test
    fun benchmark_ironySample_extractsContextAndValidatesRubric() {
        val sentence0 = SentenceSpan(0, "What a delightful weather for a picnic in the heavy rain.", 0, 58)
        val snapshot = createSingleSentenceSnapshot(sentence0, topY = 10f, bottomY = 50f, viewportBottom = 500f)

        val extraction = extractor.extractContext("delightful weather", 0, 0, snapshot)
        assertTrue(extraction is ExtractedContextResult.Success)

        val mockAiResponse = ExplainResponseDto(
            selectedText = "delightful weather",
            contextualMeaning = "Ужасная погода (ироничное значение)",
            phenomenonType = "irony",
            explanation = "Использование слова 'восхитительная' в контексте проливного дождя выражает сарказм.",
            stylisticColoring = "ironic",
            spoilerAlert = false,
            cached = false,
            processingTimeMs = 110L
        ).toDomain()

        assertValidRubricResponse(mockAiResponse)
    }

    @Test
    fun benchmark_metaphorSample_extractsContextAndValidatesRubric() {
        val sentence0 = SentenceSpan(0, "Time is a thief that steals our memories.", 0, 41)
        val snapshot = createSingleSentenceSnapshot(sentence0, topY = 10f, bottomY = 50f, viewportBottom = 500f)

        val extraction = extractor.extractContext("Time is a thief", 0, 0, snapshot)
        assertTrue(extraction is ExtractedContextResult.Success)

        val mockAiResponse = ExplainResponseDto(
            selectedText = "Time is a thief",
            contextualMeaning = "Время незаметно проходит и забирает воспоминания",
            phenomenonType = "metaphor",
            explanation = "Сравнение времени с вором подчеркивает его необратимость.",
            stylisticColoring = "literary",
            spoilerAlert = false,
            cached = false,
            processingTimeMs = 95L
        ).toDomain()

        assertValidRubricResponse(mockAiResponse)
    }

    @Test
    fun benchmark_spoilerBoundarySample_enforcesViewportBoundary() {
        val sentence0 = SentenceSpan(0, "Visible sentence on screen.", 0, 27)
        val sentence1 = SentenceSpan(1, "Offscreen spoiler sentence revealing the murderer.", 28, 77)

        val snapshot = ViewportLayoutSnapshot(
            viewportTopY = 0f,
            viewportBottomY = 100f,
            chapterTitle = "Chapter 5",
            paragraphs = listOf(
                ParagraphLayoutInfo(
                    paragraphIndex = 0,
                    text = "Visible sentence on screen. Offscreen spoiler sentence revealing the murderer.",
                    topY = 0f,
                    bottomY = 200f,
                    sentences = listOf(
                        SentenceLayoutInfo(sentence0, 10f, 50f),
                        SentenceLayoutInfo(sentence1, 110f, 160f)
                    )
                )
            )
        )

        val extraction = extractor.extractContext("Visible", 0, 0, snapshot)
        assertTrue(extraction is ExtractedContextResult.Success)
        val success = extraction as ExtractedContextResult.Success

        assertTrue(success.sentencesAfter.isEmpty())
        assertTrue(success.isViewportTruncated)
    }

    private fun assertValidRubricResponse(analysis: ExplainAnalysis) {
        assertTrue("selectedText must not be blank", analysis.selectedText.isNotBlank())
        assertTrue("contextualMeaning must not be blank", analysis.contextualMeaning.isNotBlank())
        assertTrue("explanation must not be blank", analysis.explanation.isNotBlank())
        assertTrue(
            "phenomenonType '${analysis.phenomenonType}' must be valid",
            validPhenomenonTypes.contains(analysis.phenomenonType.lowercase())
        )
        assertTrue(
            "stylisticColoring '${analysis.stylisticColoring}' must be valid",
            validStylisticColorings.contains(analysis.stylisticColoring.lowercase())
        )
    }

    private fun createSingleSentenceSnapshot(
        sentenceSpan: SentenceSpan,
        topY: Float,
        bottomY: Float,
        viewportBottom: Float
    ): ViewportLayoutSnapshot {
        return ViewportLayoutSnapshot(
            viewportTopY = 0f,
            viewportBottomY = viewportBottom,
            chapterTitle = "Test Chapter",
            paragraphs = listOf(
                ParagraphLayoutInfo(
                    paragraphIndex = 0,
                    text = sentenceSpan.text,
                    topY = topY,
                    bottomY = bottomY,
                    sentences = listOf(SentenceLayoutInfo(sentenceSpan, topY, bottomY))
                )
            )
        )
    }
}
