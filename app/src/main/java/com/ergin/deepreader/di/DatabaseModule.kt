package com.ergin.deepreader.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ergin.deepreader.core.database.AppDatabase
import com.ergin.deepreader.core.database.dao.BookDao
import com.ergin.deepreader.core.database.dao.LocalAiCacheDao
import com.ergin.deepreader.core.database.dao.PositionDao
import com.ergin.deepreader.core.database.dao.SavedQuoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `saved_quotes` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `bookSha256` TEXT NOT NULL,
                    `chapterIndex` INTEGER NOT NULL,
                    `chapterTitle` TEXT NOT NULL,
                    `paragraphIndex` INTEGER NOT NULL,
                    `charOffset` INTEGER NOT NULL,
                    `quoteText` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_saved_quotes_bookSha256` ON `saved_quotes` (`bookSha256`)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "deep_reader.db"
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao = database.bookDao()

    @Provides
    fun providePositionDao(database: AppDatabase): PositionDao = database.positionDao()

    @Provides
    fun provideLocalAiCacheDao(database: AppDatabase): LocalAiCacheDao = database.localAiCacheDao()

    @Provides
    fun provideSavedQuoteDao(database: AppDatabase): SavedQuoteDao = database.savedQuoteDao()
}