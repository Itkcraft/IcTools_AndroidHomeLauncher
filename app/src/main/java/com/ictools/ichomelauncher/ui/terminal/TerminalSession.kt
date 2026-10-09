package com.ictools.ichomelauncher.ui.terminal

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ictools.ichomelauncher.BuildConfig
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.data.PanelIds

/**
 * ターミナルのセッション：出力ログ・入力履歴の保持とコマンドの解釈・実行を行う。
 * パネルを閉じてもログが消えないよう ViewModel が保持する。
 */
class TerminalSession(
    private val apps: () -> List<AppEntry>,
    private val launchApp: (AppEntry) -> Boolean,
    private val openPanel: (String) -> Unit,
    private val closePanel: (String) -> Unit
) {
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

    /** 入力履歴（セッション中のみ・新しいものが末尾） */
    val history: SnapshotStateList<String> = mutableStateListOf()

    private fun print(vararg text: String) {
        lines.addAll(text)
        if (lines.size > MAX_LOG_LINES) lines.removeRange(0, lines.size - MAX_LOG_LINES)
    }

    /** 1 行分の入力を実行する */
    fun execute(rawInput: String) {
        val input = rawInput.trim()
        print("$PROMPT $input".trimEnd())
        if (input.isEmpty()) return

        history.add(input)
        if (history.size > MAX_HISTORY) history.removeRange(0, history.size - MAX_HISTORY)

        val command = input.substringBefore(' ').lowercase()
        val argument = input.substringAfter(' ', "").trim()

        // 引数を取らないコマンドは単独で入力された場合のみコマンドとして扱う
        if (argument.isEmpty()) {
            when (command) {
                "help" -> { print(*HELP.toTypedArray()); return }
                "apps", "drawer" -> { openPanel(PanelIds.DRAWER); return }
                "settings" -> { openPanel(PanelIds.SETTINGS); return }
                "clear" -> { lines.clear(); return }
                "close", "exit" -> { closePanel(PanelIds.TERMINAL); return }
                "version" -> { print("IcHomeLauncher v${BuildConfig.VERSION_NAME}"); return }
                "open" -> { print("usage: open <app name>"); return }
            }
        }
        if (command == "open") {
            openByName(argument)
        } else {
            // コマンド名と一致しない入力はアプリ名として扱う（open の省略形）
            openByName(input)
        }
    }

    /** アプリ名での起動：完全一致 → 部分一致（1件なら起動、複数なら候補表示、0件ならエラー） */
    private fun openByName(query: String) {
        val all = apps()
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
        if (!launchApp(app)) print("failed to open: ${app.label}")
    }
}
