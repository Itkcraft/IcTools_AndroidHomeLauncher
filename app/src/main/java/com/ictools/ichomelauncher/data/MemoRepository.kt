package com.ictools.ichomelauncher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** メモ1件分（1 メモ = 1 パネル） */
@Serializable
data class Memo(
    val id: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** 一覧やタイトルバーに出す見出し（1 行目） */
    val headline: String get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}

/** メモ本文の保存と読み込み */
class MemoRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val keyMemos = stringPreferencesKey("memos")

    suspend fun load(): List<Memo> {
        val raw = context.ihlDataStore.data.first()[keyMemos] ?: return emptyList()
        return runCatching { json.decodeFromString<List<Memo>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun save(memos: List<Memo>) {
        val raw = json.encodeToString(memos)
        context.ihlDataStore.edit { it[keyMemos] = raw }
    }
}
