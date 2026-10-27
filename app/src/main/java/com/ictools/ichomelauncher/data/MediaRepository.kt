package com.ictools.ichomelauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** キューの1曲分 */
@Serializable
data class QueueEntry(val id: Long, val title: String, val subtitle: String)

/** 再生アプリが提供するキュー（プレイリスト） */
@Serializable
data class QueueInfo(
    val title: String = "",
    val items: List<QueueEntry> = emptyList(),
    val activeId: Long = -1
) {
    /** 再生中の曲の次の曲 */
    val next: QueueEntry?
        get() {
            val i = items.indexOfFirst { it.id == activeId }
            return if (i >= 0) items.getOrNull(i + 1) else null
        }
}

/** 再生中（または最後に再生した）メディアの表示用情報 */
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
    val speed: Float,
    val active: Boolean,        // メディアセッションが存在するか（false は保存された前回の情報）
    val queue: QueueInfo?       // キュー（アプリが提供しない場合は null）
) {
    /** 現在の再生位置（経過時間から推定） */
    fun currentPosition(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying) return positionMs
        val p = positionMs + ((nowElapsed - positionUpdatedAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** 保存する前回のメディア情報（アートワークは別ファイル） */
@Serializable
private data class SavedMedia(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val positionMs: Long,
    val queue: QueueInfo? = null
)

/**
 * 再生中のメディアセッションを監視し、操作する。
 * 「通知へのアクセス」が許可されている場合のみセッションを取得できる。
 * 最後に表示した曲情報は保存し、再生停止後やアプリ再起動後も表示する。
 */
class MediaRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }
    private val keySaved = stringPreferencesKey("last_media")
    private val artFile = File(appContext.filesDir, "last_media_art.png")

    private val _media = MutableStateFlow<MediaInfo?>(null)

    /** 表示対象のメディア（一度も再生していなければ null） */
    val media: StateFlow<MediaInfo?> = _media.asStateFlow()

    private var started = false
    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private var saveJob: Job? = null
    private var savedArtKey: String? = null

    // セッションの増減（アプリが再生を始めた／終えた）
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list.orEmpty())
    }

    // 各セッションの再生状態・曲情報・キューの変化
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = selectAndUpdate()
        override fun onMetadataChanged(metadata: MediaMetadata?) = selectAndUpdate()
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = selectAndUpdate()
        override fun onQueueTitleChanged(title: CharSequence?) = selectAndUpdate()
        override fun onSessionDestroyed() = refreshSessions()
    }

    init {
        // 前回の曲情報を読み込んでおく（セッションが無くても表示できるように）
        scope.launch {
            val raw = appContext.ihlDataStore.data.first()[keySaved] ?: return@launch
            val saved = runCatching { json.decodeFromString<SavedMedia>(raw) }.getOrNull() ?: return@launch
            val art = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(artFile.path)?.asImageBitmap() }.getOrNull()
            }
            if (_media.value == null) {
                _media.value = MediaInfo(
                    packageName = saved.packageName, appLabel = saved.appLabel,
                    title = saved.title, artist = saved.artist, art = art,
                    isPlaying = false, durationMs = saved.durationMs, positionMs = saved.positionMs,
                    positionUpdatedAt = SystemClock.elapsedRealtime(), speed = 1f,
                    active = false, queue = saved.queue
                )
            }
        }
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
        markInactive()
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
        val c = current
        if (c == null) {
            markInactive()
            return
        }
        val info = toInfo(c)
        // 曲情報が空のセッション（再生前など）は前回の表示を残す
        val prev = _media.value
        if (info.title.isEmpty() && info.artist.isEmpty() && prev != null) {
            _media.value = if (prev.packageName == info.packageName) {
                prev.copy(isPlaying = info.isPlaying, active = true)
            } else {
                prev.copy(isPlaying = false, active = false)
            }
            return
        }
        _media.value = info
        scheduleSave(info, c)
    }

    /** セッションが無くなったら、最後の情報を「停止中」として残す */
    private fun markInactive() {
        _media.value = _media.value?.let {
            it.copy(isPlaying = false, positionMs = it.currentPosition(), positionUpdatedAt = SystemClock.elapsedRealtime(), active = false)
        }
    }

    private fun toInfo(c: MediaController): MediaInfo {
        val meta = c.metadata
        val state = c.playbackState
        val art: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val queue = runCatching { c.queue }.getOrNull()?.let { items ->
            QueueInfo(
                title = c.queueTitle?.toString().orEmpty(),
                items = items.map {
                    QueueEntry(it.queueId, it.description.title?.toString().orEmpty(), it.description.subtitle?.toString().orEmpty())
                },
                activeId = state?.activeQueueItemId ?: -1
            )
        }
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
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            active = true,
            queue = queue
        )
    }

    /** 最後の曲情報を保存する（頻繁な更新をまとめるため少し待つ） */
    private fun scheduleSave(info: MediaInfo, c: MediaController) {
        val meta = c.metadata
        val bitmap = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1000)
            val saved = SavedMedia(
                info.packageName, info.appLabel, info.title, info.artist,
                info.durationMs, info.currentPosition(), info.queue?.let { q -> q.copy(items = q.items.take(100)) }
            )
            appContext.ihlDataStore.edit { it[keySaved] = json.encodeToString(saved) }
            // アートワークは曲が変わったときだけ書き出す
            val artKey = "${info.packageName}|${info.title}|${info.artist}"
            if (artKey != savedArtKey) {
                savedArtKey = artKey
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (bitmap != null && !bitmap.isRecycled) {
                            artFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        } else {
                            artFile.delete()
                        }
                    }
                }
            }
        }
    }

    private fun labelOf(packageName: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(packageName)

    // ---- 操作 ----

    /**
     * セッションが無い場合はメディアボタンをシステムへ送る。
     * OS が最後に再生していたアプリへ届ける（再開に対応していないアプリは反応しない）。
     */
    private fun sendMediaKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    fun playPause() {
        val c = current
        if (c == null) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return
        }
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        current?.transportControls?.skipToNext() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous() {
        current?.transportControls?.skipToPrevious() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun seekTo(positionMs: Long) = current?.transportControls?.seekTo(positionMs)

    /** キューの曲へ移動する（セッションがある場合のみ） */
    fun skipToQueueItem(id: Long) = current?.transportControls?.skipToQueueItem(id)

    /** 再生中（または最後に再生した）アプリの画面を開く。開けたら true */
    fun openPlayer(): Boolean {
        current?.sessionActivity?.let { pi ->
            if (runCatching { PendingIntents.send(appContext, pi) }.isSuccess) return true
        }
        val pkg = current?.packageName ?: _media.value?.packageName ?: return false
        val launch = appContext.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching {
            appContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
