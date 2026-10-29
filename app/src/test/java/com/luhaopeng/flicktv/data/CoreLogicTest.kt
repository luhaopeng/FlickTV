package com.luhaopeng.flicktv.data

import com.google.gson.Gson
import com.luhaopeng.flicktv.data.merge.MultiSourceMerger
import com.luhaopeng.flicktv.data.model.*
import com.luhaopeng.flicktv.data.recommendation.RecommendationTags
import com.luhaopeng.flicktv.data.recommendation.RecommendationType
import org.junit.Assert.*
import org.junit.Test

class CoreLogicTest {
    @Test fun `同名清洗后应聚合多来源`() {
        val a = SourceVod(SourceConfig("a", "A", "https://a"), VodItem(vodName = "你好，李焕英"))
        val b = SourceVod(SourceConfig("b", "B", "https://b"), VodItem(vodName = "你好 李焕英"))
        val result = MultiSourceMerger.merge(listOf(a, b))
        assertEquals(1, result.size)
        assertEquals(2, result.first().sources.size)
    }

    @Test fun `LibreTV推荐应包含电影剧集各十个分类`() {
        assertEquals(10, RecommendationTags.movie.size)
        assertEquals(10, RecommendationTags.tv.size)
        assertEquals("热门", RecommendationTags.normalize(RecommendationType.MOVIE, "未知分类"))
        assertEquals("纪录片", RecommendationTags.normalize(RecommendationType.TV, "纪录片"))
    }

    @Test fun `旧远程配置中的网格列字段应被忽略`() {
        val json = """{"sources":[{"id":"demo","name":"示例源","api":"https://example.com/api.php/provide/vod"}],"gridColumns":8}"""
        val config = Gson().fromJson(json, VodConfig::class.java)
        assertEquals(1, config.sources.size)
        assertEquals("demo", config.sources.first().id)
        assertFalse(VodConfig::class.java.declaredFields.any { it.name == "gridColumns" })
    }

    @Test fun `播放串应解析线路和集数`() {
        val vod = VodItem(
            vodPlayFrom = "lineA${'$'}${'$'}${'$'}lineB",
            vodPlayUrl = "第1集${'$'}https://a/1.m3u8#第2集${'$'}https://a/2.m3u8${'$'}${'$'}${'$'}正片${'$'}https://b/1.m3u8"
        )
        val lines = PlayUrlParser.parse(vod)
        assertEquals(2, lines.size)
        assertEquals(2, lines[0].episodes.size)
        assertEquals("lineB", lines[1].name)
    }
}
