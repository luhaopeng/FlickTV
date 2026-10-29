package com.luhaopeng.flicktv.ui.history

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusOrder
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.luhaopeng.flicktv.data.db.FavoriteEntity
import com.luhaopeng.flicktv.data.db.PlayHistoryEntity
import com.luhaopeng.flicktv.ui.components.PosterTile
import com.luhaopeng.flicktv.ui.components.TvPill
import com.luhaopeng.flicktv.ui.focus.GridCursor
import com.luhaopeng.flicktv.ui.focus.GridDirection
import com.luhaopeng.flicktv.ui.theme.VodAccent
import com.luhaopeng.flicktv.ui.theme.VodTextSecondary
import kotlinx.coroutines.launch

/** 播放历史与收藏共用单一真实焦点网格，避免屏幕外 Lazy 项寻焦失败。 */
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
    val gridFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var selectedIndex by remember { mutableStateOf(0) }
    var gridFocused by remember { mutableStateOf(false) }
    val itemCount = if (tab == 0) history.size else favorites.size

    LaunchedEffect(tab, itemCount) {
        selectedIndex = 0
        if (itemCount > 0) gridState.scrollToItem(0)
    }

    fun select(index: Int) {
        if (itemCount == 0) return
        selectedIndex = index.coerceIn(0, itemCount - 1)
        scope.launch { gridState.animateScrollToItem(selectedIndex / columnCount) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        LazyRow(contentPadding = PaddingValues(vertical = 10.dp)) {
            item {
                TvPill(
                    "播放历史", { tab = 0 }, tab == 0,
                    Modifier.padding(5.dp).focusOrder(contentEntryFocusRequester) {
                        up = tabFocusRequester
                        if (history.isNotEmpty()) down = gridFocusRequester
                    },
                    onKeyDown = { keyCode ->
                        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && history.isNotEmpty()) {
                            gridFocusRequester.requestFocus(); true
                        } else false
                    }
                )
            }
            item {
                TvPill(
                    "我的收藏", { tab = 1 }, tab == 1, Modifier.padding(5.dp),
                    onKeyDown = { keyCode ->
                        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && favorites.isNotEmpty()) {
                            gridFocusRequester.requestFocus(); true
                        } else false
                    }
                )
            }
        }

        if (itemCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (tab == 0) "还没有播放记录\n播放影片后会在这里继续观看"
                    else "还没有收藏影片\n可在影片详情页选择“收藏影片”",
                    color = VodTextSecondary
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(gridFocusRequester)
                    .onFocusChanged { gridFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        val handled = native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            native.keyCode == KeyEvent.KEYCODE_ENTER ||
                            native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                            native.keyCode == KeyEvent.KEYCODE_BUTTON_A
                        if (handled && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                        if (!handled || native.repeatCount != 0) return@onPreviewKeyEvent handled
                        val cursor = GridCursor(selectedIndex, itemCount, columnCount)
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> cursor.move(GridDirection.LEFT)?.let(::select)
                            KeyEvent.KEYCODE_DPAD_RIGHT -> cursor.move(GridDirection.RIGHT)?.let(::select)
                            KeyEvent.KEYCODE_DPAD_DOWN -> cursor.move(GridDirection.DOWN)?.let(::select)
                            KeyEvent.KEYCODE_DPAD_UP -> cursor.move(GridDirection.UP)?.let(::select)
                                ?: contentEntryFocusRequester.requestFocus()
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                                if (tab == 0) history.getOrNull(selectedIndex)?.let(onHistory)
                                else favorites.getOrNull(selectedIndex)?.let(onFavorite)
                            }
                        }
                        true
                    }
                    .focusable()
            ) {
                LazyVerticalGrid(cells = GridCells.Fixed(columnCount), state = gridState) {
                    if (tab == 0) {
                        itemsIndexed(history) { index, item ->
                            val progress = if (item.durationMs > 0) {
                                (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            Column(Modifier.padding(8.dp)) {
                                PosterTile(
                                    item.movieName,
                                    item.poster,
                                    "${item.episodeTitle} · ${(progress * 100).toInt()}%",
                                    gridFocused && index == selectedIndex,
                                    badge = "续播"
                                )
                                LinearProgressIndicator(
                                    progress = progress,
                                    color = VodAccent,
                                    backgroundColor = Color.Transparent,
                                    modifier = Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 4.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(favorites) { index, item ->
                            PosterTile(
                                item.movieName,
                                item.poster,
                                "跨源收藏",
                                gridFocused && index == selectedIndex,
                                Modifier.padding(8.dp),
                                "收藏"
                            )
                        }
                    }
                }
            }
        }
    }
}
