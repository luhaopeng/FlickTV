package com.luhaopeng.flicktv.data.adblock

import java.net.URI

/** HLS 清洗结果。 */
data class FilteredPlaylist(val text: String, val removedSegments: Int)

/**
 * 保守型 m3u8 广告过滤器：
 * 1) 明确的 #EXT-X-AD 区段；2) DISCONTINUITY 相邻的极短片段；3) 低于 2 秒且疑似广告 URI。
 * 不确定时原样保留，避免误删正片。
 */
object M3u8AdFilter {
    private val adWords = Regex("(?i)(^|[/_.-])(ad|ads|advert|commercial|promo)([/_.-]|$)")

    fun filter(text: String, baseUrl: String): FilteredPlaylist {
        if (!text.trimStart().startsWith("#EXTM3U")) return FilteredPlaylist(text, 0)
        val lines = text.lines()
        val output = mutableListOf<String>()
        var index = 0
        var removed = 0
        var discontinuityNearby = false
        var adBlock = false

        while (index < lines.size) {
            val line = lines[index].trim()
            when {
                line.startsWith("#EXT-X-AD", ignoreCase = true) -> {
                    adBlock = !line.contains("END", ignoreCase = true)
                    index++
                }
                line == "#EXT-X-DISCONTINUITY" -> {
                    discontinuityNearby = true
                    output += lines[index]
                    index++
                }
                line.startsWith("#EXTINF:") && index + 1 < lines.size -> {
                    val duration = line.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: Double.MAX_VALUE
                    val uriLine = lines[index + 1].trim()
                    val shortSuspicious = duration < 2.0 && (discontinuityNearby || adWords.containsMatchIn(uriLine))
                    if (adBlock || shortSuspicious) {
                        removed++
                    } else {
                        output += lines[index]
                        output += absolutizeIfNeeded(uriLine, baseUrl)
                    }
                    discontinuityNearby = false
                    index += 2
                }
                else -> {
                    // 分片 URI 在上面已消费；这里主要复制标签、空行和主播放列表内容。
                    output += lines[index]
                    index++
                }
            }
        }
        return FilteredPlaylist(output.joinToString("\n"), removed)
    }

    private fun absolutizeIfNeeded(value: String, baseUrl: String): String {
        if (value.isBlank() || value.startsWith("#") || value.startsWith("http://") || value.startsWith("https://")) return value
        return runCatching { URI(baseUrl).resolve(value).toString() }.getOrDefault(value)
    }
}
