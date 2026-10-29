package com.luhaopeng.flicktv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusOrder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luhaopeng.flicktv.data.merge.MultiSourceMerger
import com.luhaopeng.flicktv.data.model.*
import com.luhaopeng.flicktv.ui.components.TvPill
import com.luhaopeng.flicktv.ui.detail.DetailScreen
import com.luhaopeng.flicktv.ui.history.RecordsScreen
import com.luhaopeng.flicktv.ui.home.CategoryScreen
import com.luhaopeng.flicktv.ui.home.RecommendationsScreen
import com.luhaopeng.flicktv.ui.player.PlayerScreen
import com.luhaopeng.flicktv.ui.search.SearchScreen
import com.luhaopeng.flicktv.ui.settings.SettingsScreen
import com.luhaopeng.flicktv.ui.theme.VodBackground
import com.luhaopeng.flicktv.ui.theme.VodBoxTheme
import com.luhaopeng.flicktv.ui.theme.VodDimens
import com.luhaopeng.flicktv.ui.theme.VodTextPrimary
import com.luhaopeng.flicktv.ui.theme.VodType

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VodBoxTheme { VodBoxApp(viewModel) } }
    }
}

@Composable
private fun VodBoxApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val history by viewModel.history.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as VodBoxApplication

    Box(Modifier.fillMaxSize().background(VodBackground)) {
        when (val screen = state.screen) {
            is AppScreen.Player -> PlayerScreen(screen.movie, screen.line, screen.episodeIndex, app.playbackStore) {
                viewModel.navigate(AppScreen.Detail(screen.detailMovie))
            }
            is AppScreen.Detail -> {
                BackHandler(onBack = viewModel::returnFromDetail)
                DetailScreen(
                    movie = screen.movie,
                    selected = state.selectedSourceVod,
                    detail = state.detailVod,
                    playbackStore = app.playbackStore,
                    history = history,
                    favorite = favorites.any { it.normalizedName == screen.movie.normalizedName },
                    onSource = viewModel::selectDetailSource,
                    onFavorite = { viewModel.toggleFavorite(screen.movie) },
                    onPlay = { movie, line, index ->
                        viewModel.navigate(AppScreen.Player(screen.movie, movie, line, index))
                    }
                )
            }
            else -> MainShell(state, history, favorites, viewModel)
        }
    }
}

private const val POSTER_GRID_COLUMNS = 5

@Composable
private fun MainShell(
    state: MainUiState,
    history: List<com.luhaopeng.flicktv.data.db.PlayHistoryEntity>,
    favorites: List<com.luhaopeng.flicktv.data.db.FavoriteEntity>,
    viewModel: MainViewModel
) {
    val screen = state.screen
    val homeTabFocus = remember { FocusRequester() }
    val categoryTabFocus = remember { FocusRequester() }
    val searchTabFocus = remember { FocusRequester() }
    val historyTabFocus = remember { FocusRequester() }
    val settingsTabFocus = remember { FocusRequester() }
    val contentEntryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { homeTabFocus.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(
                VodDimens.ScreenHorizontal,
                VodDimens.ScreenVertical,
                VodDimens.ScreenHorizontal,
                VodDimens.ItemGap
            ),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                "FlickTV",
                color = VodTextPrimary,
                fontSize = VodType.Brand,
                modifier = Modifier.padding(end = 28.dp)
            )
            LazyRow {
                item {
                    TvPill(
                        "热门推荐", viewModel::openRecommendations,
                        state.browseSource == null && screen is AppScreen.Home,
                        Modifier.padding(horizontal = 5.dp).focusOrder(homeTabFocus) {
                            right = categoryTabFocus
                            down = contentEntryFocus
                        }
                    )
                }
                item {
                    TvPill("分类浏览", {
                        val source = state.browseSource ?: state.config.sources.firstOrNull()
                        if (source != null && state.categories.isEmpty()) viewModel.loadCategories(source)
                        viewModel.navigate(AppScreen.Home)
                    }, state.browseSource != null && screen is AppScreen.Home, Modifier.padding(horizontal = 5.dp).focusOrder(categoryTabFocus) {
                        left = homeTabFocus
                        right = searchTabFocus
                        down = contentEntryFocus
                    })
                }
                item { TvPill("搜索", { viewModel.navigate(AppScreen.Search) }, screen is AppScreen.Search, Modifier.padding(horizontal = 5.dp).focusOrder(searchTabFocus) { left = categoryTabFocus; right = historyTabFocus; down = contentEntryFocus }) }
                item { TvPill("我的记录", { viewModel.navigate(AppScreen.History) }, screen is AppScreen.History, Modifier.padding(horizontal = 5.dp).focusOrder(historyTabFocus) { left = searchTabFocus; right = settingsTabFocus; down = contentEntryFocus }) }
                item { TvPill("设置", { viewModel.navigate(AppScreen.Settings) }, screen is AppScreen.Settings, Modifier.padding(horizontal = 5.dp).focusOrder(settingsTabFocus) { left = historyTabFocus; down = contentEntryFocus }) }
            }
        }
        Box(Modifier.weight(1f)) {
            when (screen) {
                AppScreen.Home -> {
                    if (state.browseSource == null) {
                        RecommendationsScreen(
                            columns = POSTER_GRID_COLUMNS,
                            tabFocusRequester = homeTabFocus,
                            contentEntryFocusRequester = contentEntryFocus,
                            type = state.recommendationType,
                            tag = state.recommendationTag,
                            movies = state.recommendations,
                            loading = state.recommendationsLoading,
                            message = state.recommendationsMessage,
                            onTypeChange = viewModel::setRecommendationType,
                            onTagChange = viewModel::setRecommendationTag,
                            onRefresh = viewModel::refreshRecommendations,
                            onMovieClick = { viewModel.search(it, openSingle = true) }
                        )
                    } else {
                        val browseSource = state.browseSource
                        CategoryScreen(
                            sources = state.config.sources,
                            selectedSource = browseSource,
                            categories = state.categories,
                            selectedCategory = state.browseCategory,
                            items = state.browseItems,
                            page = state.browsePage,
                            pageCount = state.browsePageCount,
                            total = state.browseTotal,
                            limit = state.browseLimit,
                            columns = POSTER_GRID_COLUMNS,
                            loading = state.browseLoading,
                            message = state.browseMessage,
                            tabFocusRequester = categoryTabFocus,
                            contentEntryFocusRequester = contentEntryFocus,
                            onSource = viewModel::loadCategories,
                            onCategory = { category -> viewModel.browse(browseSource, category, 1) },
                            onPage = { page -> state.browseCategory?.let { viewModel.browse(browseSource, it, page) } },
                            onRetry = {
                                val category = state.browseCategory
                                if (category == null || state.categories.isEmpty()) {
                                    viewModel.loadCategories(browseSource)
                                } else {
                                    viewModel.browse(browseSource, category, state.browsePage)
                                }
                            },
                            onItem = viewModel::openBrowseItem
                        )
                    }
                }
                AppScreen.Search -> SearchScreen(
                    query = state.searchQuery,
                    results = state.searchResults,
                    searching = state.searching,
                    message = state.message,
                    columns = POSTER_GRID_COLUMNS,
                    tabFocusRequester = searchTabFocus,
                    contentEntryFocusRequester = contentEntryFocus,
                    onQueryChange = viewModel::setSearchQuery,
                    onSearch = { viewModel.search() },
                    onMovieClick = viewModel::openDetail
                )
                AppScreen.History -> RecordsScreen(
                    history, favorites, POSTER_GRID_COLUMNS,
                    tabFocusRequester = historyTabFocus,
                    contentEntryFocusRequester = contentEntryFocus,
                    onHistory = { item ->
                        val source = state.config.sources.firstOrNull { it.id == item.sourceId } ?: SourceConfig(item.sourceId, item.sourceName, "")
                        val vod = VodItem(vodId = item.vodId, vodName = item.movieName, vodPic = item.poster)
                        val sourceVod = SourceVod(source, vod)
                        val detailMovie = MergedMovie(
                            normalizedName = MultiSourceMerger.normalizeName(item.movieName),
                            displayName = item.movieName,
                            poster = item.poster,
                            sources = listOf(sourceVod)
                        )
                        viewModel.navigate(
                            AppScreen.Player(
                                detailMovie,
                                sourceVod,
                                PlayLine(item.lineName, listOf(Episode(item.episodeTitle, item.episodeUrl))),
                                0
                            )
                        )
                    },
                    onFavorite = { viewModel.search(it.movieName, openSingle = true) }
                )
                AppScreen.Settings -> SettingsScreen(
                    state.remoteUrl,
                    state.config.sources,
                    state.message,
                    state.sourceHealthRevision,
                    state.sourceCheckingId,
                    state.sourceCheckMessage,
                    viewModel::sourceHealth,
                    { url -> viewModel.setRemoteUrl(url) },
                    { viewModel.updateRemote(state.remoteUrl) },
                    viewModel::setManualSource,
                    viewModel::checkSource,
                    viewModel::restoreDefault,
                    tabFocusRequester = settingsTabFocus,
                    contentEntryFocusRequester = contentEntryFocus
                )
                else -> Unit
            }
            if (state.message.isNotBlank() && screen !is AppScreen.Settings && screen !is AppScreen.Search && !(screen is AppScreen.Home && state.browseSource != null)) Text(state.message, color = Color.White, modifier = Modifier.padding(30.dp).background(Color.Black.copy(alpha = .8f)).padding(12.dp))
        }
    }
}

private fun MainViewModel.setRemoteUrl(url: String) {
    // 文本编辑仅更新 UI；真正持久化发生在“立即更新”。
    this.updateRemoteDraft(url)
}
