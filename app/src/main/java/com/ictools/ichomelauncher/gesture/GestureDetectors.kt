package com.ictools.ichomelauncher.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** スワイプと判定する最小移動量 */
private val SWIPE_THRESHOLD = 56.dp

/** ダブルタップの2回目として認める1回目からの最大距離 */
private val DOUBLE_TAP_SLOP = 96.dp

/** ピンチ確定となる2本指の距離変化率（±30%） */
private const val PINCH_RATIO = 0.3f

/**
 * ピンチ検出（画面全体）。ルートに付け、Initial パスで 2 本指の距離変化だけを監視する。
 * イベントは消費しないため、パネル側の 1 本指操作を邪魔しない。
 */
@Composable
fun Modifier.pinchGestures(onGesture: (GestureType) -> Unit): Modifier {
    val callback by rememberUpdatedState(onGesture)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var startDistance = 0f
            var fired = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val distance = (pressed[0].position - pressed[1].position).getDistance()
                    if (startDistance <= 0f) {
                        startDistance = distance
                    } else if (!fired) {
                        val ratio = distance / startDistance
                        if (ratio >= 1f + PINCH_RATIO) {
                            fired = true
                            callback(GestureType.PINCH_OUT)
                        } else if (ratio <= 1f - PINCH_RATIO) {
                            fired = true
                            callback(GestureType.PINCH_IN)
                        }
                    }
                } else {
                    // 2本指でなくなったら基準距離をリセット
                    startDistance = 0f
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

/**
 * 背景レイヤーのジェスチャー検出（1本指のスワイプ・ダブルタップ・長押し）。
 * システムジェスチャー領域（画面端）で始まったスワイプは無視する。
 * [onDown] は背景に指が触れた瞬間に呼ばれる（入力欄のフォーカス解除などに使う）。
 */
@Composable
fun Modifier.backgroundGestures(
    onDown: () -> Unit,
    onGesture: (GestureType) -> Unit
): Modifier {
    val callback by rememberUpdatedState(onGesture)
    val downCallback by rememberUpdatedState(onDown)
    val systemGestures = WindowInsets.systemGestures
    val layoutDirection = LocalLayoutDirection.current
    return this.pointerInput(Unit) {
        // ジェスチャー開始位置がシステムジェスチャー領域内か
        fun inSystemZone(pos: Offset, size: IntSize, density: Density, dir: LayoutDirection): Boolean {
            val left = systemGestures.getLeft(density, dir)
            val right = systemGestures.getRight(density, dir)
            val top = systemGestures.getTop(density)
            val bottom = systemGestures.getBottom(density)
            return pos.x < left || pos.x > size.width - right || pos.y < top || pos.y > size.height - bottom
        }

        val swipeThreshold = SWIPE_THRESHOLD.toPx()
        val doubleTapSlop = DOUBLE_TAP_SLOP.toPx()

        awaitEachGesture {
            val down = awaitFirstDown()
            downCallback()
            val startedInSystemZone = inSystemZone(down.position, size, this, layoutDirection)
            val touchSlop = viewConfiguration.touchSlop

            // 長押し時間内に「離した／動いた／2本指になった」のどれが起きたかを判定
            var outcome = Outcome.NONE
            val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } > 1) { outcome = Outcome.MULTI; break }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp()) { outcome = Outcome.UP; break }
                    if (change.isConsumed) break
                    if ((change.position - down.position).getDistance() > touchSlop) { outcome = Outcome.DRAG; break }
                }
            }

            when {
                // 時間切れ＝指を動かさずに押し続けた → 長押し
                finished == null -> {
                    callback(GestureType.LONG_PRESS)
                    waitForAllUp()
                }

                outcome == Outcome.DRAG -> {
                    val end = trackDrag(down.id, down.position) ?: return@awaitEachGesture
                    if (startedInSystemZone) return@awaitEachGesture
                    val d = end - down.position
                    if (maxOf(abs(d.x), abs(d.y)) < swipeThreshold) return@awaitEachGesture
                    val type = if (abs(d.x) > abs(d.y)) {
                        if (d.x > 0) GestureType.SWIPE_RIGHT else GestureType.SWIPE_LEFT
                    } else {
                        if (d.y < 0) GestureType.SWIPE_UP else GestureType.SWIPE_DOWN
                    }
                    callback(type)
                }

                outcome == Outcome.UP -> {
                    // 2回目のタップを待つ
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
                        ?: return@awaitEachGesture
                    if ((second.position - down.position).getDistance() > doubleTapSlop) {
                        waitForAllUp()
                        return@awaitEachGesture
                    }
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                    if (up != null && (up.position - second.position).getDistance() <= touchSlop * 2) {
                        callback(GestureType.DOUBLE_TAP)
                    } else {
                        waitForAllUp()
                    }
                }

                else -> waitForAllUp()
            }
        }
    }
}

private enum class Outcome { NONE, UP, DRAG, MULTI }

/** 1本指のドラッグを指が離れるまで追跡し、終了位置を返す。途中で2本指になったら null */
private suspend fun AwaitPointerEventScope.trackDrag(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
    start: Offset
): Offset? {
    var end = start
    var multi = false
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } > 1) multi = true
        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
        change.consume()
        end = change.position
        if (!change.pressed) break
    }
    if (multi) {
        waitForAllUp()
        return null
    }
    return end
}

/** すべての指が離れるまで待つ */
private suspend fun AwaitPointerEventScope.waitForAllUp() {
    while (currentEvent.changes.any { it.pressed }) {
        awaitPointerEvent()
    }
}
