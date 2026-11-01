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
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest

/** 一覧に追加した HTML ファイル1件分 */
@Serializable
data class HtmlEntry(
    val uri: String,     // ファイルピッカーで選んだドキュメントの URI
    val name: String,    // 表示名（ファイル名）
    val addedAt: Long
) {
    /** アプリ内のコピー・表示用 URL に使う、URI から決まる短い ID */
    val key: String
        get() = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
}

/** HTML ビューアの状態（一覧と、表示中のファイル） */
@Serializable
data class HtmlLibrary(
    val entries: List<HtmlEntry> = emptyList(),
    val selected: String? = null
) {
    val current: HtmlEntry? get() = entries.firstOrNull { it.uri == selected }
}

/** 追加の結果 */
sealed interface HtmlAddResult {
    data class Added(val entry: HtmlEntry) : HtmlAddResult
    data class Failed(val reason: String) : HtmlAddResult
}

/**
 * HTML ビューアの一覧の保存と、ファイルの読み込み。
 * - 選んだ URI には可能なら takePersistableUriPermission を行い、元のファイルを読み続ける（編集が反映される）
 * - 追加時にアプリ内へコピーも保存する。元のファイルが読めなくなった場合や、
 *   永続的な許可を出さないファイルアプリから選んだ場合はコピーを表示する
 */
class HtmlRepository(context: Context) {

    companion object {
        /** 追加できる大きさの上限 */
        private const val MAX_BYTES = 20L * 1024 * 1024
    }

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("html_library")
    private val dir = File(appContext.filesDir, "html").apply { mkdirs() }

    suspend fun load(): HtmlLibrary {
        val raw = appContext.ihlDataStore.data.first()[key] ?: return HtmlLibrary()
        return runCatching { json.decodeFromString<HtmlLibrary>(raw) }.getOrDefault(HtmlLibrary())
    }

    suspend fun save(library: HtmlLibrary) {
        val raw = json.encodeToString(library)
        appContext.ihlDataStore.edit { it[key] = raw }
    }

    private fun copyOf(entry: HtmlEntry) = File(dir, entry.key + ".html")

    /** 選ばれたファイルを一覧用に準備する（許可の永続化を試み、中身をアプリ内にコピー） */
    suspend fun prepare(uri: Uri): HtmlAddResult = withContext(Dispatchers.IO) {
        // 永続的な許可は出せないファイルアプリもあるため、失敗してもコピーで続ける
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val bytes = runCatching { readBytes(uri) }.getOrElse {
            return@withContext HtmlAddResult.Failed("ファイルを読めませんでした（${it.javaClass.simpleName}）")
        } ?: return@withContext HtmlAddResult.Failed("ファイルが大きすぎます（20MB まで）")
        val entry = HtmlEntry(uri.toString(), displayName(uri), System.currentTimeMillis())
        runCatching { copyOf(entry).writeBytes(bytes) }
            .onFailure { return@withContext HtmlAddResult.Failed("アプリ内に保存できませんでした") }
        HtmlAddResult.Added(entry)
    }

    /** 中身を読む（大きすぎる場合は null） */
    private fun readBytes(uri: Uri): ByteArray? {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) return null
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
        throw IllegalStateException("cannot open")
    }

    /**
     * 表示する HTML を読む。元のファイルが読めればそれを使い（コピーも更新）、読めなければコピーを使う。
     * どちらも無ければ null。
     */
    suspend fun read(entry: HtmlEntry): String? = withContext(Dispatchers.IO) {
        val copy = copyOf(entry)
        val fresh = runCatching { readBytes(Uri.parse(entry.uri)) }.getOrNull()
        if (fresh != null) runCatching { copy.writeBytes(fresh) }
        val bytes = fresh ?: runCatching { copy.readBytes() }.getOrNull() ?: return@withContext null
        decode(bytes)
    }

    /** 元のファイルかコピーのどちらかがあるか */
    fun canRead(entry: HtmlEntry): Boolean =
        copyOf(entry).exists() ||
            appContext.contentResolver.persistedUriPermissions.any { it.uri.toString() == entry.uri && it.isReadPermission }

    /** 一覧から外したファイルの許可を返し、コピーを消す */
    fun release(entry: HtmlEntry) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(Uri.parse(entry.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { copyOf(entry).delete() }
    }

    /** 文字コード：BOM → <meta charset> → UTF-8 の順に判定する */
    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val name = Regex("""charset\s*=\s*["']?([\w-]+)""", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
        val charset = name?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, charset)
    }

    private fun displayName(uri: Uri): String = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "untitled.html"
}
