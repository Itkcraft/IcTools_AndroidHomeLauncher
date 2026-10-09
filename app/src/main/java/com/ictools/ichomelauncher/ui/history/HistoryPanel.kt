package com.ictools.ichomelauncher.ui.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.data.LaunchRecord
import com.ictools.ichomelauncher.data.NotificationRecord
import com.ictools.ichomelauncher.ui.common.shortTime
import com.ictools.ichomelauncher.ui.media.NotificationAccessPrompt
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.SmallCutShape
import kotlinx.coroutines.delay

/** ヒストリーパネルのタブ */
private enum class HistoryTab(val label: String) { APPS("Apps"), COMMANDS("Cmds"), NOTIFICATIONS("Notif") }

/** ヒストリーパネルで表示するデータ */
class HistoryData(
    val launches: List<LaunchRecord>,
    val apps: List<AppEntry>,
    val commands: List<String>,
    val notifications: List<NotificationRecord>,
    val hasNotificationAccess: Boolean
)

/** ヒストリーパネルから呼ぶ操作 */
class HistoryActions(
    val launchApp: (AppEntry) -> Unit,
    val loadIcon: suspend (AppEntry, Int) -> ImageBitmap?,
    val runCommand: (String) -> Unit,
    val openNotification: (NotificationRecord) -> Unit,
    val clearLaunches: () -> Unit,
    val clearCommands: () -> Unit,
    val clearNotifications: () -> Unit,
    val requestNotificationAccess: () -> Unit,
    val openAppInfo: () -> Unit
)

/** ヒストリーパネルの中身：アプリ起動／コマンド／通知の履歴をタブで切り替え */
@Composable
fun HistoryPanel(data: HistoryData, actions: HistoryActions) {
    var tab by rememberSaveable { mutableStateOf(HistoryTab.APPS) }

    Column(Modifier.fillMaxSize().padding(start = 8.dp, top = 4.dp, end = 8.dp)) {
        // ---- タブ行と消去ボタン ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HistoryTab.entries.forEach { t ->
                val selected = t == tab
                Text(
                    if (selected) "[${t.label}]" else " ${t.label} ",
                    color = if (selected) IhlColors.Accent else IhlColors.TextDim,
                    modifier = Modifier.clickable { tab = t }.padding(horizontal = 2.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            ClearButton {
                when (tab) {
                    HistoryTab.APPS -> actions.clearLaunches()
                    HistoryTab.COMMANDS -> actions.clearCommands()
                    HistoryTab.NOTIFICATIONS -> actions.clearNotifications()
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(IhlColors.Line))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                HistoryTab.APPS -> AppsTab(data, actions)
                HistoryTab.COMMANDS -> CommandsTab(data.commands, actions.runCommand)
                HistoryTab.NOTIFICATIONS ->
                    if (data.hasNotificationAccess) NotificationsTab(data.notifications, actions.openNotification)
                    else NotificationAccessPrompt(actions.requestNotificationAccess, actions.openAppInfo)
            }
        }
    }
}

/** 消去ボタン（2回押しで確定） */
@Composable
private fun ClearButton(onClear: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(confirm) {
        if (confirm) {
            delay(3000)
            confirm = false
        }
    }
    Text(
        if (confirm) "clear?" else "clear",
        color = if (confirm) IhlColors.Accent else IhlColors.TextDim,
        modifier = Modifier
            .clickable {
                if (confirm) {
                    onClear()
                    confirm = false
                } else {
                    confirm = true
                }
            }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = IhlColors.TextDim, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AppsTab(data: HistoryData, actions: HistoryActions) {
    if (data.launches.isEmpty()) {
        EmptyText("no launches yet")
        return
    }
    val byKey = remember(data.apps) { data.apps.associateBy { it.key } }
    LazyColumn(Modifier.fillMaxSize()) {
        items(data.launches, key = { it.appKey }) { record ->
            val app = byKey[record.appKey]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = app != null) { app?.let(actions.launchApp) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(app, actions.loadIcon)
                Spacer(Modifier.width(8.dp))
                Text(
                    record.label,
                    color = if (app != null) IhlColors.Text else IhlColors.TextDim,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(shortTime(record.time), color = IhlColors.TextDim, fontSize = 11.sp)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun AppIcon(app: AppEntry?, loadIcon: suspend (AppEntry, Int) -> ImageBitmap?) {
    val sizePx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app?.key) { value = app?.let { loadIcon(it, sizePx) } }
    Box(Modifier.size(24.dp)) {
        val bitmap = icon
        if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(IhlColors.Line, SmallCutShape))
    }
}

@Composable
private fun CommandsTab(commands: List<String>, onRun: (String) -> Unit) {
    if (commands.isEmpty()) {
        EmptyText("no commands yet")
        return
    }
    // 新しい順に表示（タップで再実行）
    val newestFirst = remember(commands) { commands.asReversed().toList() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(newestFirst.size) { i ->
            Text(
                "ihl: ${newestFirst[i]}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { onRun(newestFirst[i]) }.padding(vertical = 5.dp)
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun NotificationsTab(records: List<NotificationRecord>, onOpen: (NotificationRecord) -> Unit) {
    if (records.isEmpty()) {
        EmptyText("no notifications yet")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(records, key = { it.id }) { r ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(r) }.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.appLabel, color = IhlColors.TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(shortTime(r.time), color = IhlColors.TextDim, fontSize = 11.sp)
                }
                if (r.title.isNotEmpty()) Text(r.title, color = IhlColors.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.text.isNotEmpty()) Text(r.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
