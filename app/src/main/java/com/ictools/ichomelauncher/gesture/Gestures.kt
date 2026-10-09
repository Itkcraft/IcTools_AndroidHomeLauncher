package com.ictools.ichomelauncher.gesture

/** 検出するジェスチャーの種類 */
enum class GestureType(val id: String, val label: String) {
    SWIPE_UP("swipe_up", "Swipe up"),
    SWIPE_DOWN("swipe_down", "Swipe down"),
    SWIPE_LEFT("swipe_left", "Swipe left"),
    SWIPE_RIGHT("swipe_right", "Swipe right"),
    DOUBLE_TAP("double_tap", "Double tap"),
    LONG_PRESS("long_press", "Long press"),
    PINCH_IN("pinch_in", "Pinch in"),
    PINCH_OUT("pinch_out", "Pinch out");

    companion object {
        fun fromId(id: String): GestureType? = entries.firstOrNull { it.id == id }

        /** デフォルトの割り当て（明記したもの以外は none） */
        fun defaultMapping(): Map<GestureType, GestureAction> = entries.associateWith {
            when (it) {
                SWIPE_UP -> GestureAction.OPEN_TERMINAL
                PINCH_OUT -> GestureAction.CLOSE_FOCUSED
                LONG_PRESS -> GestureAction.OPEN_SETTINGS
                else -> GestureAction.NONE
            }
        }
    }
}

/** ジェスチャーに割り当てられるアクション */
enum class GestureAction(val id: String, val label: String) {
    NONE("none", "none"),
    OPEN_TERMINAL("open_terminal", "open terminal"),
    OPEN_DRAWER("open_drawer", "open drawer"),
    OPEN_SETTINGS("open_settings", "open settings"),
    OPEN_MEDIA("open_media", "open media"),
    OPEN_SCHEDULE("open_schedule", "open schedule"),
    OPEN_HISTORY("open_history", "open history"),
    OPEN_CLOCK("open_clock", "open clock"),
    OPEN_CALENDAR("open_calendar", "open calendar"),
    OPEN_STATUS("open_status", "open status"),
    OPEN_NETWORK("open_network", "open network"),
    OPEN_FAVORITES("open_favorites", "open favorites"),
    NEW_MEMO("new_memo", "new memo"),
    CLOSE_FOCUSED("close_focused", "close focused"),
    CLOSE_ALL("close_all", "close all");

    companion object {
        fun fromId(id: String): GestureAction? = entries.firstOrNull { it.id == id }
    }
}
