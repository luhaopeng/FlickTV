package com.luhaopeng.flicktv.data.recommendation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.luhaopeng.flicktv.data.api.NetworkModule
import com.luhaopeng.flicktv.data.model.RecommendedMovie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** LibreTV 官网首页的一级推荐类型。 */
enum class RecommendationType(val apiValue: String, val label: String) {
    MOVIE("movie", "电影"),
    TV("tv", "剧集");

    companion object {
        fun fromStorage(value: String?): RecommendationType = values().firstOrNull { it.apiValue == value } ?: MOVIE
    }
}

/** LibreTV 官网当前使用的豆瓣分类，顺序与官网一致。 */
object RecommendationTags {
    val movie = listOf("热门", "最新", "经典", "豆瓣高分", "冷门佳片", "华语", "欧美", "韩国", "日本", "动画")
    val tv = listOf("热门", "美剧", "英剧", "韩剧", "日剧", "国产剧", "港剧", "日本动画", "综艺", "纪录片")

    fun forType(type: RecommendationType): List<String> = if (type == RecommendationType.MOVIE) movie else tv
    fun normalize(type: RecommendationType, tag: String?): String = tag?.takeIf { it in forType(type) } ?: "热门"
}

/** 推荐请求结果。stale=true 表示网络失败后展示了过期缓存。 */
data class RecommendationResult(
    val items: List<RecommendedMovie>,
    val stale: Boolean = false,
    val warning: String = ""
)

/**
 * LibreTV 式豆瓣动态推荐仓库。
 * 请求 movie.douban.com/j/search_subjects，参数与官网一致：type + tag + sort=recommend + page_limit=50。
 * 每个“类型+标签”单独缓存 10 分钟；网络失败时回退该分类的过期缓存，不跨分类串数据。
 */
class RecommendationRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun selectedType(): RecommendationType = RecommendationType.fromStorage(prefs.getString(KEY_TYPE, null))

    fun selectedTag(type: RecommendationType): String = RecommendationTags.normalize(
        type,
        prefs.getString(if (type == RecommendationType.MOVIE) KEY_MOVIE_TAG else KEY_TV_TAG, null)
    )

    fun saveSelection(type: RecommendationType, tag: String) {
        val normalized = RecommendationTags.normalize(type, tag)
        prefs.edit()
            .putString(KEY_TYPE, type.apiValue)
            .putString(if (type == RecommendationType.MOVIE) KEY_MOVIE_TAG else KEY_TV_TAG, normalized)
            .apply()
    }

    suspend fun load(type: RecommendationType, tag: String, force: Boolean = false): RecommendationResult = withContext(Dispatchers.IO) {
        val normalizedTag = RecommendationTags.normalize(type, tag)
        val cached = readCache(type, normalizedTag)
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cached.timestamp < CACHE_TTL_MS && cached.items.size >= PAGE_SIZE) {
            return@withContext RecommendationResult(cached.items.take(PAGE_SIZE))
        }

        try {
            val fresh = fetchDouban(type, normalizedTag).take(PAGE_SIZE)
            if (fresh.isEmpty()) throw IOException("该分类暂无推荐内容")
            writeCache(type, normalizedTag, fresh)
            RecommendationResult(fresh)
        } catch (error: Throwable) {
            if (cached != null && cached.items.isNotEmpty()) {
                RecommendationResult(cached.items.take(PAGE_SIZE), stale = true, warning = "推荐更新失败，已显示本分类缓存")
            } else {
                RecommendationResult(emptyList(), warning = error.message ?: "推荐加载失败")
            }
        }
    }

    private fun fetchDouban(type: RecommendationType, tag: String): List<RecommendedMovie> {
        val encodedTag = URLEncoder.encode(tag, "UTF-8")
        val url = "$DOUBAN_API?type=${type.apiValue}&tag=$encodedTag&sort=recommend&page_limit=$PAGE_SIZE&page_start=0"
        val root = fetchDoubanJson(url).asJsonObject
        val subjects = root.array("subjects") ?: throw IOException("豆瓣推荐数据格式异常")
        return subjects.mapNotNull { element ->
            val item = element.asJsonObject
            val id = item.string("id")
            val title = item.string("title")
            val cover = item.string("cover")
            if (id.isBlank() || title.isBlank() || cover.isBlank()) return@mapNotNull null
            RecommendedMovie(
                id = id,
                title = title,
                score = item.string("rate").takeUnless { it == "0" } ?: "",
                poster = cover,
                posterCandidates = imageCandidates(cover),
                source = "douban",
                isTv = type == RecommendationType.TV
            )
        }
    }

    /**
     * LibreTV 旧版同款两级请求：先直连豆瓣，失败后走 AllOrigins。
     * AllOrigins 返回 {contents:"原始 JSON"}，代理通常约 7 秒，使用独立 20 秒客户端，
     * 不受采集站网络层 5 秒 callTimeout 影响。
     */
    private fun fetchDoubanJson(url: String): com.google.gson.JsonElement {
        try {
            return requestJson(NetworkModule.okHttp, url, withDoubanHeaders = true)
        } catch (_: Throwable) {
            val proxyUrl = "$ALL_ORIGINS?url=${URLEncoder.encode(url, "UTF-8")}"
            val wrapper = requestJson(recommendationClient, proxyUrl, withDoubanHeaders = false).asJsonObject
            val contents = wrapper.string("contents")
            if (contents.isBlank()) throw IOException("豆瓣代理未返回有效内容")
            return JsonParser.parseString(contents)
        }
    }

    private fun requestJson(client: OkHttpClient, url: String, withDoubanHeaders: Boolean) = client.newCall(
        Request.Builder().url(url).get()
            .header("User-Agent", DESKTOP_UA)
            .apply { if (withDoubanHeaders) header("Referer", "https://movie.douban.com/") }
            .header("Accept", "application/json")
            .build()
    ).execute().use { response ->
        if (!response.isSuccessful) throw IOException("推荐接口响应异常：HTTP ${response.code}")
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) throw IOException("推荐接口返回空内容")
        JsonParser.parseString(body)
    }

    /** Android 无 LibreTV 服务端代理，优先两个公共镜像，最后尝试带 Referer 的豆瓣原图。 */
    private fun imageCandidates(original: String): List<String> {
        if (original.isBlank()) return emptyList()
        val candidates = mutableListOf<String>()
        if (original.contains("doubanio.com")) {
            candidates += original.replace(Regex("img\\d+\\.doubanio\\.com"), "img.doubanio.cmliussss.net")
            candidates += original.replace(Regex("img\\d+\\.doubanio\\.com"), "img.doubanio.cmliussss.com")
        }
        candidates += original
        return candidates.distinct()
    }

    private fun writeCache(type: RecommendationType, tag: String, items: List<RecommendedMovie>) {
        prefs.edit()
            .putLong(cacheTimeKey(type, tag), System.currentTimeMillis())
            .putString(cacheDataKey(type, tag), gson.toJson(items))
            .apply()
    }

    private fun readCache(type: RecommendationType, tag: String): CacheEntry? {
        val json = prefs.getString(cacheDataKey(type, tag), null) ?: return null
        return try {
            val listType = object : TypeToken<List<RecommendedMovie>>() {}.type
            val items: List<RecommendedMovie> = gson.fromJson(json, listType) ?: emptyList()
            CacheEntry(prefs.getLong(cacheTimeKey(type, tag), 0L), items)
        } catch (_: Throwable) {
            null
        }
    }

    private fun cacheDataKey(type: RecommendationType, tag: String) = "douban_v2_${type.apiValue}_${tag}_data"
    private fun cacheTimeKey(type: RecommendationType, tag: String) = "douban_v2_${type.apiValue}_${tag}_time"
    private data class CacheEntry(val timestamp: Long, val items: List<RecommendedMovie>)

    companion object {
        private const val PREFS = "recommendations"
        private const val KEY_TYPE = "douban_type"
        private const val KEY_MOVIE_TAG = "douban_movie_tag"
        private const val KEY_TV_TAG = "douban_tv_tag"
        private const val PAGE_SIZE = 50
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private const val DOUBAN_API = "https://movie.douban.com/j/search_subjects"
        private const val ALL_ORIGINS = "https://api.allorigins.win/get"
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36"
        private val recommendationClient: OkHttpClient by lazy {
            NetworkModule.okHttp.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}

private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.array(name: String): JsonArray? = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
