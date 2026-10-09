package com.ictools.ichomelauncher.ui.media

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ictools.ichomelauncher.data.AudioVisualizer
import com.ictools.ichomelauncher.data.EqState
import com.ictools.ichomelauncher.data.MediaInfo
import com.ictools.ichomelauncher.data.MediaSettings
import com.ictools.ichomelauncher.data.VisualizerMode
import com.ictools.ichomelauncher.ui.common.PermissionPrompt
import com.ictools.ichomelauncher.ui.common.WireButton
import com.ictools.ichomelauncher.ui.common.formatDuration
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.SmallCutShape
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** メディアパネルから呼ぶ操作 */
class MediaActions(
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (Long) -> Unit,
    val skipToQueueItem: (Long) -> Unit,
    val openPlayer: () -> Unit,
    val requestAccess: () -> Unit,
    val openAppInfo: () -> Unit,
    val onPermissionResult: () -> Unit,
    val eqSetEnabled: (Boolean) -> Unit,
    val eqSelectPreset: (Int) -> Unit,
    val eqSetBand: (Int, Int) -> Unit,
    val eqReset: () -> Unit
)

/** メディアパネルの状態 */
class MediaPanelState(
    val media: MediaInfo?,
    val hasNotificationAccess: Boolean,
    val hasRecordAudio: Boolean,
    val settings: MediaSettings,
    val eq: EqState
)

private enum class MediaTab(val label: String) { QUEUE("Queue"), EQ("EQ") }

/**
 * メディアパネルの中身。
 * 曲情報 → 波形 → 再生バー → 操作ボタン（固定）→ キュー／EQ のタブ。
 */
@Composable
fun MediaPanel(state: MediaPanelState, actions: MediaActions) {
    if (!state.hasNotificationAccess) {
        NotificationAccessPrompt(actions.requestAccess, actions.openAppInfo)
        return
    }
    val media = state.media

    Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 4.dp)) {
        // ---- 曲情報（タップで再生アプリを開く） ----
        if (media == null) {
            Text("no media yet", color = IhlColors.TextDim)
        } else {
            TrackInfo(media, actions.openPlayer)
        }
        // ---- 音楽波形 ----
        if (state.settings.visualizerEnabled) {
            Spacer(Modifier.height(6.dp))
            VisualizerView(
                mode = state.settings.visualizerMode,
                playing = media?.isPlaying == true,
                hasPermission = state.hasRecordAudio,
                onPermissionResult = actions.onPermissionResult,
                openAppInfo = actions.openAppInfo
            )
        }
        // ---- 再生バー ----
        Spacer(Modifier.height(6.dp))
        if (media != null) ProgressBar(media, actions.seek)
        // ---- 操作ボタン（常にこの位置） ----
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton("|<", actions.previous)
            ControlButton(if (media?.isPlaying == true) "||" else ">", actions.playPause)
            ControlButton(">|", actions.next)
        }
        // ---- キュー／EQ ----
        Spacer(Modifier.height(6.dp))
        val tabs = if (state.settings.showQueue) MediaTab.entries else listOf(MediaTab.EQ)
        var tab by rememberSaveable { mutableStateOf(MediaTab.QUEUE) }
        val current = if (tab in tabs) tab else tabs.first()
        Row(verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val selected = t == current
                Text(
                    if (selected) "[${t.label}]" else " ${t.label} ",
                    color = if (selected) IhlColors.Accent else IhlColors.TextDim,
                    modifier = Modifier.clickable { tab = t }.padding(end = 4.dp, top = 2.dp, bottom = 2.dp)
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(IhlColors.Line))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (current) {
                MediaTab.QUEUE -> QueueView(media, actions.skipToQueueItem)
                MediaTab.EQ -> EqView(media, state.eq, actions)
            }
        }
    }
}

@Composable
private fun TrackInfo(media: MediaInfo, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(SmallCutShape).border(1.dp, IhlColors.Border, SmallCutShape)) {
            val art = media.art
            if (art != null) {
                Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(IhlColors.Line))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(media.title.ifEmpty { "(unknown)" }, color = IhlColors.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(media.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val suffix = if (media.active) "" else "  (stopped)"
            Text(media.appLabel + suffix, color = IhlColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 再生位置バー（長さが分かる場合のみ。タップした位置へシーク） */
@Composable
private fun ProgressBar(media: MediaInfo, onSeek: (Long) -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // 再生中は 0.5 秒ごとに表示を更新
    LaunchedEffect(media.isPlaying, media.positionUpdatedAt) {
        while (media.isPlaying) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
        now = SystemClock.elapsedRealtime()
    }
    val duration = media.durationMs
    val position = media.currentPosition(now)
    if (duration <= 0) {
        Text(formatDuration(position), color = IhlColors.TextDim)
        return
    }
    val fraction = (position.toFloat() / duration).coerceIn(0f, 1f)
    val wire = IhlColors.BorderFocused
    val line = IhlColors.Line
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(duration, media.active) {
                if (!media.active) return@pointerInput
                detectTapGestures { offset -> onSeek((offset.x / size.width * duration).toLong().coerceIn(0, duration)) }
            }
    ) {
        val y = size.height / 2
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        drawLine(wire, Offset(0f, y), Offset(size.width * fraction, y), strokeWidth = 2.dp.toPx())
        drawCircle(wire, radius = 4.dp.toPx(), center = Offset(size.width * fraction, y))
    }
    Row(Modifier.fillMaxWidth()) {
        Text(formatDuration(position), color = IhlColors.TextDim, modifier = Modifier.weight(1f))
        Text(formatDuration(duration), color = IhlColors.TextDim)
    }
}

@Composable
private fun ControlButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, IhlColors.Border, SmallCutShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = IhlColors.Accent)
    }
}

// ---- キュー ----

@Composable
private fun QueueView(media: MediaInfo?, onSkip: (Long) -> Unit) {
    val queue = media?.queue
    if (queue == null || queue.items.isEmpty()) {
        Text("キュー情報を取得できません", color = IhlColors.TextDim, modifier = Modifier.padding(top = 6.dp))
        return
    }
    Column(Modifier.fillMaxSize()) {
        val next = queue.next
        Text(
            "next: ${next?.title ?: "-"}",
            color = IhlColors.Accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (queue.title.isNotEmpty()) Text(queue.title, color = IhlColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(queue.items, key = { it.id }) { item ->
                val playing = item.id == queue.activeId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = media.active) { onSkip(item.id) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (playing) "> " else "  ", color = IhlColors.Accent)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = if (playing) IhlColors.Accent else IhlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.subtitle.isNotEmpty()) {
                            Text(item.subtitle, color = IhlColors.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ---- EQ ----

@Composable
private fun EqView(media: MediaInfo?, eq: EqState, actions: MediaActions) {
    val settings = eq.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp, end = 14.dp, bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("EQ", Modifier.weight(1f))
            WireButton(if (settings.enabled) "ON " else "OFF", onClick = { actions.eqSetEnabled(!settings.enabled) })
        }
        // 対応状況
        val supported = media != null && eq.sessions.containsKey(media.packageName)
        val status = when {
            media == null -> "再生中のアプリがありません"
            !supported -> "このアプリはEQに対応していません"
            else -> "対象: ${media.appLabel}"
        }
        Text(status, color = if (supported) IhlColors.Text else IhlColors.TextDim, fontSize = 11.sp)
        if (!supported) {
            Text(
                "※ 再生開始時に通知を送るアプリのみ対応。IHL 起動前に再生を始めた場合は、曲を変えるか再生し直してください。",
                color = IhlColors.TextDim, fontSize = 10.sp, lineHeight = 13.sp
            )
        }
        eq.error?.let { Text(it, color = IhlColors.Accent, fontSize = 11.sp) }

        if (eq.bands.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("対応アプリの再生中に EQ を ON にすると、バンドを調整できます", color = IhlColors.TextDim, fontSize = 11.sp)
            return@Column
        }
        // プリセット
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("preset", Modifier.weight(1f))
            var expanded by remember { mutableStateOf(false) }
            Box {
                WireButton("${eq.presets.getOrNull(settings.preset) ?: "custom"} ▾", onClick = { expanded = true })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("custom") }, onClick = { expanded = false; actions.eqSelectPreset(-1) })
                    eq.presets.forEachIndexed { i, name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; actions.eqSelectPreset(i) })
                    }
                }
            }
        }
        // バンドごとのスライダー
        eq.bands.forEachIndexed { i, band ->
            val level = settings.levels.getOrNull(i) ?: 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatHz(band.centerHz), Modifier.width(52.dp), fontSize = 11.sp)
                Slider(
                    value = level.toFloat(),
                    onValueChange = { actions.eqSetBand(i, it.roundToInt()) },
                    valueRange = eq.levelRange.first.toFloat()..eq.levelRange.last.toFloat(),
                    enabled = settings.enabled,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = IhlColors.BorderFocused,
                        activeTrackColor = IhlColors.BorderFocused,
                        inactiveTrackColor = IhlColors.Line
                    )
                )
                Text("%+.1f".format(level / 100f), Modifier.width(40.dp), fontSize = 11.sp, color = IhlColors.TextDim)
            }
        }
        WireButton("フラットに戻す", onClick = actions.eqReset)
    }
}

private fun formatHz(hz: Int): String = if (hz >= 1000) "%.1fk".format(hz / 1000f) else "$hz"

// ---- 音楽波形 ----

/**
 * 出力音声の波形／スペクトラム。
 * パネル表示中・アプリが前面・再生中のときだけ Visualizer を動かす（電池消費を抑える）。
 */
@Composable
private fun VisualizerView(
    mode: VisualizerMode,
    playing: Boolean,
    hasPermission: Boolean,
    onPermissionResult: () -> Unit,
    openAppInfo: () -> Unit
) {
    if (!hasPermission) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionResult() }
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            PermissionPrompt(
                message = "音楽波形の表示には「マイク」権限が必要です。",
                buttons = listOf(
                    "許可する" to { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                    "アプリ情報を開く" to openAppInfo
                ),
                note = "端末から出ている音の波形を解析するために Android の仕様上必要な権限です。マイクで録音することはありません。不要なら設定で波形表示を OFF にできます。"
            )
        }
        return
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val active = playing && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    var data by remember { mutableStateOf<FloatArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val visualizer = remember { AudioVisualizer() }
    DisposableEffect(active, mode) {
        if (active) {
            error = visualizer.start(mode == VisualizerMode.SPECTRUM) { data = it }
        } else {
            data = null
        }
        onDispose { visualizer.stop() }
    }

    val wire = IhlColors.BorderFocused
    val line = IhlColors.Line
    Box(Modifier.fillMaxWidth().height(40.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val values = data
            val midY = size.height / 2
            if (values == null || values.isEmpty()) {
                drawLine(line, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx())
                return@Canvas
            }
            if (mode == VisualizerMode.WAVEFORM) {
                val path = Path()
                val step = size.width / (values.size - 1).coerceAtLeast(1)
                values.forEachIndexed { i, v ->
                    val x = i * step
                    val y = size.height * (1f - v)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, wire, style = Stroke(width = 1.dp.toPx()))
            } else {
                val barW = size.width / values.size
                values.forEachIndexed { i, v ->
                    val h = (size.height * v).coerceAtLeast(1f)
                    val x = i * barW + barW / 2
                    drawLine(wire, Offset(x, size.height), Offset(x, size.height - h), strokeWidth = (barW * 0.6f).coerceAtLeast(1f))
                }
            }
        }
        error?.let {
            Text("波形を取得できません（$it）", color = IhlColors.TextDim, fontSize = 10.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** 「通知へのアクセス」が無いときの案内（メディア・通知履歴で共通） */
@Composable
fun NotificationAccessPrompt(requestAccess: () -> Unit, openAppInfo: () -> Unit) {
    PermissionPrompt(
        message = "「通知へのアクセス」の許可が必要です",
        buttons = listOf(
            "許可する" to requestAccess,
            "アプリ情報を開く" to openAppInfo
        ),
        note = "再生中の曲情報の取得と操作に使います。許可できない（グレーアウトしている）場合：アプリ情報 → 右上の︙ →「制限付き設定を許可」を行ってから、もう一度許可してください。"
    )
}
