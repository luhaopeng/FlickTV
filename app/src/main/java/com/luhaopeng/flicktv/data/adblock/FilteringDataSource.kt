package com.luhaopeng.flicktv.data.adblock

import android.net.Uri
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import com.luhaopeng.flicktv.data.api.NetworkModule
import okhttp3.Request
import java.io.ByteArrayInputStream

/**
 * 仅拦截 m3u8 文本并返回清洗后的字节；TS/KEY 等资源继续走普通 HTTP DataSource。
 * 清洗异常时自动使用原文，保证播放不中断。
 */
class FilteringDataSource(
    private val upstreamFactory: DataSource.Factory
) : DataSource {
    private var upstream: DataSource? = null
    private var playlistStream: ByteArrayInputStream? = null
    private var openedUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstreamFactory.createDataSource().addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        openedUri = dataSpec.uri
        val url = dataSpec.uri.toString()
        if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            return runCatching {
                NetworkModule.okHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful)
                    val original = response.body?.string().orEmpty()
                    val clean = M3u8AdFilter.filter(original, url).text.toByteArray(Charsets.UTF_8)
                    playlistStream = ByteArrayInputStream(clean)
                    clean.size.toLong()
                }
            }.getOrElse {
                openUpstream(dataSpec)
            }
        }
        return openUpstream(dataSpec)
    }

    private fun openUpstream(dataSpec: DataSpec): Long {
        upstream = upstreamFactory.createDataSource()
        return upstream!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        return playlistStream?.read(buffer, offset, readLength) ?: upstream?.read(buffer, offset, readLength) ?: -1
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        playlistStream?.close()
        playlistStream = null
        upstream?.close()
        upstream = null
        openedUri = null
    }

    class Factory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = FilteringDataSource(upstreamFactory)
    }
}
