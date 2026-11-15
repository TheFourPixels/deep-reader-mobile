package com.ergin.deepreader.di

import com.ergin.deepreader.feature.reader.EpubParserEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EpubModule {

    @Provides
    @Singleton
    fun provideEpubParserEngine(): EpubParserEngine = EpubParserEngine()
}