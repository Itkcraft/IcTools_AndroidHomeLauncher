package com.ictools.ichomelauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ictools.ichomelauncher.LauncherViewModel
import com.ictools.ichomelauncher.data.PanelIds
import com.ictools.ichomelauncher.gesture.backgroundGestures
import com.ictools.ichomelauncher.gesture.pinchGestures
import com.ictools.ichomelauncher.ui.drawer.DrawerPanel
import com.ictools.ichomelauncher.ui.panel.FloatingPanel
import com.ictools.ichomelauncher.ui.settings.SettingsActions
import com.ictools.ichomelauncher.ui.settings.SettingsPanel
import com.ictools.ichomelauncher.ui.settings.PermissionStatus
import com.ictools.ichomelauncher.ui.media.MediaActions
import com.ictools.ichomelauncher.ui.media.MediaPanel
import com.ictools.ichomelauncher.ui.schedule.SchedulePanel
import com.ictools.ichomelauncher.ui.history.HistoryActions
import com.ictools.ichomelauncher.ui.history.HistoryData
import com.ictools.ichomelauncher.ui.history.HistoryPanel
import com.ictools.ichomelauncher.ui.memo.MemoPanel
import com.ictools.ichomelauncher.data.Permissions
import androidx.compose.ui.platform.LocalContext
import com.ictools.ichomelauncher.ui.terminal.TerminalPanel
import kotlin.math.max
import kotlin.math.min

/**
 * ランチャーのルート画面。
 * レイヤー0：システム壁紙（ウィンドウ透過で表示）／レイヤー1：スモーク／
 * レイヤー2：背景ジェスチャー／レイヤー3：フローティングパネル群
 */
@Composable
fun LauncherScreen(vm: LauncherViewModel, onOpenHomeSettings: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val panels by vm.panels.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val blurAvailable by vm.blurAvailable.collectAsStateWithLifecycle()
    val memos by vm.memos.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val launchHistory by vm.launchHistory.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val notificationAccess by vm.notificationAccess.collectAsStateWithLifecycle()
    val calendarAccess by vm.calendarAccess.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestNotificationAccess = {
        if (!vm.startActivitySafely(Permissions.notificationAccessIntent(context))) {
            vm.startActivitySafely(Permissions.notificationAccessListIntent())
        }
    }
    val openAppInfo = { vm.startActivitySafely(Permissions.appDetailsIntent(context)); Unit }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    // ピンチは画面全体（パネル上も含む）で検出する
    BoxWithConstraints(Modifier.fillMaxSize().pinchGestures(vm::onGesture)) {
        val screenW = maxWidth.value
        val screenH = maxHeight.value
        LaunchedEffect(screenW, screenH) { vm.onScreenSize(screenW, screenH) }

        // ---- レイヤー1：スモーク ----
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = settings.background.smokePercent / 100f))
        )

        // ---- レイヤー2：背景ジェスチャー ----
        Box(
            Modifier
                .fillMaxSize()
                .backgroundGestures(onDown = { focusManager.clearFocus() }, onGesture = vm::onGesture)
        )

        // ---- レイヤー3：パネル群 ----
        val focusedId = vm.focusedId(panels)
        // キーボード表示中は、フォーカス中のパネルが隠れないよう表示位置だけ一時的に持ち上げる（保存はしない）
        val imeHeightDp = WindowInsets.ime.getBottom(density) / density.density

        panels.filter { it.isOpen }.forEach { panel ->
            key(panel.id) {
                val displayY = if (panel.id == focusedId && imeHeightDp > 0f) {
                    min(panel.yDp, max(0f, screenH - imeHeightDp - panel.heightDp))
                } else {
                    panel.yDp
                }
                // メモのタイトルバーには1行目を出す
                val memo = if (PanelIds.isMemo(panel.id)) memos[PanelIds.memoIdOf(panel.id)] else null
                val title = if (memo != null && memo.headline.isNotEmpty()) "Memo: ${memo.headline}" else PanelIds.title(panel.id)
                FloatingPanel(
                    title = title,
                    xDp = panel.xDp,
                    yDp = displayY,
                    widthDp = panel.widthDp,
                    heightDp = panel.heightDp,
                    focused = panel.id == focusedId,
                    onFocus = { vm.focusPanel(panel.id) },
                    onClose = { vm.closePanel(panel.id) },
                    onMove = { dx, dy -> vm.movePanelBy(panel.id, dx, dy) },
                    onResize = { dw, dh -> vm.resizePanelBy(panel.id, dw, dh) },
                    onDragEnd = vm::onDragEnd,
                    modifier = Modifier.zIndex(panel.zOrder.toFloat() + 1f)
                ) {
                    when (panel.id) {
                        PanelIds.TERMINAL -> TerminalPanel(vm.terminal)
                        PanelIds.DRAWER -> DrawerPanel(
                            apps = apps,
                            loadIcon = vm::loadIcon,
                            onLaunch = { vm.launchApp(it) }
                        )
                        PanelIds.SETTINGS -> SettingsPanel(
                            settings = settings,
                            blurAvailable = blurAvailable,
                            permissions = PermissionStatus(notificationAccess, calendarAccess),
                            actions = SettingsActions(
                                setBlurEnabled = { vm.setBlurEnabled(it) },
                                setBlurRadius = { vm.setBlurRadius(it) },
                                setSmoke = { vm.setSmoke(it) },
                                setGestureAction = { type, action -> vm.setGestureAction(type, action) },
                                resetGestures = { vm.resetGestures() },
                                resetLayout = vm::resetLayout,
                                openHomeSettings = onOpenHomeSettings,
                                setScheduleDays = { vm.setScheduleDays(it) },
                                requestNotificationAccess = requestNotificationAccess,
                                openAppInfo = openAppInfo
                            )
                        )
                        PanelIds.MEDIA -> MediaPanel(
                            media = media,
                            hasAccess = notificationAccess,
                            actions = MediaActions(
                                playPause = vm::mediaPlayPause,
                                next = { vm.mediaNext() },
                                previous = { vm.mediaPrevious() },
                                seek = { vm.mediaSeek(it) },
                                openPlayer = { vm.openMediaPlayer() },
                                requestAccess = requestNotificationAccess,
                                openAppInfo = openAppInfo
                            )
                        )
                        PanelIds.SCHEDULE -> SchedulePanel(
                            events = events,
                            days = settings.scheduleDays,
                            hasAccess = calendarAccess,
                            onPermissionResult = vm::refreshPermissions,
                            onOpenAppInfo = openAppInfo,
                            onOpenEvent = vm::openEvent,
                            onDayChanged = vm::reloadEvents
                        )
                        PanelIds.HISTORY -> HistoryPanel(
                            data = HistoryData(
                                launches = launchHistory,
                                apps = apps,
                                commands = vm.terminal.history.toList(),
                                notifications = notifications,
                                hasNotificationAccess = notificationAccess
                            ),
                            actions = HistoryActions(
                                launchApp = { vm.launchApp(it) },
                                loadIcon = vm::loadIcon,
                                runCommand = vm::runCommand,
                                openNotification = vm::openNotification,
                                clearLaunches = vm::clearLaunchHistory,
                                clearCommands = vm::clearCommandHistory,
                                clearNotifications = vm::clearNotifications,
                                requestNotificationAccess = requestNotificationAccess,
                                openAppInfo = openAppInfo
                            )
                        )
                        else -> if (memo != null) {
                            MemoPanel(
                                memo = memo,
                                onEdit = { vm.editMemo(memo.id, it) },
                                onDelete = { vm.deleteMemo(memo.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
