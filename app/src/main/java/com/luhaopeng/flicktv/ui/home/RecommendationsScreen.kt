package com.luhaopeng.flicktv.ui.home

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luhaopeng.flicktv.data.model.RecommendedMovie
import com.luhaopeng.flicktv.data.recommendation.RecommendationTags
import com.luhaopeng.flicktv.data.recommendation.RecommendationType
import com.luhaopeng.flicktv.ui.components.PosterTile
import com.luhaopeng.flicktv.ui.theme.VodAccent
import kotlinx.coroutines.launch

/** 三层单焦点状态机：电影/剧集 → 分类标签 → 50 条海报，避免旧 Compose 空间寻焦串跳。 */
private enum class RecommendationFocusLayer { TYPE, TAG, GRID }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecommendationsScreen(
    columns: Int,
    tabFocusRequester: FocusRequester,
    contentEntryFocusRequester: FocusRequester,
    type: RecommendationType,
    tag: String,
    movies: List<RecommendedMovie>,
    loading: Boolean,
    message: String,
    onTypeChange: (RecommendationType) -> Unit,
    onTagChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onMovieClick: (String) -> Unit
) {
    val columnCount = columns.coerceIn(3, 8)
    val tags = RecommendationTags.forType(type)
    val gridState = rememberLazyListState()
    val tagState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedIndex by remember { mutableStateOf(0) }
    var focusLayer by remember { mutableStateOf(RecommendationFocusLayer.TYPE) }
    var containerFocused by remember { mutableStateOf(false) }

    val tagIndex = tags.indexOf(tag).coerceAtLeast(0)

    LaunchedEffect(type, tag, movies) {
        selectedIndex = 0
        if (movies.isNotEmpty()) gridState.scrollToItem(0)
        tagState.scrollToItem(tagIndex)
    }

    fun selectMovie(index: Int) {
        if (movies.isEmpty()) return
        selectedIndex = index.coerceIn(movies.indices)
        scope.launch { gridState.animateScrollToItem(selectedIndex / columnCount) }
    }

    fun moveType(delta: Int) {
        val all = RecommendationType.values()
        val index = all.indexOf(type)
        onTypeChange(all[(index + delta + all.size) % all.size])
    }

    fun moveTag(delta: Int) {
        val target = (tagIndex + delta).coerceIn(tags.indices)
        if (target != tagIndex) {
            onTagChange(tags[target])
            scope.launch { tagState.animateScrollToItem(target) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .focusRequester(contentEntryFocusRequester)
            .onFocusChanged { containerFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN || native.repeatCount != 0) return@onPreviewKeyEvent false
                when (focusLayer) {
                    RecommendationFocusLayer.TYPE -> when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> { tabFocusRequester.requestFocus(); true }
                        KeyEvent.KEYCODE_DPAD_LEFT -> { moveType(-1); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { moveType(1); true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { focusLayer = RecommendationFocusLayer.TAG; true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> { onRefresh(); true }
                        else -> false
                    }
                    RecommendationFocusLayer.TAG -> when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> { focusLayer = RecommendationFocusLayer.TYPE; true }
                        KeyEvent.KEYCODE_DPAD_LEFT -> { moveTag(-1); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { moveTag(1); true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { if (movies.isNotEmpty()) focusLayer = RecommendationFocusLayer.GRID; true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> { onRefresh(); true }
                        else -> false
                    }
                    RecommendationFocusLayer.GRID -> {
                        val row = selectedIndex / columnCount
                        val column = selectedIndex % columnCount
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (row == 0) focusLayer = RecommendationFocusLayer.TAG else selectMovie(selectedIndex - columnCount)
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                val target = selectedIndex + columnCount
                                if (target < movies.size) selectMovie(target)
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> { if (column > 0) selectMovie(selectedIndex - 1); true }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (column < columnCount - 1 && selectedIndex + 1 < movies.size) selectMovie(selectedIndex + 1)
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                                movies.getOrNull(selectedIndex)?.let { onMovieClick(it.title) }
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
            .focusable()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
            RecommendationType.values().forEach { option ->
                val active = option == type
                SelectorChip(
                    text = option.label,
                    active = active,
                    focused = active && focusLayer == RecommendationFocusLayer.TYPE && containerFocused
                )
            }
        }

        LazyRow(
            state = tagState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(tags) { index, option ->
                val active = option == tag
                SelectorChip(
                    text = option,
                    active = active,
                    focused = index == tagIndex && focusLayer == RecommendationFocusLayer.TAG && containerFocused,
                    endPadding = 0.dp
                )
            }
        }

        if (message.isNotBlank()) {
            Text(message, color = Color(0xFFFFC66D), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }

        when {
            loading && movies.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VodAccent)
                    Text("正在加载 $tag…", color = Color(0xFFA8A8B3), modifier = Modifier.padding(top = 12.dp))
                }
            }
            movies.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("该分类暂时不可用，在分类行按确定键重试", color = Color(0xFFA8A8B3))
            }
            else -> LazyVerticalGrid(
                cells = GridCells.Fixed(columnCount),
                state = gridState,
                contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(movies) { index, movie ->
                    val subtitle = if (movie.score.isNotBlank()) "豆瓣 ${movie.score}" else if (movie.isTv) "剧集" else "电影"
                    PosterTile(
                        title = movie.title,
                        poster = movie.poster,
                        posterCandidates = movie.posterCandidates,
                        subtitle = subtitle,
                        selected = containerFocused && focusLayer == RecommendationFocusLayer.GRID && index == selectedIndex,
                        modifier = Modifier.padding(8.dp),
                        badge = "豆瓣",
                        posterAspectRatio = 8f / 9f
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectorChip(
    text: String,
    active: Boolean,
    focused: Boolean,
    endPadding: androidx.compose.ui.unit.Dp = 10.dp
) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .padding(end = endPadding)
            .background(if (active) VodAccent.copy(alpha = .28f) else Color(0xFF17171F), RoundedCornerShape(8.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 15.dp, vertical = 8.dp)
    )
}
