package com.luhaopeng.flicktv.data.health

import android.content.Context
import com.luhaopeng.flicktv.data.model.SourceConfig

/** 持久化的源健康快照。 */
data class SourceHealth(
    val failureCount: Int,
    val lastSuccessAt: Long,
    val autoDisabled: Boolean,
    val disabledAt: Long,
    val retryStep: Int,
    val manualEnabled: Boolean
) {
    fun nextRetryAt(): Long {
        if (!autoDisabled) return 0L
        val minutes = RETRY_MINUTES[retryStep.coerceIn(RETRY_MINUTES.indices)]
        return disabledAt + minutes * 60_000L
    }

    companion object {
        val RETRY_MINUTES = longArrayOf(10, 30, 60, 120, 360, 720)
    }
}

/**
 * LibreTV 式健康状态机：连续失败 3 次禁用；到阶梯时间才放行探测；
 * 探测成功完全恢复，探测失败进入下一阶梯。
 */
class SourceHealthManager(context: Context) {
    private val prefs = context.getSharedPreferences("source_health", Context.MODE_PRIVATE)

    @Synchronized
    fun state(sourceId: String): SourceHealth = SourceHealth(
        failureCount = prefs.getInt("$sourceId.failures", 0),
        lastSuccessAt = prefs.getLong("$sourceId.last_success", 0L),
        autoDisabled = prefs.getBoolean("$sourceId.auto_disabled", false),
        disabledAt = prefs.getLong("$sourceId.disabled_at", 0L),
        retryStep = prefs.getInt("$sourceId.retry_step", 0),
        manualEnabled = prefs.getBoolean("$sourceId.manual_enabled", true)
    )

    /** 返回 true 表示正常可用，或已到自动恢复探测时刻。 */
    fun shouldRequest(source: SourceConfig, now: Long = System.currentTimeMillis()): Boolean {
        val health = state(source.id)
        return health.manualEnabled && (!health.autoDisabled || now >= health.nextRetryAt())
    }

    @Synchronized
    fun recordSuccess(sourceId: String, now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putInt("$sourceId.failures", 0)
            .putLong("$sourceId.last_success", now)
            .putBoolean("$sourceId.auto_disabled", false)
            .putLong("$sourceId.disabled_at", 0L)
            .putInt("$sourceId.retry_step", 0)
            .apply()
    }

    @Synchronized
    fun recordFailure(sourceId: String, now: Long = System.currentTimeMillis()) {
        val old = state(sourceId)
        if (old.autoDisabled) {
            // 只有到期探测才会请求；失败后进入更长退避期。
            prefs.edit()
                .putInt("$sourceId.failures", old.failureCount + 1)
                .putLong("$sourceId.disabled_at", now)
                .putInt("$sourceId.retry_step", (old.retryStep + 1).coerceAtMost(SourceHealth.RETRY_MINUTES.lastIndex))
                .apply()
        } else {
            val failures = old.failureCount + 1
            prefs.edit()
                .putInt("$sourceId.failures", failures)
                .putBoolean("$sourceId.auto_disabled", failures >= 3)
                .putLong("$sourceId.disabled_at", if (failures >= 3) now else 0L)
                .putInt("$sourceId.retry_step", 0)
                .apply()
        }
    }

    fun setManualEnabled(sourceId: String, enabled: Boolean) {
        prefs.edit().putBoolean("$sourceId.manual_enabled", enabled).apply()
    }

    fun resetAll(sourceIds: List<String>) {
        val editor = prefs.edit()
        sourceIds.forEach { id ->
            listOf("failures", "last_success", "auto_disabled", "disabled_at", "retry_step", "manual_enabled")
                .forEach { editor.remove("$id.$it") }
        }
        editor.apply()
    }
}
