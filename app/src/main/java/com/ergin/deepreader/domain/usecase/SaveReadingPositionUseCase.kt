package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.core.database.dao.PositionDao
import com.ergin.deepreader.core.database.entity.ReadingPositionEntity
import com.ergin.deepreader.domain.model.ReadingPosition
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SaveReadingPositionUseCase @Inject constructor(
    private val positionDao: PositionDao
) {
    suspend operator fun invoke(position: ReadingPosition) {
        try {
            val entity = ReadingPositionEntity(
                bookSha256 = position.bookSha256,
                chapterIndex = position.chapterIndex,
                epubCfi = position.epubCfi,
                paragraphIndex = position.paragraphIndex,
                charOffset = position.charOffset,
                scrollProgressPercentage = position.scrollProgressPercentage,
                updatedAt = System.currentTimeMillis()
            )
            positionDao.savePosition(entity)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // FT-21 & FT-32: Catch error on save failure to prevent app crashes
        }
    }
}
