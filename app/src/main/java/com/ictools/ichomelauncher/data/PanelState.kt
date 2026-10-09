package com.ictools.ichomelauncher.data

import kotlinx.serialization.Serializable

/** パネル種別ID（v0.1.0 では各種別 1 枚ずつ） */
object PanelIds {
    const val TERMINAL = "terminal"
    const val DRAWER = "drawer"
    const val SETTINGS = "settings"

    val ALL = listOf(TERMINAL, DRAWER, SETTINGS)

    /** タイトルバーに表示するパネル名 */
    fun title(id: String): String = when (id) {
        TERMINAL -> "Terminal"
        DRAWER -> "Apps"
        SETTINGS -> "Settings"
        else -> id
    }
}

// パネル1枚分の保存データ
@Serializable
data class PanelState(
    val id: String,        // パネル種別ID（terminal / drawer / settings）
    val xDp: Float,        // 左上X座標（dp）
    val yDp: Float,        // 左上Y座標（dp）
    val widthDp: Float,    // 幅（dp）
    val heightDp: Float,   // 高さ（dp）
    val isOpen: Boolean,   // 開いているか
    val zOrder: Int        // 重なり順（大きいほど前面）
)
