package com.ictools.ichomelauncher.ui.pattern

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.ui.theme.IhlColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** 点の数の選択肢（n × n） */
private val GRID_SIZES = listOf(3, 4, 5)

/**
 * パターンパネル：パターン認証のような点の格子を指でなぞって線をつなぐ、手持ち無沙汰用のパネル。
 * 認証やアプリ起動には使わない。指を離すと少し光ったあとフェードアウトする。
 */
@Composable
fun PatternPanel() {
    var gridSize by rememberSaveable { mutableIntStateOf(3) }
    var drawnCount by rememberSaveable { mutableIntStateOf(0) }

    // なぞっている点の順番、指の位置、線の濃さ（フェードアウト用）
    val path = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val alpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var fadeJob by remember { mutableStateOf<Job?>(null) }
    val view = LocalView.current

    Column(Modifier.fillMaxSize().padding(start = 8.dp, top = 2.dp, end = 8.dp, bottom = 6.dp)) {
        // ---- ヘッダー：格子の大きさと描いた回数 ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GRID_SIZES.forEach { n ->
                val selected = n == gridSize
                Text(
                    if (selected) "[${n}x$n]" else " ${n}x$n ",
                    color = if (selected) IhlColors.Accent else IhlColors.TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable {
                            gridSize = n
                            path.clear()
                        }
                        .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                )
            }
            Box(Modifier.weight(1f))
            Text("drawn $drawnCount", color = IhlColors.TextDim, fontSize = 11.sp)
        }

        // ---- 点の格子（パネルの中で一番大きい正方形） ----
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min(maxWidth.value, maxHeight.value).dp
            val wire = IhlColors.BorderFocused
            val dim = IhlColors.Line
            Canvas(
                Modifier
                    .size(side)
                    .pointerInput(gridSize) {
                        val n = gridSize
                        val cell = size.width.toFloat() / n
                        val hitRadius = cell * 0.36f

                        fun center(i: Int) = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)

                        /** 指の位置に点があればつなぐ（間を飛ばした点も、本物のパターン認証のように自動でつなぐ） */
                        fun touch(pos: Offset) {
                            val col = (pos.x / cell).toInt()
                            val row = (pos.y / cell).toInt()
                            if (col !in 0 until n || row !in 0 until n) return
                            val i = row * n + col
                            if (i in path || (pos - center(i)).getDistance() > hitRadius) return
                            path.lastOrNull()?.let { last ->
                                val dc = col - last % n
                                val dr = row - last / n
                                val steps = gcd(abs(dc), abs(dr))
                                for (k in 1 until steps) {
                                    val mid = (last / n + dr / steps * k) * n + (last % n + dc / steps * k)
                                    if (mid !in path) path.add(mid)
                                }
                            }
                            path.add(i)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            // 前の線が消えきる前に描き始めたら、すぐ消して新しく始める
                            fadeJob?.cancel()
                            scope.launch { alpha.snapTo(1f) }
                            path.clear()
                            finger = down.position
                            touch(down.position)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                finger = change.position
                                touch(change.position)
                            }
                            finger = null
                            if (path.size >= 2) drawnCount++
                            // 少し光らせてからフェードアウト
                            fadeJob = scope.launch {
                                delay(350)
                                alpha.animateTo(0f, tween(700))
                                path.clear()
                                alpha.snapTo(1f)
                            }
                        }
                    }
            ) {
                val n = gridSize
                val cell = size.width / n
                fun center(i: Int) = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)
                val a = alpha.value
                val stroke = 3.dp.toPx()

                // つないだ線
                for (k in 1 until path.size) {
                    drawLine(wire.copy(alpha = wire.alpha * a), center(path[k - 1]), center(path[k]), strokeWidth = stroke, cap = StrokeCap.Round)
                }
                // 最後の点から指までの線
                val f = finger
                val last = path.lastOrNull()
                if (f != null && last != null) {
                    drawLine(wire.copy(alpha = wire.alpha * 0.6f), center(last), f, strokeWidth = stroke, cap = StrokeCap.Round)
                }
                // 点
                val dotRadius = min(4.dp.toPx(), cell * 0.08f)
                val ringRadius = min(13.dp.toPx(), cell * 0.25f)
                for (i in 0 until n * n) {
                    val c = center(i)
                    if (i in path) {
                        drawCircle(wire.copy(alpha = wire.alpha * a), radius = ringRadius, center = c, style = Stroke(width = 1.5.dp.toPx()))
                        drawCircle(wire.copy(alpha = wire.alpha * a), radius = dotRadius * 1.4f, center = c)
                    } else {
                        drawCircle(dim, radius = dotRadius, center = c)
                    }
                }
            }
        }
    }
}

/** 最大公約数（間の点を数えるため） */
private fun gcd(a: Int, b: Int): Int = if (b == 0) maxOf(a, 1) else gcd(b, a % b)
