package com.luhaopeng.flicktv.ui.focus

/** TV 单焦点网格的纯逻辑方向。 */
enum class GridDirection { UP, DOWN, LEFT, RIGHT }

/**
 * 不依赖 Compose 的网格游标计算器。
 * 返回 null 表示该方向没有相邻条目，调用方可把焦点交给上一层或保持不动。
 */
data class GridCursor(
    val index: Int,
    val itemCount: Int,
    val columns: Int
) {
    init {
        require(itemCount >= 0)
        require(columns > 0)
    }

    val safeIndex: Int
        get() = if (itemCount == 0) 0 else index.coerceIn(0, itemCount - 1)

    val row: Int get() = safeIndex / columns
    val column: Int get() = safeIndex % columns

    fun move(direction: GridDirection): Int? {
        if (itemCount == 0) return null
        val current = safeIndex
        return when (direction) {
            GridDirection.UP -> (current - columns).takeIf { it >= 0 }
            GridDirection.DOWN -> (current + columns).takeIf { it < itemCount }
            GridDirection.LEFT -> (current - 1).takeIf { column > 0 }
            GridDirection.RIGHT -> (current + 1).takeIf { column < columns - 1 && it < itemCount }
        }
    }
}

/** 统一旧 Compose TV 的完整按键周期规则。 */
object TvKeyCycle {
    const val ACTION_DOWN = 0

    fun shouldDispatch(action: Int, repeatCount: Int): Boolean =
        action == ACTION_DOWN && repeatCount == 0

    fun consume(action: Int, repeatCount: Int, handledKey: Boolean): Boolean = when {
        !handledKey -> false
        action != ACTION_DOWN -> true
        repeatCount != 0 -> true
        else -> false
    }
}
