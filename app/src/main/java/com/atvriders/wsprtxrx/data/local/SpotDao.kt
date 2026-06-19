package com.atvriders.wsprtxrx.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(spots: List<SpotEntity>)

    @Query("SELECT * FROM spots ORDER BY timeUtc DESC LIMIT :limit")
    suspend fun recent(limit: Int = 2000): List<SpotEntity>

    @Query("DELETE FROM spots WHERE timeUtc < :cutoffEpochSec")
    suspend fun deleteOlderThan(cutoffEpochSec: Long)

    @Query("DELETE FROM spots")
    suspend fun clear()
}
