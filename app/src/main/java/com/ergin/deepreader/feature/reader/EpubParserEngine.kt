package com.ergin.deepreader.feature.reader

import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ParagraphBlock
import com.ergin.deepreader.domain.model.SentenceSpan
import java.text.BreakIterator
import java.util.Locale

class EpubParserEngine {

    fun parseChapterContent(xhtmlContent: String, chapterIndex: Int): ChapterContent {
        val sanitizedXhtml = sanitizeXhtml(xhtmlContent)
        val title = extractChapterTitle(sanitizedXhtml)
        val paragraphs = extractParagraphBlocks(sanitizedXhtml, chapterIndex)

        return ChapterContent(
            chapterIndex = chapterIndex,
            title = title,
            paragraphs = paragraphs
        )
    }

    fun tokenizeSentences(paragraphText: String, chapterIndex: Int = 0, paragraphIndex: Int = 0): List<SentenceSpan> {
        if (paragraphText.isBlank()) return emptyList()

        val iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH)
        iterator.setText(paragraphText)

        val rawSpans = mutableListOf<Pair<Int, Int>>()
        var start = iterator.first()
        var end = iterator.next()

        // Match honorific abbreviations like Mr., Mrs., Dr. that do not end sentences
        val honorificRegex = Regex("""(?i)\b(mr|mrs|ms|dr|prof|sr|jr)\.\s*$""")

        var currentStart = start
        while (end != BreakIterator.DONE) {
            val segment = paragraphText.substring(currentStart, end)
            if (honorificRegex.containsMatchIn(segment.trim())) {
                end = iterator.next()
                continue
            }
            rawSpans.add(Pair(currentStart, end))
            currentStart = end
            end = iterator.next()
        }
        if (currentStart < paragraphText.length) {
            rawSpans.add(Pair(currentStart, paragraphText.length))
        }

        val sentences = mutableListOf<SentenceSpan>()
        var sentenceIndex = 0

        for ((s, e) in rawSpans) {
            val raw = paragraphText.substring(s, e)
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty()) {
                val matchStart = paragraphText.indexOf(trimmed, s)
                val matchEnd = matchStart + trimmed.length
                val cfi = "/6/${(chapterIndex + 1) * 2}!/4/${(paragraphIndex + 1) * 2}/1:$matchStart"

                sentences.add(
                    SentenceSpan(
                        sentenceIndex = sentenceIndex++,
                        text = trimmed,
                        startOffset = matchStart,
                        endOffset = matchEnd,
                        epubCfi = cfi
                    )
                )
            }
        }

        return sentences
    }

    private fun sanitizeXhtml(content: String): String {
        var result = content.replace(Regex("""(?s)<script.*?>.*?</script>"""), "")
        result = result.replace(Regex("""(?s)<style.*?>.*?</style>"""), "")
        return result
    }

    private fun extractChapterTitle(xhtmlContent: String): String {
        val titleRegex = Regex("""<h[1-3][^>]*>(.*?)</h[1-3]>""", RegexOption.DOT_MATCHES_ALL)
        val match = titleRegex.find(xhtmlContent)
        if (match != null) {
            return stripHtmlTags(match.groupValues[1]).trim()
        }

        val htmlTitleRegex = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
        val htmlTitleMatch = htmlTitleRegex.find(xhtmlContent)
        if (htmlTitleMatch != null) {
            return stripHtmlTags(htmlTitleMatch.groupValues[1]).trim()
        }

        return "Chapter"
    }

    private fun extractParagraphBlocks(xhtmlContent: String, chapterIndex: Int): List<ParagraphBlock> {
        val paragraphRegex = Regex("""<(p|div)([^>]*)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
        val matches = paragraphRegex.findAll(xhtmlContent)

        val paragraphs = mutableListOf<ParagraphBlock>()
        var pIndex = 0

        for (match in matches) {
            val attributes = match.groupValues[2]
            val innerHtml = match.groupValues[3]
            val cleanText = stripHtmlTags(innerHtml).trim()

            if (cleanText.isBlank()) continue

            val idMatch = Regex("""id=["']([^"']+)["']""").find(attributes)
            val xhtmlId = idMatch?.groupValues?.get(1)

            val sentenceSpans = tokenizeSentences(cleanText, chapterIndex, pIndex)

            paragraphs.add(
                ParagraphBlock(
                    paragraphIndex = pIndex,
                    xhtmlId = xhtmlId,
                    text = cleanText,
                    sentences = sentenceSpans
                )
            )
            pIndex++
        }

        return paragraphs
    }

    private fun stripHtmlTags(html: String): String {
        return html.replace(Regex("""<[^>]*>"""), " ").replace(Regex("""\s+"""), " ").trim()
    }
}