package com.ictools.ichomelauncher.ui.terminal

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ictools.ichomelauncher.BuildConfig
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.data.Memo
import com.ictools.ichomelauncher.data.PanelIds

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
}

/**
 * ターミナルのセッション：出力ログ・入力履歴の保持とコマンドの解釈・実行を行う。
 * パネルを閉じてもログが消えないよう ViewModel が保持する。
 */
class TerminalSession(private val host: TerminalHost) {

    companion object {
        const val PROMPT = "ihl:"
        const val MAX_LOG_LINES = 500
        const val MAX_HISTORY = 50

        private val HELP = listOf(
            "commands:",
            "  help             show this help",
            "  apps | drawer    open the app drawer",
            "  open <app>       launch an app",
            "  <app>            launch an app (shorthand)",
            "  settings         open settings",
            "  media            open media control",
            "  schedule | cal   open schedule",
            "  history          open history",
            "  memo [text]      create a new memo",
            "  memos            list memos",
            "  memos <n>        open memo #n",
            "  memos rm <n>     delete memo #n",
            "  clear            clear the log",
            "  close | exit     close the terminal",
            "  version          show version"
        )
    }

    /** 出力ログ（新しい行が末尾） */
    val lines: SnapshotStateList<String> = mutableStateListOf(
        "IcHomeLauncher v${BuildConfig.VERSION_NAME}",
        "type 'help' for commands"
    )

    /** 入力履歴（新しいものが末尾。再起動後も残るよう保存する） */
    val history: SnapshotStateList<String> = mutableStateListOf()

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
        lines.addAll(text)
        if (lines.size > MAX_LOG_LINES) lines.removeRange(0, lines.size - MAX_LOG_LINES)
    }

    /** 1 行分の入力を実行する */
    fun execute(rawInput: String) {
        val input = rawInput.trim()
        print("$PROMPT $input".trimEnd())
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
                "memo" -> { host.newMemo(""); return }
                "memos" -> { listMemos(); return }
                "clear" -> { lines.clear(); return }
                "close", "exit" -> { host.closePanel(PanelIds.TERMINAL); return }
                "version" -> { print("IcHomeLauncher v${BuildConfig.VERSION_NAME}"); return }
                "open" -> { print("usage: open <app name>"); return }
            }
        }
        when (command) {
            "open" -> openByName(argument)
            "memo" -> {
                host.newMemo(argument)
                print("memo created")
            }
            "memos" -> memosCommand(argument)
            // コマンド名と一致しない入力はアプリ名として扱う（open の省略形）
            else -> openByName(input)
        }
    }

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

    /** アプリ名での起動：完全一致 → 部分一致（1件なら起動、複数なら候補表示、0件ならエラー） */
    private fun openByName(query: String) {
        val all = host.apps()
        val exact = all.firstOrNull { it.label.equals(query, ignoreCase = true) }
        if (exact != null) {
            launch(exact)
            return
        }
        val partial = all.filter { it.label.contains(query, ignoreCase = true) }
        when (partial.size) {
            0 -> print("command not found: $query")
            1 -> launch(partial.first())
            else -> {
                print("multiple matches (${partial.size}):")
                print(*partial.map { "  ${it.label}" }.toTypedArray())
            }
        }
    }

    private fun launch(app: AppEntry) {
        print("Opening ${app.label}")
        if (!host.launchApp(app)) print("failed to open: ${app.label}")
    }
}
