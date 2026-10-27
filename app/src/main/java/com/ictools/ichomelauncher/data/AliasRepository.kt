package com.ictools.ichomelauncher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** ターミナルの短縮コマンド（名前 → 置き換える文字列）の保存 */
class AliasRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("terminal_aliases")

    suspend fun load(): Map<String, String> {
        val raw = context.ihlDataStore.data.first()[key] ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    suspend fun save(aliases: Map<String, String>) {
        val raw = json.encodeToString(aliases)
        context.ihlDataStore.edit { it[key] = raw }
    }
}
