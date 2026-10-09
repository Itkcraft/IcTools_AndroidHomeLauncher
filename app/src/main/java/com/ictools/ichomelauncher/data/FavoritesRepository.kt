package com.ictools.ichomelauncher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** お気に入りアプリ（AppEntry.key の並び順付きリスト）の保存 */
class FavoritesRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("favorites")

    suspend fun load(): List<String> {
        val raw = context.ihlDataStore.data.first()[key] ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun save(keys: List<String>) {
        val raw = json.encodeToString(keys)
        context.ihlDataStore.edit { it[key] = raw }
    }
}
