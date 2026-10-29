package com.luhaopeng.flicktv.data.db

private const val WATCHED_PROGRESS_THRESHOLD_MS = 15_000L

/** 根据已持久化的播放位置计算详情页分集进度；无有效记录时不显示标记。 */
internal fun watchedPercent(positionMs: Long, durationMs: Long): Int? {
    if (positionMs <= WATCHED_PROGRESS_THRESHOLD_MS || durationMs <= 0L) return null
    return ((positionMs * 100L) / durationMs)
        .coerceIn(1L, 100L)
        .toInt()
}
