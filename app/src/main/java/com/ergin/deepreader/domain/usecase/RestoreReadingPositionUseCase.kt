package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.core.database.dao.PositionDao
import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ReadingPosition
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class RestoreReadingPositionUseCase @Inject constructor(
    private val positionDao: PositionDao
) {
    suspend operator fun invoke(bookSha256: String, chapters: List<ChapterContent>): ReadingPosition {
        val saved = try {
            positionDao.getPosition(bookSha256)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

        if (saved == null || chapters.isEmpty()) {
            return ReadingPosition(
                bookSha256 = bookSha256,
                chapterIndex = saved?.chapterIndex?.coerceAtLeast(0) ?: 0,
                epubCfi = saved?.epubCfi,
                paragraphIndex = saved?.paragraphIndex?.coerceAtLeast(0) ?: 0,
                charOffset = saved?.charOffset?.coerceAtLeast(0) ?: 0,
                scrollProgressPercentage = saved?.scrollProgressPercentage ?: 0f
            )
        }

        val chapterIndex = saved.chapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        val chapter = chapters.getOrNull(chapterIndex)

        val paragraphIndex = if (chapter != null && chapter.paragraphs.isNotEmpty()) {
            saved.paragraphIndex.coerceIn(0, chapter.paragraphs.size - 1)
        } else {
            0
        }

        return ReadingPosition(
            bookSha256 = bookSha256,
            chapterIndex = chapterIndex,
            epubCfi = saved.epubCfi,
            paragraphIndex = paragraphIndex,
            charOffset = saved.charOffset.coerceAtLeast(0),
            scrollProgressPercentage = saved.scrollProgressPercentage
        )
    }
}
