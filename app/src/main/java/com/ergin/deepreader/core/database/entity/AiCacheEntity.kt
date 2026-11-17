package com.ergin.deepreader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_ai_cache")
data class AiCacheEntity(
    @PrimaryKey val cacheKey: String,
    val cacheType: String,
    val responseJson: String,
    val createdAt: Long = System.currentTimeMillis()
)