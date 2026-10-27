package com.ictools.ichomelauncher.ui.sound

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ictools.ichomelauncher.data.SoundController
import com.ictools.ichomelauncher.data.VolumeStream
import com.ictools.ichomelauncher.ui.common.WireButton
import com.ictools.ichomelauncher.ui.theme.IhlColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 音量・明るさパネル：メディア・着信・通知・アラームの音量と、画面の明るさ・自動調整。
 * 表示中かつ前面の間は 1 秒ごとに現在値を読み直す（本体のボタンで変えた分も反映）。
 */
@Composable
fun SoundPanel(sound: SoundController, requestWriteSettings: () -> Unit) {
    var state by remember { mutableStateOf(sound.snapshot()) }
    var message by remember { mutableStateOf<String?>(null) }
    // スライダー操作中は読み直しで値が戻らないようにする
    var dragging by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(resumed) {
        while (resumed) {
            if (!dragging) state = sound.snapshot()
            delay(1000)
        }
    }

    fun refresh() {
        state = sound.snapshot()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 22.dp)) {
        Text("VOLUME", color = IhlColors.Accent)
        VolumeStream.entries.forEach { v ->
            val muted = v == VolumeStream.MEDIA && state.mediaMuted
            LevelSlider(
                label = v.label + if (muted) "（ミュート）" else "",
                value = state.volumes[v] ?: 0,
                enabled = true,
                onDrag = { dragging = true },
                onChange = { p ->
                    message = sound.setVolume(v, p)
                    state = state.copy(volumes = state.volumes + (v to p))
                },
                onDone = { dragging = false; refresh() }
            )
        }
        WireButton(if (state.mediaMuted) "ミュート解除" else "メディアをミュート", onClick = { message = sound.toggleMute(); refresh() })

        Spacer(Modifier.height(12.dp))
        Text("BRIGHTNESS", color = IhlColors.Accent)
        if (!state.canWriteSettings) {
            Text(
                "明るさの変更には「システム設定の変更」の許可が必要です（明るさと自動調整の設定だけに使います）",
                color = IhlColors.TextDim, fontSize = 11.sp, lineHeight = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            WireButton("許可する（設定を開く）", onClick = requestWriteSettings)
            Spacer(Modifier.height(6.dp))
        }
        LevelSlider(
            label = "明るさ" + if (state.autoBrightness) "（自動）" else "",
            value = state.brightness,
            enabled = state.canWriteSettings,
            onDrag = { dragging = true },
            onChange = { p ->
                message = sound.setBrightness(p)
                state = state.copy(brightness = p, autoBrightness = false)
            },
            onDone = { dragging = false; refresh() }
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("自動調整", Modifier.weight(1f), color = if (state.canWriteSettings) IhlColors.Text else IhlColors.TextDim)
            WireButton(
                if (state.autoBrightness) "ON " else "OFF",
                enabled = state.canWriteSettings,
                onClick = { message = sound.setAutoBrightness(!state.autoBrightness); refresh() }
            )
        }
        Text("手動で明るさを変えると自動調整は OFF になります", color = IhlColors.TextDim, fontSize = 10.sp)
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = IhlColors.Accent, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LevelSlider(
    label: String,
    value: Int,
    enabled: Boolean,
    onDrag: () -> Unit,
    onChange: (Int) -> Unit,
    onDone: () -> Unit
) {
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = if (enabled) IhlColors.Text else IhlColors.TextDim)
        Text("$value%", color = IhlColors.TextDim)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = {
            onDrag()
            onChange(it.roundToInt())
        },
        onValueChangeFinished = onDone,
        valueRange = 0f..100f,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = IhlColors.BorderFocused,
            activeTrackColor = IhlColors.BorderFocused,
            inactiveTrackColor = IhlColors.Line,
            disabledThumbColor = IhlColors.TextDim,
            disabledActiveTrackColor = IhlColors.Border,
            disabledInactiveTrackColor = IhlColors.Line
        )
    )
}
