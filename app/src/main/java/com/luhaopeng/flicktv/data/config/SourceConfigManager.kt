package com.luhaopeng.flicktv.data.config

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.luhaopeng.flicktv.data.api.NetworkModule
import com.luhaopeng.flicktv.data.model.VodConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * 三层配置管理：内部缓存 > assets 默认。
 * 远程更新成功时原子覆盖内部缓存；失败保持旧缓存并返回当前可用配置。
 */
class SourceConfigManager(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("source_config", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "sources_cache.json")
    private val atomicCache = AtomicFile(cacheFile)

    fun load(): VodConfig {
        val cached = runCatching { cacheFile.takeIf { it.exists() }?.readText() }
            .getOrNull()
            ?.let(::parseAndValidate)
        return cached ?: loadDefault()
    }

    fun loadDefault(): VodConfig {
        val text = context.assets.open("default_sources.json").bufferedReader().use { it.readText() }
        return requireNotNull(parseAndValidate(text)) { "assets/default_sources.json 格式错误" }
    }

    fun remoteUrl(): String = prefs.getString("remote_url", "").orEmpty()

    fun saveRemoteUrl(url: String) {
        prefs.edit().putString("remote_url", url.trim()).apply()
    }

    suspend fun updateFromRemote(url: String): Result<VodConfig> = withContext(Dispatchers.IO) {
        saveRemoteUrl(url)
        runCatching {
            require(url.startsWith("http://") || url.startsWith("https://")) { "请输入有效的 HTTP(S) URL" }
            val response = NetworkModule.okHttp.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                check(it.isSuccessful) { "HTTP ${it.code}" }
                val body = it.body?.string().orEmpty()
                val config = requireNotNull(parseAndValidate(body)) { "远程 JSON 格式不合法" }
                val output = atomicCache.startWrite()
                try {
                    output.write(body.toByteArray(Charsets.UTF_8))
                    atomicCache.finishWrite(output)
                } catch (error: Throwable) {
                    atomicCache.failWrite(output)
                    throw error
                }
                config
            }
        }
    }

    fun restoreDefault(): VodConfig {
        if (cacheFile.exists()) cacheFile.delete()
        return loadDefault()
    }

    private fun parseAndValidate(text: String): VodConfig? = runCatching {
        gson.fromJson(text, VodConfig::class.java).also { config ->
            require(config.sources.isNotEmpty())
            require(config.sources.all { it.id.isNotBlank() && it.name.isNotBlank() && it.api.startsWith("http") })
        }
    }.getOrNull()
}
