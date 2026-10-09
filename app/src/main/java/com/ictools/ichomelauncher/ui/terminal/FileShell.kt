package com.ictools.ichomelauncher.ui.terminal

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** ファイル操作の失敗（メッセージをそのままターミナルに表示する） */
class ShellException(message: String) : Exception(message)

/**
 * ターミナルのファイル操作（ls / cd / pwd / cp / mv / mkdir / rm / open）。
 * 「すべてのファイルへのアクセス」権限で内部ストレージを直接扱う。初期ディレクトリは内部ストレージ直下。
 */
class FileShell(private val context: Context) {

    /** 内部ストレージ直下（~ で表す） */
    val root: File = Environment.getExternalStorageDirectory()

    /** 現在のディレクトリ */
    var cwd: File = root
        private set

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    /** パスを解決する（~ は内部ストレージ直下、/ 始まりは絶対パス、それ以外は現在のディレクトリから） */
    fun resolve(path: String): File {
        val f = when {
            path == "~" -> root
            path.startsWith("~/") -> File(root, path.removePrefix("~/"))
            path.startsWith("/") -> File(path)
            else -> File(cwd, path)
        }
        return f.toPath().normalize().toFile()
    }

    /** 表示用のパス（内部ストレージ配下は ~ で始める） */
    fun display(f: File): String {
        val r = root.path
        return when {
            f.path == r -> "~"
            f.path.startsWith("$r/") -> "~" + f.path.removePrefix(r)
            else -> f.path
        }
    }

    fun pwd(): String = "${display(cwd)}  (${cwd.path})"

    fun cd(path: String?) {
        val target = if (path.isNullOrEmpty()) root else resolve(path)
        if (!target.exists()) throw ShellException("cd: no such directory: ${path}")
        if (!target.isDirectory) throw ShellException("cd: not a directory: ${path}")
        if (!target.canRead()) throw ShellException("cd: permission denied: ${path}")
        cwd = target
    }

    /** 一覧（ディレクトリが先、名前順。ディレクトリは末尾に /） */
    fun ls(path: String?, showHidden: Boolean): List<String> {
        val dir = if (path.isNullOrEmpty()) cwd else resolve(path)
        if (!dir.exists()) throw ShellException("ls: no such file or directory: $path")
        if (dir.isFile) return listOf(formatEntry(dir))
        val children = dir.listFiles() ?: throw ShellException("ls: permission denied: ${display(dir)}")
        val list = children
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .map { formatEntry(it) }
        return list.ifEmpty { listOf("(empty)") }
    }

    private fun formatEntry(f: File): String =
        if (f.isDirectory) "${f.name}/" else "${f.name}  ${formatSize(f.length())}"

    fun mkdir(path: String) {
        val dir = resolve(path)
        if (dir.exists()) throw ShellException("mkdir: already exists: $path")
        if (!dir.mkdirs()) throw ShellException("mkdir: cannot create: $path")
    }

    /** コピー先を決める（既存ディレクトリならその中へ。既存ファイルは上書きしない） */
    private fun destination(src: File, dstPath: String, cmd: String): File {
        val dst = resolve(dstPath)
        val target = if (dst.isDirectory) File(dst, src.name) else dst
        if (target.exists()) throw ShellException("$cmd: already exists: ${display(target)}")
        if (target.path.startsWith(src.path + "/")) throw ShellException("$cmd: cannot copy a directory into itself")
        return target
    }

    fun cp(srcPath: String, dstPath: String): String {
        val src = resolve(srcPath)
        if (!src.exists()) throw ShellException("cp: no such file or directory: $srcPath")
        val target = destination(src, dstPath, "cp")
        val ok = runCatching { src.copyRecursively(target, overwrite = false) }.getOrDefault(false)
        if (!ok) throw ShellException("cp: failed: ${display(src)} -> ${display(target)}")
        return "copied: ${display(src)} -> ${display(target)}"
    }

    fun mv(srcPath: String, dstPath: String): String {
        val src = resolve(srcPath)
        if (!src.exists()) throw ShellException("mv: no such file or directory: $srcPath")
        if (src == root) throw ShellException("mv: refusing to move the storage root")
        val target = destination(src, dstPath, "mv")
        if (!src.renameTo(target)) {
            // 別ボリュームなどで rename できない場合はコピーして削除
            val copied = runCatching { src.copyRecursively(target, overwrite = false) }.getOrDefault(false)
            if (!copied || !src.deleteRecursively()) throw ShellException("mv: failed: ${display(src)} -> ${display(target)}")
        }
        return "moved: ${display(src)} -> ${display(target)}"
    }

    /** 削除対象を確認する（実際の削除は確認後に [rmConfirmed]） */
    fun rmTarget(path: String): File {
        val f = resolve(path)
        if (!f.exists()) throw ShellException("rm: no such file or directory: $path")
        if (f == root || root.path.startsWith(f.path + "/")) throw ShellException("rm: refusing to remove ${display(f)}")
        return f
    }

    fun rmConfirmed(f: File): String {
        if (!f.deleteRecursively()) throw ShellException("rm: failed to remove: ${display(f)}")
        if (cwd == f || cwd.path.startsWith(f.path + "/")) cwd = f.parentFile ?: root
        return "removed: ${display(f)}"
    }

    /** ファイルを種類に応じたアプリで開く Intent（FileProvider 経由） */
    fun openIntent(f: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 補完用：[dirPart] 内で [prefix] に前方一致する名前（ディレクトリは末尾 /） */
    fun complete(dirPart: String, prefix: String, dirsOnly: Boolean): List<String> {
        val dir = if (dirPart.isEmpty()) cwd else resolve(dirPart)
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { !dirsOnly || it.isDirectory }
            .filter { it.name.startsWith(prefix, ignoreCase = true) && (prefix.startsWith(".") || !it.name.startsWith(".")) }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .map { if (it.isDirectory) "${it.name}/" else it.name }
    }
}

/** バイト数を読みやすい単位に */
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val units = listOf("K", "M", "G", "T")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return "%.1f%s".format(v, units[i])
}

/** 引数の分割（"..." / '...' の引用と \ によるエスケープに対応） */
fun splitArgs(input: String): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    var quote: Char? = null
    var inToken = false
    var i = 0
    while (i < input.length) {
        val c = input[i]
        when {
            c == '\\' && i + 1 < input.length && quote != '\'' -> {
                sb.append(input[i + 1]); i++; inToken = true
            }
            quote != null -> if (c == quote) quote = null else sb.append(c)
            c == '"' || c == '\'' -> { quote = c; inToken = true }
            c.isWhitespace() -> if (inToken) {
                result.add(sb.toString()); sb.clear(); inToken = false
            }
            else -> { sb.append(c); inToken = true }
        }
        i++
    }
    if (inToken) result.add(sb.toString())
    return result
}

/** 補完結果をコマンドラインに入れるため空白をエスケープする */
fun escapeArg(s: String): String = s.replace("\\", "\\\\").replace(" ", "\\ ")
