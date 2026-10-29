package com.luhaopeng.flicktv.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusOrder
import androidx.compose.ui.unit.dp
import com.luhaopeng.flicktv.data.db.FavoriteEntity
import com.luhaopeng.flicktv.data.db.PlayHistoryEntity
import com.luhaopeng.flicktv.ui.components.PosterCard
import com.luhaopeng.flicktv.ui.components.TvPill
import com.luhaopeng.flicktv.ui.components.ensureItemVisible
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordsScreen(
    history: List<PlayHistoryEntity>, favorites: List<FavoriteEntity>, columns: Int,
    tabFocusRequester: FocusRequester,
    contentEntryFocusRequester: FocusRequester,
    onHistory: (PlayHistoryEntity) -> Unit, onFavorite: (FavoriteEntity) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    val columnCount = columns.coerceIn(3, 8)
    val gridState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        LazyRow(contentPadding = PaddingValues(vertical = 10.dp)) {
            item {
                TvPill(
                    "播放历史", { tab = 0 }, tab == 0,
                    Modifier.padding(5.dp).focusOrder(contentEntryFocusRequester) { up = tabFocusRequester }
                )
            }
            item { TvPill("我的收藏", { tab = 1 }, tab == 1, Modifier.padding(5.dp)) }
        }
        LazyVerticalGrid(cells = GridCells.Fixed(columnCount), state = gridState) {
            if (tab == 0) itemsIndexed(history) { index, item ->
                val percent = if (item.durationMs > 0) item.positionMs * 100 / item.durationMs else 0
                PosterCard(
                    item.movieName, item.poster, "${item.episodeTitle} · $percent%",
                    { onHistory(item) }, Modifier.padding(8.dp), "续播",
                    onFocused = { scope.launch { gridState.ensureItemVisible(index / columnCount) } }
                )
            } else itemsIndexed(favorites) { index, item ->
                PosterCard(
                    item.movieName, item.poster, "跨源收藏",
                    { onFavorite(item) }, Modifier.padding(8.dp), "收藏",
                    onFocused = { scope.launch { gridState.ensureItemVisible(index / columnCount) } }
                )
            }
        }
    }
}
