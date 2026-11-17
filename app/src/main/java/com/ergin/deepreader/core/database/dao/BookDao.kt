package com.ergin.deepreader.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ergin.deepreader.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

data class BookWithProgressEntity(
    @Embedded val book: BookEntity,
    @ColumnInfo(name = "scrollProgressPercentage") val scrollProgressPercentage: Float?
)

@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: BookEntity)

    @Query("""
        SELECT books.*, reading_positions.scrollProgressPercentage AS scrollProgressPercentage 
        FROM books 
        LEFT JOIN reading_positions ON books.sha256 = reading_positions.bookSha256 
        ORDER BY CASE WHEN books.lastOpenedAt IS NULL THEN 0 ELSE 1 END DESC, books.lastOpenedAt DESC, books.addedAt DESC
    """)
    fun getBooksOrderedByLastOpened(): Flow<List<BookWithProgressEntity>>

    @Query("""
        SELECT books.*, reading_positions.scrollProgressPercentage AS scrollProgressPercentage 
        FROM books 
        LEFT JOIN reading_positions ON books.sha256 = reading_positions.bookSha256 
        WHERE books.sha256 = :sha256 LIMIT 1
    """)
    suspend fun getBookBySha256(sha256: String): BookWithProgressEntity?

    @Query("SELECT * FROM books WHERE LOWER(title) = LOWER(:title) AND (LOWER(author) = LOWER(:author) OR (:author IS NULL AND author IS NULL))")
    suspend fun findBooksByTitleAndAuthor(title: String, author: String?): List<BookEntity>

    @Query("UPDATE books SET lastOpenedAt = :lastOpenedAt WHERE sha256 = :sha256")
    suspend fun updateLastOpened(sha256: String, lastOpenedAt: Long)

    @Query("UPDATE books SET coverPath = :coverPath WHERE sha256 = :sha256")
    suspend fun updateCoverPath(sha256: String, coverPath: String)

    @Query("SELECT * FROM books WHERE coverPath IS NULL OR coverPath = ''")
    suspend fun getBooksWithoutCover(): List<BookEntity>

    @Query("DELETE FROM books WHERE sha256 = :sha256")
    suspend fun deleteBook(sha256: String)
}