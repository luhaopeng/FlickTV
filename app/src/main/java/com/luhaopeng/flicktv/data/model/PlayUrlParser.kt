package com.luhaopeng.flicktv.data.model

/** 将 vod_play_from / vod_play_url 解析成线路和分集。 */
object PlayUrlParser {
    fun parse(vod: VodItem): List<PlayLine> {
        val urls = splitLines(vod.vodPlayUrl)
        val names = splitLines(vod.vodPlayFrom)
        return urls.mapIndexedNotNull lineLoop@ { lineIndex, lineText ->
            val episodes = lineText.split('#')
                .mapIndexedNotNull episodeLoop@ { index, raw ->
                    val token = raw.trim()
                    if (token.isBlank()) return@episodeLoop null
                    val dollar = token.indexOf('$')
                    if (dollar > 0 && dollar < token.lastIndex) {
                        Episode(token.substring(0, dollar).trim(), token.substring(dollar + 1).trim())
                    } else if (token.startsWith("http")) {
                        Episode("第${index + 1}集", token)
                    } else null
                }
                .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
            if (episodes.isEmpty()) null else PlayLine(names.getOrNull(lineIndex)?.ifBlank { "线路${lineIndex + 1}" } ?: "线路${lineIndex + 1}", episodes)
        }
    }

    private fun splitLines(value: String): List<String> = when {
        value.contains("$$$") -> value.split("$$$")
        value.contains("$$") -> value.split("$$")
        value.isBlank() -> emptyList()
        else -> listOf(value)
    }
}
