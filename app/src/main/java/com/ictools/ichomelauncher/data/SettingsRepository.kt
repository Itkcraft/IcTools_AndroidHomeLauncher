package com.ictools.ichomelauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ictools.ichomelauncher.gesture.GestureAction
import com.ictools.ichomelauncher.gesture.GestureType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** 背景（ぼかし・スモーク）の設定値 */
data class BackgroundSettings(
    val blurEnabled: Boolean = true,
    val blurRadiusDp: Float = 30f,   // 0〜100
    val smokePercent: Float = 0f     // 0〜90
)

/** パネルの形 */
enum class PanelShapeType(val id: String, val label: String) {
    CUT("cut", "角カット"), ROUND("round", "角丸"), SQUARE("square", "直角");

    companion object {
        fun fromId(id: String?): PanelShapeType = entries.firstOrNull { it.id == id } ?: CUT
    }
}

/** 見た目のカスタマイズ */
data class Appearance(
    val wireColor: Int = DEFAULT_WIRE,        // ワイヤー（枠線）の色（ARGB）
    val panelColor: Int = DEFAULT_PANEL,      // パネル背景の色（RGB、不透明度は panelAlpha）
    val panelAlpha: Float = DEFAULT_PANEL_ALPHA,
    val shape: PanelShapeType = PanelShapeType.CUT,
    val cornerDp: Float = DEFAULT_CORNER      // カット／角丸の大きさ（0〜24dp）
) {
    companion object {
        const val DEFAULT_WIRE = 0xFFE6E6E6.toInt()
        const val DEFAULT_PANEL = 0xFF000000.toInt()
        const val DEFAULT_PANEL_ALPHA = 0.7f
        const val DEFAULT_CORNER = 12f
    }
}

/** 音楽波形の表示方式 */
enum class VisualizerMode(val id: String, val label: String) {
    WAVEFORM("waveform", "波形"), SPECTRUM("spectrum", "スペクトラム");

    companion object {
        fun fromId(id: String?): VisualizerMode = entries.firstOrNull { it.id == id } ?: WAVEFORM
    }
}

/** メディアパネルの表示設定 */
data class MediaSettings(
    val showQueue: Boolean = true,
    val visualizerEnabled: Boolean = true,
    val visualizerMode: VisualizerMode = VisualizerMode.WAVEFORM
)

/** 設定全体 */
data class LauncherSettings(
    val background: BackgroundSettings = BackgroundSettings(),
    val gestures: Map<GestureType, GestureAction> = GestureType.defaultMapping(),
    val scheduleDays: Int = DEFAULT_SCHEDULE_DAYS,
    val appearance: Appearance = Appearance(),
    val media: MediaSettings = MediaSettings()
) {
    companion object {
        const val DEFAULT_SCHEDULE_DAYS = 7

        /** スケジュールの表示日数の選択肢 */
        val SCHEDULE_DAY_OPTIONS = listOf(1, 3, 7, 14)
    }
}

/** 背景・ジェスチャー設定の保存と読み込み */
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val keyBlurEnabled = booleanPreferencesKey("blur_enabled")
    private val keyBlurRadius = floatPreferencesKey("blur_radius_dp")
    private val keySmoke = floatPreferencesKey("smoke_percent")
    private val keyGestures = stringPreferencesKey("gesture_map")
    private val keyScheduleDays = intPreferencesKey("schedule_days")
    private val keyWireColor = intPreferencesKey("wire_color")
    private val keyPanelColor = intPreferencesKey("panel_color")
    private val keyPanelAlpha = floatPreferencesKey("panel_alpha")
    private val keyShape = stringPreferencesKey("panel_shape")
    private val keyCorner = floatPreferencesKey("panel_corner")
    private val keyShowQueue = booleanPreferencesKey("media_show_queue")
    private val keyVisualizer = booleanPreferencesKey("media_visualizer")
    private val keyVisualizerMode = stringPreferencesKey("media_visualizer_mode")

    /** 設定の変更を監視する Flow */
    val settings: Flow<LauncherSettings> = context.ihlDataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): LauncherSettings {
        val defaults = BackgroundSettings()
        val background = BackgroundSettings(
            blurEnabled = this[keyBlurEnabled] ?: defaults.blurEnabled,
            blurRadiusDp = (this[keyBlurRadius] ?: defaults.blurRadiusDp).coerceIn(0f, 100f),
            smokePercent = (this[keySmoke] ?: defaults.smokePercent).coerceIn(0f, 90f)
        )
        val days = this[keyScheduleDays]?.takeIf { it in LauncherSettings.SCHEDULE_DAY_OPTIONS }
            ?: LauncherSettings.DEFAULT_SCHEDULE_DAYS
        val appearance = Appearance(
            wireColor = this[keyWireColor] ?: Appearance.DEFAULT_WIRE,
            panelColor = this[keyPanelColor] ?: Appearance.DEFAULT_PANEL,
            panelAlpha = (this[keyPanelAlpha] ?: Appearance.DEFAULT_PANEL_ALPHA).coerceIn(0.1f, 1f),
            shape = PanelShapeType.fromId(this[keyShape]),
            cornerDp = (this[keyCorner] ?: Appearance.DEFAULT_CORNER).coerceIn(0f, 24f)
        )
        val media = MediaSettings(
            showQueue = this[keyShowQueue] ?: true,
            visualizerEnabled = this[keyVisualizer] ?: true,
            visualizerMode = VisualizerMode.fromId(this[keyVisualizerMode])
        )
        return LauncherSettings(background, decodeGestures(this[keyGestures]), days, appearance, media)
    }

    /** 保存された「ジェスチャーID → アクションID」の JSON を復元する。未知のIDは無視し、欠けた分は初期値で補う */
    private fun decodeGestures(raw: String?): Map<GestureType, GestureAction> {
        val result = GestureType.defaultMapping().toMutableMap()
        if (raw == null) return result
        val stored = runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrNull() ?: return result
        for ((g, a) in stored) {
            val type = GestureType.fromId(g) ?: continue
            val action = GestureAction.fromId(a) ?: continue
            result[type] = action
        }
        return result
    }

    suspend fun setBlurEnabled(enabled: Boolean) {
        context.ihlDataStore.edit { it[keyBlurEnabled] = enabled }
    }

    suspend fun setBlurRadius(dp: Float) {
        context.ihlDataStore.edit { it[keyBlurRadius] = dp.coerceIn(0f, 100f) }
    }

    suspend fun setSmoke(percent: Float) {
        context.ihlDataStore.edit { it[keySmoke] = percent.coerceIn(0f, 90f) }
    }

    suspend fun setScheduleDays(days: Int) {
        context.ihlDataStore.edit { it[keyScheduleDays] = days }
    }

    suspend fun setAppearance(a: Appearance) {
        context.ihlDataStore.edit {
            it[keyWireColor] = a.wireColor
            it[keyPanelColor] = a.panelColor
            it[keyPanelAlpha] = a.panelAlpha.coerceIn(0.1f, 1f)
            it[keyShape] = a.shape.id
            it[keyCorner] = a.cornerDp.coerceIn(0f, 24f)
        }
    }

    suspend fun setMediaSettings(m: MediaSettings) {
        context.ihlDataStore.edit {
            it[keyShowQueue] = m.showQueue
            it[keyVisualizer] = m.visualizerEnabled
            it[keyVisualizerMode] = m.visualizerMode.id
        }
    }

    suspend fun setGestures(map: Map<GestureType, GestureAction>) {
        val raw = json.encodeToString(map.entries.associate { it.key.id to it.value.id })
        context.ihlDataStore.edit { it[keyGestures] = raw }
    }
}
