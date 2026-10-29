package com.luhaopeng.flicktv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luhaopeng.flicktv.data.db.FavoriteEntity
import com.luhaopeng.flicktv.data.merge.MultiSourceMerger
import com.luhaopeng.flicktv.data.model.*
import com.luhaopeng.flicktv.data.recommendation.RecommendationTags
import com.luhaopeng.flicktv.data.recommendation.RecommendationType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AppScreen {
    object Home : AppScreen()
    object Search : AppScreen()
    data class Detail(val movie: MergedMovie) : AppScreen()
    data class Player(val movie: SourceVod, val line: PlayLine, val episodeIndex: Int) : AppScreen()
    object History : AppScreen()
    object Settings : AppScreen()
}

data class MainUiState(
    val screen: AppScreen = AppScreen.Home,
    val config: VodConfig = VodConfig(),
    val searchQuery: String = "",
    val searchResults: List<MergedMovie> = emptyList(),
    val searching: Boolean = false,
    val message: String = "",
    val selectedSourceVod: SourceVod? = null,
    val detailVod: VodItem? = null,
    val detailLoading: Boolean = false,
    val categories: List<VodCategory> = emptyList(),
    val browseItems: List<VodItem> = emptyList(),
    val browseSource: SourceConfig? = null,
    val browseCategory: VodCategory? = null,
    val browsePage: Int = 1,
    val browsePageCount: Int = 1,
    val browseTotal: Int = 0,
    val browseLimit: Int = 0,
    /** 分类浏览独立状态，避免错误复用全局悬浮消息遮挡筛选栏。 */
    val browseLoading: Boolean = false,
    val browseMessage: String = "",
    /** SharedPreferences 中的源健康数据变化时递增，驱动设置页立即读取新快照。 */
    val sourceHealthRevision: Int = 0,
    val sourceCheckingId: String? = null,
    val sourceCheckMessage: String = "",
    val remoteUrl: String = "",
    val recommendationType: RecommendationType = RecommendationType.MOVIE,
    val recommendationTag: String = "热门",
    val recommendations: List<RecommendedMovie> = emptyList(),
    val recommendationsLoading: Boolean = false,
    val recommendationsMessage: String = "",
    /** 详情页进入前的应用内页面；返回时回到该页，而不是结束 Activity。 */
    val detailReturnScreen: AppScreen = AppScreen.Home
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as VodBoxApplication
    private val initialRecommendationType = container.recommendationRepository.selectedType()
    private val _state = MutableStateFlow(
        MainUiState(
            config = container.configManager.load(),
            remoteUrl = container.configManager.remoteUrl(),
            recommendationType = initialRecommendationType,
            recommendationTag = container.recommendationRepository.selectedTag(initialRecommendationType)
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    val history = container.database.historyDao().observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favorites = container.database.favoriteDao().observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadRecommendations()
    }

    fun navigate(screen: AppScreen) { _state.update { it.copy(screen = screen, message = "") } }
    fun openRecommendations() {
        _state.update {
            it.copy(
                screen = AppScreen.Home,
                browseSource = null,
                browseCategory = null,
                categories = emptyList(),
                browseItems = emptyList(),
                browseLoading = false,
                browseMessage = "",
                message = ""
            )
        }
    }
    fun setSearchQuery(value: String) {
        _state.update { it.copy(searchQuery = value, message = "") }
    }
    fun updateRemoteDraft(value: String) { _state.update { it.copy(remoteUrl = value) } }

    /** 切换电影/剧集；各自恢复上次使用的分类标签。 */
    fun setRecommendationType(type: RecommendationType) {
        if (_state.value.recommendationType == type) return
        val tag = container.recommendationRepository.selectedTag(type)
        container.recommendationRepository.saveSelection(type, tag)
        _state.update {
            it.copy(
                recommendationType = type,
                recommendationTag = tag,
                recommendations = emptyList(),
                recommendationsMessage = ""
            )
        }
        loadRecommendations()
    }

    /** 切换当前电影/剧集下的 LibreTV 分类。 */
    fun setRecommendationTag(tag: String) {
        val type = _state.value.recommendationType
        val normalized = RecommendationTags.normalize(type, tag)
        if (_state.value.recommendationTag == normalized) return
        container.recommendationRepository.saveSelection(type, normalized)
        _state.update {
            it.copy(
                recommendationTag = normalized,
                recommendations = emptyList(),
                recommendationsMessage = ""
            )
        }
        loadRecommendations()
    }

    fun refreshRecommendations() = loadRecommendations(force = true)

    private fun loadRecommendations(force: Boolean = false) {
        val type = _state.value.recommendationType
        val tag = _state.value.recommendationTag
        _state.update { it.copy(recommendationsLoading = true, recommendationsMessage = "") }
        viewModelScope.launch {
            val result = container.recommendationRepository.load(type, tag, force)
            // 快速切换分类时，旧请求不能覆盖新分类。
            if (_state.value.recommendationType != type || _state.value.recommendationTag != tag) return@launch
            _state.update {
                it.copy(
                    recommendations = result.items,
                    recommendationsLoading = false,
                    recommendationsMessage = result.warning
                )
            }
        }
    }

    fun search(keyword: String = _state.value.searchQuery, openSingle: Boolean = false) {
        if (keyword.isBlank()) return
        // 推荐/收藏点击会经过一次后台聚合搜索；详情返回应回到真正的入口页，而不是中间搜索态。
        val originScreen = _state.value.screen
        _state.update { it.copy(searchQuery = keyword, searching = true, screen = AppScreen.Search, message = "") }
        viewModelScope.launch {
            val results = container.repository.searchAll(keyword, _state.value.config.sources)
            _state.update { it.copy(searchResults = results, searching = false, message = if (results.isEmpty()) "未找到可播放资源" else "") }
            if (openSingle && results.isNotEmpty()) openDetailReturningTo(results.first(), originScreen)
        }
    }

    fun openDetail(movie: MergedMovie) {
        openDetailReturningTo(movie, _state.value.screen)
    }

    private fun openDetailReturningTo(movie: MergedMovie, requestedReturnScreen: AppScreen) {
        val sourceVod = movie.sources.firstOrNull() ?: return
        val returnScreen = when (requestedReturnScreen) {
            AppScreen.Home, AppScreen.Search, AppScreen.History, AppScreen.Settings -> requestedReturnScreen
            is AppScreen.Detail -> _state.value.detailReturnScreen
            is AppScreen.Player -> AppScreen.Home
        }
        _state.update {
            it.copy(
                screen = AppScreen.Detail(movie),
                selectedSourceVod = sourceVod,
                detailReturnScreen = returnScreen
            )
        }
        loadDetail(sourceVod)
    }

    fun returnFromDetail() {
        _state.update {
            it.copy(
                screen = it.detailReturnScreen,
                selectedSourceVod = null,
                detailVod = null,
                detailLoading = false,
                message = ""
            )
        }
    }

    fun selectDetailSource(sourceVod: SourceVod) {
        _state.update { it.copy(selectedSourceVod = sourceVod) }
        loadDetail(sourceVod)
    }

    private fun loadDetail(sourceVod: SourceVod) {
        _state.update { it.copy(detailLoading = true, detailVod = sourceVod.vod, message = "") }
        viewModelScope.launch {
            container.repository.detail(sourceVod)
                .onSuccess { vod ->
                    _state.update { it.copy(detailVod = vod, selectedSourceVod = sourceVod.copy(vod = vod), detailLoading = false) }
                }
                .onFailure { error -> _state.update { it.copy(detailLoading = false, message = error.message ?: "详情加载失败") } }
        }
    }

    fun loadCategories(source: SourceConfig) {
        _state.update {
            it.copy(
                browseSource = source,
                browseCategory = null,
                browsePage = 1,
                browsePageCount = 1,
                browseTotal = 0,
                browseLimit = 0,
                categories = emptyList(),
                browseItems = emptyList(),
                browseLoading = true,
                browseMessage = ""
            )
        }
        viewModelScope.launch {
            container.repository.categories(source)
                .onSuccess { categories ->
                    // 快速切源时，旧源返回不能覆盖当前页面。
                    if (_state.value.browseSource?.id != source.id) return@onSuccess
                    val first = categories.firstOrNull()
                    if (first == null) {
                        _state.update {
                            it.copy(
                                categories = emptyList(),
                                browseCategory = null,
                                browseItems = emptyList(),
                                browseLoading = false,
                                browseMessage = "该源没有可用分类"
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                categories = categories,
                                browseCategory = first,
                                browsePage = 1,
                                browsePageCount = 1,
                                browseTotal = 0,
                                browseLimit = 0,
                                browseItems = emptyList(),
                                browseLoading = true,
                                browseMessage = ""
                            )
                        }
                        browse(source, first, 1)
                    }
                }
                .onFailure { error ->
                    if (_state.value.browseSource?.id != source.id) return@onFailure
                    _state.update {
                        it.copy(
                            categories = emptyList(),
                            browseCategory = null,
                            browseItems = emptyList(),
                            browseLoading = false,
                            browseMessage = "分类加载失败：${error.message ?: "请稍后重试"}"
                        )
                    }
                }
        }
    }

    fun browse(source: SourceConfig, category: VodCategory, page: Int) {
        _state.update {
            it.copy(
                browseSource = source,
                browseCategory = category,
                browsePage = page,
                browsePageCount = 1,
                browseTotal = 0,
                browseLimit = 0,
                browseItems = emptyList(),
                browseLoading = true,
                browseMessage = ""
            )
        }
        viewModelScope.launch {
            container.repository.browse(source, category.typeId, page)
                .onSuccess { response ->
                    val current = _state.value
                    if (current.browseSource?.id != source.id ||
                        current.browseCategory?.typeId != category.typeId ||
                        current.browsePage != page
                    ) return@onSuccess
                    _state.update {
                        it.copy(
                            browseItems = response.list,
                            browsePageCount = response.pageCount.coerceAtLeast(1),
                            browseTotal = response.total,
                            browseLimit = response.limit,
                            browseLoading = false,
                            browseMessage = if (response.list.isEmpty()) "该分类第 ${page} 页暂无影片" else ""
                        )
                    }
                }
                .onFailure { error ->
                    val current = _state.value
                    if (current.browseSource?.id != source.id ||
                        current.browseCategory?.typeId != category.typeId ||
                        current.browsePage != page
                    ) return@onFailure
                    _state.update {
                        it.copy(
                            browseItems = emptyList(),
                            browseLoading = false,
                            browseMessage = "影片加载失败：${error.message ?: "请稍后重试"}"
                        )
                    }
                }
        }
    }

    fun openBrowseItem(item: VodItem) {
        val source = _state.value.browseSource ?: return
        openDetail(MergedMovie(MultiSourceMerger.normalizeName(item.vodName), item.vodName, item.vodPic, listOf(SourceVod(source, item))))
    }

    fun toggleFavorite(movie: MergedMovie) {
        viewModelScope.launch {
            val exists = favorites.value.any { it.normalizedName == movie.normalizedName }
            if (exists) container.database.favoriteDao().delete(movie.normalizedName)
            else container.database.favoriteDao().upsert(FavoriteEntity(movie.normalizedName, movie.displayName, movie.poster, System.currentTimeMillis()))
        }
    }

    fun setManualSource(sourceId: String, enabled: Boolean) {
        container.healthManager.setManualEnabled(sourceId, enabled)
        _state.update { it.copy(sourceHealthRevision = it.sourceHealthRevision + 1) }
    }

    /** 设置页立即检测；手动停用的源会先启用，再执行一次真实网络探测。 */
    fun checkSource(sourceId: String) {
        if (_state.value.sourceCheckingId != null) return
        val source = _state.value.config.sources.firstOrNull { it.id == sourceId } ?: return
        val current = container.healthManager.state(sourceId)
        if (!current.manualEnabled) container.healthManager.setManualEnabled(sourceId, true)
        _state.update {
            it.copy(
                sourceHealthRevision = it.sourceHealthRevision + 1,
                sourceCheckingId = sourceId,
                sourceCheckMessage = "正在检测 ${source.name}…"
            )
        }
        viewModelScope.launch {
            val result = container.repository.probe(source)
            val finalMessage = if (result.isSuccess) {
                "${source.name} 检测成功，已恢复在线"
            } else {
                "${source.name} 检测失败：${result.exceptionOrNull()?.message ?: "请稍后重试"}"
            }
            _state.update {
                it.copy(
                    sourceHealthRevision = it.sourceHealthRevision + 1,
                    sourceCheckingId = null,
                    sourceCheckMessage = finalMessage
                )
            }
            // 只清除本次写入的结果；如果 5 秒内开始了新检测或产生新消息，不误删新状态。
            delay(5_000L)
            _state.update {
                if (it.sourceCheckingId == null && it.sourceCheckMessage == finalMessage) {
                    it.copy(sourceCheckMessage = "")
                } else it
            }
        }
    }

    fun sourceHealth(sourceId: String) = container.healthManager.state(sourceId)

    fun updateRemote(url: String) {
        _state.update { it.copy(remoteUrl = url, message = "正在更新配置…") }
        viewModelScope.launch {
            val result = container.configManager.updateFromRemote(url)
            val finalMessage: String
            val config: VodConfig
            if (result.isSuccess) {
                config = result.getOrThrow()
                finalMessage = "远程配置更新成功"
            } else {
                config = container.configManager.load()
                finalMessage = "更新失败，已使用本地配置：${result.exceptionOrNull()?.message ?: "请稍后重试"}"
            }
            _state.update { it.copy(config = config, message = finalMessage) }
            // 只清除本次更新结果；5 秒内若出现更新或其他新消息，不误删新状态。
            delay(5_000L)
            _state.update { if (it.message == finalMessage) it.copy(message = "") else it }
        }
    }

    fun restoreDefault() {
        val config = container.configManager.restoreDefault()
        container.healthManager.resetAll(config.sources.map { it.id })
        _state.update {
            it.copy(
                config = config,
                sourceHealthRevision = it.sourceHealthRevision + 1,
                message = "已恢复默认配置"
            )
        }
    }
}
