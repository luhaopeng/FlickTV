package com.luhaopeng.flicktv.ui.components

import androidx.compose.foundation.lazy.LazyListState

/**
 * Compose 1.0 的 Lazy 容器不会可靠地跟随 TV 焦点滚动。
 * 网格调用方传入行号；只要目标条目不存在或被任一边界裁切，就滚动到完整可见。
 */
suspend fun LazyListState.ensureItemVisible(index: Int) {
    val target = index.coerceAtLeast(0)
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == target }
    when {
        item == null -> animateScrollToItem(target)
        item.offset < info.viewportStartOffset -> animateScrollToItem(target)
        item.offset + item.size > info.viewportEndOffset -> animateScrollToItem(target)
    }
}
