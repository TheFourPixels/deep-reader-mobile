package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.domain.model.ExtractedContextResult
import com.ergin.deepreader.domain.model.SentenceLayoutInfo
import com.ergin.deepreader.domain.model.ViewportLayoutSnapshot
import javax.inject.Inject

class EpubContextExtractorUseCase @Inject constructor() {

    fun extractContext(
        selectedText: String,
        targetParagraphIndex: Int,
        targetSentenceIndex: Int,
        viewportSnapshot: ViewportLayoutSnapshot
    ): ExtractedContextResult {
        val trimmedSelection = selectedText.trim()
        if (trimmedSelection.isEmpty()) {
            return ExtractedContextResult.EmptySelection
        }
        if (trimmedSelection.length > 5000) {
            return ExtractedContextResult.SelectionTooLongForBoth(trimmedSelection.length)
        }
        if (trimmedSelection.length > 500) {
            return ExtractedContextResult.SelectionTooLongForAi(trimmedSelection.length)
        }

        // All visible sentences flattened with layout info
        val allVisibleSentences = mutableListOf<Pair<Int, SentenceLayoutInfo>>()
        for (paragraphLayout in viewportSnapshot.paragraphs) {
            for (sentenceLayout in paragraphLayout.sentences) {
                // Filter out sentences that start after the viewport bottom (Spoiler Guardrail)
                if (sentenceLayout.topY < viewportSnapshot.viewportBottomY) {
                    allVisibleSentences.add(Pair(paragraphLayout.paragraphIndex, sentenceLayout))
                }
            }
        }

        // Locate target sentence
        val targetIndex = allVisibleSentences.indexOfFirst { (pIndex, sLayout) ->
            pIndex == targetParagraphIndex && sLayout.sentence.sentenceIndex == targetSentenceIndex
        }.takeIf { it != -1 } ?: allVisibleSentences.indexOfFirst { (_, sLayout) ->
            sLayout.sentence.text.contains(trimmedSelection, ignoreCase = true)
        }.takeIf { it != -1 } ?: 0

        val targetPair = allVisibleSentences.getOrNull(targetIndex)
        val currentSentenceText = targetPair?.second?.sentence?.text ?: trimmedSelection

        // Sentences before (up to 2)
        val beforeList = mutableListOf<String>()
        val beforeEndIndex = (targetIndex - 1).coerceAtLeast(0)
        val beforeStartIndex = (targetIndex - 2).coerceAtLeast(0)

        if (targetIndex > 0) {
            for (i in beforeStartIndex..beforeEndIndex) {
                if (i < targetIndex) {
                    beforeList.add(allVisibleSentences[i].second.sentence.text)
                }
            }
        }

        // Sentences after (up to 2) — Strict Spoiler Guardrail enforced
        val afterList = mutableListOf<String>()
        val afterStartIndex = targetIndex + 1
        val afterEndIndex = (targetIndex + 2).coerceAtMost(allVisibleSentences.size - 1)

        if (afterStartIndex <= afterEndIndex && afterStartIndex < allVisibleSentences.size) {
            for (i in afterStartIndex..afterEndIndex) {
                val candidateLayout = allVisibleSentences[i].second
                // Strict Spoiler Guardrail: Sentence bottomY MUST NOT exceed viewportBottomY
                if (candidateLayout.bottomY <= viewportSnapshot.viewportBottomY) {
                    afterList.add(candidateLayout.sentence.text)
                }
            }
        }

        // Paragraph text (truncated to 4000)
        val targetParagraphLayout = viewportSnapshot.paragraphs.find { it.paragraphIndex == targetParagraphIndex }
        val rawParagraph = targetParagraphLayout?.text ?: currentSentenceText
        val truncatedParagraph = if (rawParagraph.length > 4000) rawParagraph.take(4000) else rawParagraph

        // Chapter context (truncated to 2000)
        val rawChapterContext = viewportSnapshot.chapterTitle
        val truncatedChapterContext = if (rawChapterContext != null && rawChapterContext.length > 2000) {
            rawChapterContext.take(2000)
        } else {
            rawChapterContext
        }

        return ExtractedContextResult.Success(
            selectedText = trimmedSelection,
            currentSentence = currentSentenceText,
            sentencesBefore = beforeList,
            sentencesAfter = afterList,
            paragraph = truncatedParagraph,
            chapterContext = truncatedChapterContext
        )
    }
}