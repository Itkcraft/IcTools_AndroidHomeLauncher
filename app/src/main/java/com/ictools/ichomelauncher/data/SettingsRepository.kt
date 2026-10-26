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

/**
 * アシスタントの起動方法。
 * 電源ボタン長押しのアシスタント表示（Gemini のオーバーレイ）を他アプリから呼ぶ公開 API は無いため、
 * Google アプリが受け取る可能性のある Intent を候補として用意し、実機で効くものを選んでもらう。
 */
enum class AssistMethod(val id: String, val label: String) {
    AUTO("auto", "自動（voice → voiceassist → app）"),
    VOICE_COMMAND("voice", "音声コマンド（VOICE_COMMAND）"),
    VOICE_ASSIST("voiceassist", "音声アシスト（VOICE_ASSIST）"),
    ASSIST("assist", "アシスト（ASSIST）"),
    GEMINI_APP("app", "Gemini アプリ");

    companion object {
        fun fromId(id: String?): AssistMethod? = entries.firstOrNull { it.id == id?.lowercase() }
    }
}

/** 設定全体 */
data class LauncherSettings(
    val background: BackgroundSettings = BackgroundSettings(),
    val gestures: Map<GestureType, GestureAction> = GestureType.defaultMapping(),
    val padGestures: Map<GestureType, GestureAction> = GestureType.defaultPadMapping(),
    val favoritesGrid: Boolean = true,
    val assistMethod: AssistMethod = AssistMethod.AUTO,
    val snapEnabled: Boolean = false,             // グリッド（スナップ）モード
    val gridSizeDp: Int = DEFAULT_GRID_SIZE,      // グリッドの間隔（dp）
    val rotationEnabled: Boolean = true,          // 横画面に対応する（OS の自動回転に従う）
    val scheduleDays: Int = DEFAULT_SCHEDULE_DAYS,
    val appearance: Appearance = Appearance(),
    val media: MediaSettings = MediaSettings()
) {
    companion object {
        const val DEFAULT_SCHEDULE_DAYS = 7
        const val DEFAULT_GRID_SIZE = 16

        /** グリッドの間隔の選択肢（dp） */
        val GRID_SIZE_OPTIONS = listOf(8, 12, 16, 24, 32)

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
    private val keyPadGestures = stringPreferencesKey("pad_gesture_map")
    private val keyFavoritesGrid = booleanPreferencesKey("favorites_grid")
    private val keyAssistMethod = stringPreferencesKey("assist_method")
    private val keySnap = booleanPreferencesKey("snap_enabled")
    private val keyGridSize = intPreferencesKey("grid_size_dp")
    private val keyRotation = booleanPreferencesKey("rotation_enabled")
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
        return LauncherSettings(
            background = background,
            gestures = decodeGestures(this[keyGestures], GestureType.defaultMapping()),
            padGestures = decodeGestures(this[keyPadGestures], GestureType.defaultPadMapping()),
            favoritesGrid = this[keyFavoritesGrid] ?: true,
            assistMethod = AssistMethod.fromId(this[keyAssistMethod]) ?: AssistMethod.AUTO,
            snapEnabled = this[keySnap] ?: false,
            gridSizeDp = this[keyGridSize]?.takeIf { it in LauncherSettings.GRID_SIZE_OPTIONS } ?: LauncherSettings.DEFAULT_GRID_SIZE,
            rotationEnabled = this[keyRotation] ?: true,
            scheduleDays = days,
            appearance = appearance,
            media = media
        )
    }

    /** 保存された「ジェスチャーID → アクションID」の JSON を復元する。未知のIDは無視し、欠けた分は初期値で補う */
    private fun decodeGestures(raw: String?, defaults: Map<GestureType, GestureAction>): Map<GestureType, GestureAction> {
        val result = defaults.toMutableMap()
        if (raw == null) return result
        val stored = runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrNull() ?: return result
        for ((g, a) in stored) {
            val type = GestureType.fromId(g)?.takeIf { it in defaults } ?: continue
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

    suspend fun setPadGestures(map: Map<GestureType, GestureAction>) {
        val raw = json.encodeToString(map.entries.associate { it.key.id to it.value.id })
        context.ihlDataStore.edit { it[keyPadGestures] = raw }
    }

    suspend fun setSnap(enabled: Boolean) {
        context.ihlDataStore.edit { it[keySnap] = enabled }
    }

    suspend fun setGridSize(dp: Int) {
        context.ihlDataStore.edit { it[keyGridSize] = dp }
    }

    suspend fun setRotationEnabled(enabled: Boolean) {
        context.ihlDataStore.edit { it[keyRotation] = enabled }
    }

    suspend fun setAssistMethod(method: AssistMethod) {
        context.ihlDataStore.edit { it[keyAssistMethod] = method.id }
    }

    suspend fun setFavoritesGrid(grid: Boolean) {
        context.ihlDataStore.edit { it[keyFavoritesGrid] = grid }
    }

    suspend fun setGestures(map: Map<GestureType, GestureAction>) {
        val raw = json.encodeToString(map.entries.associate { it.key.id to it.value.id })
        context.ihlDataStore.edit { it[keyGestures] = raw }
    }
}
