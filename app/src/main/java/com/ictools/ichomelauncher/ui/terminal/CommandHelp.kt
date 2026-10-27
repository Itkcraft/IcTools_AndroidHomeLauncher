package com.ictools.ichomelauncher.ui.terminal

/**
 * コマンド1件分の説明。
 * [names] は help <コマンド名> で引く名前、[insert] はヘルプパネルでタップしたときにターミナルへ入れる文字列。
 */
data class HelpEntry(
    val usage: String,
    val description: String,
    val names: List<String>,
    val insert: String,
    val example: String? = null
)

/** ヘルプの1ページ（カテゴリ） */
data class HelpSection(val title: String, val entries: List<HelpEntry>)

/** ターミナルの help（ページ表示）とヘルプパネルで共通に使うコマンド一覧 */
object CommandHelp {

    private fun e(usage: String, description: String, vararg names: String, insert: String = names.first(), example: String? = null) =
        HelpEntry(usage, description, names.toList(), insert, example)

    val SECTIONS = listOf(
        HelpSection(
            "apps",
            listOf(
                e("<app>", "アプリを起動（名前の一部でも可）", "app", insert = "", example = "chrome"),
                e("open -a <app>", "アプリとして起動", "open", insert = "open -a "),
                e("new", "新しくインストールされたアプリ", "new"),
                e("fav", "お気に入りの一覧", "fav"),
                e("fav add <app>", "お気に入りに追加", "fav", insert = "fav add "),
                e("fav rm <app>", "お気に入りから外す", "fav", insert = "fav rm "),
                e("uninstall <app>", "アンインストール（OS の確認あり）", "uninstall", insert = "uninstall "),
                e("info <app>", "アプリ情報を開く", "info", insert = "info "),
                e("alias", "短縮コマンドの一覧", "alias"),
                e("alias <name>=<text>", "短縮コマンドを登録（1語目を置き換え。名前・中身とも日本語可）", "alias", insert = "alias ", example = "alias dc=Discord / alias 地図=マップ"),
                e("unalias <name>", "短縮コマンドを削除", "unalias", insert = "unalias ")
            )
        ),
        HelpSection(
            "panels",
            listOf(
                e("apps | drawer", "アプリ一覧", "apps", "drawer"),
                e("favs | favorites", "お気に入り", "favs", "favorites"),
                e("media", "メディア", "media"),
                e("sound", "音量・明るさ", "sound"),
                e("schedule | cal", "予定", "schedule", "cal"),
                e("calendar", "カレンダー（月表示）", "calendar"),
                e("clock", "時計", "clock"),
                e("status", "稼働状況", "status"),
                e("net | network", "通信", "net", "network"),
                e("history", "履歴", "history"),
                e("pad", "タッチパッド", "pad"),
                e("?", "ヘルプパネル", "?"),
                e("settings", "設定", "settings")
            )
        ),
        HelpSection(
            "tools",
            listOf(
                e("calc <expr>", "電卓（+ - * / % ^ () sqrt sin cos tan log ln pi）", "calc", insert = "calc ", example = "calc 12*8"),
                e("g [words]", "Google で検索（ブラウザ）。語が無ければ Google を開く", "g", insert = "g "),
                e("br [url|words]", "Brave で開く／検索。無ければ Brave を起動", "br", insert = "br "),
                e("timer <time>", "タイマー（時計アプリ）", "timer", insert = "timer ", example = "timer 3m / timer 1h30m / timer 90s"),
                e("alarm <HH:MM> [label]", "アラーム（時計アプリ）", "alarm", insert = "alarm ", example = "alarm 7:00 起床"),
                e("vol", "音量の一覧", "vol"),
                e("vol <0-100|up|down|mute>", "メディアの音量", "vol", insert = "vol "),
                e("vol <ring|notif|alarm> <0-100>", "着信・通知・アラームの音量", "vol", insert = "vol ring "),
                e("bright [0-100|auto]", "画面の明るさ", "bright", insert = "bright "),
                e("light [on|off]", "ライト", "light"),
                e("camera", "カメラ", "camera"),
                e("assist", "アシスタント（Gemini）を起動", "assist"),
                e("assist list", "起動方法の一覧（* が現在の設定）", "assist", insert = "assist list"),
                e("assist <method>", "その方法で試す（auto voice voiceassist assist app）", "assist", insert = "assist ", example = "assist voice"),
                e("assist set <method>", "起動方法を保存", "assist", insert = "assist set ")
            )
        ),
        HelpSection(
            "files",
            listOf(
                e("pwd", "現在のディレクトリ", "pwd"),
                e("ls [-a] [path]", "ファイル一覧", "ls"),
                e("cd [path]", "移動（~ は内部ストレージ直下）", "cd", insert = "cd "),
                e("find <name>", "名前でファイルを探す（現在のディレクトリ以下）", "find", insert = "find "),
                e("open <file>", "ファイルを開く", "open", insert = "open "),
                e("mkdir <path>", "ディレクトリ作成", "mkdir", insert = "mkdir "),
                e("cp <src> <dst>", "コピー", "cp", insert = "cp "),
                e("mv <src> <dst>", "移動・名前変更", "mv", insert = "mv "),
                e("rm <path>", "削除（y/N の確認あり）", "rm", insert = "rm ")
            )
        ),
        HelpSection(
            "memo",
            listOf(
                e("memo [text]", "新しいメモ", "memo", insert = "memo "),
                e("memos", "メモの一覧", "memos"),
                e("memos <n>", "n 番のメモを開く", "memos", insert = "memos "),
                e("memos rm <n>", "n 番のメモを削除", "memos", insert = "memos rm ")
            )
        ),
        HelpSection(
            "terminal",
            listOf(
                e("help [page|command]", "ヘルプ（ページ番号かコマンド名）", "help", insert = "help "),
                e("clear", "ログを消去", "clear"),
                e("close | exit", "ターミナルを閉じる", "close", "exit"),
                e("version", "バージョン", "version"),
                e("→ / Tab", "グレーの補完候補を確定", "tab", insert = "")
            )
        )
    )

    /** コマンド名で説明を探す */
    fun find(name: String): List<HelpEntry> {
        val n = name.lowercase()
        return SECTIONS.flatMap { it.entries }.filter { n in it.names }
    }
}
