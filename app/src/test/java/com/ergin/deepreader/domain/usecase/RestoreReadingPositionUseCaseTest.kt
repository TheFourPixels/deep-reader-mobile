package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.core.database.dao.PositionDao
import com.ergin.deepreader.core.database.entity.ReadingPositionEntity
import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ParagraphBlock
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RestoreReadingPositionUseCaseTest {

    private val positionDao: PositionDao = mockk(relaxed = true)
    private lateinit var useCase: RestoreReadingPositionUseCase

    private val sampleChapters = listOf(
        ChapterContent(0, "Ch 1", listOf(ParagraphBlock(0, null, "P1", emptyList()), ParagraphBlock(1, null, "P2", emptyList()))),
        ChapterContent(1, "Ch 2", listOf(ParagraphBlock(0, null, "P1", emptyList())))
    )

    @Before
    fun setUp() {
        useCase = RestoreReadingPositionUseCase(positionDao)
    }

    @Test
    fun invoke_noSavedPosition_returnsDefaultPosition() = runTest {
        coEvery { positionDao.getPosition("sha1") } returns null

        val result = useCase("sha1", sampleChapters)

        assertEquals(0, result.chapterIndex)
        assertEquals(0, result.paragraphIndex)
        assertEquals(0, result.charOffset)
    }

    @Test
    fun invoke_savedPosition_restoresValidChapterAndParagraph() = runTest {
        val savedEntity = ReadingPositionEntity(
            bookSha256 = "sha1",
            chapterIndex = 0,
            epubCfi = "/6/2!/4/4/1:0",
            paragraphIndex = 1,
            charOffset = 0,
            scrollProgressPercentage = 50f,
            updatedAt = 100L
        )
        coEvery { positionDao.getPosition("sha1") } returns savedEntity

        val result = useCase("sha1", sampleChapters)

        assertEquals(0, result.chapterIndex)
        assertEquals(1, result.paragraphIndex)
        assertEquals(50f, result.scrollProgressPercentage, 0.01f)
    }

    @Test
    fun invoke_outOfBoundsChapterOrParagraph_coercesToNearestValidIndex() = runTest {
        val savedEntity = ReadingPositionEntity(
            bookSha256 = "sha1",
            chapterIndex = 10, // out of bounds
            epubCfi = null,
            paragraphIndex = 99, // out of bounds
            charOffset = -5,
            scrollProgressPercentage = 100f,
            updatedAt = 100L
        )
        coEvery { positionDao.getPosition("sha1") } returns savedEntity

        val result = useCase("sha1", sampleChapters)

        assertEquals(1, result.chapterIndex) // clamped to last chapter index (1)
        assertEquals(0, result.paragraphIndex) // Chapter 1 has 1 paragraph (index 0)
        assertEquals(0, result.charOffset) // clamped to 0
    }

    @Test
    fun invoke_dbException_returnsDefaultPositionWithoutCrashing() = runTest {
        coEvery { positionDao.getPosition("sha1") } throws RuntimeException("DB error")

        val result = useCase("sha1", sampleChapters)

        assertEquals(0, result.chapterIndex)
        assertEquals(0, result.paragraphIndex)
    }
}