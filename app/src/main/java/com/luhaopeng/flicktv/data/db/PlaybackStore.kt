package com.luhaopeng.flicktv.data.db

import android.content.Context
import com.luhaopeng.flicktv.data.merge.MultiSourceMerger
import com.luhaopeng.flicktv.data.model.Episode
import com.luhaopeng.flicktv.data.model.SourceVod

/** 精确进度使用 SharedPreferences；Room 同步保存可展示的历史元数据。 */
class PlaybackStore(context: Context, private val historyDao: HistoryDao) {
    private val prefs = context.getSharedPreferences("playback_progress", Context.MODE_PRIVATE)

    fun key(movieName: String, lineName: String, episodeTitle: String): String =
        listOf(MultiSourceMerger.normalizeName(movieName), lineName.trim(), episodeTitle.trim()).joinToString("|")

    fun loadPosition(key: String): Long = prefs.getLong(key, 0L)

    fun savePositionNow(movieName: String, lineName: String, episodeTitle: String, positionMs: Long) {
        if (positionMs <= 0L) return
        prefs.edit().putLong(key(movieName, lineName, episodeTitle), positionMs).apply()
    }

    suspend fun save(
        movie: SourceVod,
        lineName: String,
        episode: Episode,
        positionMs: Long,
        durationMs: Long
    ) {
        val key = key(movie.vod.vodName, lineName, episode.title)
        prefs.edit().putLong(key, positionMs.coerceAtLeast(0L)).apply()
        historyDao.upsert(
            PlayHistoryEntity(
                progressKey = key,
                movieName = movie.vod.vodName,
                poster = movie.vod.vodPic,
                sourceId = movie.source.id,
                sourceName = movie.source.name,
                vodId = movie.vod.vodId,
                lineName = lineName,
                episodeTitle = episode.title,
                episodeUrl = episode.url,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun hasProgress(movieName: String, lineName: String, episodeTitle: String): Boolean =
        loadPosition(key(movieName, lineName, episodeTitle)) > 15_000L
}
