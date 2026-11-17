package com.ergin.deepreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ergin.deepreader.core.database.entity.SavedQuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedQuoteDao {

    @Query("SELECT * FROM saved_quotes WHERE bookSha256 = :bookSha256 ORDER BY createdAt DESC")
    fun observeQuotesForBook(bookSha256: String): Flow<List<SavedQuoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: SavedQuoteEntity): Long

    @Query(
        "SELECT EXISTS(SELECT 1 FROM saved_quotes WHERE bookSha256 = :bookSha256 AND chapterIndex = :chapterIndex AND paragraphIndex = :paragraphIndex AND quoteText = :quoteText)"
    )
    suspend fun existsQuote(
        bookSha256: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        quoteText: String
    ): Boolean

    @Query("DELETE FROM saved_quotes WHERE id = :quoteId")
    suspend fun deleteQuoteById(quoteId: Long)
}
