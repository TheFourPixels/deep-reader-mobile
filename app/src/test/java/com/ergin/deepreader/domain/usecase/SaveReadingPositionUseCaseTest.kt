package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.core.database.dao.PositionDao
import com.ergin.deepreader.domain.model.ReadingPosition
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SaveReadingPositionUseCaseTest {

    private val positionDao: PositionDao = mockk(relaxed = true)
    private lateinit var useCase: SaveReadingPositionUseCase

    @Before
    fun setUp() {
        useCase = SaveReadingPositionUseCase(positionDao)
    }

    @Test
    fun invoke_validPosition_savesEntityToDao() = runTest {
        val position = ReadingPosition("sha1", 1, "/6/4!/4/2/1:0", 2, 0, 25f)

        useCase(position)

        coVerify {
            positionDao.savePosition(match {
                it.bookSha256 == "sha1" &&
                        it.chapterIndex == 1 &&
                        it.paragraphIndex == 2 &&
                        it.scrollProgressPercentage == 25f
            })
        }
    }

    @Test
    fun invoke_dbError_catchesErrorSilentlyWithoutCrashing() = runTest {
        coEvery { positionDao.savePosition(any()) } throws RuntimeException("Database error")

        val position = ReadingPosition("sha1", 1, null, 2, 0, 25f)

        // Must not throw RuntimeException
        useCase(position)
    }
}