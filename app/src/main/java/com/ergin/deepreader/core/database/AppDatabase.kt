package com.ergin.deepreader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ergin.deepreader.core.database.dao.BookDao
import com.ergin.deepreader.core.database.dao.PositionDao
import com.ergin.deepreader.core.database.entity.BookEntity
import com.ergin.deepreader.core.database.entity.ReadingPositionEntity

@Database(
    entities = [BookEntity::class, ReadingPositionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun positionDao(): PositionDao
}