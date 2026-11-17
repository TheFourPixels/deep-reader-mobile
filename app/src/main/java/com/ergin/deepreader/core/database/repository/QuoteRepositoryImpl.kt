package com.ergin.deepreader.core.database.repository

import com.ergin.deepreader.core.database.dao.SavedQuoteDao
import com.ergin.deepreader.core.database.entity.SavedQuoteEntity
import com.ergin.deepreader.di.IoDispatcher
import com.ergin.deepreader.domain.model.SavedQuote
import com.ergin.deepreader.domain.repository.QuoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class QuoteRepositoryImpl @Inject constructor(
    private val savedQuoteDao: SavedQuoteDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : QuoteRepository {

    override fun observeQuotesForBook(bookSha256: String): Flow<List<SavedQuote>> {
        return savedQuoteDao.observeQuotesForBook(bookSha256).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveQuote(quote: SavedQuote) {
        withContext(ioDispatcher) {
            val alreadyExists = savedQuoteDao.existsQuote(
                bookSha256 = quote.bookSha256,
                chapterIndex = quote.chapterIndex,
                paragraphIndex = quote.paragraphIndex,
                quoteText = quote.quoteText
            )
            if (!alreadyExists) {
                savedQuoteDao.insertQuote(
                    SavedQuoteEntity(
                        bookSha256 = quote.bookSha256,
                        chapterIndex = quote.chapterIndex,
                        chapterTitle = quote.chapterTitle,
                        paragraphIndex = quote.paragraphIndex,
                        charOffset = quote.charOffset,
                        quoteText = quote.quoteText,
                        createdAt = if (quote.createdAt > 0L) quote.createdAt else System.currentTimeMillis()
                    )
                )
            }
        }
    }

    override suspend fun deleteQuote(quoteId: Long) {
        withContext(ioDispatcher) {
            savedQuoteDao.deleteQuoteById(quoteId)
        }
    }

    private fun SavedQuoteEntity.toDomain() = SavedQuote(
        id = id,
        bookSha256 = bookSha256,
        chapterIndex = chapterIndex,
        chapterTitle = chapterTitle,
        paragraphIndex = paragraphIndex,
        charOffset = charOffset,
        quoteText = quoteText,
        createdAt = createdAt
    )
}
