package com.ictools.ichomelauncher.ui.media

import android.os.SystemClock
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ictools.ichomelauncher.data.MediaInfo
import com.ictools.ichomelauncher.ui.common.PermissionPrompt
import com.ictools.ichomelauncher.ui.common.formatDuration
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.SmallCutShape
import kotlinx.coroutines.delay

/** メディアパネルから呼ぶ操作 */
class MediaActions(
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (Long) -> Unit,
    val openPlayer: () -> Unit,
    val requestAccess: () -> Unit,
    val openAppInfo: () -> Unit
)

/** メディアパネルの中身：アートワーク・曲名・再生位置・操作ボタン */
@Composable
fun MediaPanel(media: MediaInfo?, hasAccess: Boolean, actions: MediaActions) {
    if (!hasAccess) {
        NotificationAccessPrompt(actions.requestAccess, actions.openAppInfo)
        return
    }
    if (media == null) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            Text("no media playing", color = IhlColors.TextDim)
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 6.dp)) {
        // ---- 曲情報（タップで再生中のアプリを開く） ----
        Row(
            Modifier.fillMaxWidth().clickable(onClick = actions.openPlayer),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                Text(media.artist, color = IhlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(media.appLabel, color = IhlColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(media, actions.seek)
        Spacer(Modifier.weight(1f))
        // ---- 操作ボタン ----
        Row(
            Modifier.fillMaxWidth().padding(end = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton("|<", actions.previous)
            ControlButton(if (media.isPlaying) "||" else ">", actions.playPause)
            ControlButton(">|", actions.next)
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
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(duration) {
                detectTapGestures { offset -> onSeek((offset.x / size.width * duration).toLong().coerceIn(0, duration)) }
            }
    ) {
        val y = size.height / 2
        drawLine(IhlColors.Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        drawLine(IhlColors.BorderFocused, Offset(0f, y), Offset(size.width * fraction, y), strokeWidth = 2.dp.toPx())
        drawCircle(IhlColors.Accent, radius = 4.dp.toPx(), center = Offset(size.width * fraction, y))
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
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = IhlColors.Accent)
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
        note = "許可できない（グレーアウトしている）場合：アプリ情報 → 右上の︙ →「制限付き設定を許可」を行ってから、もう一度許可してください。"
    )
}
