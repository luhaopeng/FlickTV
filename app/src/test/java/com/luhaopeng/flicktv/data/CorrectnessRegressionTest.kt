package com.luhaopeng.flicktv.data

import com.luhaopeng.flicktv.AppScreen
import com.luhaopeng.flicktv.data.adblock.M3u8AdFilter
import com.luhaopeng.flicktv.data.api.NetworkModule
import com.luhaopeng.flicktv.data.health.SourceHealth
import com.luhaopeng.flicktv.data.merge.MultiSourceMerger
import com.luhaopeng.flicktv.data.model.*
import org.junit.Assert.*
import org.junit.Test

class CorrectnessRegressionTest {
    private fun source(id: String, name: String = id) = SourceConfig(id, name, "https://$id.example/api.php/provide/vod")

    @Test fun `规范化片名应忽略中英文标点符号与空格`() {
        assertEquals("你好李焕英", MultiSourceMerger.normalizeName(" 你好，李焕英！ "))
        assertEquals("hello2026", MultiSourceMerger.normalizeName("Hello · 2026"))
    }

    @Test fun `空片名不应进入合并结果`() {
        val result = MultiSourceMerger.merge(listOf(SourceVod(source("a"), VodItem(vodName = "  "))))
        assertTrue(result.isEmpty())
    }

    @Test fun `同名来源应按来源名称稳定排序`() {
        val items = listOf(
            SourceVod(source("z", "中源"), VodItem(vodName = "影片")),
            SourceVod(source("a", "A源"), VodItem(vodName = "影片"))
        )
        assertEquals(listOf("A源", "中源"), MultiSourceMerger.merge(items).single().sources.map { it.source.name })
    }

    @Test fun `代表片名应选择信息和线路更完整的来源`() {
        val simple = SourceVod(source("a"), VodItem(vodName = "movie"))
        val rich = SourceVod(
            source("b"),
            VodItem(vodName = "Movie", vodPic = "poster", vodYear = "2026", vodPlayUrl = "1${'$'}https://a/1.m3u8${'$'}${'$'}${'$'}2${'$'}https://b/2.m3u8")
        )
        assertEquals("Movie", MultiSourceMerger.merge(listOf(simple, rich)).single().displayName)
    }

    @Test fun `多来源卡片应优先有图的海报`() {
        val noPoster = SourceVod(source("a"), VodItem(vodName = "影片"))
        val withPoster = SourceVod(source("b"), VodItem(vodName = "影片", vodPic = "https://img/poster.jpg"))
        assertEquals("https://img/poster.jpg", MultiSourceMerger.merge(listOf(noPoster, withPoster)).single().poster)
    }

    @Test fun `播放串应支持双美元线路分隔`() {
        val vod = VodItem(
            vodPlayFrom = "A${'$'}${'$'}B",
            vodPlayUrl = "1${'$'}https://a/1.m3u8${'$'}${'$'}2${'$'}https://b/2.m3u8"
        )
        val lines = PlayUrlParser.parse(vod)
        assertEquals(2, lines.size)
        assertEquals(listOf("A", "B"), lines.map { it.name })
    }

    @Test fun `裸地址应自动生成集标题`() {
        val lines = PlayUrlParser.parse(VodItem(vodPlayUrl = "https://a/1.m3u8#https://a/2.m3u8"))
        assertEquals(listOf("第1集", "第2集"), lines.single().episodes.map { it.title })
    }

    @Test fun `非法协议和无地址条目应被过滤`() {
        val vod = VodItem(vodPlayUrl = "坏条目#FTP${'$'}ftp://a/1.ts#正片${'$'}https://a/ok.m3u8")
        val episodes = PlayUrlParser.parse(vod).single().episodes
        assertEquals(1, episodes.size)
        assertEquals("正片", episodes.single().title)
    }

    @Test fun `空线路名应回退为默认线路名`() {
        val vod = VodItem(vodPlayFrom = "", vodPlayUrl = "正片${'$'}https://a/ok.m3u8")
        assertEquals("线路1", PlayUrlParser.parse(vod).single().name)
    }

    @Test fun `空播放串应返回空线路`() {
        assertTrue(PlayUrlParser.parse(VodItem()).isEmpty())
    }

    @Test fun `健康退避首级应为十分钟`() {
        val state = SourceHealth(3, 0, true, 1_000L, 0, true)
        assertEquals(1_000L + 10 * 60_000L, state.nextRetryAt())
    }

    @Test fun `健康退避第二级应为三十分钟`() {
        val state = SourceHealth(4, 0, true, 1_000L, 1, true)
        assertEquals(1_000L + 30 * 60_000L, state.nextRetryAt())
    }

    @Test fun `健康退避应覆盖一二六十二小时`() {
        val expected = listOf(60L, 120L, 360L, 720L)
        expected.forEachIndexed { index, minutes ->
            val state = SourceHealth(5 + index, 0, true, 0L, index + 2, true)
            assertEquals(minutes * 60_000L, state.nextRetryAt())
        }
    }

    @Test fun `健康退避超范围应钳制到十二小时`() {
        val state = SourceHealth(99, 0, true, 0L, 99, true)
        assertEquals(720L * 60_000L, state.nextRetryAt())
    }

    @Test fun `在线状态不应提供重试时间`() {
        assertEquals(0L, SourceHealth(0, 0, false, 0, 0, true).nextRetryAt())
    }

    @Test fun `明确广告区块内的分片应全部移除`() {
        val input = """#EXTM3U
#EXT-X-AD-START
#EXTINF:8.0,
ads/a.ts
#EXTINF:8.0,
ads/b.ts
#EXT-X-AD-END
#EXTINF:8.0,
main.ts
#EXT-X-ENDLIST"""
        val result = M3u8AdFilter.filter(input, "https://host/path/index.m3u8")
        assertEquals(2, result.removedSegments)
        assertFalse(result.text.contains("ads/a.ts"))
        assertTrue(result.text.contains("https://host/path/main.ts"))
    }

    @Test fun `普通短分片没有广告线索时必须保留`() {
        val input = """#EXTM3U
#EXTINF:1.5,
main/part.ts
#EXT-X-ENDLIST"""
        val result = M3u8AdFilter.filter(input, "https://host/path/index.m3u8")
        assertEquals(0, result.removedSegments)
        assertTrue(result.text.contains("https://host/path/main/part.ts"))
    }

    @Test fun `疑似广告路径的极短分片应移除`() {
        val input = """#EXTM3U
#EXTINF:1.0,
promo/part.ts
#EXTINF:8.0,
main.ts"""
        val result = M3u8AdFilter.filter(input, "https://host/path/index.m3u8")
        assertEquals(1, result.removedSegments)
        assertFalse(result.text.contains("promo/part.ts"))
    }

    @Test fun `绝对分片地址不得被重复解析`() {
        val input = """#EXTM3U
#EXTINF:8.0,
https://cdn.example/video.ts"""
        val result = M3u8AdFilter.filter(input, "https://host/path/index.m3u8")
        assertTrue(result.text.contains("https://cdn.example/video.ts"))
    }

    @Test fun `CRLF播放列表仍应正确处理`() {
        val input = "#EXTM3U\r\n#EXT-X-DISCONTINUITY\r\n#EXTINF:1.0,\r\nads/a.ts\r\n#EXTINF:8.0,\r\nmain.ts"
        val result = M3u8AdFilter.filter(input, "https://host/path/index.m3u8")
        assertEquals(1, result.removedSegments)
        assertTrue(result.text.contains("https://host/path/main.ts"))
    }

    @Test fun `播放清单客户端不应继承业务接口的五秒总超时`() {
        assertEquals(5_000L, NetworkModule.okHttp.callTimeoutMillis.toLong())
        assertEquals(0L, NetworkModule.playbackOkHttp.callTimeoutMillis.toLong())
        assertEquals(12_000L, NetworkModule.playbackOkHttp.connectTimeoutMillis.toLong())
        assertEquals(20_000L, NetworkModule.playbackOkHttp.readTimeoutMillis.toLong())
        assertTrue(NetworkModule.playbackOkHttp.retryOnConnectionFailure)
    }

    @Test fun `播放器页面应保留完整详情影片上下文`() {
        val a = SourceVod(source("a"), VodItem(vodId = "1", vodName = "影片"))
        val b = SourceVod(source("b"), VodItem(vodId = "2", vodName = "影片"))
        val merged = MergedMovie("影片", "影片", "", listOf(a, b))
        val screen = AppScreen.Player(merged, b, PlayLine("线路", listOf(Episode("1", "https://b/1.m3u8"))), 0)
        assertEquals(2, screen.detailMovie.sources.size)
        assertEquals("b", screen.movie.source.id)
    }
}
