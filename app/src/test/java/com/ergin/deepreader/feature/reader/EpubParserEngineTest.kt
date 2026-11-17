package com.ergin.deepreader.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EpubParserEngineTest {

    private lateinit var epubParserEngine: EpubParserEngine

    @Before
    fun setUp() {
        epubParserEngine = EpubParserEngine()
    }

    @Test
    fun parseChapterContent_extractsHeadingsAndParagraphsWithEntityDecoding() {
        val xhtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <html>
            <head><title>Test Chapter</title></head>
            <body>
                <h1>Heading &amp; Title</h1>
                <p>First paragraph with &mdash; dash and &quot;quotes&quot;.</p>
                <blockquote>A quoted &nbsp; text &hellip;</blockquote>
            </body>
            </html>
        """.trimIndent()

        val chapter = epubParserEngine.parseChapterContent(xhtml, 0)

        assertEquals("Heading & Title", chapter.title)
        assertEquals(3, chapter.paragraphs.size)

        val headerBlock = chapter.paragraphs[0]
        assertEquals("Heading & Title", headerBlock.text)
        assertTrue(headerBlock.isHeader)

        val pBlock = chapter.paragraphs[1]
        assertEquals("First paragraph with — dash and \"quotes\".", pBlock.text)
        assertFalse(pBlock.isHeader)

        val bqBlock = chapter.paragraphs[2]
        assertEquals("A quoted text …", bqBlock.text)
    }

    @Test
    fun parseChapterContent_malformedTags_stripsTagsGracefullyWithoutCrashing() {
        val xhtml = """
            <html><body>
                <p>Broken <img src="missing.jpg" unclosed <b>text</b> format</p>
            </body></html>
        """.trimIndent()

        val chapter = epubParserEngine.parseChapterContent(xhtml, 0)

        assertEquals(1, chapter.paragraphs.size)
        assertEquals("Broken text format", chapter.paragraphs[0].text)
    }

    @Test
    fun parseChapterContent_noStandardParagraphTags_fallsBackToBodyTextSplit() {
        val xhtml = """
            <html><body>
                Raw body text without paragraph tags line 1.

                Raw body text line 2.
            </body></html>
        """.trimIndent()

        val chapter = epubParserEngine.parseChapterContent(xhtml, 0)

        assertTrue(chapter.paragraphs.isNotEmpty())
        assertEquals("Raw body text without paragraph tags line 1.", chapter.paragraphs[0].text)
        assertEquals("Raw body text line 2.", chapter.paragraphs[1].text)
    }

    @Test
    fun tokenizeSentences_cyrillicAndEnglish_splitsCorrectlyIgnoringHonorifics() {
        val text = "Dr. Watson arrived in Moscow. Г-н. Иванов приветствовал его!"

        val sentences = epubParserEngine.tokenizeSentences(text, 0, 0)

        assertEquals(2, sentences.size)
        assertEquals("Dr. Watson arrived in Moscow.", sentences[0].text)
        assertEquals("Г-н. Иванов приветствовал его!", sentences[1].text)
    }

    @Test
    fun decodeHtmlEntities_decodesNumericAndHexEntities() {
        val raw = "Hex &#x2014; Dec &#8212; Quote &#34;"
        val decoded = epubParserEngine.decodeHtmlEntities(raw)

        assertEquals("Hex — Dec — Quote \"", decoded)
    }
}