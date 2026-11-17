package com.ergin.deepreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ergin.deepreader.core.database.entity.AiCacheEntity

@Dao
interface LocalAiCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCache(cacheEntry: AiCacheEntity)

    @Query("SELECT * FROM local_ai_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getCache(cacheKey: String): AiCacheEntity?

    @Query("DELETE FROM local_ai_cache WHERE cacheKey = :cacheKey")
    suspend fun deleteCache(cacheKey: String)
}