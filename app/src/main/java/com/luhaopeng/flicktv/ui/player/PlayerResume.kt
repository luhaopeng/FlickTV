package com.luhaopeng.flicktv.ui.player

/** 续播位置必须为正且不能越过已知时长；未知时长时保留已保存位置。 */
internal fun validResumePosition(savedPositionMs: Long, durationMs: Long = 0L): Long {
    if (savedPositionMs <= 0L) return 0L
    return if (durationMs > 0L) savedPositionMs.coerceAtMost(durationMs) else savedPositionMs
}
