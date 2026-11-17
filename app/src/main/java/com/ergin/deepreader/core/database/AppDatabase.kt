package com.ergin.deepreader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ergin.deepreader.core.database.dao.BookDao
import com.ergin.deepreader.core.database.dao.LocalAiCacheDao
import com.ergin.deepreader.core.database.dao.PositionDao
import com.ergin.deepreader.core.database.dao.SavedQuoteDao
import com.ergin.deepreader.core.database.entity.AiCacheEntity
import com.ergin.deepreader.core.database.entity.BookEntity
import com.ergin.deepreader.core.database.entity.ReadingPositionEntity
import com.ergin.deepreader.core.database.entity.SavedQuoteEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingPositionEntity::class,
        AiCacheEntity::class,
        SavedQuoteEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun positionDao(): PositionDao
    abstract fun localAiCacheDao(): LocalAiCacheDao
    abstract fun savedQuoteDao(): SavedQuoteDao
}