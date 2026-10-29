package com.luhaopeng.flicktv.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM play_history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE progressKey = :key LIMIT 1")
    suspend fun find(key: String): PlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE progressKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE normalizedName = :name)")
    fun observeFavorite(name: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE normalizedName = :name")
    suspend fun delete(name: String)
}
