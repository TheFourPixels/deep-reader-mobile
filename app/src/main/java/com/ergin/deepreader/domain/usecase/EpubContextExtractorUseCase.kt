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
        if (trimmedSelection.length > 1000) {
            return ExtractedContextResult.SelectionTooLongForAi(trimmedSelection.length)
        }

        var isViewportTruncated = false
        val allVisibleSentences = mutableListOf<Pair<Int, SentenceLayoutInfo>>()

        for (paragraphLayout in viewportSnapshot.paragraphs) {
            for (sentenceLayout in paragraphLayout.sentences) {
                // Filter out sentences that start after the viewport bottom (Spoiler Guardrail)
                if (sentenceLayout.topY < viewportSnapshot.viewportBottomY) {
                    allVisibleSentences.add(Pair(paragraphLayout.paragraphIndex, sentenceLayout))
                } else {
                    isViewportTruncated = true
                }
            }
        }

        // Locate target sentence or fallback
        val targetIndex = if (allVisibleSentences.isNotEmpty()) {
            allVisibleSentences.indexOfFirst { (pIndex, sLayout) ->
                pIndex == targetParagraphIndex && sLayout.sentence.sentenceIndex == targetSentenceIndex
            }.takeIf { it != -1 } ?: allVisibleSentences.indexOfFirst { (_, sLayout) ->
                sLayout.sentence.text.contains(trimmedSelection, ignoreCase = true)
            }.takeIf { it != -1 } ?: 0
        } else {
            -1
        }

        val targetPair = if (targetIndex != -1) allVisibleSentences.getOrNull(targetIndex) else null

        // Tokenizer Fallback (CE-04, FT-85, FT-86): 250-char window if sentence matching fails
        val currentSentenceText = if (targetPair != null) {
            targetPair.second.sentence.text
        } else {
            val targetParagraph = viewportSnapshot.paragraphs.find { it.paragraphIndex == targetParagraphIndex }?.text ?: trimmedSelection
            val matchIdx = targetParagraph.indexOf(trimmedSelection, ignoreCase = true)
            if (matchIdx != -1) {
                val winStart = (matchIdx - 250).coerceAtLeast(0)
                val winEnd = (matchIdx + trimmedSelection.length + 250).coerceAtMost(targetParagraph.length)
                targetParagraph.substring(winStart, winEnd).trim()
            } else {
                trimmedSelection
            }
        }

        // Sentences before (up to 2)
        val beforeList = mutableListOf<String>()
        if (targetIndex > 0) {
            val beforeEndIndex = (targetIndex - 1).coerceAtLeast(0)
            val beforeStartIndex = (targetIndex - 2).coerceAtLeast(0)
            for (i in beforeStartIndex..beforeEndIndex) {
                if (i < targetIndex) {
                    beforeList.add(allVisibleSentences[i].second.sentence.text)
                }
            }
        }

        // Sentences after (up to 2) — Strict Spoiler Guardrail enforced
        val afterList = mutableListOf<String>()

        if (targetIndex != -1 && targetIndex + 1 < allVisibleSentences.size) {
            val afterStartIndex = targetIndex + 1
            val afterEndIndex = (targetIndex + 2).coerceAtMost(allVisibleSentences.size - 1)

            for (i in afterStartIndex..afterEndIndex) {
                val candidateLayout = allVisibleSentences[i].second
                if (candidateLayout.bottomY <= viewportSnapshot.viewportBottomY) {
                    afterList.add(candidateLayout.sentence.text)
                } else {
                    isViewportTruncated = true
                }
            }

            if (afterEndIndex < allVisibleSentences.size - 1) {
                isViewportTruncated = true
            }
        }

        // Paragraph text
        val targetParagraphLayout = viewportSnapshot.paragraphs.find { it.paragraphIndex == targetParagraphIndex }
        val rawParagraph = targetParagraphLayout?.text ?: currentSentenceText
        val truncatedParagraph = if (rawParagraph.length > 4000) rawParagraph.take(4000) else rawParagraph

        // Chapter context
        val rawChapterContext = viewportSnapshot.chapterTitle
        val truncatedChapterContext = if (rawChapterContext != null && rawChapterContext.length > 2000) {
            rawChapterContext.take(2000)
        } else {
            rawChapterContext
        }

        // Priority-Based Context Budget Pruning (MAX_CONTEXT_CHARS = 3000)
        var chapterCtx: String? = truncatedChapterContext
        var sentBefore = beforeList.toList()
        var sentAfter = afterList.toList()
        var paraCtx: String? = truncatedParagraph

        fun currentLength(): Int {
            return trimmedSelection.length +
                    currentSentenceText.length +
                    (paraCtx?.length ?: 0) +
                    sentBefore.sumOf { it.length } +
                    sentAfter.sumOf { it.length } +
                    (chapterCtx?.length ?: 0)
        }

        // Priority 5: Drop / truncate chapterContext
        if (currentLength() > 3000 && chapterCtx != null) {
            val budget = 3000 - (currentLength() - chapterCtx.length)
            if (budget <= 0) {
                chapterCtx = null
            } else {
                chapterCtx = chapterCtx.take(budget)
            }
        }

        // Priority 4: Drop distant sentences
        while (currentLength() > 3000 && (sentBefore.isNotEmpty() || sentAfter.isNotEmpty())) {
            if (sentAfter.isNotEmpty()) {
                sentAfter = sentAfter.dropLast(1)
            } else if (sentBefore.isNotEmpty()) {
                sentBefore = sentBefore.drop(1)
            }
        }

        // Priority 3: Truncate outer paragraph
        if (currentLength() > 3000 && paraCtx != null) {
            val maxParaBudget = 3000 - (trimmedSelection.length + currentSentenceText.length)
            if (maxParaBudget > 0) {
                val matchIndex = paraCtx.indexOf(currentSentenceText)
                if (matchIndex != -1) {
                    val half = maxParaBudget / 2
                    val start = (matchIndex - half).coerceAtLeast(0)
                    val end = (matchIndex + currentSentenceText.length + half).coerceAtMost(paraCtx.length)
                    paraCtx = paraCtx.substring(start, end).trim()
                } else {
                    paraCtx = paraCtx.take(maxParaBudget)
                }
            } else {
                paraCtx = null
            }
        }

        return ExtractedContextResult.Success(
            selectedText = trimmedSelection,
            currentSentence = currentSentenceText,
            sentencesBefore = sentBefore,
            sentencesAfter = sentAfter,
            paragraph = paraCtx,
            chapterContext = chapterCtx,
            isViewportTruncated = isViewportTruncated
        )
    }
}