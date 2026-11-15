package com.ergin.deepreader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["title", "author"])
    ]
)
data class BookEntity(
    @PrimaryKey val sha256: String,
    val title: String,
    val author: String?,
    val filePath: String,
    val coverPath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null
)