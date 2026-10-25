package com.ictools.ichomelauncher.data

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import kotlin.math.roundToInt

/** 音量の種類 */
enum class VolumeStream(val id: String, val label: String, val stream: Int) {
    MEDIA("media", "メディア", AudioManager.STREAM_MUSIC),
    RING("ring", "着信音", AudioManager.STREAM_RING),
    NOTIF("notif", "通知", AudioManager.STREAM_NOTIFICATION),
    ALARM("alarm", "アラーム", AudioManager.STREAM_ALARM);

    companion object {
        fun fromId(id: String): VolumeStream? = entries.firstOrNull { it.id == id.lowercase() }
    }
}

/** 音量・明るさのスナップショット */
data class SoundState(
    val volumes: Map<VolumeStream, Int> = emptyMap(),  // 0〜100
    val mediaMuted: Boolean = false,
    val brightness: Int = 50,                          // 0〜100
    val autoBrightness: Boolean = false,
    val canWriteSettings: Boolean = false              // 「システム設定の変更」が許可されているか
)

/**
 * 音量と画面の明るさの取得・変更。
 * 音量は権限不要（マナーモード中の着信・通知は変更できないことがある）。
 * 明るさの変更は「システム設定の変更」（WRITE_SETTINGS）の許可が必要。
 */
class SoundController(context: Context) {

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val resolver = appContext.contentResolver

    companion object {
        private const val BRIGHTNESS_MAX = 255
    }

    fun snapshot(): SoundState = SoundState(
        volumes = VolumeStream.entries.associateWith { getVolume(it) },
        mediaMuted = runCatching { audio.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(false),
        brightness = getBrightness(),
        autoBrightness = isAutoBrightness(),
        canWriteSettings = canWriteSettings()
    )

    // ---- 音量 ----

    fun getVolume(s: VolumeStream): Int = runCatching {
        val max = audio.getStreamMaxVolume(s.stream).coerceAtLeast(1)
        audio.getStreamVolume(s.stream) * 100 / max
    }.getOrDefault(0)

    /** 音量を 0〜100 で設定する。失敗したら理由を返す */
    fun setVolume(s: VolumeStream, percent: Int, showUi: Boolean = false): String? = runCatching {
        val max = audio.getStreamMaxVolume(s.stream)
        val index = (percent.coerceIn(0, 100) * max / 100f).roundToInt()
        audio.setStreamVolume(s.stream, index, if (showUi) AudioManager.FLAG_SHOW_UI else 0)
    }.exceptionOrNull()?.let { errorText(it) }

    /** 1 段階上げる／下げる */
    fun adjust(s: VolumeStream, up: Boolean): String? = runCatching {
        audio.adjustStreamVolume(s.stream, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }.exceptionOrNull()?.let { errorText(it) }

    /** メディアのミュートを切り替える */
    fun toggleMute(): String? = runCatching {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
    }.exceptionOrNull()?.let { errorText(it) }

    private fun errorText(e: Throwable): String =
        if (e is SecurityException) "マナーモード（おやすみ時間）中のため変更できません" else "変更できませんでした（${e.javaClass.simpleName}）"

    // ---- 明るさ ----

    fun canWriteSettings(): Boolean = Settings.System.canWrite(appContext)

    fun getBrightness(): Int = runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / BRIGHTNESS_MAX
    }.getOrDefault(50).coerceIn(0, 100)

    fun isAutoBrightness(): Boolean = runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    /** 明るさを 0〜100 で設定する（自動調整は OFF にする）。失敗したら理由を返す */
    fun setBrightness(percent: Int): String? {
        if (!canWriteSettings()) return NEED_PERMISSION
        return runCatching {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            // 0 だと真っ暗になる端末があるため最低 1
            val value = (percent.coerceIn(0, 100) * BRIGHTNESS_MAX / 100f).roundToInt().coerceAtLeast(1)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
        }.exceptionOrNull()?.let { "変更できませんでした（${it.javaClass.simpleName}）" }
    }

    /** 明るさの自動調整を切り替える。失敗したら理由を返す */
    fun setAutoBrightness(auto: Boolean): String? {
        if (!canWriteSettings()) return NEED_PERMISSION
        return runCatching {
            Settings.System.putInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        }.exceptionOrNull()?.let { "変更できませんでした（${it.javaClass.simpleName}）" }
    }

    /** 「システム設定の変更」の許可画面 */
    fun writeSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", appContext.packageName, null))
}

/** 明るさの変更に権限が無いときのメッセージ */
const val NEED_PERMISSION = "明るさの変更には「システム設定の変更」の許可が必要です"
