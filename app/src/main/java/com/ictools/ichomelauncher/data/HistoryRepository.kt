package com.ictools.ichomelauncher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** アプリ起動履歴1件分 */
@Serializable
data class LaunchRecord(
    val appKey: String,    // AppEntry.key（コンポーネント名＋ユーザー）
    val label: String,     // 起動時のアプリ名（アンインストール後の表示用）
    val time: Long         // 起動時刻（ミリ秒）
)

/** アプリ起動履歴・ターミナルのコマンド履歴の保存（再起動後も残す） */
class HistoryRepository(private val context: Context) {

    companion object {
        const val MAX_LAUNCHES = 50
        const val MAX_COMMANDS = 50
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val keyLaunches = stringPreferencesKey("launch_history")
    private val keyCommands = stringPreferencesKey("command_history")

    suspend fun loadLaunches(): List<LaunchRecord> {
        val raw = context.ihlDataStore.data.first()[keyLaunches] ?: return emptyList()
        return runCatching { json.decodeFromString<List<LaunchRecord>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun saveLaunches(list: List<LaunchRecord>) {
        val raw = json.encodeToString(list.take(MAX_LAUNCHES))
        context.ihlDataStore.edit { it[keyLaunches] = raw }
    }

    suspend fun loadCommands(): List<String> {
        val raw = context.ihlDataStore.data.first()[keyCommands] ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun saveCommands(list: List<String>) {
        val raw = json.encodeToString(list.takeLast(MAX_COMMANDS))
        context.ihlDataStore.edit { it[keyCommands] = raw }
    }
}
