package com.ergin.deepreader.domain.repository

import com.ergin.deepreader.domain.model.SavedQuote
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {
    fun observeQuotesForBook(bookSha256: String): Flow<List<SavedQuote>>
    suspend fun saveQuote(quote: SavedQuote)
    suspend fun deleteQuote(quoteId: Long)
}
