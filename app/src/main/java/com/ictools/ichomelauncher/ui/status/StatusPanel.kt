package com.ictools.ichomelauncher.ui.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ictools.ichomelauncher.data.SystemSnapshot
import com.ictools.ichomelauncher.data.thermalLabel
import com.ictools.ichomelauncher.ui.common.WireButton
import com.ictools.ichomelauncher.ui.terminal.formatSize
import com.ictools.ichomelauncher.ui.theme.IhlColors
import kotlinx.coroutines.delay

/**
 * 稼働状況パネル：RAM・バッテリー・発熱・ストレージ・稼働時間・最近使ったアプリ。
 * パネル表示中かつアプリが前面の間だけ 2 秒ごとに更新する。
 */
@Composable
fun StatusPanel(
    load: suspend () -> SystemSnapshot,
    hasUsageAccess: Boolean,
    requestUsageAccess: () -> Unit
) {
    var snap by remember { mutableStateOf<SystemSnapshot?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(resumed, hasUsageAccess) {
        while (resumed) {
            snap = load()
            delay(2000)
        }
    }
    val s = snap
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 22.dp)) {
        if (s == null) {
            Text("loading...", color = IhlColors.TextDim)
            return@Column
        }
        // ---- メモリ ----
        val used = s.ramTotal - s.ramAvailable
        Meter("RAM", "${formatSize(used)} / ${formatSize(s.ramTotal)}", used.toFloat() / s.ramTotal.coerceAtLeast(1))
        if (s.lowMemory) Text("  low memory", color = IhlColors.Accent, fontSize = 11.sp)

        // ---- バッテリー ----
        Spacer(Modifier.height(6.dp))
        val temp = s.batteryTempC?.let { "  %.1f℃".format(it) } ?: ""
        Meter("BAT", "${s.batteryPercent}%$temp", s.batteryPercent / 100f)
        Line("  ${s.batteryStatus}" + if (s.chargeSource != "-") " (${s.chargeSource})" else "")

        // ---- 発熱 ----
        Spacer(Modifier.height(6.dp))
        val headroom = s.thermalHeadroom?.let { "  headroom %.2f".format(it) } ?: ""
        Line("THERMAL  ${thermalLabel(s.thermalStatus)}$headroom")

        // ---- ストレージ ----
        Spacer(Modifier.height(6.dp))
        val storageUsed = s.storageTotal - s.storageFree
        Meter("DISK", "${formatSize(storageUsed)} / ${formatSize(s.storageTotal)}", storageUsed.toFloat() / s.storageTotal.coerceAtLeast(1))
        Line("  free ${formatSize(s.storageFree)}")

        // ---- 稼働時間 ----
        Spacer(Modifier.height(6.dp))
        Line("UPTIME  ${formatUptime(s.uptimeMs)}")

        // ---- 最近使ったアプリ ----
        Spacer(Modifier.height(10.dp))
        Text("RECENT APPS (today)", color = IhlColors.Accent)
        val recent = s.recentApps
        if (recent == null || !hasUsageAccess) {
            Text("最近使ったアプリの表示には「使用状況へのアクセス」の許可が必要です", color = IhlColors.TextDim, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            WireButton("許可する（設定を開く）", onClick = requestUsageAccess)
            Text(
                "許可できない場合：アプリ情報 → 右上の︙ →「制限付き設定を許可」を行ってからもう一度お試しください。",
                color = IhlColors.TextDim, fontSize = 10.sp, lineHeight = 13.sp
            )
        } else if (recent.isEmpty()) {
            Text("no usage today", color = IhlColors.TextDim)
        } else {
            recent.forEach { u ->
                Row(Modifier.fillMaxWidth()) {
                    Text(u.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatUsage(u.foregroundMs), color = IhlColors.TextDim)
                }
            }
        }
    }
}

/** ラベル・値・横棒メーター */
@Composable
private fun Meter(label: String, value: String, fraction: Float) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(56.dp), color = IhlColors.Accent)
        Text(value, Modifier.weight(1f))
    }
    val wire = IhlColors.BorderFocused
    val line = IhlColors.Line
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val y = size.height / 2
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
        drawLine(wire, Offset(0f, y), Offset(size.width * fraction.coerceIn(0f, 1f), y), strokeWidth = 3.dp.toPx())
    }
}

@Composable
private fun Line(text: String) {
    Text(text, color = IhlColors.Text)
}

private fun formatUptime(ms: Long): String {
    val m = ms / 60_000
    val d = m / (60 * 24)
    val h = (m / 60) % 24
    return if (d > 0) "${d}d ${h}h ${m % 60}m" else "${h}h ${m % 60}m"
}

private fun formatUsage(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
