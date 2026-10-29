package com.luhaopeng.flicktv.data.merge

import com.luhaopeng.flicktv.data.model.MergedMovie
import com.luhaopeng.flicktv.data.model.SourceVod
import java.util.Locale

/** 多源结果按“去空格/标点后的完全相同片名”聚合。 */
object MultiSourceMerger {
    fun normalizeName(name: String): String = name
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\p{P}\\p{S}\\s]+"), "")

    fun merge(items: List<SourceVod>): List<MergedMovie> = items
        .filter { it.vod.vodName.isNotBlank() }
        .groupBy { normalizeName(it.vod.vodName) }
        .map { (normalized, group) ->
            val representative = group.maxByOrNull { score(it) } ?: group.first()
            MergedMovie(
                normalizedName = normalized,
                displayName = representative.vod.vodName,
                poster = group.firstOrNull { it.vod.vodPic.isNotBlank() }?.vod?.vodPic.orEmpty(),
                sources = group.sortedBy { it.source.name }
            )
        }
        .sortedWith(compareByDescending<MergedMovie> { it.sources.size }.thenBy { it.displayName })

    private fun score(item: SourceVod): Int =
        (if (item.vod.vodPic.isNotBlank()) 2 else 0) +
            (if (item.vod.vodYear.isNotBlank()) 1 else 0) + item.lineCount
}
