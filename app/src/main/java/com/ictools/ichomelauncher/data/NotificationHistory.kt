package com.ictools.ichomelauncher.data

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/** 通知履歴1件分 */
@Serializable
data class NotificationRecord(
    val id: Long,            // 履歴内の通し番号
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val time: Long           // 通知の投稿時刻（ミリ秒）
)

/**
 * 通知履歴の保持・保存。
 * 通知リスナー（サービス）と画面（ViewModel）の両方から使うため、プロセス内で1つだけ持つ。
 */
class NotificationHistory private constructor(context: Context) {

    companion object {
        const val MAX_RECORDS = 200

        @Volatile
        private var instance: NotificationHistory? = null

        fun get(context: Context): NotificationHistory =
            instance ?: synchronized(this) {
                instance ?: NotificationHistory(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("notification_history")
    private val saveMutex = Mutex()
    // 保存済みの履歴と番号が重ならないよう時刻を初期値にする
    private val nextId = AtomicLong(System.currentTimeMillis())

    // 通知を開くための PendingIntent（保存はできないのでプロセス生存中のみ）
    private val contentIntents = ConcurrentHashMap<Long, PendingIntent>()

    private val _records = MutableStateFlow<List<NotificationRecord>>(emptyList())

    /** 新しい順の通知履歴 */
    val records: StateFlow<List<NotificationRecord>> = _records.asStateFlow()

    init {
        scope.launch {
            val raw = appContext.ihlDataStore.data.first()[key]
            val saved = raw?.let { runCatching { json.decodeFromString<List<NotificationRecord>>(it) }.getOrNull() }.orEmpty()
            // 読み込み中に届いた通知と合わせる
            _records.update { (it + saved).distinctBy { r -> r.id }.sortedByDescending { r -> r.time }.take(MAX_RECORDS) }
            nextId.updateAndGet { max(it, (_records.value.maxOfOrNull { r -> r.id } ?: 0L) + 1) }
        }
    }

    /** 通知を記録する（常駐通知・グループのまとめ通知・自分自身・本文の無いものは除外） */
    fun record(sbn: StatusBarNotification) {
        if (sbn.packageName == appContext.packageName) return
        val n = sbn.notification
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty().trim()
        if (title.isEmpty() && text.isEmpty()) return

        // 同じアプリから同じ内容が続いた場合（更新通知など）は記録しない
        val latest = _records.value.firstOrNull { it.packageName == sbn.packageName }
        if (latest != null && latest.title == title && latest.text == text) return

        val record = NotificationRecord(
            id = nextId.getAndIncrement(),
            packageName = sbn.packageName,
            appLabel = labelOf(sbn.packageName),
            title = title,
            text = text,
            time = sbn.postTime
        )
        n.contentIntent?.let { contentIntents[record.id] = it }
        _records.update { (listOf(record) + it).take(MAX_RECORDS) }
        persist()
    }

    /** 通知を開いたときに使う PendingIntent（無ければ null） */
    fun contentIntentOf(record: NotificationRecord): PendingIntent? = contentIntents[record.id]

    fun clear() {
        contentIntents.clear()
        _records.value = emptyList()
        persist()
    }

    private fun labelOf(packageName: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(packageName)

    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                val raw = json.encodeToString(_records.value)
                appContext.ihlDataStore.edit { it[key] = raw }
            }
        }
    }
}
