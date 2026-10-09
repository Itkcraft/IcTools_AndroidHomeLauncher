package com.ictools.ichomelauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.BuildConfig
import com.ictools.ichomelauncher.data.Appearance
import com.ictools.ichomelauncher.data.LauncherSettings
import com.ictools.ichomelauncher.data.MediaSettings
import com.ictools.ichomelauncher.data.PanelShapeType
import com.ictools.ichomelauncher.data.PermissionState
import com.ictools.ichomelauncher.data.VisualizerMode
import com.ictools.ichomelauncher.ui.common.WireButton
import com.ictools.ichomelauncher.gesture.GestureAction
import com.ictools.ichomelauncher.gesture.GestureType
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.SmallCutShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 設定パネルから呼ぶ操作 */
class SettingsActions(
    val setBlurEnabled: (Boolean) -> Unit,
    val setBlurRadius: (Float) -> Unit,
    val setSmoke: (Float) -> Unit,
    val setGestureAction: (GestureType, GestureAction) -> Unit,
    val resetGestures: () -> Unit,
    val resetLayout: () -> Unit,
    val openHomeSettings: () -> Unit,
    val setScheduleDays: (Int) -> Unit,
    val requestNotificationAccess: () -> Unit,
    val openAppInfo: () -> Unit,
    val setAppearance: (Appearance) -> Unit,
    val setMediaSettings: (MediaSettings) -> Unit,
    val requestAllFiles: () -> Unit,
    val requestUsageAccess: () -> Unit
)

/** 設定パネルの中身：背景／ジェスチャー／パネル／情報 */
@Composable
fun SettingsPanel(settings: LauncherSettings, blurAvailable: Boolean, permissions: PermissionState, actions: SettingsActions) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 24.dp)
    ) {
        // ---- 背景 ----
        SectionHeader("背景")
        val bg = settings.background
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ぼかし", Modifier.weight(1f))
            ToggleButton(bg.blurEnabled) { actions.setBlurEnabled(it) }
        }
        if (!blurAvailable) {
            Text("この端末／状態ではぼかしが無効です", color = IhlColors.TextDim, fontSize = 11.sp)
        }
        LabeledSlider(
            label = "ぼかし半径",
            value = bg.blurRadiusDp,
            range = 0f..100f,
            valueText = "${bg.blurRadiusDp.roundToInt()}",
            enabled = bg.blurEnabled && blurAvailable,
            onChange = actions.setBlurRadius
        )
        LabeledSlider(
            label = "スモーク（暗さ）",
            value = bg.smokePercent,
            range = 0f..90f,
            valueText = "${bg.smokePercent.roundToInt()}%",
            enabled = true,
            onChange = actions.setSmoke
        )

        // ---- 見た目 ----
        SectionHeader("見た目")
        AppearanceSection(settings.appearance, actions.setAppearance)

        // ---- メディア ----
        SectionHeader("メディア")
        val media = settings.media
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("キュー表示", Modifier.weight(1f))
            ToggleButton(media.showQueue) { actions.setMediaSettings(media.copy(showQueue = it)) }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("音楽波形", Modifier.weight(1f))
            ToggleButton(media.visualizerEnabled) { actions.setMediaSettings(media.copy(visualizerEnabled = it)) }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("波形の種類", Modifier.weight(1f))
            OptionDropdown(
                current = media.visualizerMode.label,
                options = VisualizerMode.entries.map { it.label to it },
                onSelect = { actions.setMediaSettings(media.copy(visualizerMode = it)) }
            )
        }

        // ---- ジェスチャー ----
        SectionHeader("ジェスチャー")
        GestureType.entries.forEach { type ->
            GestureRow(type, settings.gestures[type] ?: GestureAction.NONE) { actions.setGestureAction(type, it) }
        }
        Spacer(Modifier.height(6.dp))
        WireButton("初期値に戻す", onClick = actions.resetGestures)

        // ---- パネル ----
        SectionHeader("パネル")
        WireButton("パネル配置をリセット", onClick = actions.resetLayout)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("スケジュールの表示日数", Modifier.weight(1f))
            OptionDropdown(
                current = "${settings.scheduleDays}d",
                options = LauncherSettings.SCHEDULE_DAY_OPTIONS.map { "${it}d" to it },
                onSelect = actions.setScheduleDays
            )
        }

        // ---- 権限 ----
        SectionHeader("権限")
        PermissionRow("通知へのアクセス", permissions.notificationAccess, actions.requestNotificationAccess)
        Text("メディア・通知履歴で使用", color = IhlColors.TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        PermissionRow("カレンダー", permissions.calendar, actions.openAppInfo)
        Text("スケジュール・カレンダーで使用（パネルからも許可できます）", color = IhlColors.TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        PermissionRow("マイク", permissions.recordAudio, actions.openAppInfo)
        Text("音楽波形で使用（音の出力を解析するだけで録音はしません）", color = IhlColors.TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        PermissionRow("すべてのファイル", permissions.allFiles, actions.requestAllFiles)
        Text("ターミナルのファイル操作で使用", color = IhlColors.TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        PermissionRow("使用状況へのアクセス", permissions.usageStats, actions.requestUsageAccess)
        Text("稼働状況の「最近使ったアプリ」で使用", color = IhlColors.TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        PermissionRow("位置情報", permissions.fineLocation, actions.openAppInfo)
        Text("通信パネルの SSID 表示で使用（位置情報サービスの ON も必要）", color = IhlColors.TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        PermissionRow("電話", permissions.phoneState, actions.openAppInfo)
        Text("通信パネルの回線種別（5G/LTE）表示で使用", color = IhlColors.TextDim, fontSize = 11.sp)

        // ---- 情報 ----
        SectionHeader("情報")
        Text("IcHomeLauncher v${BuildConfig.VERSION_NAME}")
        Text("by IcTools", color = IhlColors.TextDim)
        Spacer(Modifier.height(8.dp))
        WireButton("デフォルトのホームアプリを変更", onClick = actions.openHomeSettings)
        Spacer(Modifier.height(10.dp))
        Text("使用ライブラリ", fontWeight = FontWeight.Bold)
        USED_LIBRARIES.forEach { lib ->
            Text("・${lib.name}")
            Text("    ${lib.license}", color = IhlColors.TextDim)
        }
        Spacer(Modifier.height(8.dp))
        LicenseViewer()
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(12.dp))
    Text("[ $title ]", color = IhlColors.Accent, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

/** ON/OFF 切り替え */
@Composable
private fun ToggleButton(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .border(1.dp, if (checked) IhlColors.BorderFocused else IhlColors.Border, SmallCutShape)
            .clickable { onChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(if (checked) "ON " else "OFF", color = if (checked) IhlColors.Accent else IhlColors.TextDim)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = if (enabled) IhlColors.Text else IhlColors.TextDim)
        Text(valueText, color = IhlColors.TextDim)
    }
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = IhlColors.Accent,
            activeTrackColor = IhlColors.BorderFocused,
            inactiveTrackColor = IhlColors.Line,
            disabledThumbColor = IhlColors.TextDim,
            disabledActiveTrackColor = IhlColors.Border,
            disabledInactiveTrackColor = IhlColors.Line
        )
    )
}

/** 見た目のカスタマイズ：ワイヤー色・パネル背景・形 */
@Composable
private fun AppearanceSection(a: Appearance, onChange: (Appearance) -> Unit) {
    // ワイヤーの色
    Text("ワイヤーの色")
    ColorPicker(a.wireColor, WIRE_PRESETS) { onChange(a.copy(wireColor = it)) }
    // パネル背景
    Spacer(Modifier.height(8.dp))
    Text("パネル背景の色")
    ColorPicker(a.panelColor, PANEL_PRESETS) { onChange(a.copy(panelColor = it)) }
    LabeledSlider(
        label = "パネル背景の不透明度",
        value = a.panelAlpha * 100f,
        range = 10f..100f,
        valueText = "${(a.panelAlpha * 100).roundToInt()}%",
        enabled = true,
        onChange = { onChange(a.copy(panelAlpha = it / 100f)) }
    )
    // 形
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("パネルの形", Modifier.weight(1f))
        OptionDropdown(
            current = a.shape.label,
            options = PanelShapeType.entries.map { it.label to it },
            onSelect = { onChange(a.copy(shape = it)) }
        )
    }
    LabeledSlider(
        label = if (a.shape == PanelShapeType.ROUND) "角丸の大きさ" else "カットの大きさ",
        value = a.cornerDp,
        range = 0f..24f,
        valueText = "${a.cornerDp.roundToInt()}dp",
        enabled = a.shape != PanelShapeType.SQUARE,
        onChange = { onChange(a.copy(cornerDp = it)) }
    )
    Spacer(Modifier.height(6.dp))
    WireButton("見た目を初期値に戻す", onClick = { onChange(Appearance()) })
}

private val WIRE_PRESETS = listOf(
    0xFFE6E6E6, 0xFFFFFFFF, 0xFF9E9E9E, 0xFF4DD0E1, 0xFF64FFDA, 0xFF69F0AE,
    0xFFFFD740, 0xFFFF9100, 0xFFFF5252, 0xFFFF4081, 0xFFB388FF, 0xFF448AFF
).map { it.toInt() }

private val PANEL_PRESETS = listOf(
    0xFF000000, 0xFF101010, 0xFF1A1A2E, 0xFF0D1B2A, 0xFF102018, 0xFF2A1010, 0xFF202020, 0xFFFFFFFF
).map { it.toInt() }

/** 色の選択：プリセットの色見本＋RGB スライダー */
@Composable
private fun ColorPicker(color: Int, presets: List<Int>, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        presets.forEach { p ->
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(20.dp)
                    .background(Color(p), SmallCutShape)
                    .border(1.dp, if (p == color) IhlColors.Accent else IhlColors.Line, SmallCutShape)
                    .clickable { onChange(p) }
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).background(Color(color), SmallCutShape).border(1.dp, IhlColors.Line, SmallCutShape))
        Spacer(Modifier.width(6.dp))
        Text("#%06X".format(color and 0xFFFFFF), color = IhlColors.TextDim, modifier = Modifier.weight(1f))
        Text(
            if (expanded) "RGB ▴" else "RGB ▾",
            color = IhlColors.TextDim,
            modifier = Modifier.clickable { expanded = !expanded }.padding(4.dp)
        )
    }
    if (expanded) {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        fun rgb(nr: Int, ng: Int, nb: Int) = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        LabeledSlider("R", r.toFloat(), 0f..255f, "$r", true) { onChange(rgb(it.roundToInt(), g, b)) }
        LabeledSlider("G", g.toFloat(), 0f..255f, "$g", true) { onChange(rgb(r, it.roundToInt(), b)) }
        LabeledSlider("B", b.toFloat(), 0f..255f, "$b", true) { onChange(rgb(r, g, it.roundToInt())) }
    }
}

/** ジェスチャー1件分：名前とアクション選択ドロップダウン */
@Composable
private fun GestureRow(type: GestureType, action: GestureAction, onSelect: (GestureAction) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(type.id, Modifier.weight(1f))
        OptionDropdown(
            current = action.id,
            options = GestureAction.entries.map { it.id to it },
            dim = action == GestureAction.NONE,
            onSelect = onSelect
        )
    }
}

/** 選択肢のドロップダウン */
@Composable
private fun <T> OptionDropdown(current: String, options: List<Pair<String, T>>, dim: Boolean = false, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .border(1.dp, IhlColors.Border, SmallCutShape)
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("$current ▾", color = if (dim) IhlColors.TextDim else IhlColors.Text)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

/** 権限1件分：状態と設定ボタン */
@Composable
private fun PermissionRow(label: String, granted: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(if (granted) "OK " else "未許可", color = if (granted) IhlColors.Accent else IhlColors.TextDim)
        Spacer(Modifier.width(8.dp))
        WireButton("設定", onClick = onOpen)
    }
}

/** ライセンス全文（assets に同梱）の表示・非表示 */
@Composable
private fun LicenseViewer() {
    var shown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    WireButton(if (shown) "ライセンス全文を閉じる" else "ライセンス全文（Apache 2.0）", onClick = { shown = !shown })
    if (shown) {
        val text by produceState("loading...") {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open(APACHE_LICENSE_ASSET).bufferedReader().use { it.readText() }
                }.getOrDefault("ライセンスファイルを読み込めませんでした")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text, fontSize = 10.sp, lineHeight = 13.sp, color = IhlColors.TextDim)
    }
}
