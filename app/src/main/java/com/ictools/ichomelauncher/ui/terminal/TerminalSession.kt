package com.ictools.ichomelauncher.ui.terminal

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ictools.ichomelauncher.BuildConfig
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.data.Memo
import com.ictools.ichomelauncher.data.NEED_PERMISSION
import com.ictools.ichomelauncher.data.PanelIds
import com.ictools.ichomelauncher.data.SoundController
import com.ictools.ichomelauncher.data.VolumeStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ターミナルからランチャー本体を操作するための窓口 */
interface TerminalHost {
    fun apps(): List<AppEntry>
    fun launchApp(app: AppEntry): Boolean
    fun openPanel(id: String)
    /** パネルを閉じる。固定中で閉じられなければ false */
    fun closePanel(id: String): Boolean
    fun memos(): List<Memo>
    fun newMemo(text: String)
    fun openMemo(memo: Memo)
    fun deleteMemo(memo: Memo)
    fun onHistoryChanged(history: List<String>)
    fun favorites(): List<AppEntry>
    fun addFavorite(app: AppEntry)
    fun removeFavorite(app: AppEntry)
    fun openCamera(): Boolean
    fun launchAssist(): Boolean
    fun torchOn(): Boolean
    fun setTorch(on: Boolean): String?
    fun startActivity(intent: Intent): Boolean
    fun requestAllFilesAccess()
    fun uninstall(app: AppEntry): Boolean
    fun openAppInfo(app: AppEntry): Boolean
    fun newApps(): List<AppEntry>
    fun aliases(): Map<String, String>
    fun saveAliases(aliases: Map<String, String>)
    fun sound(): SoundController
    fun requestWriteSettings()
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
            "memo", "memos", "clock", "calendar", "status", "network", "net", "favorites", "favs", "fav", "pad",
            "uninstall", "info", "new",
            "camera", "assist", "light", "ls", "cd", "pwd", "cp", "mv", "mkdir", "rm", "find",
            "calc", "g", "br", "alias", "unalias", "timer", "alarm", "vol", "bright", "sound", "?",
            "clear", "close", "exit", "version"
        )

        /** ファイルパスを引数に取るコマンド */
        private val FILE_COMMANDS = setOf("ls", "cd", "cp", "mv", "mkdir", "rm", "open")

        /** Brave のパッケージ名（br コマンド） */
        private const val BRAVE_PACKAGE = "com.brave.browser"

        /** alias の名前に使える文字 */
        private val ALIAS_NAME = Regex("^[a-z0-9_-]+$")
    }

    /** 出力ログ（新しい行が末尾） */
    val lines: SnapshotStateList<TerminalLine> = mutableStateListOf(
        TerminalLine("IcHomeLauncher v${BuildConfig.VERSION_NAME}"),
        TerminalLine("type 'help' for commands")
    )

    /** 入力履歴（新しいものが末尾。再起動後も残るよう保存する） */
    val history: SnapshotStateList<String> = mutableStateListOf()

    /** 次に入力欄へ入れる文字列（ターミナルパネルが受け取って空に戻す） */
    val pendingInput = mutableStateOf<String?>(null)

    /** 入力欄に文字列を入れる（実行はしない） */
    fun prefill(text: String) {
        pendingInput.value = text
    }

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

        // 1 語目が短縮コマンドなら置き換える（1 段だけ）
        val expanded = expandAlias(input)
        val command = expanded.substringBefore(' ').lowercase()
        val argument = expanded.substringAfter(' ', "").trim()

        // 引数を取らないコマンドは単独で入力された場合のみコマンドとして扱う
        if (argument.isEmpty()) {
            when (command) {
                "help" -> { printHelpPage(1); return }
                "?" -> { host.openPanel(PanelIds.HELP); return }
                "sound" -> { host.openPanel(PanelIds.SOUND); return }
                "alias" -> { listAliases(); return }
                "vol" -> { showVolumes(); return }
                "bright" -> { showBrightness(); return }
                "calc" -> { print("usage: calc <expression>  e.g. calc 12*8"); return }
                "g" -> { print("usage: g <words>"); return }
                "br" -> { print("usage: br <url|words>"); return }
                "timer" -> { print("usage: timer <time>  e.g. timer 3m / 1h30m / 90s"); return }
                "alarm" -> { print("usage: alarm <HH:MM> [label]"); return }
                "find" -> { print("usage: find <name>"); return }
                "unalias" -> { print("usage: unalias <name>"); return }
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
                "pad" -> { host.openPanel(PanelIds.PAD); return }
                "new" -> { listNewApps(); return }
                "uninstall" -> { print("usage: uninstall <app>"); return }
                "info" -> { print("usage: info <app>"); return }
                "fav" -> { listFavorites(); return }
                "memo" -> { host.newMemo(""); return }
                "memos" -> { listMemos(); return }
                "camera" -> { if (!host.openCamera()) print("camera: no camera app found"); return }
                "assist" -> {
                    print(if (host.launchAssist()) "Opening assistant" else "assist: no assistant app found")
                    return
                }
                "light" -> { toggleLight(!host.torchOn()); return }
                "pwd" -> { fileCommand { print(files.pwd()) }; return }
                "ls" -> { fileCommand { print(*files.ls(null, false).toTypedArray()) }; return }
                "cd" -> { fileCommand { files.cd(null); print(files.display(files.cwd)) }; return }
                "clear" -> { lines.clear(); return }
                "close", "exit" -> {
                    if (!host.closePanel(PanelIds.TERMINAL)) print("terminal is locked. unlock it from the ⋮ menu to close")
                    return
                }
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
            "help" -> helpCommand(argument)
            "calc" -> calc(argument)
            "g" -> googleSearch(argument)
            "br" -> brave(argument)
            "alias" -> aliasCommand(argument)
            "unalias" -> unaliasCommand(argument)
            "timer" -> timer(argument)
            "alarm" -> alarm(argument)
            "vol" -> volCommand(args)
            "bright" -> brightCommand(argument)
            "find" -> fileCommand { find(argument) }
            "uninstall" -> {
                val app = findApp(argument, host.apps()) ?: return
                if (app.isSystem) {
                    print("uninstall: ${app.label} is a system app and cannot be uninstalled")
                    printAction("  [ アプリ情報を開く（無効化はここから） ]") { host.openAppInfo(app) }
                } else if (host.uninstall(app)) {
                    print("uninstall: confirm in the system dialog (${app.label})")
                } else {
                    print("uninstall: cannot open the uninstaller")
                }
            }
            "info" -> {
                val app = findApp(argument, host.apps()) ?: return
                if (!host.openAppInfo(app)) print("info: cannot open app info")
            }
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
            else -> openByName(expanded)
        }
    }

    // ---- ヘルプ（ページ表示） ----

    /** ページの目次と指定ページを表示する */
    private fun printHelpPage(page: Int) {
        val sections = CommandHelp.SECTIONS
        val index = (page - 1).coerceIn(0, sections.lastIndex)
        val section = sections[index]
        print("help ${index + 1}/${sections.size}: ${section.title}")
        val width = section.entries.maxOf { it.usage.length }.coerceAtMost(26)
        section.entries.forEach { e -> print("  ${e.usage.padEnd(width)}  ${e.description}") }
        val pages = sections.mapIndexed { i, s -> "${i + 1}:${s.title}" }.joinToString("  ")
        print("pages  $pages")
        print("'help <n>' for a page, 'help <command>' for details, '?' for the help panel")
    }

    private fun helpCommand(argument: String) {
        argument.toIntOrNull()?.let {
            printHelpPage(it)
            return
        }
        val entries = CommandHelp.find(argument)
        if (entries.isEmpty()) {
            print("help: no such command: $argument")
            return
        }
        entries.forEach { e ->
            print(e.usage)
            print("  ${e.description}")
            e.example?.let { print("  e.g. $it") }
        }
    }

    // ---- 電卓・検索 ----

    private fun calc(expression: String) {
        try {
            val result = Calculator.evaluate(expression)
            // 結果をタップすると入力欄に入る（続けて計算できる）
            printAction("= $result") { prefill(result) }
        } catch (e: CalcException) {
            print(e.message ?: "calc: error")
        }
    }

    /** Google の検索結果をブラウザで開く */
    private fun googleSearch(words: String) {
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(words))
        print("Searching: $words")
        if (!host.startActivity(Intent(Intent.ACTION_VIEW, uri))) print("g: no browser found")
    }

    /** Brave で開く：URL ならそのまま、それ以外は Brave に設定された検索エンジンで検索 */
    private fun brave(text: String) {
        val url = toUrl(text)
        val intent = if (url != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        } else {
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)
        }.setPackage(BRAVE_PACKAGE)
        print(if (url != null) "Opening in Brave: $url" else "Searching in Brave: $text")
        if (host.startActivity(intent)) return
        // 検索の Intent に対応していない場合は Brave Search の URL で開く
        if (url == null && host.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://search.brave.com/search?q=" + Uri.encode(text))).setPackage(BRAVE_PACKAGE)
            )
        ) return
        print("br: Brave is not installed")
    }

    /** URL らしければ https 付きの URL を返す（空白を含まず、ドットを含む／スキーム付き） */
    private fun toUrl(text: String): String? {
        val t = text.trim()
        if (t.contains(' ')) return null
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        return if (Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$").matches(t)) "https://$t" else null
    }

    // ---- 短縮コマンド ----

    private fun expandAlias(input: String): String {
        val first = input.substringBefore(' ')
        val value = host.aliases()[first.lowercase()] ?: return input
        val rest = input.substringAfter(' ', "")
        return if (rest.isEmpty()) value else "$value $rest"
    }

    private fun listAliases() {
        val aliases = host.aliases()
        if (aliases.isEmpty()) {
            print("no aliases. e.g. alias dc=Discord")
            return
        }
        aliases.toSortedMap().forEach { (k, v) -> print("  $k = $v") }
    }

    private fun aliasCommand(argument: String) {
        val eq = argument.indexOf('=')
        if (eq < 0) {
            // alias <name>：1 件表示
            val v = host.aliases()[argument.lowercase()]
            print(if (v != null) "  ${argument.lowercase()} = $v" else "alias: not found: $argument")
            return
        }
        val name = argument.substring(0, eq).trim().lowercase()
        val value = argument.substring(eq + 1).trim().removeSurrounding("\"").removeSurrounding("'")
        when {
            !ALIAS_NAME.matches(name) -> print("alias: use a-z 0-9 _ - for the name")
            name in COMMANDS -> print("alias: '$name' is a built-in command")
            value.isEmpty() -> print("usage: alias <name>=<text>")
            else -> {
                host.saveAliases(host.aliases() + (name to value))
                print("alias: $name = $value")
            }
        }
    }

    private fun unaliasCommand(argument: String) {
        val name = argument.trim().lowercase()
        if (name !in host.aliases()) {
            print("unalias: not found: $name")
            return
        }
        host.saveAliases(host.aliases() - name)
        print("unalias: $name")
    }

    // ---- タイマー・アラーム（時計アプリへ） ----

    /** 3m / 90s / 1h30m / 1:30（分:秒）/ 5（分）を秒に */
    private fun parseDuration(text: String): Int? {
        val t = text.trim().lowercase().replace(" ", "")
        t.toIntOrNull()?.let { return it * 60 }
        Regex("^(\\d+):(\\d{1,2})$").matchEntire(t)?.let { m ->
            return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
        }
        val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m(?:in)?)?(?:(\\d+)s(?:ec)?)?$").matchEntire(t) ?: return null
        if (m.value.isEmpty()) return null
        val (h, mi, sec) = m.destructured
        return (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (sec.toIntOrNull() ?: 0)
    }

    private fun timer(argument: String) {
        val seconds = parseDuration(argument)
        if (seconds == null || seconds <= 0 || seconds > 24 * 3600) {
            print("timer: e.g. timer 3m / 1h30m / 90s")
            return
        }
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val text = buildString {
            if (seconds >= 3600) append("${seconds / 3600}h")
            if (seconds % 3600 >= 60) append("${seconds % 3600 / 60}m")
            if (seconds % 60 > 0) append("${seconds % 60}s")
        }
        print(if (host.startActivity(intent)) "timer set: $text" else "timer: the clock app does not support timers")
    }

    private fun alarm(argument: String) {
        val time = argument.substringBefore(' ')
        val label = argument.substringAfter(' ', "").trim()
        val m = Regex("^(\\d{1,2})[:：]?(\\d{2})$").matchEntire(time)
        val hour = m?.groupValues?.get(1)?.toIntOrNull()
        val minute = m?.groupValues?.get(2)?.toIntOrNull()
        if (hour == null || minute == null || hour > 23 || minute > 59) {
            print("alarm: e.g. alarm 7:00 / alarm 06:30 label")
            return
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotEmpty()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        val text = "%d:%02d".format(hour, minute) + if (label.isNotEmpty()) " ($label)" else ""
        print(if (host.startActivity(intent)) "alarm set: $text" else "alarm: the clock app does not support alarms")
    }

    // ---- 音量・明るさ ----

    private fun showVolumes() {
        val s = host.sound().snapshot()
        VolumeStream.entries.forEach { v ->
            val mute = if (v == VolumeStream.MEDIA && s.mediaMuted) " (muted)" else ""
            print("  ${v.id.padEnd(6)} ${s.volumes[v] ?: 0}%$mute")
        }
    }

    private fun volCommand(args: List<String>) {
        val sound = host.sound()
        val first = args.firstOrNull()?.lowercase() ?: return showVolumes()
        // vol <ring|notif|alarm|media> <値> / vol <値>（メディア）
        val target = VolumeStream.fromId(first)
        val stream = target ?: VolumeStream.MEDIA
        val value = (if (target != null) args.getOrNull(1) else first)?.lowercase()
        val error = when {
            value == null -> { print("  ${stream.id} ${sound.getVolume(stream)}%"); return }
            value == "up" -> sound.adjust(stream, up = true)
            value == "down" -> sound.adjust(stream, up = false)
            value == "mute" && stream == VolumeStream.MEDIA -> sound.toggleMute()
            value.removeSuffix("%").toIntOrNull() != null -> sound.setVolume(stream, value.removeSuffix("%").toInt(), showUi = true)
            else -> { print("usage: vol [media|ring|notif|alarm] <0-100|up|down|mute>"); return }
        }
        print(error ?: "  ${stream.id} ${sound.getVolume(stream)}%")
    }

    private fun showBrightness() {
        val s = host.sound().snapshot()
        print("  brightness ${s.brightness}%" + if (s.autoBrightness) " (auto)" else "")
        if (!s.canWriteSettings) printWriteSettingsRequest()
    }

    private fun brightCommand(argument: String) {
        val sound = host.sound()
        val a = argument.trim().lowercase()
        val error = when {
            a == "auto" -> sound.setAutoBrightness(true)
            a == "manual" -> sound.setAutoBrightness(false)
            a.removeSuffix("%").toIntOrNull() != null -> sound.setBrightness(a.removeSuffix("%").toInt())
            else -> { print("usage: bright [0-100|auto]"); return }
        }
        if (error == null) {
            showBrightness()
        } else {
            print(error)
            if (error == NEED_PERMISSION) printWriteSettingsRequest()
        }
    }

    private fun printWriteSettingsRequest() {
        printAction("  [ 「システム設定の変更」を許可する ]") { host.requestWriteSettings() }
    }

    // ---- ファイル検索 ----

    private fun find(query: String) {
        print("searching '$query' under ${files.display(files.cwd)} ...")
        scope.launch {
            val (found, truncated) = withContext(Dispatchers.IO) { runCatching { files.find(query) }.getOrDefault(emptyList<File>() to false) }
            if (found.isEmpty()) {
                print("find: no match")
                return@launch
            }
            found.forEach { f ->
                val label = files.display(f) + if (f.isDirectory) "/" else ""
                // タップ：ファイルは開く、ディレクトリはそこへ移動
                printAction("  $label") {
                    if (f.isDirectory) {
                        runCatching { files.cd(f.path) }
                        print(files.display(files.cwd))
                    } else {
                        openFile(f)
                    }
                }
            }
            print("${found.size} found" + if (truncated) " (stopped at the limit)" else "")
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

    // ---- 新しいアプリ ----

    private fun listNewApps() {
        val apps = host.newApps()
        if (apps.isEmpty()) {
            print("no new apps")
            return
        }
        print("new apps (${apps.size}):")
        val f = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        apps.forEach { a -> printAction("  ${f.format(Date(a.installTime))}  ${a.label}") { host.launchApp(a) } }
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
                ?: host.aliases().keys.sorted().firstOrNull { it.startsWith(input, ignoreCase = true) && it.length > input.length }
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
            "uninstall", "info" -> rest.takeIf { it.isNotEmpty() }?.let { appCompletion(it, host.apps()) }?.let { head + it }
            "unalias" -> host.aliases().keys.sorted().firstOrNull { it.startsWith(rest, ignoreCase = true) && it.length > rest.length }?.let { head + it }
            "vol" -> if (rest.contains(' ')) null else listOf("media", "ring", "notif", "alarm", "up", "down", "mute")
                .firstOrNull { it.startsWith(rest, ignoreCase = true) && it.length > rest.length }?.let { head + it }
            "bright" -> listOf("auto", "manual").firstOrNull { it.startsWith(rest, ignoreCase = true) && it.length > rest.length }?.let { head + it }
            "help" -> (CommandHelp.SECTIONS.flatMap { it.entries.flatMap { e -> e.names } }.distinct())
                .firstOrNull { it.startsWith(rest, ignoreCase = true) && it.length > rest.length }?.let { head + it }
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
