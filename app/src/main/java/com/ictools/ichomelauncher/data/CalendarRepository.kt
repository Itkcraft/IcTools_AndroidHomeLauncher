package com.ictools.ichomelauncher.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

/** 予定1件分（繰り返し予定は1回ごとに1件） */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String
) {
    /** 表示用の日付（終日予定は UTC 基準で保存されているため UTC で日付を取る） */
    fun dayKey(): Long {
        val tz = if (allDay) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
        val cal = Calendar.getInstance(tz).apply { timeInMillis = begin }
        // ローカル日付の 0 時に揃えて返す
        return Calendar.getInstance().apply {
            clear()
            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
}

/** 端末のカレンダーから予定を読む（読み取りのみ） */
class CalendarRepository(context: Context) {

    private val appContext = context.applicationContext
    private var observer: ContentObserver? = null

    /** 今日の 0 時から [days] 日分の予定を開始時刻順に取得する */
    suspend fun loadUpcoming(days: Int): List<CalendarEvent> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return loadRange(start, start + days * 24L * 60 * 60 * 1000)
    }

    /** [start]〜[end] に重なる予定を取得する（権限が無ければ空） */
    suspend fun loadRange(start: Long, end: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!Permissions.hasCalendar(appContext)) return@withContext emptyList()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION
        )
        val result = mutableListOf<CalendarEvent>()
        runCatching {
            appContext.contentResolver.query(
                uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    result += CalendarEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(no title)",
                        begin = c.getLong(2),
                        end = c.getLong(3),
                        allDay = c.getInt(4) != 0,
                        color = c.getInt(5),
                        location = c.getString(6).orEmpty()
                    )
                }
            }
        }
        // 終日予定は同じ日の時間指定予定より前に並べる
        result.sortedWith(compareBy<CalendarEvent>({ it.dayKey() }, { !it.allDay }, { it.begin }))
    }

    /** 予定の変更を監視する（権限が無い場合は登録しない） */
    fun observe(onChange: () -> Unit) {
        if (observer != null || !Permissions.hasCalendar(appContext)) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        runCatching {
            appContext.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, o)
            observer = o
        }
    }

    fun stopObserving() {
        observer?.let { appContext.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    /** カレンダーアプリで予定を開く Intent */
    fun viewIntent(event: CalendarEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
