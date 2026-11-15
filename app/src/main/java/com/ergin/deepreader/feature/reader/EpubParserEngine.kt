package com.ergin.deepreader.feature.reader

import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ParagraphBlock
import com.ergin.deepreader.domain.model.SentenceSpan
import java.io.File
import java.text.BreakIterator
import java.util.Locale
import java.util.zip.ZipFile

class EpubParserEngine {

    fun getChapterCount(epubFile: File): Int {
        if (!epubFile.exists() || !epubFile.canRead()) return 1
        return try {
            val spinePaths = extractSpineHtmlPaths(epubFile)
            if (spinePaths.isEmpty()) 1 else spinePaths.size
        } catch (e: Exception) {
            1
        }
    }

    fun readChapter(epubFile: File, chapterIndex: Int): ChapterContent {
        if (!epubFile.exists() || !epubFile.canRead()) {
            return createFallbackChapter(chapterIndex, "Файл книги недоступен")
        }

        return try {
            ZipFile(epubFile).use { zip ->
                val spinePaths = extractSpineHtmlPaths(zip)
                if (spinePaths.isEmpty()) {
                    return createFallbackChapter(chapterIndex, "Пустая книга или неверный формат")
                }

                val safeIndex = chapterIndex.coerceIn(0, spinePaths.size - 1)
                val targetPath = spinePaths[safeIndex]

                val zipEntry = zip.getEntry(targetPath)
                    ?: zip.entries().asSequence().find { it.name.lowercase().endsWith(targetPath.lowercase()) }

                if (zipEntry == null) {
                    return createFallbackChapter(chapterIndex, "Глава $chapterIndex не найдена")
                }

                val xhtmlText = zip.getInputStream(zipEntry).bufferedReader().use { it.readText() }
                parseChapterContent(xhtmlText, safeIndex)
            }
        } catch (e: Exception) {
            createFallbackChapter(chapterIndex, "Ошибка чтения главы: ${e.message}")
        }
    }

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
        val start = iterator.first()
        var end = iterator.next()

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

    private fun extractSpineHtmlPaths(epubFile: File): List<String> {
        ZipFile(epubFile).use { zip ->
            return extractSpineHtmlPaths(zip)
        }
    }

    private fun extractSpineHtmlPaths(zip: ZipFile): List<String> {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return emptyList()
        val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }

        val opfPathMatch = Regex("""full-path=["']([^"']+)["']""").find(containerXml) ?: return emptyList()
        val opfFullPath = opfPathMatch.groupValues[1]

        val opfEntry = zip.getEntry(opfFullPath) ?: return emptyList()
        val opfXml = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }

        val opfDirPrefix = if (opfFullPath.contains("/")) opfFullPath.substringBeforeLast("/") + "/" else ""

        // Manifest id -> href
        val manifestItemRegex = Regex("""<item[^>]*id=["']([^"']+)["'][^>]*href=["']([^"']+)["'][^>]*>| <item[^>]*href=["']([^"']+)["'][^>]*id=["']([^"']+)["'][^>]*>""")
        val manifestMap = mutableMapOf<String, String>()

        for (match in manifestItemRegex.findAll(opfXml)) {
            val id1 = match.groupValues[1]
            val href1 = match.groupValues[2]
            val href2 = match.groupValues[3]
            val id2 = match.groupValues[4]

            if (id1.isNotBlank() && href1.isNotBlank()) {
                manifestMap[id1] = href1
            } else if (id2.isNotBlank() && href2.isNotBlank()) {
                manifestMap[id2] = href2
            }
        }

        // Spine itemrefs
        val spineItemRefRegex = Regex("""<itemref[^>]*idref=["']([^"']+)["'][^>]*>""")
        val spinePaths = mutableListOf<String>()

        for (match in spineItemRefRegex.findAll(opfXml)) {
            val idref = match.groupValues[1]
            val relativeHref = manifestMap[idref]
            if (relativeHref != null) {
                // Combine opfDirPrefix + relativeHref
                val fullZipPath = resolvePath(opfDirPrefix, relativeHref)
                spinePaths.add(fullZipPath)
            }
        }

        return spinePaths
    }

    private fun resolvePath(baseDir: String, relativePath: String): String {
        if (baseDir.isEmpty()) return relativePath
        val combined = baseDir + relativePath
        val parts = combined.split("/")
        val resultParts = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> continue
                ".." -> if (resultParts.isNotEmpty()) resultParts.removeAt(resultParts.size - 1)
                else -> resultParts.add(part)
            }
        }
        return resultParts.joinToString("/")
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

    private fun createFallbackChapter(chapterIndex: Int, message: String): ChapterContent {
        return ChapterContent(
            chapterIndex = chapterIndex,
            title = "Глава ${chapterIndex + 1}",
            paragraphs = listOf(
                ParagraphBlock(
                    paragraphIndex = 0,
                    xhtmlId = "fallback",
                    text = message,
                    sentences = listOf(
                        SentenceSpan(0, message, 0, message.length)
                    )
                )
            )
        )
    }
}