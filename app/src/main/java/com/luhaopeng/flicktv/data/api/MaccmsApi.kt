package com.luhaopeng.flicktv.data.api

import com.luhaopeng.flicktv.data.model.MaccmsResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/** 苹果 CMS JSON API；@Url 允许每个源使用不同根地址。 */
interface MaccmsApi {
    @GET
    suspend fun search(
        @Url url: String,
        @Query("ac") action: String = "detail",
        @Query("wd") keyword: String,
        @Query("pg") page: Int = 1
    ): MaccmsResponse

    @GET
    suspend fun detail(
        @Url url: String,
        @Query("ac") action: String = "detail",
        @Query("ids") ids: String
    ): MaccmsResponse

    @GET
    suspend fun categories(
        @Url url: String,
        @Query("ac") action: String = "list"
    ): MaccmsResponse

    @GET
    suspend fun browse(
        @Url url: String,
        @Query("ac") action: String = "detail",
        @Query("t") typeId: String,
        @Query("pg") page: Int
    ): MaccmsResponse
}
