package com.ergin.deepreader.di

import android.content.Context
import androidx.room.Room
import com.ergin.deepreader.core.database.AppDatabase
import com.ergin.deepreader.core.database.dao.BookDao
import com.ergin.deepreader.core.database.dao.LocalAiCacheDao
import com.ergin.deepreader.core.database.dao.PositionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "deep_reader.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao = database.bookDao()

    @Provides
    fun providePositionDao(database: AppDatabase): PositionDao = database.positionDao()

    @Provides
    fun provideLocalAiCacheDao(database: AppDatabase): LocalAiCacheDao = database.localAiCacheDao()
}