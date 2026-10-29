package com.luhaopeng.flicktv.data.repository

import com.luhaopeng.flicktv.data.api.MaccmsApi
import com.luhaopeng.flicktv.data.health.SourceHealthManager
import com.luhaopeng.flicktv.data.merge.MultiSourceMerger
import com.luhaopeng.flicktv.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

/** 苹果 CMS 业务仓库：每个源独立失败、并行检索，任何单源异常都不会中断整体。 */
class VodRepository(
    private val api: MaccmsApi,
    private val health: SourceHealthManager
) {
    suspend fun searchAll(keyword: String, sources: List<SourceConfig>): List<MergedMovie> = coroutineScope {
        sources.filter { health.shouldRequest(it) }.map { source ->
            async {
                runCatching {
                    val response = withTimeout(5_000L) { api.search(source.api, keyword = keyword.trim()) }
                    health.recordSuccess(source.id)
                    response.list.map { SourceVod(source, it) }
                }.getOrElse {
                    health.recordFailure(source.id)
                    emptyList()
                }
            }
        }.awaitAll().flatten().let(MultiSourceMerger::merge)
    }

    suspend fun detail(sourceVod: SourceVod): Result<VodItem> = request(sourceVod.source) {
        api.detail(sourceVod.source.api, ids = sourceVod.vod.vodId).list.firstOrNull()
            ?: error("详情为空")
    }

    suspend fun categories(source: SourceConfig): Result<List<VodCategory>> = request(source) {
        api.categories(source.api).categories
    }

    suspend fun browse(source: SourceConfig, typeId: String, page: Int): Result<MaccmsResponse> = request(source) {
        api.browse(source.api, typeId = typeId, page = page)
    }

    /**
     * 设置页主动检测：无视自动禁用的退避时间立即请求一次分类接口。
     * 成功会清零失败次数并恢复源；失败按健康状态机累计或进入下一阶梯。
     */
    suspend fun probe(source: SourceConfig): Result<Unit> {
        return runCatching {
            withTimeout(5_000L) { api.categories(source.api) }
            health.recordSuccess(source.id)
        }.onFailure {
            health.recordFailure(source.id)
        }.map { Unit }
    }

    private suspend fun <T> request(source: SourceConfig, block: suspend () -> T): Result<T> {
        if (!health.shouldRequest(source)) return Result.failure(IllegalStateException("源当前被禁用"))
        return runCatching { withTimeout(5_000L) { block() } }
            .onSuccess { health.recordSuccess(source.id) }
            .onFailure { health.recordFailure(source.id) }
    }
}
