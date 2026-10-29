package com.luhaopeng.flicktv.ui.player

/**
 * 计算连续快进/后退后的目标位置。
 * pendingTarget 不为空时表示上一次 seek 尚未稳定，下一次操作应在该目标上继续累加，
 * 而不是重新读取仍停留在旧位置的播放器 currentPosition。
 */
internal fun accumulateSeekTarget(
    currentPositionMs: Long,
    pendingTargetMs: Long?,
    deltaMs: Long,
    durationMs: Long
): Long {
    val base = pendingTargetMs ?: currentPositionMs
    val target = (base + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
}
