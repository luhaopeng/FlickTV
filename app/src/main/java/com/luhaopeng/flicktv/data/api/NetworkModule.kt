package com.luhaopeng.flicktv.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** 全局网络入口：浏览器 UA + 单请求 5 秒超时。 */
object NetworkModule {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 Chrome/114 Safari/537.36 FlickTV/1.0"

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json,text/plain,*/*")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    val api: MaccmsApi by lazy {
        Retrofit.Builder()
            // 实际请求地址由 @Url 提供，这里只需一个合法占位基址。
            .baseUrl("https://localhost/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MaccmsApi::class.java)
    }
}
