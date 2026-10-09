package com.ictools.ichomelauncher.ui.terminal

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ictools.ichomelauncher.BuildConfig
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.data.Memo
import com.ictools.ichomelauncher.data.PanelIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** ターミナルからランチャー本体を操作するための窓口 */
interface TerminalHost {
    fun apps(): List<AppEntry>
    fun launchApp(app: AppEntry): Boolean
    fun openPanel(id: String)
    fun closePanel(id: String)
    fun memos(): List<Memo>
    fun newMemo(text: String)
    fun openMemo(memo: Memo)
    fun deleteMemo(memo: Memo)
    fun onHistoryChanged(history: List<String>)
    fun favorites(): List<AppEntry>
    fun addFavorite(app: AppEntry)
    fun removeFavorite(app: AppEntry)
    fun openCamera(): Boolean
    fun torchOn(): Boolean
    fun setTorch(on: Boolean): String?
    fun startActivity(intent: android.content.Intent): Boolean
    fun requestAllFilesAccess()
}

/** ログ1行分。[action] があればタップで実行できる */
data class TerminalLine(val text: String, val action: (() -> Unit)? = null)

/**
 * ターミナルのセッション：出力ログ・入力履歴の保持、コマンドの解釈・実行、入力補完を行う。
 * パネルを閉じてもログが消えないよう ViewModel が保持する。
 */
class TerminalSession(
    private val host: TerminalHost,
    private val files: FileShell,
    private val scope: CoroutineScope
) {

    companion object {
        const val PROMPT = "ihl:"
        const val MAX_LOG_LINES = 500
        const val MAX_HISTORY = 50

        /** 補完候補になるコマンド名 */
        val COMMANDS = listOf(
            "help", "apps", "drawer", "open", "settings", "media", "schedule", "cal", "history",
            "memo", "memos", "clock", "calendar", "status", "network", "net", "favorites", "favs", "fav",
            "camera", "light", "ls", "cd", "pwd", "cp", "mv", "mkdir", "rm",
            "clear", "close", "exit", "version"
        )

        /** ファイルパスを引数に取るコマンド */
        private val FILE_COMMANDS = setOf("ls", "cd", "cp", "mv", "mkdir", "rm", "open")

        private val HELP = listOf(
            "commands:",
            "  help               show this help",
            "  apps | drawer      open the app drawer",
            "  open <app|file>    open a file in the current dir,",
            "                     otherwise launch an app",
            "  open -a <app>      launch an app",
            "  <app>              launch an app (shorthand)",
            "  camera             open the camera app",
            "  light [on|off]     toggle / switch the flashlight",
            "  fav                list favorites",
            "  fav add <app>      add an app to favorites",
            "  fav rm <app>       remove an app from favorites",
            "panels:",
            "  settings | media | history | clock | calendar",
            "  status | network (net) | favorites (favs)",
            "  schedule (cal)",
            "memos:",
            "  memo [text]        create a new memo",
            "  memos              list memos",
            "  memos <n>          open memo #n",
            "  memos rm <n>       delete memo #n",
            "files (needs all files access):",
            "  pwd                show the current directory",
            "  ls [-a] [path]     list files",
            "  cd [path]          change directory (~ = storage root)",
            "  mkdir <path>       create a directory",
            "  cp <src> <dst>     copy a file / directory",
            "  mv <src> <dst>     move / rename",
            "  rm <path>          delete (asks y/N first)",
            "others:",
            "  clear              clear the log",
            "  close | exit       close the terminal",
            "  version            show version",
            "keys: → or Tab accepts the gray suggestion"
        )
    }

    /** 出力ログ（新しい行が末尾） */
    val lines: SnapshotStateList<TerminalLine> = mutableStateListOf(
        TerminalLine("IcHomeLauncher v${BuildConfig.VERSION_NAME}"),
        TerminalLine("type 'help' for commands")
    )

    /** 入力履歴（新しいものが末尾。再起動後も残るよう保存する） */
    val history: SnapshotStateList<String> = mutableStateListOf()

    // y/N の確認待ち（rm など）
    private var pendingConfirm: (suspend () -> Unit)? = null

    /** 保存されていた履歴を読み込む */
    fun restoreHistory(saved: List<String>) {
        history.clear()
        history.addAll(saved.takeLast(MAX_HISTORY))
    }

    fun clearHistory() {
        history.clear()
        host.onHistoryChanged(emptyList())
    }

    private fun print(vararg text: String) {
        lines.addAll(text.map { TerminalLine(it) })
        trim()
    }

    private fun printAction(text: String, action: () -> Unit) {
        lines.add(TerminalLine(text, action))
        trim()
    }

    private fun trim() {
        if (lines.size > MAX_LOG_LINES) lines.removeRange(0, lines.size - MAX_LOG_LINES)
    }

    /** 1 行分の入力を実行する */
    fun execute(rawInput: String) {
        val input = rawInput.trim()
        print("$PROMPT $input".trimEnd())

        // 確認待ちなら y/N の返答として扱う
        pendingConfirm?.let { confirm ->
            pendingConfirm = null
            if (input.lowercase() in setOf("y", "yes")) {
                scope.launch { confirm() }
            } else {
                print("cancelled")
            }
            return
        }
        if (input.isEmpty()) return

        // 同じコマンドの連続は1件にまとめる
        if (history.lastOrNull() != input) history.add(input)
        if (history.size > MAX_HISTORY) history.removeRange(0, history.size - MAX_HISTORY)
        host.onHistoryChanged(history.toList())

        val command = input.substringBefore(' ').lowercase()
        val argument = input.substringAfter(' ', "").trim()

        // 引数を取らないコマンドは単独で入力された場合のみコマンドとして扱う
        if (argument.isEmpty()) {
            when (command) {
                "help" -> { print(*HELP.toTypedArray()); return }
                "apps", "drawer" -> { host.openPanel(PanelIds.DRAWER); return }
                "settings" -> { host.openPanel(PanelIds.SETTINGS); return }
                "media" -> { host.openPanel(PanelIds.MEDIA); return }
                "schedule", "cal" -> { host.openPanel(PanelIds.SCHEDULE); return }
                "history" -> { host.openPanel(PanelIds.HISTORY); return }
                "clock" -> { host.openPanel(PanelIds.CLOCK); return }
                "calendar" -> { host.openPanel(PanelIds.CALENDAR); return }
                "status" -> { host.openPanel(PanelIds.STATUS); return }
                "network", "net" -> { host.openPanel(PanelIds.NETWORK); return }
                "favorites", "favs" -> { host.openPanel(PanelIds.FAVORITES); return }
                "fav" -> { listFavorites(); return }
                "memo" -> { host.newMemo(""); return }
                "memos" -> { listMemos(); return }
                "camera" -> { if (!host.openCamera()) print("camera: no camera app found"); return }
                "light" -> { toggleLight(!host.torchOn()); return }
                "pwd" -> { fileCommand { print(files.pwd()) }; return }
                "ls" -> { fileCommand { print(*files.ls(null, false).toTypedArray()) }; return }
                "cd" -> { fileCommand { files.cd(null); print(files.display(files.cwd)) }; return }
                "clear" -> { lines.clear(); return }
                "close", "exit" -> { host.closePanel(PanelIds.TERMINAL); return }
                "version" -> { print("IcHomeLauncher v${BuildConfig.VERSION_NAME}"); return }
                "open" -> { print("usage: open <file|app> | open -a <app>"); return }
            }
        }
        val args = splitArgs(argument)
        when (command) {
            "open" -> open(argument, args)
            "memo" -> {
                host.newMemo(argument)
                print("memo created")
            }
            "memos" -> memosCommand(argument)
            "fav" -> favCommand(args)
            "light" -> when (argument.lowercase()) {
                "on" -> toggleLight(true)
                "off" -> toggleLight(false)
                else -> print("usage: light [on|off]")
            }
            "ls" -> fileCommand {
                val all = args.contains("-a")
                print(*files.ls(args.firstOrNull { it != "-a" }, all).toTypedArray())
            }
            "cd" -> fileCommand {
                files.cd(args.firstOrNull())
                print(files.display(files.cwd))
            }
            "mkdir" -> fileCommand {
                args.forEach { files.mkdir(it) }
                print("created: ${args.joinToString(" ")}")
            }
            "cp", "mv" -> fileCommand {
                if (args.size != 2) {
                    print("usage: $command <src> <dst>")
                    return@fileCommand
                }
                // 大きなファイルでも画面が止まらないよう別スレッドで実行
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { if (command == "cp") files.cp(args[0], args[1]) else files.mv(args[0], args[1]) }
                    }
                    print(result.getOrElse { it.message ?: "$command: failed" })
                }
            }
            "rm" -> fileCommand { confirmRemove(args) }
            // コマンド名と一致しない入力はアプリ名として扱う（open の省略形）
            else -> openByName(input)
        }
    }

    // ---- ファイル ----

    /** ファイル操作の共通処理：権限確認とエラー表示 */
    private inline fun fileCommand(block: () -> Unit) {
        if (!files.hasAccess()) {
            print("ファイル操作には「すべてのファイルへのアクセス」の許可が必要です")
            printAction("  [ 許可する（設定を開く） ]") { host.requestAllFilesAccess() }
            return
        }
        try {
            block()
        } catch (e: ShellException) {
            print(e.message ?: "error")
        } catch (e: Exception) {
            print("error: ${e.javaClass.simpleName}")
        }
    }

    private fun confirmRemove(args: List<String>) {
        if (args.isEmpty()) {
            print("usage: rm <path>...")
            return
        }
        val targets = args.map { files.rmTarget(it) }
        val label = targets.joinToString(" ") { "'${files.display(it)}'" }
        val dirNote = if (targets.any { it.isDirectory }) " (including contents)" else ""
        print("rm: remove $label$dirNote? (y/N)")
        pendingConfirm = {
            val results = withContext(Dispatchers.IO) {
                targets.map { t -> runCatching { files.rmConfirmed(t) }.getOrElse { it.message ?: "rm: failed" } }
            }
            print(*results.toTypedArray())
        }
    }

    /** open：-a ならアプリ、現在のディレクトリのファイル／パスに一致すればファイル、それ以外はアプリ */
    private fun open(argument: String, args: List<String>) {
        if (args.firstOrNull() == "-a") {
            val name = argument.removePrefix("-a").trim()
            if (name.isEmpty()) print("usage: open -a <app>") else openByName(name)
            return
        }
        val path = args.singleOrNull()
        if (path != null && files.hasAccess()) {
            val f = runCatching { files.resolve(path) }.getOrNull()
            if (f != null && f.exists()) {
                openFile(f)
                return
            }
        } else if (path != null && path.contains('/')) {
            // パスらしい入力だが権限が無い
            fileCommand { }
            return
        }
        openByName(argument)
    }

    private fun openFile(f: File) {
        if (f.isDirectory) {
            print("open: ${files.display(f)} is a directory (use cd)")
            return
        }
        val intent = runCatching { files.openIntent(f) }.getOrNull()
        if (intent == null) {
            print("open: cannot share this file: ${files.display(f)}")
            return
        }
        print("Opening ${f.name}")
        if (!host.startActivity(intent)) print("open: no app can open this file")
    }

    // ---- ライト ----

    private fun toggleLight(on: Boolean) {
        val error = host.setTorch(on)
        print(error ?: "light ${if (on) "on" else "off"}")
    }

    // ---- お気に入り ----

    private fun listFavorites() {
        val favs = host.favorites()
        if (favs.isEmpty()) {
            print("no favorites. add one with: fav add <app>")
            return
        }
        print("favorites (${favs.size}):")
        favs.forEachIndexed { i, a -> print("  ${i + 1}. ${a.label}") }
    }

    private fun favCommand(args: List<String>) {
        val sub = args.firstOrNull()?.lowercase()
        val name = args.drop(1).joinToString(" ")
        if (sub !in setOf("add", "rm") || name.isEmpty()) {
            print("usage: fav | fav add <app> | fav rm <app>")
            return
        }
        val pool = if (sub == "rm") host.favorites() else host.apps()
        val app = findApp(name, pool) ?: return
        if (sub == "add") {
            if (host.favorites().any { it.key == app.key }) {
                print("already in favorites: ${app.label}")
            } else {
                host.addFavorite(app)
                print("added to favorites: ${app.label}")
            }
        } else {
            host.removeFavorite(app)
            print("removed from favorites: ${app.label}")
        }
    }

    // ---- メモ ----

    /** メモ一覧を番号付きで表示（番号は新しく更新された順） */
    private fun listMemos() {
        val memos = sortedMemos()
        if (memos.isEmpty()) {
            print("no memos. create one with: memo [text]")
            return
        }
        print("memos (${memos.size}):")
        memos.forEachIndexed { i, m -> print("  #${i + 1}  ${m.headline.ifEmpty { "(empty)" }.take(40)}") }
    }

    private fun sortedMemos(): List<Memo> = host.memos().sortedByDescending { it.updatedAt }

    /** memos <n> / memos rm <n> */
    private fun memosCommand(argument: String) {
        val parts = argument.split(Regex("\\s+"))
        val remove = parts.first().lowercase() == "rm"
        val numberText = (if (remove) parts.getOrNull(1) else parts.first())?.removePrefix("#")
        val index = numberText?.toIntOrNull()
        val memos = sortedMemos()
        if (index == null || index !in 1..memos.size) {
            print("usage: memos <n> | memos rm <n>  (see 'memos')")
            return
        }
        val memo = memos[index - 1]
        if (remove) {
            host.deleteMemo(memo)
            print("deleted memo #$index")
        } else {
            host.openMemo(memo)
        }
    }

    // ---- アプリ ----

    /** アプリ名で探す：完全一致 → 部分一致（1件ならそれ、複数なら候補表示、0件ならエラー） */
    private fun findApp(query: String, pool: List<AppEntry>): AppEntry? {
        pool.firstOrNull { it.label.equals(query, ignoreCase = true) }?.let { return it }
        val partial = pool.filter { it.label.contains(query, ignoreCase = true) }
        when (partial.size) {
            0 -> print("command not found: $query")
            1 -> return partial.first()
            else -> {
                print("multiple matches (${partial.size}):")
                print(*partial.map { "  ${it.label}" }.toTypedArray())
            }
        }
        return null
    }

    private fun openByName(query: String) {
        val app = findApp(query, host.apps()) ?: return
        print("Opening ${app.label}")
        if (!host.launchApp(app)) print("failed to open: ${app.label}")
    }

    // ---- 入力補完 ----

    /**
     * 入力中の文字列に対する補完候補（入力全体を置き換える文字列）を返す。
     * 候補は入力の続き（大文字小文字は無視）になっている場合のみ返す。
     */
    fun suggest(input: String): String? {
        if (input.isBlank() || pendingConfirm != null) return null
        val firstSpace = input.indexOf(' ')
        // 1 語目：コマンド名 → アプリ名
        if (firstSpace < 0) {
            val candidate = COMMANDS.firstOrNull { it.startsWith(input, ignoreCase = true) && it.length > input.length }
                ?: appCompletion(input, host.apps())
            return candidate?.takeIf { it.length > input.length }
        }
        val command = input.substring(0, firstSpace).lowercase()
        val rest = input.substring(firstSpace + 1)
        val head = input.substring(0, firstSpace + 1)
        val result: String? = when (command) {
            "fav" -> {
                val sub = rest.substringBefore(' ', "")
                if (sub.isEmpty()) {
                    listOf("add", "rm").firstOrNull { it.startsWith(rest, ignoreCase = true) && it.length > rest.length }?.let { head + it }
                } else {
                    val name = rest.substringAfter(' ')
                    val pool = if (sub.lowercase() == "rm") host.favorites() else host.apps()
                    if (name.isEmpty()) null else appCompletion(name, pool)?.let { head + sub + " " + it }
                }
            }
            "light" -> listOf("on", "off").firstOrNull { it.startsWith(rest, ignoreCase = true) && it.length > rest.length }?.let { head + it }
            "memos" -> if ("rm".startsWith(rest, ignoreCase = true) && rest.length < 2) head + "rm" else null
            "open" -> when {
                rest.startsWith("-a ") -> rest.removePrefix("-a ").takeIf { it.isNotEmpty() }
                    ?.let { appCompletion(it, host.apps()) }?.let { head + "-a " + it }
                else -> fileCompletion(input, command) ?: rest.takeIf { it.isNotEmpty() }
                    ?.let { appCompletion(it, host.apps()) }?.let { head + it }
            }
            in FILE_COMMANDS -> fileCompletion(input, command)
            else -> null
        }
        return result?.takeIf { it.length > input.length && it.startsWith(input, ignoreCase = true) }
    }

    private fun appCompletion(prefix: String, pool: List<AppEntry>): String? =
        pool.firstOrNull { it.label.startsWith(prefix, ignoreCase = true) && it.label.length > prefix.length }?.label

    /** 最後の引数をファイル名として補完する（権限が無ければ補完しない） */
    private fun fileCompletion(input: String, command: String): String? {
        if (!files.hasAccess() || input.endsWith(" ") && !input.endsWith("\\ ")) return null
        // 最後の引数（エスケープされていない空白の後ろ）を取り出す
        var start = input.length
        while (start > 0) {
            val c = input[start - 1]
            if (c == ' ' && !(start >= 2 && input[start - 2] == '\\')) break
            start--
        }
        val token = input.substring(start).replace("\\ ", " ").replace("\\\\", "\\")
        if (token.isEmpty() || token == "-a") return null
        val slash = token.lastIndexOf('/')
        val dirPart = if (slash >= 0) token.substring(0, slash + 1) else ""
        val namePrefix = token.substring(slash + 1)
        val candidate = runCatching { files.complete(dirPart, namePrefix, dirsOnly = command == "cd") }
            .getOrDefault(emptyList())
            .firstOrNull { it.trimEnd('/').length > namePrefix.length || it.endsWith("/") && namePrefix.length < it.length }
            ?: return null
        return input.substring(0, start) + escapeArg(dirPart + candidate)
    }
}
