package com.luhaopeng.flicktv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 播放历史；同一“影片+线路+集”只保留最新记录。 */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val progressKey: String,
    val movieName: String,
    val poster: String,
    val sourceId: String,
    val sourceName: String,
    val vodId: String,
    val lineName: String,
    val episodeTitle: String,
    val episodeUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

/** 收藏按规范化片名跨源存储。 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val normalizedName: String,
    val movieName: String,
    val poster: String,
    val addedAt: Long
)
