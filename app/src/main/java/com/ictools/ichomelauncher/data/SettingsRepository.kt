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

/** 設定全体 */
data class LauncherSettings(
    val background: BackgroundSettings = BackgroundSettings(),
    val gestures: Map<GestureType, GestureAction> = GestureType.defaultMapping(),
    val scheduleDays: Int = DEFAULT_SCHEDULE_DAYS
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
        return LauncherSettings(background, decodeGestures(this[keyGestures]), days)
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

    suspend fun setGestures(map: Map<GestureType, GestureAction>) {
        val raw = json.encodeToString(map.entries.associate { it.key.id to it.value.id })
        context.ihlDataStore.edit { it[keyGestures] = raw }
    }
}
