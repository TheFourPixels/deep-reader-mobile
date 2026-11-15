package com.ergin.deepreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ergin.deepreader.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: BookEntity)

    @Query("SELECT * FROM books ORDER BY CASE WHEN lastOpenedAt IS NULL THEN 0 ELSE 1 END DESC, lastOpenedAt DESC, addedAt DESC")
    fun getBooksOrderedByLastOpened(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getBookBySha256(sha256: String): BookEntity?

    @Query("SELECT * FROM books WHERE LOWER(title) = LOWER(:title) AND (LOWER(author) = LOWER(:author) OR (:author IS NULL AND author IS NULL))")
    suspend fun findBooksByTitleAndAuthor(title: String, author: String?): List<BookEntity>

    @Query("UPDATE books SET lastOpenedAt = :lastOpenedAt WHERE sha256 = :sha256")
    suspend fun updateLastOpened(sha256: String, lastOpenedAt: Long)

    @Query("DELETE FROM books WHERE sha256 = :sha256")
    suspend fun deleteBook(sha256: String)
}