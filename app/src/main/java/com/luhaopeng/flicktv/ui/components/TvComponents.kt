package com.luhaopeng.flicktv.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import com.luhaopeng.flicktv.ui.theme.VodAccent
import com.luhaopeng.flicktv.ui.theme.VodDimens
import com.luhaopeng.flicktv.ui.theme.VodFocus
import com.luhaopeng.flicktv.ui.theme.VodSurface
import com.luhaopeng.flicktv.ui.theme.VodSurfaceRaised
import com.luhaopeng.flicktv.ui.theme.VodTextMuted
import com.luhaopeng.flicktv.ui.theme.VodTextPrimary
import com.luhaopeng.flicktv.ui.theme.VodTextSecondary
import com.luhaopeng.flicktv.ui.theme.VodType

/** 通用 TV 焦点容器：聚焦时放大并显示白色边框。 */
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onKeyDown: ((Int) -> Boolean)? = null,
    focusedScale: Float = 1.06f,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var consumedKeyCode by remember { mutableStateOf<Int?>(null) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f)
    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(VodDimens.PillRadius))
            .background(if (selected) VodAccent.copy(alpha = .24f) else VodSurface)
            .border(if (focused) VodDimens.FocusBorder else 1.dp, if (focused) VodFocus else Color.Transparent, RoundedCornerShape(VodDimens.PillRadius))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            // Compose 1.0 的 clickable 在部分 Android TV/投影仪上不会把确认键转换为点击，故显式消费按下事件。
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val isConfirm = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == KeyEvent.KEYCODE_ENTER ||
                    native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                    native.keyCode == KeyEvent.KEYCODE_BUTTON_A
                if (native.action == KeyEvent.ACTION_UP && consumedKeyCode == native.keyCode) {
                    consumedKeyCode = null
                    true
                } else if (native.action == KeyEvent.ACTION_DOWN && native.repeatCount == 0 && onKeyDown?.invoke(native.keyCode) == true) {
                    consumedKeyCode = native.keyCode
                    true
                } else if (isConfirm && native.action == KeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                    consumedKeyCode = native.keyCode
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .focusable(),
        content = content
    )
}

@Composable
fun TvPill(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    onKeyDown: ((Int) -> Boolean)? = null,
    focusedScale: Float = 1.06f
) {
    TvFocusable(onClick, modifier = modifier, selected = selected, onFocused = onFocused, onKeyDown = onKeyDown, focusedScale = focusedScale) {
        Text(
            text = text,
            color = VodTextPrimary,
            fontSize = VodType.Body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 自适应高度的普通胶囊视觉不变；当调用方指定固定高度时，文字在容器中真正居中。
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center).padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun PosterTile(
    title: String,
    poster: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    badge: String = "",
    posterCandidates: List<String> = if (poster.isBlank()) emptyList() else listOf(poster),
    /** 默认 2:3；热门推荐传 8:9 可令同宽海报高度缩为原来的 75%。 */
    posterAspectRatio: Float = 2f / 3f
) {
    val scale by animateFloatAsState(if (selected) 1.04f else 1f)
    Column(
        modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(VodDimens.CardRadius))
            .background(VodSurface)
            .border(if (selected) VodDimens.FocusBorder else 1.dp, if (selected) VodFocus else Color.Transparent, RoundedCornerShape(VodDimens.CardRadius))
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(posterAspectRatio).background(VodSurfaceRaised)) {
            FallbackPosterImage(title = title, urls = posterCandidates.ifEmpty { listOfNotNull(poster.takeIf { it.isNotBlank() }) })
            if (badge.isNotBlank()) Text(badge, color = Color.Black, fontSize = 11.sp, modifier = Modifier.padding(6.dp).background(VodAccent, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
        }
        Text(title, color = VodTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 2.dp))
        Text(subtitle, color = VodTextSecondary, fontSize = VodType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp, 0.dp, 8.dp, 8.dp))
    }
}

/**
 * 带候选链的海报加载器。豆瓣图按镜像、原图逐个尝试；请求原图时补 Referer，
 * 解决 TV 端直接加载 doubanio 经常被防盗链拒绝的问题。
 */
@Composable
private fun BoxScope.FallbackPosterImage(title: String, urls: List<String>) {
    val candidates = remember(urls) { urls.filter { it.isNotBlank() }.distinct() }
    var index by remember(candidates) { mutableStateOf(0) }
    val current = candidates.getOrNull(index)
    if (current == null) {
        Text(
            text = title.take(4),
            color = VodTextMuted,
            fontSize = VodType.Body,
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center).padding(12.dp)
        )
        return
    }
    val context = LocalContext.current
    val request = remember(current) {
        ImageRequest.Builder(context)
            .data(current)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 Chrome/122 Safari/537.36 FlickTV/1.0")
            .apply {
                if (current.contains("doubanio.com")) addHeader("Referer", "https://movie.douban.com/")
            }
            .listener(onError = { _, _ -> index += 1 })
            .build()
    }
    Image(
        painter = rememberImagePainter(request),
        contentDescription = title,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun PosterCard(
    title: String,
    poster: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String = "",
    onFocused: (() -> Unit)? = null,
    focusModifier: Modifier = Modifier
) {
    TvFocusable(onClick, modifier = modifier.then(focusModifier), onFocused = onFocused) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(VodSurfaceRaised)) {
                if (poster.isNotBlank()) {
                    Image(painter = rememberImagePainter(poster), contentDescription = title, modifier = Modifier.fillMaxSize())
                }
                if (badge.isNotBlank()) {
                    Text(badge, color = Color.Black, fontSize = 11.sp, modifier = Modifier.padding(6.dp).background(VodAccent, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
            Text(title, color = VodTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 2.dp))
            Text(subtitle, color = VodTextSecondary, fontSize = VodType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp, 0.dp, 8.dp, 8.dp))
        }
    }
}
