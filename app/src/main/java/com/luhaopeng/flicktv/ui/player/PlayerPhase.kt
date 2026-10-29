package com.luhaopeng.flicktv.ui.player

/** 播放器业务阶段，独立于 ExoPlayer 与 Compose，便于验证状态转换。 */
enum class PlayerPhase {
    INITIAL_LOADING,
    BUFFERING,
    READY,
    ERROR,
    TIMEOUT
}

data class PlayerPhaseState(
    val phase: PlayerPhase = PlayerPhase.INITIAL_LOADING,
    val hasStartedPlayback: Boolean = false,
    val isPlaying: Boolean = false
) {
    fun onPrepare(): PlayerPhaseState = PlayerPhaseState()

    fun onBuffering(): PlayerPhaseState = copy(
        phase = if (hasStartedPlayback) PlayerPhase.BUFFERING else PlayerPhase.INITIAL_LOADING,
        isPlaying = false
    )

    fun onReady(): PlayerPhaseState = copy(
        phase = if (hasStartedPlayback) PlayerPhase.READY else PlayerPhase.INITIAL_LOADING,
        isPlaying = false
    )

    fun onPlayingChanged(playing: Boolean): PlayerPhaseState = if (playing) {
        copy(phase = PlayerPhase.READY, hasStartedPlayback = true, isPlaying = true)
    } else {
        copy(isPlaying = false)
    }

    fun onError(): PlayerPhaseState = copy(phase = PlayerPhase.ERROR, isPlaying = false)

    fun onTimeout(): PlayerPhaseState = copy(phase = PlayerPhase.TIMEOUT, isPlaying = false)
}
