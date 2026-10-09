package com.ictools.ichomelauncher.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 再生中メディアの表示用情報 */
data class MediaInfo(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val isPlaying: Boolean,
    val durationMs: Long,       // 不明なら 0
    val positionMs: Long,       // positionUpdatedAt 時点の再生位置
    val positionUpdatedAt: Long,// SystemClock.elapsedRealtime 基準
    val speed: Float
) {
    /** 現在の再生位置（経過時間から推定） */
    fun currentPosition(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying) return positionMs
        val p = positionMs + ((nowElapsed - positionUpdatedAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * 再生中のメディアセッションを監視し、操作する。
 * 「通知へのアクセス」が許可されている場合のみ動作する。
 */
class MediaRepository(context: Context) {

    private val appContext = context.applicationContext
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _media = MutableStateFlow<MediaInfo?>(null)

    /** 表示対象のメディア（無ければ null） */
    val media: StateFlow<MediaInfo?> = _media.asStateFlow()

    private var started = false
    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null

    // セッションの増減（アプリが再生を始めた／終えた）
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list.orEmpty())
    }

    // 各セッションの再生状態・曲情報の変化
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = selectAndUpdate()
        override fun onMetadataChanged(metadata: MediaMetadata?) = selectAndUpdate()
        override fun onSessionDestroyed() = refreshSessions()
    }

    /** 監視を開始する（権限が無ければ何もしない。許可後に再度呼べばよい） */
    fun start() {
        if (started || !Permissions.hasNotificationAccess(appContext)) return
        runCatching {
            val component = Permissions.listenerComponent(appContext)
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            started = true
            setControllers(sessionManager.getActiveSessions(component))
        }.onFailure { started = false }
    }

    fun stop() {
        if (!started) return
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        controllers = emptyList()
        current = null
        started = false
        _media.value = null
    }

    private fun refreshSessions() {
        if (!started) return
        runCatching { setControllers(sessionManager.getActiveSessions(Permissions.listenerComponent(appContext))) }
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        controllers = list
        list.forEach { it.registerCallback(controllerCallback, handler) }
        selectAndUpdate()
    }

    /** 再生中のセッションを優先し、無ければ最近使われたセッションを表示対象にする */
    private fun selectAndUpdate() {
        val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        current = playing ?: controllers.firstOrNull()
        _media.value = current?.let { toInfo(it) }
    }

    private fun toInfo(c: MediaController): MediaInfo {
        val meta = c.metadata
        val state = c.playbackState
        val art: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        return MediaInfo(
            packageName = c.packageName,
            appLabel = labelOf(c.packageName),
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            art = art?.let { runCatching { it.asImageBitmap() }.getOrNull() },
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            positionMs = state?.position?.coerceAtLeast(0) ?: 0,
            positionUpdatedAt = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f
        )
    }

    private fun labelOf(packageName: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(packageName)

    // ---- 操作 ----

    fun playPause() {
        val c = current ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = current?.transportControls?.skipToNext()
    fun previous() = current?.transportControls?.skipToPrevious()

    fun seekTo(positionMs: Long) = current?.transportControls?.seekTo(positionMs)

    /** 再生中アプリの画面を開く。開けたら true */
    fun openPlayer(): Boolean {
        val c = current ?: return false
        c.sessionActivity?.let { pi ->
            if (runCatching { PendingIntents.send(appContext, pi) }.isSuccess) return true
        }
        val launch = appContext.packageManager.getLaunchIntentForPackage(c.packageName) ?: return false
        return runCatching {
            appContext.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
