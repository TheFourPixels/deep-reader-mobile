package com.ergin.deepreader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_quotes",
    indices = [Index(value = ["bookSha256"])]
)
data class SavedQuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookSha256: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val paragraphIndex: Int,
    val charOffset: Int,
    val quoteText: String,
    val createdAt: Long = System.currentTimeMillis()
)
