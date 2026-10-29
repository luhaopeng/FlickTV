package com.luhaopeng.flicktv.ui.search

import android.view.KeyEvent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusOrder
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luhaopeng.flicktv.data.model.MergedMovie
import com.luhaopeng.flicktv.ui.components.PosterTile
import com.luhaopeng.flicktv.ui.components.TvPill
import com.luhaopeng.flicktv.ui.components.ensureItemVisible
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    query: String,
    results: List<MergedMovie>,
    searching: Boolean,
    message: String,
    columns: Int,
    tabFocusRequester: FocusRequester,
    contentEntryFocusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onMovieClick: (MergedMovie) -> Unit
) {
    val columnCount = columns.coerceIn(3, 8)
    val gridState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searchButtonFocusRequester = remember { FocusRequester() }
    val searchRowNavigationFocusRequester = remember { FocusRequester() }
    val resultGridFocusRequester = remember { FocusRequester() }
    var selectedIndex by remember { mutableStateOf(0) }
    var gridFocused by remember { mutableStateOf(false) }

    LaunchedEffect(results) {
        selectedIndex = 0
        if (results.isNotEmpty()) gridState.scrollToItem(0)
    }

    fun selectResult(index: Int) {
        if (results.isEmpty()) return
        val targetIndex = index.coerceIn(results.indices)
        selectedIndex = targetIndex
        // 本工程使用 Compose 1.0 的实验版 LazyVerticalGrid；其 LazyListState 索引代表“行”。
        // 上下方向统一按目标行执行动画，避免新版 API 语义套用后过度滚动。
        scope.launch { gridState.animateScrollToItem(targetIndex / columnCount) }
    }

    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("输入影片名") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
                colors = TextFieldDefaults.textFieldColors(textColor = Color.White, backgroundColor = Color(0xFF17171F)),
                modifier = Modifier
                    .weight(1f)
                    .focusOrder(contentEntryFocusRequester) {
                        up = tabFocusRequester
                        right = searchButtonFocusRequester
                        down = if (results.isNotEmpty()) resultGridFocusRequester else searchButtonFocusRequester
                    }
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        val handledKey = native.keyCode == KeyEvent.KEYCODE_BACK ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        // 返回键不仅关闭软键盘，还把真实焦点交给搜索行导航代理；
                        // 避免 EditText 继续持有焦点，导致下一次方向键被 IME/空间寻焦反向解释。
                        if (handledKey && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                        if (!handledKey || native.repeatCount != 0) return@onPreviewKeyEvent handledKey
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_BACK -> searchRowNavigationFocusRequester.requestFocus()
                            KeyEvent.KEYCODE_DPAD_UP -> tabFocusRequester.requestFocus()
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (results.isNotEmpty()) {
                                    selectedIndex = selectedIndex.coerceIn(results.indices)
                                    resultGridFocusRequester.requestFocus()
                                } else {
                                    searchButtonFocusRequester.requestFocus()
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> searchButtonFocusRequester.requestFocus()
                        }
                        true
                    }
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.height(IntrinsicSize.Max), contentAlignment = androidx.compose.ui.Alignment.Center) {
                TvPill(
                    if (searching) "搜索中…" else "多源搜索",
                    onSearch,
                    modifier = Modifier
                        .height(56.dp)
                        .focusRequester(searchButtonFocusRequester)
                        .focusOrder {
                            left = contentEntryFocusRequester
                            up = tabFocusRequester
                        },
                    onKeyDown = { keyCode ->
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                contentEntryFocusRequester.requestFocus()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                tabFocusRequester.requestFocus()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (results.isNotEmpty()) {
                                    selectedIndex = selectedIndex.coerceIn(results.indices)
                                    resultGridFocusRequester.requestFocus()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                )
            }
        }
            // 透明导航代理：只在关闭软键盘后暂存焦点，不参与绘制。
            Box(
                Modifier
                    .matchParentSize()
                    .focusRequester(searchRowNavigationFocusRequester)
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        val handledKey = native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            native.keyCode == KeyEvent.KEYCODE_ENTER
                        if (handledKey && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                        if (!handledKey || native.repeatCount != 0) return@onPreviewKeyEvent handledKey
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> tabFocusRequester.requestFocus()
                            KeyEvent.KEYCODE_DPAD_RIGHT -> searchButtonFocusRequester.requestFocus()
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (results.isNotEmpty()) {
                                    selectedIndex = selectedIndex.coerceIn(results.indices)
                                    resultGridFocusRequester.requestFocus()
                                } else {
                                    searchButtonFocusRequester.requestFocus()
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER -> contentEntryFocusRequester.requestFocus()
                        }
                        true
                    }
                    .focusable()
            )
        }
        Spacer(Modifier.height(10.dp))
        if (message.isNotBlank()) {
            Text(
                text = message,
                color = Color(0xFFFFC66D),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF17171F), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Spacer(Modifier.height(10.dp))
        } else {
            Spacer(Modifier.height(10.dp))
        }
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(resultGridFocusRequester)
                .onFocusChanged { gridFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val handledKey = native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                        native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                        native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                        native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                        native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == KeyEvent.KEYCODE_ENTER ||
                        native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                        native.keyCode == KeyEvent.KEYCODE_BUTTON_A
                    // 旧 Compose TV 焦点系统可能在 ACTION_UP 再执行一次空间寻焦；
                    // 方向键与确认键的完整按键周期都由本容器消费，只在首次 ACTION_DOWN 更新状态。
                    if (handledKey && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                    if (!handledKey || native.repeatCount != 0) return@onPreviewKeyEvent handledKey
                    val row = selectedIndex / columnCount
                    val column = selectedIndex % columnCount
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (row == 0) {
                                contentEntryFocusRequester.requestFocus()
                            } else {
                                selectResult(selectedIndex - columnCount)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val target = selectedIndex + columnCount
                            if (target < results.size) selectResult(target)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (column > 0) selectResult(selectedIndex - 1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (column < columnCount - 1 && selectedIndex + 1 < results.size) selectResult(selectedIndex + 1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                            results.getOrNull(selectedIndex)?.let(onMovieClick)
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
        ) {
            LazyVerticalGrid(
                cells = GridCells.Fixed(columnCount),
                state = gridState,
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(results) { index, movie ->
                    PosterTile(
                        title = movie.displayName,
                        poster = movie.poster,
                        posterCandidates = if (movie.poster.isBlank()) emptyList() else listOf(movie.poster),
                        subtitle = "${movie.sources.size}个来源 · ${movie.sources.joinToString("/") { it.source.name }}",
                        badge = "${movie.sources.size}源",
                        selected = gridFocused && index == selectedIndex,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
