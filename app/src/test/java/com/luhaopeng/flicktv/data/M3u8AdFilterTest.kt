package com.luhaopeng.flicktv.data

import com.luhaopeng.flicktv.data.adblock.M3u8AdFilter
import org.junit.Assert.*
import org.junit.Test

class M3u8AdFilterTest {
    @Test fun `应删除断点旁极短广告分片并保留正片`() {
        val input = """#EXTM3U
#EXT-X-DISCONTINUITY
#EXTINF:1.0,
ads/promo.ts
#EXTINF:8.0,
video/0001.ts
#EXT-X-ENDLIST"""
        val result = M3u8AdFilter.filter(input, "https://host/path/index.m3u8")
        assertEquals(1, result.removedSegments)
        assertFalse(result.text.contains("promo.ts"))
        assertTrue(result.text.contains("https://host/path/video/0001.ts"))
    }

    @Test fun `非m3u8内容必须原样返回`() {
        val input = "not a playlist"
        assertEquals(input, M3u8AdFilter.filter(input, "https://x").text)
    }
}
