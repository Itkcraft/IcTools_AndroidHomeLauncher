package com.ictools.ichomelauncher.gesture

/**
 * 検出するジェスチャーの種類。
 * [onBackground]：背景（とピンチは画面全体）で使う／[onPad]：タッチパッドパネルで使う
 */
enum class GestureType(val id: String, val label: String, val onBackground: Boolean, val onPad: Boolean) {
    TAP("tap", "Tap", onBackground = false, onPad = true),
    SWIPE_UP("swipe_up", "Swipe up", onBackground = true, onPad = true),
    SWIPE_DOWN("swipe_down", "Swipe down", onBackground = true, onPad = true),
    SWIPE_LEFT("swipe_left", "Swipe left", onBackground = true, onPad = true),
    SWIPE_RIGHT("swipe_right", "Swipe right", onBackground = true, onPad = true),
    DOUBLE_TAP("double_tap", "Double tap", onBackground = true, onPad = true),
    LONG_PRESS("long_press", "Long press", onBackground = true, onPad = true),
    PINCH_IN("pinch_in", "Pinch in", onBackground = true, onPad = false),
    PINCH_OUT("pinch_out", "Pinch out", onBackground = true, onPad = false);

    companion object {
        fun fromId(id: String): GestureType? = entries.firstOrNull { it.id == id }

        /** 背景ジェスチャーのデフォルトの割り当て（明記したもの以外は none） */
        fun defaultMapping(): Map<GestureType, GestureAction> = entries.filter { it.onBackground }.associateWith {
            when (it) {
                SWIPE_UP -> GestureAction.OPEN_TERMINAL
                PINCH_OUT -> GestureAction.CLOSE_FOCUSED
                LONG_PRESS -> GestureAction.OPEN_SETTINGS
                else -> GestureAction.NONE
            }
        }

        /** タッチパッドのデフォルトの割り当て */
        fun defaultPadMapping(): Map<GestureType, GestureAction> = entries.filter { it.onPad }.associateWith {
            when (it) {
                SWIPE_UP -> GestureAction.OPEN_DRAWER
                SWIPE_DOWN -> GestureAction.CLOSE_FOCUSED
                SWIPE_LEFT -> GestureAction.OPEN_FAVORITES
                SWIPE_RIGHT -> GestureAction.OPEN_MEDIA
                DOUBLE_TAP -> GestureAction.OPEN_TERMINAL
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
    OPEN_PAD("open_pad", "open pad"),
    MEDIA_PLAY_PAUSE("media_play_pause", "media play/pause"),
    MEDIA_NEXT("media_next", "media next"),
    MEDIA_PREVIOUS("media_previous", "media previous"),
    LIGHT_TOGGLE("light_toggle", "light toggle"),
    NEW_MEMO("new_memo", "new memo"),
    CLOSE_FOCUSED("close_focused", "close focused"),
    CLOSE_ALL("close_all", "close all");

    companion object {
        fun fromId(id: String): GestureAction? = entries.firstOrNull { it.id == id }
    }
}
