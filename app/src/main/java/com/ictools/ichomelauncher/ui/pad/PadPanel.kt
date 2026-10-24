package com.ictools.ichomelauncher.ui.pad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.gesture.GestureAction
import com.ictools.ichomelauncher.gesture.GestureType
import com.ictools.ichomelauncher.gesture.backgroundGestures
import com.ictools.ichomelauncher.ui.theme.IhlColors
import kotlinx.coroutines.delay

/**
 * タッチパッドパネル：中でスワイプ・タップ・ダブルタップ・長押しをすると、
 * タッチパッド用に割り当てたアクションを実行する。背景がパネルで隠れていても操作できる。
 */
@Composable
fun PadPanel(onGesture: (GestureType) -> GestureAction) {
    // 直前に検出したジェスチャーを少しの間表示する
    var last by remember { mutableStateOf<String?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    LaunchedEffect(serial) {
        if (last != null) {
            delay(1500)
            last = null
        }
    }
    val line = IhlColors.Line
    Box(
        Modifier
            .fillMaxSize()
            .backgroundGestures(onDown = {}, ignoreSystemZones = false, detectTap = true) { type ->
                val action = onGesture(type)
                last = "${type.id} → ${action.id}"
                serial++
            },
        contentAlignment = Alignment.Center
    ) {
        // 操作面であることが分かるよう、点線の十字と枠を描く
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            val w = size.width
            val h = size.height
            drawLine(line, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            drawLine(line, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val text = last
            if (text != null) {
                Text(text, color = IhlColors.Accent)
            } else {
                Text("touch pad", color = IhlColors.TextDim, fontSize = 11.sp)
                Text("swipe / tap / double tap / long press", color = IhlColors.TextDim, fontSize = 9.sp)
            }
        }
    }
}
