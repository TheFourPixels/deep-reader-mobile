package com.ergin.deepreader.di

import com.ergin.deepreader.core.database.repository.BookRepositoryImpl
import com.ergin.deepreader.core.network.repository.AnalysisRepositoryImpl
import com.ergin.deepreader.core.storage.FileStorageGateway
import com.ergin.deepreader.core.storage.FileStorageGatewayImpl
import com.ergin.deepreader.domain.repository.AnalysisRepository
import com.ergin.deepreader.domain.repository.BookRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindAnalysisRepository(impl: AnalysisRepositoryImpl): AnalysisRepository

    @Binds
    @Singleton
    abstract fun bindFileStorageGateway(impl: FileStorageGatewayImpl): FileStorageGateway
}