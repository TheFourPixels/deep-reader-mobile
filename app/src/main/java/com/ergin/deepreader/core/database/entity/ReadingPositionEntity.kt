package com.ergin.deepreader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val bookSha256: String,
    val chapterIndex: Int,
    val epubCfi: String?,
    val paragraphIndex: Int,
    val charOffset: Int,
    val scrollProgressPercentage: Float,
    val updatedAt: Long = System.currentTimeMillis()
)