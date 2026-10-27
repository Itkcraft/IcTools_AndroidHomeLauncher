package com.ictools.ichomelauncher.ui.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.data.CalendarEvent
import com.ictools.ichomelauncher.ui.common.WireButton
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.SmallCutShape
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * カレンダーパネル：月表示。今日を枠で強調し、予定のある日に点を付ける。
 * 日付をタップするとその日の予定を下に表示する（カレンダー権限がある場合）。
 */
@Composable
fun CalendarPanel(
    hasAccess: Boolean,
    loadMonth: suspend (start: Long, end: Long) -> List<CalendarEvent>,
    eventsVersion: Int,
    onOpenEvent: (CalendarEvent) -> Unit,
    onPermissionResult: () -> Unit,
    openAppInfo: () -> Unit
) {
    // 表示中の月（今月からの差）
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by rememberSaveable { mutableLongStateOf(-1L) }

    // 日付が変わったら今日の枠を移すため、1 分ごとに今日を確認
    var today by remember { mutableLongStateOf(startOfDay(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            today = startOfDay(System.currentTimeMillis())
        }
    }

    val monthStart = remember(monthOffset, today) {
        Calendar.getInstance().apply {
            timeInMillis = today
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
    }
    val monthEnd = remember(monthStart) { (monthStart.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }

    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    LaunchedEffect(monthStart.timeInMillis, hasAccess, eventsVersion) {
        events = if (hasAccess) loadMonth(monthStart.timeInMillis, monthEnd.timeInMillis) else emptyList()
    }
    val eventsByDay = remember(events) { events.groupBy { it.dayKey() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 20.dp)) {
        // ---- 年月と前後の月 ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("<", color = IhlColors.Accent, modifier = Modifier.clickable { monthOffset-- }.padding(8.dp))
            Text(
                SimpleDateFormat("yyyy/MM", Locale.getDefault()).format(monthStart.time),
                color = IhlColors.Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clickable { monthOffset = 0; selectedDay = today }
            )
            Text(">", color = IhlColors.Accent, modifier = Modifier.clickable { monthOffset++ }.padding(8.dp))
        }
        // ---- 曜日の見出し ----
        val firstDow = monthStart.firstDayOfWeek
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val dow = (firstDow - 1 + i) % 7 + 1
                val label = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, dow) }
                    .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).orEmpty()
                Text(
                    label.take(2),
                    color = when (dow) {
                        Calendar.SUNDAY -> Color(0xFFE08080)
                        Calendar.SATURDAY -> Color(0xFF80A0E0)
                        else -> IhlColors.TextDim
                    },
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // ---- 日付のマス ----
        val lead = (monthStart.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
        val daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = lead + daysInMonth
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val dayNum = index - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1.15f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..daysInMonth) {
                            val dayStart = (monthStart.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, dayNum) }.timeInMillis
                            DayCell(
                                day = dayNum,
                                isToday = dayStart == today,
                                isSelected = dayStart == selectedDay,
                                hasEvents = eventsByDay.containsKey(dayStart),
                                onClick = { selectedDay = dayStart }
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(IhlColors.Line))
        // ---- 予定 ----
        if (!hasAccess) {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionResult() }
            Text("予定を表示するにはカレンダーの読み取り許可が必要です", color = IhlColors.TextDim, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row {
                WireButton("許可する", onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) })
                Spacer(Modifier.width(6.dp))
                WireButton("アプリ情報", onClick = openAppInfo)
            }
        } else if (selectedDay >= 0) {
            Text(SimpleDateFormat("MM/dd (E)", Locale.getDefault()).format(Date(selectedDay)), color = IhlColors.Accent)
            val dayEvents = eventsByDay[selectedDay].orEmpty()
            if (dayEvents.isEmpty()) Text("no events", color = IhlColors.TextDim)
            dayEvents.forEach { e -> EventLine(e) { onOpenEvent(e) } }
        } else {
            Text("tap a day to see events", color = IhlColors.TextDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DayCell(day: Int, isToday: Boolean, isSelected: Boolean, hasEvents: Boolean, onClick: () -> Unit) {
    val wire = IhlColors.BorderFocused
    Box(
        Modifier
            .fillMaxSize()
            .padding(1.dp)
            .then(if (isToday) Modifier.border(1.dp, wire, SmallCutShape) else Modifier)
            .background(if (isSelected) IhlColors.Line else Color.Transparent, SmallCutShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("$day", color = if (isToday) IhlColors.Accent else IhlColors.Text, fontSize = 12.sp)
        if (hasEvents) {
            Canvas(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(4.dp)) {
                drawCircle(wire)
            }
        }
    }
}

@Composable
private fun EventLine(event: CalendarEvent, onClick: () -> Unit) {
    val time = if (event.allDay) "all day" else {
        val f = SimpleDateFormat("HH:mm", Locale.getDefault())
        "${f.format(Date(event.begin))}-${f.format(Date(event.end))}"
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(26.dp).background(Color(event.color)))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time, color = IhlColors.TextDim, fontSize = 11.sp)
        }
    }
}

private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
