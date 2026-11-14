package com.ergin.deepreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ergin.deepreader.core.database.entity.ReadingPositionEntity

@Dao
interface PositionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(position: ReadingPositionEntity)

    @Query("SELECT * FROM reading_positions WHERE bookSha256 = :bookSha256 LIMIT 1")
    suspend fun getPosition(bookSha256: String): ReadingPositionEntity?

    @Query("DELETE FROM reading_positions WHERE bookSha256 = :bookSha256")
    suspend fun deletePosition(bookSha256: String)
}