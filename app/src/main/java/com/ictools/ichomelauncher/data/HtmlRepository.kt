package com.ictools.ichomelauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一覧に追加した HTML ファイル1件分 */
@Serializable
data class HtmlEntry(
    val uri: String,     // ファイルピッカーで選んだドキュメントの URI（永続的な読み取り許可付き）
    val name: String,    // 表示名（ファイル名）
    val addedAt: Long
)

/** HTML ビューアの状態（一覧と、表示中のファイル） */
@Serializable
data class HtmlLibrary(
    val entries: List<HtmlEntry> = emptyList(),
    val selected: String? = null
) {
    val current: HtmlEntry? get() = entries.firstOrNull { it.uri == selected }
}

/**
 * HTML ビューアの一覧の保存と、ドキュメントへのアクセス許可の管理。
 * ファイルピッカー（OpenDocument）で選んだ URI に takePersistableUriPermission を行い、再起動後も読めるようにする。
 */
class HtmlRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("html_library")

    suspend fun load(): HtmlLibrary {
        val raw = appContext.ihlDataStore.data.first()[key] ?: return HtmlLibrary()
        return runCatching { json.decodeFromString<HtmlLibrary>(raw) }.getOrDefault(HtmlLibrary())
    }

    suspend fun save(library: HtmlLibrary) {
        val raw = json.encodeToString(library)
        appContext.ihlDataStore.edit { it[key] = raw }
    }

    /** 選ばれたファイルの読み取り許可を永続化し、一覧用の情報を作る（失敗したら null） */
    suspend fun prepare(uri: Uri): HtmlEntry? = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { return@withContext null }
        HtmlEntry(uri.toString(), displayName(uri), System.currentTimeMillis())
    }

    /** 一覧から外したファイルの許可を返す */
    fun release(entry: HtmlEntry) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(Uri.parse(entry.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** まだ読み取り許可が残っているか（ファイルが移動・削除されると失われることがある） */
    fun canRead(entry: HtmlEntry): Boolean =
        appContext.contentResolver.persistedUriPermissions.any { it.uri.toString() == entry.uri && it.isReadPermission }

    private fun displayName(uri: Uri): String = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "untitled.html"
}
