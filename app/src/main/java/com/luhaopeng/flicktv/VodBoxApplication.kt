package com.luhaopeng.flicktv

import android.app.Application
import com.luhaopeng.flicktv.data.api.NetworkModule
import com.luhaopeng.flicktv.data.config.SourceConfigManager
import com.luhaopeng.flicktv.data.db.PlaybackStore
import com.luhaopeng.flicktv.data.db.VodBoxDatabase
import com.luhaopeng.flicktv.data.health.SourceHealthManager
import com.luhaopeng.flicktv.data.recommendation.RecommendationRepository
import com.luhaopeng.flicktv.data.repository.VodRepository

/** 轻量依赖容器，避免为个人 TV 应用引入额外 DI 框架。 */
class VodBoxApplication : Application() {
    val database by lazy { VodBoxDatabase.get(this) }
    val configManager by lazy { SourceConfigManager(this) }
    val healthManager by lazy { SourceHealthManager(this) }
    val repository by lazy { VodRepository(NetworkModule.api, healthManager) }
    val recommendationRepository by lazy { RecommendationRepository(this) }
    val playbackStore by lazy { PlaybackStore(this, database.historyDao()) }
}
