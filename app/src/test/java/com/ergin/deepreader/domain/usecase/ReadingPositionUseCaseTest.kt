package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.core.database.dao.PositionDao
import com.ergin.deepreader.core.database.entity.ReadingPositionEntity
import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ParagraphBlock
import com.ergin.deepreader.domain.model.ReadingPosition
import com.ergin.deepreader.domain.model.SentenceSpan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ReadingPositionUseCaseTest {

    private lateinit var positionDao: PositionDao
    private lateinit var restoreUseCase: RestoreReadingPositionUseCase
    private lateinit var saveUseCase: SaveReadingPositionUseCase

    @Before
    fun setUp() {
        positionDao = mockk()
        restoreUseCase = RestoreReadingPositionUseCase(positionDao)
        saveUseCase = SaveReadingPositionUseCase(positionDao)
    }

    @Test
    fun restore_firstTimeOpeningBook_returnsStartOfBook() = runTest {
        coEvery { positionDao.getPosition("sha123") } returns null

        val result = restoreUseCase("sha123", emptyList())

        assertEquals(0, result.chapterIndex)
        assertEquals(0, result.paragraphIndex)
        assertEquals(0, result.charOffset)
    }

    @Test
    fun restore_validEpubCfi_resolvesExactChapterParagraphAndCharOffset() = runTest {
        val savedEntity = ReadingPositionEntity(
            bookSha256 = "sha123",
            chapterIndex = 1,
            epubCfi = "/6/2!/4/4/1:10",
            paragraphIndex = 2,
            charOffset = 10,
            scrollProgressPercentage = 0.45f
        )
        val chapters = createSampleChapters()
        coEvery { positionDao.getPosition("sha123") } returns savedEntity

        val result = restoreUseCase("sha123", chapters)

        assertEquals(1, result.chapterIndex)
        assertEquals(2, result.paragraphIndex)
        assertEquals(10, result.charOffset)
    }

    @Test
    fun restore_paragraphIndexOutOfBounds_coercesToNearestValidParagraph() = runTest {
        val savedEntity = ReadingPositionEntity(
            bookSha256 = "sha123",
            chapterIndex = 0,
            epubCfi = null,
            paragraphIndex = 999, // Out of bounds
            charOffset = 0,
            scrollProgressPercentage = 0.9f
        )
        val chapters = createSampleChapters()
        coEvery { positionDao.getPosition("sha123") } returns savedEntity

        val result = restoreUseCase("sha123", chapters)

        assertEquals(0, result.chapterIndex)
        // Chapter 0 has 2 paragraphs (index 0 and 1), so coerced to 1
        assertEquals(1, result.paragraphIndex)
    }

    @Test
    fun restore_neverUsesScrollProgressPercentageAsPositionAnchor() = runTest {
        val savedEntity = ReadingPositionEntity(
            bookSha256 = "sha123",
            chapterIndex = 0,
            epubCfi = null,
            paragraphIndex = 1,
            charOffset = 5,
            scrollProgressPercentage = 0.99f // Ignores percentage, uses paragraphIndex 1 & offset 5
        )
        val chapters = createSampleChapters()
        coEvery { positionDao.getPosition("sha123") } returns savedEntity

        val result = restoreUseCase("sha123", chapters)

        assertEquals(0, result.chapterIndex)
        assertEquals(1, result.paragraphIndex)
        assertEquals(5, result.charOffset)
    }

    @Test
    fun save_whenDatabaseThrowsException_catchesErrorAndKeepsAppRunning() = runTest {
        val position = ReadingPosition(
            bookSha256 = "sha123",
            chapterIndex = 1,
            epubCfi = null,
            paragraphIndex = 0,
            charOffset = 0,
            scrollProgressPercentage = 0.1f
        )
        coEvery { positionDao.savePosition(any()) } throws RuntimeException("DB Exception")

        // Should not throw exception
        saveUseCase(position)

        coVerify(exactly = 1) { positionDao.savePosition(any()) }
    }

    private fun createSampleChapters(): List<ChapterContent> {
        return listOf(
            ChapterContent(
                chapterIndex = 0,
                title = "Chapter 1",
                paragraphs = listOf(
                    ParagraphBlock(0, "p1", "Paragraph 0", emptyList()),
                    ParagraphBlock(1, "p2", "Paragraph 1", emptyList())
                )
            ),
            ChapterContent(
                chapterIndex = 1,
                title = "Chapter 2",
                paragraphs = listOf(
                    ParagraphBlock(0, "p1", "Chapter 2 Paragraph 0", emptyList()),
                    ParagraphBlock(1, "p2", "Chapter 2 Paragraph 1", emptyList()),
                    ParagraphBlock(2, "p3", "Chapter 2 Paragraph 2", emptyList())
                )
            )
        )
    }
}