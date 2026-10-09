package com.ictools.ichomelauncher.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ictools.ichomelauncher.PanelMetrics
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.PanelShape

/**
 * フローティングパネルの共通部品。
 * タイトルバーのドラッグで移動、右下の三角ハンドルのドラッグでリサイズ、タッチでフォーカス。
 * 座標・サイズはすべて dp。
 */
@Composable
fun FloatingPanel(
    title: String,
    xDp: Float,
    yDp: Float,
    widthDp: Float,
    heightDp: Float,
    focused: Boolean,
    onFocus: () -> Unit,
    onClose: () -> Unit,
    onMove: (dxDp: Float, dyDp: Float) -> Unit,
    onResize: (dwDp: Float, dhDp: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // pointerInput 内から常に最新のコールバックを呼ぶため
    val currentOnFocus by rememberUpdatedState(onFocus)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Column(
        modifier
            .offset { IntOffset(xDp.dp.roundToPx(), yDp.dp.roundToPx()) }
            .size(widthDp.dp, heightDp.dp)
            // パネル内のどこを触ってもフォーカス（最前面）にする。イベントは消費しない
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentOnFocus()
                }
            }
            .clip(PanelShape)
            .background(IhlColors.PanelBackground)
            .border(1.dp, if (focused) IhlColors.BorderFocused else IhlColors.Border, PanelShape)
    ) {
        // ---- タイトルバー（ドラッグで移動） ----
        Row(
            Modifier
                .fillMaxWidth()
                .height(PanelMetrics.TITLE_BAR_HEIGHT.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragEnd() }
                    ) { change, amount ->
                        change.consume()
                        currentOnMove(amount.x.toDp().value, amount.y.toDp().value)
                    }
                }
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = if (focused) IhlColors.Accent else IhlColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 閉じるボタン（右上はカットされているので少し内側に置く）
            Box(
                Modifier
                    .size(PanelMetrics.TITLE_BAR_HEIGHT.dp)
                    .padding(end = 6.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = IhlColors.TextDim)
            }
        }
        // タイトルバー下の区切り線
        Box(Modifier.fillMaxWidth().height(1.dp).background(IhlColors.Line))

        // ---- 本体 ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()

            // ---- リサイズハンドル（右下の小さな三角） ----
            Canvas(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { currentOnDragEnd() },
                            onDragCancel = { currentOnDragEnd() }
                        ) { change, amount ->
                            change.consume()
                            currentOnResize(amount.x.toDp().value, amount.y.toDp().value)
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w - 3.dp.toPx(), h * 0.4f)
                    lineTo(w - 3.dp.toPx(), h - 3.dp.toPx())
                    lineTo(w * 0.4f, h - 3.dp.toPx())
                    close()
                }
                drawPath(path, color = if (focused) IhlColors.BorderFocused else IhlColors.Border)
            }
        }
    }
}

/** パネル本体の標準的な内側余白付きコンテナ */
@Composable
fun PanelBody(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(8.dp)) { content() }
}
