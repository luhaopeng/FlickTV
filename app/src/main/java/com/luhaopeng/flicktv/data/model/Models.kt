package com.luhaopeng.flicktv.data.model

import com.google.gson.annotations.SerializedName

/** 单个苹果 CMS 采集源。manualEnabled 由本地设置覆盖。 */
data class SourceConfig(
    val id: String,
    val name: String,
    val api: String,
    var manualEnabled: Boolean = true
)

/** 源配置根对象；海报网格固定为 TV 端 5 列，不属于远程源配置。 */
data class VodConfig(
    val sources: List<SourceConfig> = emptyList()
)

/** 苹果 CMS 列表/详情中的影片对象。 */
data class VodItem(
    @SerializedName("vod_id") val vodId: String = "",
    @SerializedName("vod_name") val vodName: String = "",
    @SerializedName("vod_pic") val vodPic: String = "",
    @SerializedName("vod_remarks") val vodRemarks: String = "",
    @SerializedName("vod_year") val vodYear: String = "",
    @SerializedName("vod_area") val vodArea: String = "",
    @SerializedName("vod_class") val vodClass: String = "",
    @SerializedName("vod_content") val vodContent: String = "",
    @SerializedName("vod_actor") val vodActor: String = "",
    @SerializedName("vod_director") val vodDirector: String = "",
    @SerializedName("vod_play_from") val vodPlayFrom: String = "",
    @SerializedName("vod_play_url") val vodPlayUrl: String = ""
)

/** CMS 分类。 */
data class VodCategory(
    @SerializedName("type_id") val typeId: String = "",
    @SerializedName("type_name") val typeName: String = ""
)

/** CMS 通用响应。不同采集站可能缺少部分字段，所以均有默认值。 */
data class MaccmsResponse(
    val code: Int = 0,
    val msg: String = "",
    val page: Int = 1,
    @SerializedName("pagecount") val pageCount: Int = 1,
    val total: Int = 0,
    val limit: Int = 0,
    val list: List<VodItem> = emptyList(),
    @SerializedName("class") val categories: List<VodCategory> = emptyList()
)

/** 某个影片结果及其来源。 */
data class SourceVod(
    val source: SourceConfig,
    val vod: VodItem
) {
    val lineCount: Int
        get() = when {
            vod.vodPlayUrl.isBlank() -> 0
            vod.vodPlayUrl.contains("$$$") -> vod.vodPlayUrl.split("$$$").size
            else -> 1
        }
}

/** 多源按片名聚合后的卡片。 */
data class MergedMovie(
    val normalizedName: String,
    val displayName: String,
    val poster: String,
    val sources: List<SourceVod>
)

/** 播放线路。 */
data class PlayLine(
    val name: String,
    val episodes: List<Episode>
)

/** 单集播放信息。 */
data class Episode(
    val title: String,
    val url: String
)

/** 首页动态推荐条目；点击后按 title 执行多源并行搜索。 */
data class RecommendedMovie(
    val id: String,
    val title: String,
    val year: String = "",
    val score: String = "",
    val poster: String = "",
    /** 图片失败时按顺序尝试；豆瓣图为公共镜像 → 原图 → 备用图。 */
    val posterCandidates: List<String> = if (poster.isBlank()) emptyList() else listOf(poster),
    val source: String = "hot-list",
    val isTv: Boolean = false
)
