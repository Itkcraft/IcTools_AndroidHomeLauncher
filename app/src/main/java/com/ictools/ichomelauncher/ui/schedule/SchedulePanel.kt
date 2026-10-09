package com.ictools.ichomelauncher.ui.schedule

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.data.CalendarEvent
import com.ictools.ichomelauncher.ui.common.PermissionPrompt
import com.ictools.ichomelauncher.ui.theme.IhlColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** スケジュールパネルの中身：今日から指定日数分の予定を日付ごとに表示 */
@Composable
fun SchedulePanel(
    events: List<CalendarEvent>,
    days: Int,
    hasAccess: Boolean,
    onPermissionResult: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onDayChanged: () -> Unit
) {
    if (!hasAccess) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionResult() }
        PermissionPrompt(
            message = "予定を表示するにはカレンダーの読み取り許可が必要です",
            buttons = listOf(
                "許可する" to { launcher.launch(Manifest.permission.READ_CALENDAR) },
                "アプリ情報を開く" to onOpenAppInfo
            ),
            note = "「許可する」で何も出ない場合は、アプリ情報 → 権限 → カレンダー から許可してください。"
        )
        return
    }

    // 1 分ごとに現在時刻を更新（終わった予定を薄く表示する・日付が変わったら読み直す）
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        var today = startOfDay(now)
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
            val newToday = startOfDay(now)
            if (newToday != today) {
                today = newToday
                onDayChanged()
            }
        }
    }

    val grouped = remember(events) { events.groupBy { it.dayKey() } }
    Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 6.dp, end = 10.dp)) {
        Text("next $days day${if (days > 1) "s" else ""}", color = IhlColors.TextDim)
        Spacer(Modifier.height(4.dp))
        if (events.isEmpty()) {
            Text("no events", color = IhlColors.TextDim)
            return@Column
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            grouped.forEach { (day, dayEvents) ->
                item(key = "day-$day") { DayHeader(day, startOfDay(now)) }
                items(dayEvents, key = { "${it.eventId}-${it.begin}" }) { event ->
                    EventRow(event, ended = !event.allDay && event.end < now) { onOpenEvent(event) }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun DayHeader(day: Long, today: Long) {
    val diff = ((day - today) / (24L * 60 * 60 * 1000)).toInt()
    val suffix = when (diff) {
        0 -> "  today"
        1 -> "  tomorrow"
        else -> ""
    }
    val label = SimpleDateFormat("MM/dd (E)", Locale.getDefault()).format(Date(day))
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Text("$label$suffix", color = IhlColors.Accent)
        Box(Modifier.fillMaxWidth().height(1.dp).background(IhlColors.Line))
    }
}

@Composable
private fun EventRow(event: CalendarEvent, ended: Boolean, onClick: () -> Unit) {
    val time = if (event.allDay) {
        "all day    "
    } else {
        val f = SimpleDateFormat("HH:mm", Locale.getDefault())
        "${f.format(Date(event.begin))}-${f.format(Date(event.end))}"
    }
    val textColor = if (ended) IhlColors.TextDim else IhlColors.Text
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // カレンダーの色を細い縦線で示す
        Box(Modifier.width(2.dp).height(30.dp).background(Color(event.color).copy(alpha = if (ended) 0.4f else 1f)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(event.title, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (event.location.isNotBlank()) "$time  ${event.location}" else time
            Text(sub, color = IhlColors.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
