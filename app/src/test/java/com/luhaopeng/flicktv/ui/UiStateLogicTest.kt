package com.luhaopeng.flicktv.ui

import com.luhaopeng.flicktv.ui.focus.GridCursor
import com.luhaopeng.flicktv.ui.focus.GridDirection
import com.luhaopeng.flicktv.ui.focus.TvKeyCycle
import com.luhaopeng.flicktv.ui.player.PlayerPhase
import com.luhaopeng.flicktv.ui.player.PlayerPhaseState
import com.luhaopeng.flicktv.ui.player.accumulateSeekTarget
import org.junit.Assert.*
import org.junit.Test

class UiStateLogicTest {
    @Test fun `五列网格应按整行上下移动`() {
        val cursor = GridCursor(index = 7, itemCount = 20, columns = 5)
        assertEquals(2, cursor.move(GridDirection.UP))
        assertEquals(12, cursor.move(GridDirection.DOWN))
    }

    @Test fun `首行向上应返回空目标`() {
        assertNull(GridCursor(3, 20, 5).move(GridDirection.UP))
    }

    @Test fun `末行不足列数时向下应保持边界`() {
        assertNull(GridCursor(8, 9, 5).move(GridDirection.DOWN))
    }

    @Test fun `每行最左项不能继续向左`() {
        assertNull(GridCursor(5, 20, 5).move(GridDirection.LEFT))
    }

    @Test fun `每行最右项不能跨行向右`() {
        assertNull(GridCursor(4, 20, 5).move(GridDirection.RIGHT))
    }

    @Test fun `短末行可以在已有项目间横向移动`() {
        val cursor = GridCursor(10, 12, 5)
        assertEquals(11, cursor.move(GridDirection.RIGHT))
        assertNull(GridCursor(11, 12, 5).move(GridDirection.RIGHT))
    }

    @Test fun `游标应钳制越界初始索引`() {
        assertEquals(9, GridCursor(99, 10, 5).safeIndex)
        assertEquals(0, GridCursor(-2, 10, 5).safeIndex)
    }

    @Test fun `空网格任何方向都没有目标`() {
        GridDirection.values().forEach { assertNull(GridCursor(0, 0, 5).move(it)) }
    }

    @Test fun `只有首次按下事件应分发业务动作`() {
        assertTrue(TvKeyCycle.shouldDispatch(TvKeyCycle.ACTION_DOWN, 0))
        assertFalse(TvKeyCycle.shouldDispatch(TvKeyCycle.ACTION_DOWN, 1))
        assertFalse(TvKeyCycle.shouldDispatch(1, 0))
    }

    @Test fun `已处理按键的抬起和重复事件都应消费`() {
        assertTrue(TvKeyCycle.consume(1, 0, true))
        assertTrue(TvKeyCycle.consume(TvKeyCycle.ACTION_DOWN, 2, true))
        assertFalse(TvKeyCycle.consume(TvKeyCycle.ACTION_DOWN, 0, true))
        assertFalse(TvKeyCycle.consume(1, 0, false))
    }

    @Test fun `播放器首次缓冲保持初始加载`() {
        val state = PlayerPhaseState().onBuffering()
        assertEquals(PlayerPhase.INITIAL_LOADING, state.phase)
        assertFalse(state.hasStartedPlayback)
    }

    @Test fun `首次真正播放后进入可播放状态`() {
        val state = PlayerPhaseState().onPlayingChanged(true)
        assertEquals(PlayerPhase.READY, state.phase)
        assertTrue(state.hasStartedPlayback)
        assertTrue(state.isPlaying)
    }

    @Test fun `播放中缓冲应进入缓冲状态`() {
        val state = PlayerPhaseState().onPlayingChanged(true).onBuffering()
        assertEquals(PlayerPhase.BUFFERING, state.phase)
        assertTrue(state.hasStartedPlayback)
        assertFalse(state.isPlaying)
    }

    @Test fun `暂停后缓冲完成应恢复可操作状态但保持暂停`() {
        val state = PlayerPhaseState()
            .onPlayingChanged(true)
            .onPlayingChanged(false)
            .onBuffering()
            .onReady()
        assertEquals(PlayerPhase.READY, state.phase)
        assertTrue(state.hasStartedPlayback)
        assertFalse(state.isPlaying)
    }

    @Test fun `首次READY但未开始播放仍保持加载`() {
        assertEquals(PlayerPhase.INITIAL_LOADING, PlayerPhaseState().onReady().phase)
    }

    @Test fun `错误和超时应停止播放并保留已开始标记`() {
        val started = PlayerPhaseState().onPlayingChanged(true)
        assertEquals(PlayerPhase.ERROR, started.onError().phase)
        assertFalse(started.onError().isPlaying)
        assertEquals(PlayerPhase.TIMEOUT, started.onTimeout().phase)
        assertTrue(started.onTimeout().hasStartedPlayback)
    }

    @Test fun `连续三次快进应累计十五秒`() {
        val first = accumulateSeekTarget(30_000L, null, 5_000L, 120_000L)
        val second = accumulateSeekTarget(30_000L, first, 5_000L, 120_000L)
        val third = accumulateSeekTarget(30_000L, second, 5_000L, 120_000L)
        assertEquals(45_000L, third)
    }

    @Test fun `连续后退应基于待生效目标继续累计`() {
        val first = accumulateSeekTarget(30_000L, null, -5_000L, 120_000L)
        val second = accumulateSeekTarget(30_000L, first, -5_000L, 120_000L)
        assertEquals(20_000L, second)
    }

    @Test fun `快进后退目标不能超出播放区间`() {
        assertEquals(0L, accumulateSeekTarget(2_000L, null, -5_000L, 120_000L))
        assertEquals(120_000L, accumulateSeekTarget(118_000L, null, 5_000L, 120_000L))
    }

    @Test fun `重新准备应重置所有播放阶段`() {
        val reset = PlayerPhaseState().onPlayingChanged(true).onError().onPrepare()
        assertEquals(PlayerPhase.INITIAL_LOADING, reset.phase)
        assertFalse(reset.hasStartedPlayback)
        assertFalse(reset.isPlaying)
    }
}
