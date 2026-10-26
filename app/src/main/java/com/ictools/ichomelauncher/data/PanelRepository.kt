package com.ictools.ichomelauncher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** パネル状態（位置・サイズ・開閉・重なり順）の保存と復元。縦画面と横画面で別々に保存する */
class PanelRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    // 縦画面は v0.5 以前と同じキー（既存の配置をそのまま縦画面の配置として使う）
    private val keyPortrait = stringPreferencesKey("panel_states")
    private val keyLandscape = stringPreferencesKey("panel_states_land")

    private fun key(landscape: Boolean) = if (landscape) keyLandscape else keyPortrait

    /** 保存済みのパネル状態を読み込む。未保存・読み込み失敗時は null（＝その向きでは初回扱い） */
    suspend fun load(landscape: Boolean): List<PanelState>? {
        val raw = context.ihlDataStore.data.first()[key(landscape)] ?: return null
        return runCatching { json.decodeFromString<List<PanelState>>(raw) }.getOrNull()
    }

    /** パネル状態をまとめて保存する */
    suspend fun save(landscape: Boolean, panels: List<PanelState>) {
        val raw = json.encodeToString(panels)
        context.ihlDataStore.edit { it[key(landscape)] = raw }
    }
}
