package com.ictools.ichomelauncher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** パネル状態（位置・サイズ・開閉・重なり順）の保存と復元 */
class PanelRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val keyPanels = stringPreferencesKey("panel_states")

    /** 保存済みのパネル状態を読み込む。未保存・読み込み失敗時は null（＝初回起動扱い） */
    suspend fun load(): List<PanelState>? {
        val raw = context.ihlDataStore.data.first()[keyPanels] ?: return null
        return runCatching { json.decodeFromString<List<PanelState>>(raw) }.getOrNull()
    }

    /** パネル状態をまとめて保存する */
    suspend fun save(panels: List<PanelState>) {
        val raw = json.encodeToString(panels)
        context.ihlDataStore.edit { it[keyPanels] = raw }
    }
}
