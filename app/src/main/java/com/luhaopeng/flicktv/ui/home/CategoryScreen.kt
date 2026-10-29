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
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luhaopeng.flicktv.data.model.SourceConfig
import com.luhaopeng.flicktv.data.model.VodCategory
import com.luhaopeng.flicktv.data.model.VodItem
import com.luhaopeng.flicktv.ui.components.PosterTile
import com.luhaopeng.flicktv.ui.components.TvPill
import com.luhaopeng.flicktv.ui.theme.VodAccent
import com.luhaopeng.flicktv.ui.theme.VodSurface
import kotlinx.coroutines.launch

/**
 * 分类浏览使用四个稳定焦点区（源、分类、分页、影片网格）。
 * 子项只负责绘制，方向键由父容器维护索引并主动滚动，避免旧版 Compose
 * 无法把焦点移动到尚未挂载的横向分类或下一排海报。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(
    sources: List<SourceConfig>,
    selectedSource: SourceConfig?,
    categories: List<VodCategory>,
    selectedCategory: VodCategory?,
    items: List<VodItem>,
    page: Int,
    pageCount: Int,
    total: Int,
    limit: Int,
    columns: Int,
    loading: Boolean,
    message: String,
    tabFocusRequester: FocusRequester,
    contentEntryFocusRequester: FocusRequester,
    onSource: (SourceConfig) -> Unit,
    onCategory: (VodCategory) -> Unit,
    onPage: (Int) -> Unit,
    onRetry: () -> Unit,
    onItem: (VodItem) -> Unit
) {
    val columnCount = columns.coerceIn(3, 8)
    val scope = rememberCoroutineScope()
    val sourceState = rememberLazyListState()
    val categoryState = rememberLazyListState()
    val gridState = rememberLazyListState()
    val categoryFocusRequester = remember { FocusRequester() }
    val pagerFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }

    var sourceIndex by remember(sources, selectedSource?.id) {
        mutableStateOf(sources.indexOfFirst { it.id == selectedSource?.id }.coerceAtLeast(0))
    }
    var categoryIndex by remember(categories, selectedCategory?.typeId) {
        mutableStateOf(categories.indexOfFirst { it.typeId == selectedCategory?.typeId }.coerceAtLeast(0))
    }
    var pagerIndex by remember { mutableStateOf(if (page > 1) 0 else 1) }
    var movieIndex by remember { mutableStateOf(0) }
    var sourceFocused by remember { mutableStateOf(false) }
    var categoryFocused by remember { mutableStateOf(false) }
    var pagerFocused by remember { mutableStateOf(false) }
    var gridFocused by remember { mutableStateOf(false) }

    LaunchedEffect(items, selectedCategory?.typeId, page) {
        movieIndex = 0
        if (items.isNotEmpty()) gridState.scrollToItem(0)
    }

    fun selectSource(index: Int) {
        if (sources.isEmpty()) return
        sourceIndex = index.coerceIn(sources.indices)
        scope.launch { sourceState.animateScrollToItem(sourceIndex) }
    }

    fun selectCategory(index: Int) {
        if (categories.isEmpty()) return
        categoryIndex = index.coerceIn(categories.indices)
        scope.launch { categoryState.animateScrollToItem(categoryIndex) }
    }

    fun selectMovie(index: Int) {
        if (items.isEmpty()) return
        movieIndex = index.coerceIn(items.indices)
        // Compose 1.0 实验版 LazyVerticalGrid 的状态索引是行索引。
        scope.launch { gridState.animateScrollToItem(movieIndex / columnCount) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .focusRequester(contentEntryFocusRequester)
                .onFocusChanged { sourceFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val handled = native.keyCode in listOf(
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A
                    )
                    if (handled && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                    if (!handled || native.repeatCount != 0) return@onPreviewKeyEvent handled
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> selectSource(sourceIndex - 1)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> selectSource(sourceIndex + 1)
                        KeyEvent.KEYCODE_DPAD_UP -> tabFocusRequester.requestFocus()
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (categories.isNotEmpty()) categoryFocusRequester.requestFocus()
                            else pagerFocusRequester.requestFocus()
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> sources.getOrNull(sourceIndex)?.let(onSource)
                    }
                    true
                }
                .focusable()
        ) {
            LazyRow(state = sourceState, contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(sources) { index, source ->
                    SelectionPill(
                        text = source.name,
                        active = selectedSource?.id == source.id,
                        cursor = sourceFocused && index == sourceIndex,
                        modifier = Modifier.padding(5.dp)
                    )
                }
            }
        }

        if (categories.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(categoryFocusRequester)
                    .onFocusChanged { categoryFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        val handled = native.keyCode in listOf(
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A
                        )
                        if (handled && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                        if (!handled || native.repeatCount != 0) return@onPreviewKeyEvent handled
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> selectCategory(categoryIndex - 1)
                            KeyEvent.KEYCODE_DPAD_RIGHT -> selectCategory(categoryIndex + 1)
                            KeyEvent.KEYCODE_DPAD_UP -> contentEntryFocusRequester.requestFocus()
                            KeyEvent.KEYCODE_DPAD_DOWN -> pagerFocusRequester.requestFocus()
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> categories.getOrNull(categoryIndex)?.let(onCategory)
                        }
                        true
                    }
                    .focusable()
            ) {
                LazyRow(state = categoryState, contentPadding = PaddingValues(vertical = 4.dp)) {
                    itemsIndexed(categories) { index, category ->
                        SelectionPill(
                            text = category.typeName,
                            active = selectedCategory?.typeId == category.typeId,
                            cursor = categoryFocused && index == categoryIndex,
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .focusRequester(pagerFocusRequester)
                .onFocusChanged { pagerFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val handled = native.keyCode in listOf(
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A
                    )
                    if (handled && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                    if (!handled || native.repeatCount != 0) return@onPreviewKeyEvent handled
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> pagerIndex = 0
                        KeyEvent.KEYCODE_DPAD_RIGHT -> pagerIndex = 1
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (categories.isNotEmpty()) categoryFocusRequester.requestFocus()
                            else contentEntryFocusRequester.requestFocus()
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (items.isNotEmpty()) gridFocusRequester.requestFocus()
                            else if (!loading) retryFocusRequester.requestFocus()
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                            if (!loading && pagerIndex == 0 && page > 1) onPage(page - 1)
                            if (!loading && pagerIndex == 1 && page < pageCount) onPage(page + 1)
                        }
                    }
                    true
                }
                .focusable(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionPill("上一页", active = page > 1, cursor = pagerFocused && pagerIndex == 0, modifier = Modifier.padding(4.dp))
            val countText = if (total > 0) {
                "第 $page/${pageCount.coerceAtLeast(1)} 页 · 本页 ${items.size} 条 · 共 $total 条"
            } else {
                "第 $page/${pageCount.coerceAtLeast(1)} 页 · 本页 ${items.size} 条${if (limit > 0) " · 每页上限 $limit" else ""}"
            }
            Text(countText, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp))
            SelectionPill("下一页", active = page < pageCount, cursor = pagerFocused && pagerIndex == 1, modifier = Modifier.padding(4.dp))
        }

        if (loading && items.isNotEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        }

        when {
            loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VodAccent)
                    Text(
                        if (categories.isEmpty()) "正在加载分类…" else "正在加载 ${selectedCategory?.typeName ?: "影片"}…",
                        color = Color(0xFFA8A8B3),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (message.isBlank()) "该分类暂无影片" else message,
                        color = if (message.contains("失败")) Color(0xFFFFC66D) else Color(0xFFA8A8B3),
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    TvPill(
                        "重试",
                        onRetry,
                        modifier = Modifier.focusRequester(retryFocusRequester),
                        onKeyDown = { keyCode ->
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    pagerFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        }
                    )
                }
            }
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(gridFocusRequester)
                    .onFocusChanged { gridFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        val handled = native.keyCode in listOf(
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A
                        )
                        if (handled && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                        if (!handled || native.repeatCount != 0) return@onPreviewKeyEvent handled
                        val row = movieIndex / columnCount
                        val column = movieIndex % columnCount
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> if (column > 0) selectMovie(movieIndex - 1)
                            KeyEvent.KEYCODE_DPAD_RIGHT -> if (column < columnCount - 1 && movieIndex + 1 < items.size) selectMovie(movieIndex + 1)
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (row == 0) pagerFocusRequester.requestFocus()
                                else selectMovie(movieIndex - columnCount)
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                val target = movieIndex + columnCount
                                if (target < items.size) selectMovie(target)
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> items.getOrNull(movieIndex)?.let(onItem)
                        }
                        true
                    }
                    .focusable()
            ) {
                LazyVerticalGrid(
                    cells = GridCells.Fixed(columnCount),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(items) { index, vod ->
                        PosterTile(
                            title = vod.vodName,
                            poster = vod.vodPic,
                            subtitle = "${vod.vodYear} · ${vod.vodRemarks}",
                            selected = gridFocused && index == movieIndex,
                            modifier = Modifier.padding(8.dp),
                            posterAspectRatio = 8f / 9f
                        )
                    }
                }
            }
        }
    }
}

/** 只绘制状态、不单独获取焦点的胶囊按钮。 */
@Composable
private fun SelectionPill(
    text: String,
    active: Boolean,
    cursor: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .background(if (active) VodAccent.copy(alpha = .24f) else VodSurface, RoundedCornerShape(10.dp))
            .border(if (cursor) 2.dp else 1.dp, if (cursor) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}
