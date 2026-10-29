package com.luhaopeng.flicktv.ui.player

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusOrder
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.luhaopeng.flicktv.data.adblock.FilteringDataSource
import com.luhaopeng.flicktv.data.api.NetworkModule
import com.luhaopeng.flicktv.data.db.PlaybackStore
import com.luhaopeng.flicktv.data.model.PlayLine
import com.luhaopeng.flicktv.data.model.SourceVod
import com.luhaopeng.flicktv.ui.components.TvPill
import com.luhaopeng.flicktv.ui.theme.VodAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 原生全屏播放器：遥控控制、广告 m3u8 清洗、5 秒存档、下一集预告与自动连播。 */
@Composable
fun PlayerScreen(
    movie: SourceVod,
    line: PlayLine,
    initialEpisodeIndex: Int,
    store: PlaybackStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playerFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    var playerAreaFocused by remember { mutableStateOf(false) }
    var episodeIndex by remember { mutableStateOf(initialEpisodeIndex.coerceIn(line.episodes.indices)) }
    var controls by remember { mutableStateOf(true) }
    var controlsInteractionId by remember { mutableStateOf(0L) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playerPhase by remember { mutableStateOf(PlayerPhaseState()) }
    var nextHint by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableStateOf(0f) }
    var loadAttemptId by remember { mutableStateOf(0L) }
    var loadError by remember { mutableStateOf("") }
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    var requestNextEpisodeFocus by remember { mutableStateOf(false) }
    var nextEpisodeFocused by remember { mutableStateOf(false) }
    var consumedPlayerKeyCode by remember { mutableStateOf<Int?>(null) }
    val episode = line.episodes[episodeIndex]

    val player = remember {
        ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .build()
    }

    fun showControlsForFiveSeconds() {
        controls = true
        controlsInteractionId += 1L
    }

    fun beginLoading() {
        playerPhase = playerPhase.onPrepare()
        loadProgress = 0f
        loadError = ""
        loadAttemptId += 1L
        // 加载期间控制器必须常驻，不能进入 5 秒自动隐藏计时。
        controls = true
    }

    fun prepareCurrent() {
        beginLoading()
        pendingSeekTargetMs = null
        player.stop()
        val upstream = DefaultHttpDataSource.Factory().setUserAgent(NetworkModule.USER_AGENT).setAllowCrossProtocolRedirects(true)
        val filtering = FilteringDataSource.Factory(upstream)
        val source = HlsMediaSource.Factory(filtering).createMediaSource(MediaItem.fromUri(episode.url))
        player.setMediaSource(source)
        player.prepare()
        player.seekTo(store.loadPosition(store.key(movie.vod.vodName, line.name, episode.title)))
        player.playWhenReady = true
    }

    fun seekBy(deltaMs: Long) {
        if (!playerPhase.hasStartedPlayback ||
            playerPhase.phase == PlayerPhase.ERROR ||
            playerPhase.phase == PlayerPhase.TIMEOUT
        ) return
        val target = accumulateSeekTarget(
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            pendingTargetMs = pendingSeekTargetMs,
            deltaMs = deltaMs,
            durationMs = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: duration
        )
        pendingSeekTargetMs = target
        position = target
        player.seekTo(target)
        showControlsForFiveSeconds()
    }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
        // 首次进入播放器处于加载态，控制器常驻。
        controls = true
    }
    LaunchedEffect(controls, requestNextEpisodeFocus, episodeIndex) {
        if (controls && requestNextEpisodeFocus && episodeIndex < line.episodes.lastIndex) {
            nextEpisodeFocusRequester.requestFocus()
            requestNextEpisodeFocus = false
        }
    }
    LaunchedEffect(controlsInteractionId, playerPhase.phase, nextEpisodeFocused) {
        if (controlsInteractionId <= 0L || playerPhase.phase != PlayerPhase.READY || nextEpisodeFocused) return@LaunchedEffect
        val expectedId = controlsInteractionId
        delay(CONTROLS_HIDE_DELAY_MS)
        if (controlsInteractionId == expectedId && playerPhase.phase == PlayerPhase.READY && !nextEpisodeFocused) controls = false
    }
    LaunchedEffect(loadAttemptId) {
        if (loadAttemptId <= 0L) return@LaunchedEffect
        val expectedAttempt = loadAttemptId
        val startedAt = SystemClock.elapsedRealtime()
        while (isActive && loadAttemptId == expectedAttempt && playerPhase.phase == PlayerPhase.INITIAL_LOADING) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            loadProgress = (elapsed.toFloat() / PLAYER_LOAD_TIMEOUT_MS).coerceIn(0f, 1f)
            if (elapsed >= PLAYER_LOAD_TIMEOUT_MS) {
                player.stop()
                loadProgress = 1f
                loadError = "资源加载超时（${PLAYER_LOAD_TIMEOUT_MS / 1_000} 秒）"
                playerPhase = playerPhase.onTimeout()
                controls = true
                break
            }
            delay(100L)
        }
    }
    LaunchedEffect(episodeIndex) { prepareCurrent() }
    LaunchedEffect(player, episodeIndex) {
        while (isActive) {
            delay(1_000L)
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            nextHint = duration > 0 && duration - position in 1..5_000 && episodeIndex < line.episodes.lastIndex
        }
    }
    LaunchedEffect(player, episodeIndex) {
        while (isActive) {
            delay(5_000L)
            val currentPosition = player.currentPosition.coerceAtLeast(0L)
            if (playerPhase.hasStartedPlayback && currentPosition > 0L) {
                store.save(movie, line.name, episode, currentPosition, player.duration.coerceAtLeast(0L))
            }
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        playerPhase = playerPhase.onBuffering()
                        loadError = ""
                        controls = true
                    }
                    Player.STATE_READY -> {
                        loadProgress = 1f
                        playerPhase = playerPhase.onReady()
                        if (playerPhase.phase == PlayerPhase.READY) {
                            loadError = ""
                            controls = true
                        }
                    }
                    Player.STATE_ENDED -> {
                        if (episodeIndex < line.episodes.lastIndex) episodeIndex++
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onIsPlayingChanged(playerIsPlaying: Boolean) {
                playerPhase = playerPhase.onPlayingChanged(playerIsPlaying)
                if (playerIsPlaying) {
                    loadProgress = 1f
                    loadError = ""
                    // 确认视频真正开始播放后，才从此刻启动 5 秒隐藏计时。
                    showControlsForFiveSeconds()
                }
            }

            override fun onPositionDiscontinuity(reason: Int) {
                // SEEK 完成后才清空累积目标；在此之前连续方向键仍在目标位置上继续叠加。
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    position = player.currentPosition.coerceAtLeast(0L)
                    pendingSeekTargetMs = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerPhase = playerPhase.onError()
                loadError = playerErrorMessage(error)
                controls = true
            }
        }
        player.addListener(listener)
        onDispose {
            store.savePositionNow(
                movie.vod.vodName,
                line.name,
                episode.title,
                player.currentPosition.coerceAtLeast(0L)
            )
            player.removeListener(listener)
            player.release()
        }
    }
    BackHandler(onBack = onBack)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .onFocusChanged { playerAreaFocused = it.isFocused }
            .focusOrder {
                if (controls && episodeIndex < line.episodes.lastIndex) down = nextEpisodeFocusRequester
            }
            .focusable()
            .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val handledKey = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                native.keyCode == KeyEvent.KEYCODE_ENTER ||
                native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                native.keyCode == KeyEvent.KEYCODE_BACK
            if (native.action == KeyEvent.ACTION_UP && consumedPlayerKeyCode == native.keyCode) {
                consumedPlayerKeyCode = null
                return@onPreviewKeyEvent true
            }
            if (!playerAreaFocused || !handledKey) return@onPreviewKeyEvent false
            if (native.action != KeyEvent.ACTION_DOWN || native.repeatCount != 0) return@onPreviewKeyEvent true
            consumedPlayerKeyCode = native.keyCode
            when (native.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (playerPhase.phase == PlayerPhase.ERROR || playerPhase.phase == PlayerPhase.TIMEOUT) {
                        prepareCurrent()
                    } else if (playerPhase.phase == PlayerPhase.READY) {
                        if (nextHint && episodeIndex < line.episodes.lastIndex) episodeIndex++
                        else if (player.isPlaying) player.pause() else player.play()
                        showControlsForFiveSeconds()
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    seekBy(-SEEK_STEP_MS)
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    seekBy(SEEK_STEP_MS)
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (controls && episodeIndex < line.episodes.lastIndex) {
                        nextEpisodeFocusRequester.requestFocus()
                    } else if (episodeIndex < line.episodes.lastIndex) {
                        showControlsForFiveSeconds()
                        requestNextEpisodeFocus = true
                    }
                    true
                }
                KeyEvent.KEYCODE_BACK -> { onBack(); true }
                else -> false
            }
        }
    ) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false } }, modifier = Modifier.fillMaxSize())

        if (playerPhase.phase == PlayerPhase.INITIAL_LOADING ||
            playerPhase.phase == PlayerPhase.ERROR ||
            playerPhase.phase == PlayerPhase.TIMEOUT
        ) {
            PlayerLoadOverlay(
                state = playerPhase.phase,
                progress = loadProgress,
                error = loadError,
                timeoutSeconds = PLAYER_LOAD_TIMEOUT_MS / 1_000,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (controls) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .72f)).padding(24.dp)) {
                Text("${movie.vod.vodName} · ${episode.title}", color = Color.White)
                if (playerPhase.phase == PlayerPhase.READY || playerPhase.phase == PlayerPhase.BUFFERING) {
                    LinearProgressIndicator(progress = if (duration > 0) position.toFloat() / duration else 0f, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlaybackStateIcon(isPlaying = playerPhase.isPlaying)
                        Spacer(Modifier.width(12.dp))
                        Text("${format(position)} / ${format(duration)}", color = Color.White, modifier = Modifier.weight(1f))
                        if (episodeIndex < line.episodes.lastIndex) {
                            TvPill(
                                "下一集",
                                {
                                    episodeIndex++
                                    pendingSeekTargetMs = null
                                    controls = true
                                    playerFocusRequester.requestFocus()
                                },
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .focusRequester(nextEpisodeFocusRequester)
                                    .onFocusChanged { nextEpisodeFocused = it.isFocused }
                                    .focusOrder { up = playerFocusRequester; left = playerFocusRequester },
                                onKeyDown = { keyCode ->
                                    when (keyCode) {
                                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            playerFocusRequester.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            )
                        }
                    }
                } else {
                    val statusText = when (playerPhase.phase) {
                        PlayerPhase.INITIAL_LOADING -> "正在加载资源 · ${(loadProgress * 100).toInt()}%"
                        PlayerPhase.BUFFERING -> "正在缓冲…"
                        PlayerPhase.TIMEOUT -> "加载超时 · 按 OK 重试"
                        PlayerPhase.ERROR -> "加载失败 · 按 OK 重试"
                        PlayerPhase.READY -> ""
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(statusText, color = if (playerPhase.phase == PlayerPhase.INITIAL_LOADING) Color.White else Color(0xFFFFB36B))
                }
            }
        }
        if (nextHint && playerPhase.phase == PlayerPhase.READY) Text("下一集：${line.episodes[episodeIndex + 1].title} · 按 OK 立即切换", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(32.dp).background(Color.Black.copy(alpha = .8f)).padding(14.dp))
    }
}

/** 播放状态图标：正在播放显示暂停双竖线，已暂停显示播放三角。 */
@Composable
private fun PlaybackStateIcon(isPlaying: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        if (isPlaying) {
            val barWidth = size.width * .24f
            val gap = size.width * .18f
            val left = (size.width - barWidth * 2f - gap) / 2f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, size.height * .12f),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height * .76f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * .18f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left + barWidth + gap, size.height * .12f),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height * .76f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * .18f)
            )
        } else {
            val triangle = Path().apply {
                moveTo(size.width * .27f, size.height * .14f)
                lineTo(size.width * .82f, size.height * .5f)
                lineTo(size.width * .27f, size.height * .86f)
                close()
            }
            drawPath(triangle, Color.White)
        }
    }
}

private const val SEEK_STEP_MS = 5_000L
private const val PLAYER_LOAD_TIMEOUT_MS = 20_000L
private const val CONTROLS_HIDE_DELAY_MS = 5_000L

/** 加载状态卡片：进度条的 100% 对应播放器首帧最大等待时间。 */
@Composable
private fun PlayerLoadOverlay(
    state: PlayerPhase,
    progress: Float,
    error: String,
    timeoutSeconds: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .width(520.dp)
            .background(Color.Black.copy(alpha = .82f), RoundedCornerShape(14.dp))
            .padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val title = when (state) {
            PlayerPhase.INITIAL_LOADING -> "正在加载视频资源"
            PlayerPhase.BUFFERING -> "网络缓冲中"
            PlayerPhase.TIMEOUT -> "资源加载超时"
            PlayerPhase.ERROR -> "资源加载失败"
            PlayerPhase.READY -> ""
        }
        Text(title, color = Color.White)
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (state == PlayerPhase.INITIAL_LOADING) VodAccent else Color(0xFFFFA451),
            backgroundColor = Color.White.copy(alpha = .18f)
        )
        Spacer(Modifier.height(10.dp))
        when (state) {
            PlayerPhase.INITIAL_LOADING -> Text(
                "${(progress * 100).toInt()}% · 最长等待 ${timeoutSeconds} 秒",
                color = Color.White.copy(alpha = .76f)
            )
            PlayerPhase.BUFFERING -> Text("正在恢复播放，请稍候", color = Color.White.copy(alpha = .76f))
            PlayerPhase.ERROR, PlayerPhase.TIMEOUT -> {
                Text(error.ifBlank { "当前资源无法播放" }, color = Color(0xFFFFB36B))
                Spacer(Modifier.height(8.dp))
                Text("按 OK 重新加载 · 按返回退出", color = Color.White.copy(alpha = .82f))
            }
            PlayerPhase.READY -> Unit
        }
    }
}

private fun playerErrorMessage(error: PlaybackException): String {
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接失败，请检查网络或资源地址"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "播放地址返回异常状态"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "视频清单格式不受支持"
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "设备无法解码该视频"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "当前资源无法播放"
    }
}

private fun format(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000).toInt()
    return "%02d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
}
