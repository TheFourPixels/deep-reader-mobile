package com.ergin.deepreader.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EpubParserEngineTest {

    private lateinit var parserEngine: EpubParserEngine

    @Before
    fun setUp() {
        parserEngine = EpubParserEngine()
    }

    @Test
    fun parseChapter_validXhtml_extractsOrderedParagraphBlocksWithXhtmlIds() {
        val xhtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>Chapter 1</title></head>
              <body>
                <h1 id="title1">Chapter 1: The Start</h1>
                <p id="p1">It was a bright cold day in April, and the clocks were striking thirteen.</p>
                <p id="p2">The hallway smelt of boiled cabbage and old rag mats. Winston Smith slipped quickly through the glass doors.</p>
              </body>
            </html>
        """.trimIndent()

        val chapter = parserEngine.parseChapterContent(xhtmlContent = xhtml, chapterIndex = 0)

        assertEquals(0, chapter.chapterIndex)
        assertEquals("Chapter 1: The Start", chapter.title)
        assertEquals(2, chapter.paragraphs.size)

        val p1 = chapter.paragraphs[0]
        assertEquals(0, p1.paragraphIndex)
        assertEquals("p1", p1.xhtmlId)
        assertEquals("It was a bright cold day in April, and the clocks were striking thirteen.", p1.text)
        assertEquals(1, p1.sentences.size)

        val p2 = chapter.paragraphs[1]
        assertEquals(1, p2.paragraphIndex)
        assertEquals("p2", p2.xhtmlId)
        assertEquals(2, p2.sentences.size)
        assertEquals("The hallway smelt of boiled cabbage and old rag mats.", p2.sentences[0].text)
        assertEquals("Winston Smith slipped quickly through the glass doors.", p2.sentences[1].text)
    }

    @Test
    fun tokenizeSentences_handlesAbbreviationsQuotesAndEllipsisCorrectly() {
        val paragraphText = "Mr. Smith arrived at 5 p.m. He said: \"Hello... world!\" Then Mr. Brown left."

        val sentences = parserEngine.tokenizeSentences(paragraphText)

        assertNotNull(sentences)
        assertTrue(sentences.isNotEmpty())
        assertEquals("Mr. Smith arrived at 5 p.m.", sentences[0].text.trim())
    }

    @Test
    fun parseChapter_malformedNodeOrBrokenImage_skipsCorruptedElementAndParsesRemainingText() {
        val malformedXhtml = """
            <html xmlns="http://www.w3.org/1999/xhtml">
              <body>
                <h2>Chapter 2</h2>
                <p id="p1">Valid paragraph before broken node.</p>
                <img src="invalid<uri>" alt="broken" />
                <broken-unclosed-tag
                <p id="p2">Valid paragraph after broken node.</p>
              </body>
            </html>
        """.trimIndent()

        val chapter = parserEngine.parseChapterContent(xhtmlContent = malformedXhtml, chapterIndex = 1)

        assertEquals(1, chapter.chapterIndex)
        assertTrue(chapter.paragraphs.isNotEmpty())
        assertTrue(chapter.paragraphs.any { it.text.contains("Valid paragraph before") })
        assertTrue(chapter.paragraphs.any { it.text.contains("Valid paragraph after") })
    }
}