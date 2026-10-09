package com.ictools.ichomelauncher.data

import kotlinx.serialization.Serializable

/**
 * パネルID。
 * 単一パネル（terminal など）は種別IDがそのままパネルID、
 * 複数開けるメモは "memo:<メモID>" の形にする。
 */
object PanelIds {
    const val TERMINAL = "terminal"
    const val DRAWER = "drawer"
    const val SETTINGS = "settings"
    const val MEDIA = "media"
    const val SCHEDULE = "schedule"
    const val HISTORY = "history"

    /** メモパネルのID接頭辞 */
    const val MEMO_PREFIX = "memo:"

    /** 1 つずつしか開かないパネル */
    val SINGLETONS = listOf(TERMINAL, DRAWER, SETTINGS, MEDIA, SCHEDULE, HISTORY)

    fun isMemo(id: String): Boolean = id.startsWith(MEMO_PREFIX)
    fun memoPanelId(memoId: String): String = MEMO_PREFIX + memoId
    fun memoIdOf(panelId: String): String = panelId.removePrefix(MEMO_PREFIX)

    /** タイトルバーに表示するパネル名（メモは呼び出し側で内容から付ける） */
    fun title(id: String): String = when (id) {
        TERMINAL -> "Terminal"
        DRAWER -> "Apps"
        SETTINGS -> "Settings"
        MEDIA -> "Media"
        SCHEDULE -> "Schedule"
        HISTORY -> "History"
        else -> if (isMemo(id)) "Memo" else id
    }
}

// パネル1枚分の保存データ
@Serializable
data class PanelState(
    val id: String,        // パネルID（terminal / drawer / settings / media / schedule / history / memo:<ID>）
    val xDp: Float,        // 左上X座標（dp）
    val yDp: Float,        // 左上Y座標（dp）
    val widthDp: Float,    // 幅（dp）
    val heightDp: Float,   // 高さ（dp）
    val isOpen: Boolean,   // 開いているか
    val zOrder: Int        // 重なり順（大きいほど前面）
)
