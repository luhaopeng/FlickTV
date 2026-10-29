package com.luhaopeng.flicktv.ui.detail

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusOrder
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.luhaopeng.flicktv.data.db.PlayHistoryEntity
import com.luhaopeng.flicktv.data.db.PlaybackStore
import com.luhaopeng.flicktv.data.db.watchedPercent
import com.luhaopeng.flicktv.data.model.*
import com.luhaopeng.flicktv.ui.components.TvPill
import com.luhaopeng.flicktv.ui.components.ensureItemVisible
import com.luhaopeng.flicktv.ui.focus.GridCursor
import com.luhaopeng.flicktv.ui.focus.GridDirection
import com.luhaopeng.flicktv.ui.theme.VodAccent
import com.luhaopeng.flicktv.ui.theme.VodSurface
import kotlinx.coroutines.launch

/**
 * 详情页显式焦点路径：来源 → 收藏 → 线路 → 选集。
 * 选集使用单一真实焦点容器和索引状态机，避免旧 Compose 在多行网格向上时跳行。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    movie: MergedMovie,
    selected: SourceVod?,
    detail: VodItem?,
    playbackStore: PlaybackStore,
    history: List<PlayHistoryEntity>,
    favorite: Boolean,
    onSource: (SourceVod) -> Unit,
    onFavorite: () -> Unit,
    onPlay: (SourceVod, PlayLine, Int) -> Unit
) {
    val active = selected ?: movie.sources.first()
    val vod = detail ?: active.vod
    val lines = remember(vod.vodPlayUrl, vod.vodPlayFrom) { PlayUrlParser.parse(vod) }
    var lineIndex by remember(vod.vodId) { mutableStateOf(0) }
    val line = lines.getOrNull(lineIndex)
    val historyByKey = remember(history) { history.associateBy { it.progressKey } }
    val sourceState = rememberLazyListState()
    val lineState = rememberLazyListState()
    val episodeState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val episodeColumns = 6

    val sourceFocusRequester = remember { FocusRequester() }
    val favoriteFocusRequester = remember { FocusRequester() }
    val lineFocusRequester = remember { FocusRequester() }
    val episodeFocusRequester = remember { FocusRequester() }
    var selectedEpisode by remember { mutableStateOf(0) }
    var episodeGridFocused by remember { mutableStateOf(false) }

    LaunchedEffect(vod.vodId) {
        // 详情首次显示即建立确定的焦点起点，避免第一次方向键只用于“唤醒”焦点。
        sourceFocusRequester.requestFocus()
    }

    LaunchedEffect(vod.vodId, lineIndex, line?.episodes?.size) {
        selectedEpisode = 0
        if (!line?.episodes.isNullOrEmpty()) episodeState.scrollToItem(0)
    }

    fun selectEpisode(index: Int) {
        val episodes = line?.episodes ?: return
        if (episodes.isEmpty()) return
        val target = index.coerceIn(episodes.indices)
        selectedEpisode = target
        // Compose 1.0 实验版 LazyVerticalGrid 的状态索引是行索引。
        scope.launch { episodeState.animateScrollToItem(target / episodeColumns) }
    }

    Column(Modifier.fillMaxSize().padding(26.dp)) {
        Text("来源", color = Color(0xFFA8A8B3), fontSize = 13.sp)
        LazyRow(state = sourceState, contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(movie.sources) { index, sourceVod ->
                val focusModifier = if (index == 0) {
                    Modifier
                        .padding(5.dp)
                        .focusOrder(sourceFocusRequester) { down = favoriteFocusRequester }
                } else {
                    Modifier.padding(5.dp).focusOrder { down = favoriteFocusRequester }
                }
                TvPill(
                    "${sourceVod.source.name} ${sourceVod.vod.vodRemarks}",
                    { onSource(sourceVod) },
                    active.source.id == sourceVod.source.id,
                    focusModifier,
                    onFocused = { scope.launch { sourceState.ensureItemVisible(index) } },
                    onKeyDown = { keyCode ->
                        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            favoriteFocusRequester.requestFocus()
                            true
                        } else false
                    }
                )
            }
        }

        Row(Modifier.fillMaxWidth().weight(.52f)) {
            Image(
                painter = rememberImagePainter(vod.vodPic),
                contentDescription = vod.vodName,
                modifier = Modifier.width(190.dp).fillMaxHeight().background(Color(0xFF24242E))
            )
            Column(Modifier.padding(start = 24.dp).weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(vod.vodName, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(
                            listOf(vod.vodYear, vod.vodClass, vod.vodArea, vod.vodRemarks).filter { it.isNotBlank() }.joinToString(" · "),
                            color = Color(0xFFA8A8B3),
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    }
                    TvPill(
                        if (favorite) "已收藏 · 取消" else "收藏影片",
                        onFavorite,
                        favorite,
                        Modifier
                            .padding(start = 16.dp)
                            .focusRequester(favoriteFocusRequester)
                            .focusOrder {
                                up = sourceFocusRequester
                                down = lineFocusRequester
                            },
                        onKeyDown = { keyCode ->
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    sourceFocusRequester.requestFocus()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (lines.isNotEmpty()) lineFocusRequester.requestFocus()
                                    else if (!line?.episodes.isNullOrEmpty()) episodeFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        }
                    )
                }
                if (vod.vodActor.isNotBlank()) Text("主演：${vod.vodActor}", color = Color.LightGray, maxLines = 2)
                Text(vod.vodContent.replace(Regex("<[^>]+>"), ""), color = Color.White, maxLines = 5, modifier = Modifier.padding(top = 10.dp))
            }
        }

        LazyRow(state = lineState, contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(lines) { index, playLine ->
                val focusModifier = if (index == 0) {
                    Modifier
                        .padding(5.dp)
                        .focusOrder(lineFocusRequester) {
                            up = favoriteFocusRequester
                            down = episodeFocusRequester
                        }
                } else {
                    Modifier.padding(5.dp).focusOrder {
                        up = favoriteFocusRequester
                        down = episodeFocusRequester
                    }
                }
                TvPill(
                    playLine.name,
                    { lineIndex = index },
                    lineIndex == index,
                    focusModifier,
                    onFocused = { scope.launch { lineState.ensureItemVisible(index) } },
                    onKeyDown = { keyCode ->
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                favoriteFocusRequester.requestFocus()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (!line?.episodes.isNullOrEmpty()) episodeFocusRequester.requestFocus()
                                true
                            }
                            else -> false
                        }
                    }
                )
            }
        }

        if (line != null && line.episodes.isNotEmpty()) {
            Box(
                Modifier
                    .weight(.48f)
                    .fillMaxWidth()
                    .focusRequester(episodeFocusRequester)
                    .onFocusChanged { episodeGridFocused = it.isFocused }
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
                        val cursor = GridCursor(selectedEpisode, line.episodes.size, episodeColumns)
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> cursor.move(GridDirection.UP)?.let(::selectEpisode)
                                ?: lineFocusRequester.requestFocus()
                            KeyEvent.KEYCODE_DPAD_DOWN -> cursor.move(GridDirection.DOWN)?.let(::selectEpisode)
                            KeyEvent.KEYCODE_DPAD_LEFT -> cursor.move(GridDirection.LEFT)?.let(::selectEpisode)
                            KeyEvent.KEYCODE_DPAD_RIGHT -> cursor.move(GridDirection.RIGHT)?.let(::selectEpisode)
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                                onPlay(active.copy(vod = vod), line, selectedEpisode)
                            }
                        }
                        true
                    }
                    .focusable()
            ) {
                LazyVerticalGrid(
                    cells = GridCells.Fixed(episodeColumns),
                    state = episodeState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(line.episodes) { index, episode ->
                        val progress = historyByKey[
                            playbackStore.key(vod.vodName, line.name, episode.title)
                        ]?.let { watchedPercent(it.positionMs, it.durationMs) }
                        val selectedEpisodeTile = episodeGridFocused && index == selectedEpisode
                        Box(
                            Modifier
                                .padding(6.dp)
                                .fillMaxWidth()
                                .background(VodSurface, RoundedCornerShape(10.dp))
                                .border(
                                    if (selectedEpisodeTile) 3.dp else 1.dp,
                                    if (selectedEpisodeTile) Color.White else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Text(
                                when (progress) {
                                    null -> episode.title
                                    100 -> "${episode.title} · 已看完"
                                    else -> "${episode.title} · $progress%"
                                },
                                color = if (selectedEpisodeTile) Color.White else Color(0xFFE6E6EA),
                                fontWeight = if (selectedEpisodeTile) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        } else {
            Text("该来源暂无可识别播放地址", color = Color(0xFFA8A8B3))
        }
    }
}
