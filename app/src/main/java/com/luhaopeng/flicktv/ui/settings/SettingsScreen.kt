package com.luhaopeng.flicktv.ui.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusOrder
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luhaopeng.flicktv.data.health.SourceHealth
import com.luhaopeng.flicktv.data.model.SourceConfig
import com.luhaopeng.flicktv.ui.components.TvPill
import com.luhaopeng.flicktv.ui.components.ensureItemVisible
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    remoteUrl: String,
    sources: List<SourceConfig>,
    message: String,
    healthRevision: Int,
    checkingSourceId: String?,
    checkMessage: String,
    health: (String) -> SourceHealth,
    onUrl: (String) -> Unit,
    onUpdate: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onCheck: (String) -> Unit,
    onRestore: () -> Unit,
    tabFocusRequester: FocusRequester,
    contentEntryFocusRequester: FocusRequester
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sourceListFocusRequester = remember { FocusRequester() }
    val updateFocusRequester = remember { FocusRequester() }
    val restoreFocusRequester = remember { FocusRequester() }
    val dialogInputFocusRequester = remember { FocusRequester() }
    val dialogSaveFocusRequester = remember { FocusRequester() }
    val healthSnapshots = remember(sources, healthRevision) {
        sources.associate { source -> source.id to health(source.id) }
    }
    var selectedIndex by remember { mutableStateOf(0) }
    var listFocused by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlDraft by remember(remoteUrl) { mutableStateOf(remoteUrl) }
    var dialogInputFocused by remember { mutableStateOf(false) }
    var dialogSaveFocused by remember { mutableStateOf(false) }

    fun select(index: Int) {
        if (sources.isEmpty()) return
        val targetIndex = index.coerceIn(sources.indices)
        selectedIndex = targetIndex
        scope.launch { listState.ensureItemVisible(targetIndex) }
    }

    LaunchedEffect(showUrlDialog) {
        if (showUrlDialog) dialogInputFocusRequester.requestFocus()
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = null,
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            val directionKey = native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                                native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                                native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                                native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                            if (directionKey && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                            if (!directionKey || native.repeatCount != 0) return@onPreviewKeyEvent directionKey
                            if (dialogInputFocused) dialogSaveFocusRequester.requestFocus()
                            else if (dialogSaveFocused) dialogInputFocusRequester.requestFocus()
                            true
                        }
                ) {
                    Text("远程配置 URL", style = MaterialTheme.typography.h6)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = urlDraft,
                            onValueChange = { urlDraft = it },
                            singleLine = true,
                            placeholder = { Text("https://example.com/sources.json") },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(dialogInputFocusRequester)
                                .onFocusChanged { dialogInputFocused = it.isFocused }
                                .focusOrder { right = dialogSaveFocusRequester; down = dialogSaveFocusRequester }
                                .onPreviewKeyEvent { event ->
                                    val native = event.nativeKeyEvent
                                    val handled = native.keyCode == KeyEvent.KEYCODE_BACK ||
                                        native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                                        native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                                        native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                                        native.keyCode == KeyEvent.KEYCODE_DPAD_UP
                                    if (handled && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                                    if (!handled || native.repeatCount != 0) return@onPreviewKeyEvent handled
                                    when (native.keyCode) {
                                        KeyEvent.KEYCODE_BACK -> dialogSaveFocusRequester.requestFocus()
                                        KeyEvent.KEYCODE_DPAD_LEFT,
                                        KeyEvent.KEYCODE_DPAD_RIGHT,
                                        KeyEvent.KEYCODE_DPAD_DOWN,
                                        KeyEvent.KEYCODE_DPAD_UP -> dialogSaveFocusRequester.requestFocus()
                                    }
                                    true
                                }
                        )
                        Spacer(Modifier.width(12.dp))
                        TvPill(
                            text = "保存",
                            onClick = { onUrl(urlDraft); showUrlDialog = false },
                            modifier = Modifier
                                .height(56.dp)
                                .focusRequester(dialogSaveFocusRequester)
                                .onFocusChanged { dialogSaveFocused = it.isFocused }
                                .focusOrder { left = dialogInputFocusRequester; up = dialogInputFocusRequester },
                            onKeyDown = { keyCode ->
                                when (keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT,
                                    KeyEvent.KEYCODE_DPAD_UP,
                                    KeyEvent.KEYCODE_DPAD_RIGHT,
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        dialogInputFocusRequester.requestFocus()
                                        true
                                    }
                                    KeyEvent.KEYCODE_BACK -> {
                                        showUrlDialog = false
                                        true
                                    }
                                    else -> false
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "填写可公开访问的 HTTP(S) JSON 地址。格式示例：\n{\"sources\":[{\"id\":\"demo\",\"name\":\"示例源\",\"api\":\"https://example.com/api.php/provide/vod\"}]}\n保存后返回设置页，再点“立即更新”拉取；失败时继续使用本地缓存或内置默认配置。",
                        color = Color(0xFFA8A8B3),
                        style = MaterialTheme.typography.body2
                    )
                }
            },
            confirmButton = {}
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp)) {
        Row {
            TvPill(
                if (remoteUrl.isBlank()) "设置远程配置 URL" else remoteUrl,
                { urlDraft = remoteUrl; showUrlDialog = true },
                modifier = Modifier.weight(1f).focusOrder(contentEntryFocusRequester) {
                    up = tabFocusRequester
                    down = sourceListFocusRequester
                    right = updateFocusRequester
                },
                focusedScale = 1f
            )
            Spacer(Modifier.width(12.dp))
            TvPill("立即更新", onUpdate, modifier = Modifier.focusOrder(updateFocusRequester) {
                left = contentEntryFocusRequester
                right = restoreFocusRequester
                down = sourceListFocusRequester
                up = tabFocusRequester
            })
            Spacer(Modifier.width(12.dp))
            TvPill("恢复默认配置", onRestore, modifier = Modifier.focusOrder(restoreFocusRequester) {
                left = updateFocusRequester
                down = sourceListFocusRequester
                up = tabFocusRequester
            })
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFFFFC66D), modifier = Modifier.padding(vertical = 8.dp))
        if (checkMessage.isNotBlank()) Text(checkMessage, color = Color(0xFFFFC66D), modifier = Modifier.padding(vertical = 6.dp))
        Text("源管理", color = Color.White, style = MaterialTheme.typography.h6, modifier = Modifier.padding(top = 10.dp))
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .focusRequester(sourceListFocusRequester)
                .onFocusChanged { listFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val handled = native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                        native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                        native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == KeyEvent.KEYCODE_ENTER ||
                        native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                        native.keyCode == KeyEvent.KEYCODE_BUTTON_A
                    // 方向键与确认键完整按键周期均由列表容器消费，防止 ACTION_UP
                    // 触发第二次空间寻焦，使向上滚动看起来像瞬间跳跃。
                    if (handled && native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                    if (!handled || native.repeatCount != 0) return@onPreviewKeyEvent handled
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (selectedIndex == 0) contentEntryFocusRequester.requestFocus() else select(selectedIndex - 1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { if (selectedIndex < sources.lastIndex) select(selectedIndex + 1); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                            sources.getOrNull(selectedIndex)?.let { source ->
                                val current = healthSnapshots[source.id] ?: health(source.id)
                                val shouldCheck = checkingSourceId == null &&
                                    (!current.manualEnabled || current.autoDisabled || current.lastSuccessAt <= 0L)
                                if (shouldCheck) onCheck(source.id)
                                else if (checkingSourceId == null) onToggle(source.id, false)
                            }
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
            state = listState,
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            itemsIndexed(sources) { index, source ->
                val state = healthSnapshots[source.id] ?: health(source.id)
                val selected = listFocused && index == selectedIndex
                val status = when {
                    checkingSourceId == source.id -> "检测中…"
                    !state.manualEnabled -> "手动停用 · 按确定启用并检测"
                    state.autoDisabled -> "自动禁用 · 下次自动重试 ${format(state.nextRetryAt())} · 按确定立即检测"
                    state.lastSuccessAt > 0 -> "在线 · 上次成功 ${format(state.lastSuccessAt)}"
                    else -> "待检测 · 按确定立即检测"
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(if (selected) Color(0xFF1E3532) else Color(0xFF17171F), RoundedCornerShape(10.dp))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "● ${source.name}",
                                color = if (state.autoDisabled || !state.manualEnabled) Color.Gray else Color(0xFF63E6A8)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                source.api,
                                color = Color(0xFF7F8792),
                                style = MaterialTheme.typography.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(status, color = Color(0xFFA8A8B3))
                    }
                    Text(
                        when {
                            checkingSourceId == source.id -> "检测中"
                            !state.manualEnabled -> "检测并启用"
                            state.autoDisabled || state.lastSuccessAt <= 0L -> "立即检测"
                            else -> "停用"
                        },
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

private fun format(time: Long): String = if (time <= 0) "-" else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))
