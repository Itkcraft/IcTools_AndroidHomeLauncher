package com.ictools.ichomelauncher

import android.app.Application
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.data.AppRepository
import com.ictools.ichomelauncher.data.CalendarEvent
import com.ictools.ichomelauncher.data.CalendarRepository
import com.ictools.ichomelauncher.data.HistoryRepository
import com.ictools.ichomelauncher.data.LaunchRecord
import com.ictools.ichomelauncher.data.LauncherSettings
import com.ictools.ichomelauncher.data.MediaInfo
import com.ictools.ichomelauncher.data.MediaRepository
import com.ictools.ichomelauncher.data.Memo
import com.ictools.ichomelauncher.data.MemoRepository
import com.ictools.ichomelauncher.data.NotificationHistory
import com.ictools.ichomelauncher.data.NotificationRecord
import com.ictools.ichomelauncher.data.PanelIds
import com.ictools.ichomelauncher.data.PanelRepository
import com.ictools.ichomelauncher.data.PanelState
import com.ictools.ichomelauncher.data.PendingIntents
import com.ictools.ichomelauncher.data.Permissions
import com.ictools.ichomelauncher.data.PermissionState
import com.ictools.ichomelauncher.data.Appearance
import com.ictools.ichomelauncher.data.AudioEffects
import com.ictools.ichomelauncher.data.EqState
import com.ictools.ichomelauncher.data.FavoritesRepository
import com.ictools.ichomelauncher.data.MediaSettings
import com.ictools.ichomelauncher.data.NetworkMonitor
import com.ictools.ichomelauncher.data.NetworkSnapshot
import com.ictools.ichomelauncher.data.SystemSnapshot
import com.ictools.ichomelauncher.data.SystemStatus
import com.ictools.ichomelauncher.data.TorchController
import com.ictools.ichomelauncher.ui.terminal.FileShell
import android.provider.MediaStore
import com.ictools.ichomelauncher.data.SettingsRepository
import com.ictools.ichomelauncher.gesture.GestureAction
import com.ictools.ichomelauncher.gesture.GestureType
import com.ictools.ichomelauncher.ui.terminal.TerminalHost
import com.ictools.ichomelauncher.ui.terminal.TerminalSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** パネル配置の制約値（dp） */
object PanelMetrics {
    const val TITLE_BAR_HEIGHT = 30f   // タイトルバーの高さ
    const val MIN_WIDTH = 160f         // 最小幅
    const val MIN_HEIGHT = 120f        // 最小高さ
    const val MIN_VISIBLE = 48f        // タイトルバーが画面内に残る最小幅
}

/** ランチャー全体の状態（パネル・設定・アプリ・メモ・履歴・メディア・予定・ターミナル）を管理する ViewModel */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val panelRepository = PanelRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val appRepository = AppRepository(application, viewModelScope)
    private val memoRepository = MemoRepository(application)
    private val historyRepository = HistoryRepository(application)
    private val mediaRepository = MediaRepository(application, viewModelScope)
    private val calendarRepository = CalendarRepository(application)
    private val notificationHistory = NotificationHistory.get(application)
    private val favoritesRepository = FavoritesRepository(application)
    private val torch = TorchController(application)
    private val systemStatus = SystemStatus(application)
    private val networkMonitor = NetworkMonitor(application)
    private val audioEffects = AudioEffects.get(application)
    private val fileShell = FileShell(application)

    /** インストール済みアプリ一覧 */
    val apps: StateFlow<List<AppEntry>> = appRepository.apps

    /** 背景・ジェスチャー等の設定 */
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    /** 端末／現在の状態でウィンドウぼかしが使えるか（Activity から更新される） */
    private val _blurAvailable = MutableStateFlow(true)
    val blurAvailable: StateFlow<Boolean> = _blurAvailable.asStateFlow()

    // ---- 権限 ----

    private val _permissions = MutableStateFlow(Permissions.current(application))

    /** 各権限の許可状態 */
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    // ---- パネル ----

    private val _panels = MutableStateFlow<List<PanelState>>(emptyList())

    /** 全パネルの状態（閉じているものも含む） */
    val panels: StateFlow<List<PanelState>> = _panels.asStateFlow()

    private var screenWidth = 0f
    private var screenHeight = 0f
    private var loadStarted = false
    private var loaded = false
    private val saveMutex = Mutex()

    // ---- メモ ----

    private val _memos = MutableStateFlow<Map<String, Memo>>(emptyMap())

    /** メモID → メモ */
    val memos: StateFlow<Map<String, Memo>> = _memos.asStateFlow()
    private var memoSaveJob: Job? = null

    // ---- 履歴 ----

    private val _launchHistory = MutableStateFlow<List<LaunchRecord>>(emptyList())

    /** アプリ起動履歴（新しい順） */
    val launchHistory: StateFlow<List<LaunchRecord>> = _launchHistory.asStateFlow()

    /** 通知履歴（新しい順） */
    val notifications: StateFlow<List<NotificationRecord>> = notificationHistory.records

    // ---- メディア・予定 ----

    /** 再生中のメディア */
    val media: StateFlow<MediaInfo?> = mediaRepository.media

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())

    /** 表示期間内の予定 */
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    private val _eventsVersion = MutableStateFlow(0)

    /** 予定が変わるたびに増える番号（カレンダーパネルの読み直し用） */
    val eventsVersion: StateFlow<Int> = _eventsVersion.asStateFlow()

    /** EQ の状態 */
    val eq: StateFlow<EqState> = audioEffects.state

    /** 通信状況 */
    val network: StateFlow<NetworkSnapshot> = networkMonitor.state

    // ---- お気に入り ----

    private val _favoriteKeys = MutableStateFlow<List<String>>(emptyList())

    /** お気に入りアプリのキー（並び順） */
    val favoriteKeys: StateFlow<List<String>> = _favoriteKeys.asStateFlow()

    // ---- ターミナル ----

    /** ターミナルのセッション（ログ・履歴・コマンド実行） */
    val terminal = TerminalSession(object : TerminalHost {
        override fun favorites() = favoriteApps()
        override fun addFavorite(app: AppEntry) = this@LauncherViewModel.addFavorite(app)
        override fun removeFavorite(app: AppEntry) = this@LauncherViewModel.removeFavorite(app)
        override fun openCamera() = this@LauncherViewModel.openCamera()
        override fun torchOn() = torch.isOn.value
        override fun setTorch(on: Boolean) = torch.set(on)
        override fun startActivity(intent: Intent) = startActivitySafely(intent)
        override fun requestAllFilesAccess() = this@LauncherViewModel.requestAllFilesAccess()
        override fun apps() = appRepository.apps.value
        override fun launchApp(app: AppEntry) = this@LauncherViewModel.launchApp(app)
        override fun openPanel(id: String) = this@LauncherViewModel.openPanel(id)
        override fun closePanel(id: String) = this@LauncherViewModel.closePanel(id)
        override fun memos() = _memos.value.values.toList()
        override fun newMemo(text: String) = this@LauncherViewModel.newMemo(text)
        override fun openMemo(memo: Memo) = openPanel(PanelIds.memoPanelId(memo.id))
        override fun deleteMemo(memo: Memo) = this@LauncherViewModel.deleteMemo(memo.id)
        override fun onHistoryChanged(history: List<String>) {
            viewModelScope.launch { historyRepository.saveCommands(history) }
        }
    }, fileShell, viewModelScope)

    init {
        viewModelScope.launch {
            terminal.restoreHistory(historyRepository.loadCommands())
            _launchHistory.value = historyRepository.loadLaunches()
            _favoriteKeys.value = favoritesRepository.load()
        }
        mediaRepository.start()
        // 表示日数が変わったら予定を読み直す
        viewModelScope.launch {
            settings.map { it.scheduleDays }.distinctUntilChanged().drop(1).collect { reloadEvents() }
        }
        reloadEvents()
        calendarRepository.observe { reloadEvents() }
    }

    fun setBlurAvailable(available: Boolean) {
        _blurAvailable.value = available
    }

    /** 権限の状態を確認し直す（設定画面から戻ってきたとき等に呼ぶ） */
    fun refreshPermissions() {
        val p = Permissions.current(app)
        _permissions.value = p
        if (p.notificationAccess) mediaRepository.start() else mediaRepository.stop()
        if (p.calendar) calendarRepository.observe { reloadEvents() } else calendarRepository.stopObserving()
        reloadEvents()
        networkMonitor.refreshPermissions()
    }

    /** 画面サイズ（dp）の通知。初回はここで保存データを読み込み、以降は画面内に収まるよう補正する */
    fun onScreenSize(widthDp: Float, heightDp: Float) {
        if (widthDp <= 0f || heightDp <= 0f) return
        if (widthDp == screenWidth && heightDp == screenHeight) return
        screenWidth = widthDp
        screenHeight = heightDp
        if (!loadStarted) {
            loadStarted = true
            viewModelScope.launch {
                _memos.value = memoRepository.load().associateBy { it.id }
                val saved = panelRepository.load()
                val base = if (saved == null) defaultLayout() else mergeWithDefaults(saved)
                _panels.value = normalizeZ(base.map { clamp(it) })
                loaded = true
                persist()
            }
        } else if (loaded) {
            _panels.value = _panels.value.map { clamp(it) }
            persist()
        }
    }

    /** 単一パネルの初期配置。初回起動時はターミナルのみ画面中央下寄りに開く */
    private fun defaultLayout(): List<PanelState> {
        val w = screenWidth
        val h = screenHeight
        fun centeredX(width: Float) = (w - width) / 2f
        fun closed(id: String, width: Float, height: Float, y: Float, z: Int) =
            PanelState(id, centeredX(width), y, width, height, isOpen = false, zOrder = z)

        val termW = min(w - 32f, 360f)
        val termH = min(280f, h * 0.4f)
        val termY = min(h * 0.55f, h - termH - 64f)
        val listW = min(w - 32f, 320f)

        return listOf(
            closed(PanelIds.DRAWER, listW, min(460f, h * 0.6f), h * 0.12f, 0),
            closed(PanelIds.SETTINGS, min(w - 32f, 340f), min(540f, h * 0.65f), h * 0.08f, 1),
            closed(PanelIds.MEDIA, listW, 170f, h * 0.12f, 2),
            closed(PanelIds.SCHEDULE, listW, min(420f, h * 0.5f), h * 0.1f, 3),
            closed(PanelIds.HISTORY, listW, min(440f, h * 0.55f), h * 0.1f, 4),
            closed(PanelIds.CLOCK, min(w - 32f, 260f), 150f, h * 0.08f, 5),
            closed(PanelIds.CALENDAR, listW, min(440f, h * 0.55f), h * 0.1f, 6),
            closed(PanelIds.STATUS, listW, min(460f, h * 0.6f), h * 0.1f, 7),
            closed(PanelIds.NETWORK, listW, min(400f, h * 0.5f), h * 0.12f, 8),
            closed(PanelIds.FAVORITES, min(w - 32f, 260f), min(380f, h * 0.5f), h * 0.12f, 9),
            PanelState(PanelIds.TERMINAL, centeredX(termW), termY, termW, termH, isOpen = true, zOrder = 10)
        ).map { clamp(it) }
    }

    /** 保存データに欠けている単一パネルを初期配置で補い、未知のIDやメモが消えたパネルは捨てる */
    private fun mergeWithDefaults(saved: List<PanelState>): List<PanelState> {
        val defaults = defaultLayout()
        val known = saved.filter {
            it.id in PanelIds.SINGLETONS || (PanelIds.isMemo(it.id) && _memos.value.containsKey(PanelIds.memoIdOf(it.id)))
        }.distinctBy { it.id }
        val missing = defaults.filter { d -> known.none { it.id == d.id } }
            .map { it.copy(isOpen = false, zOrder = Int.MIN_VALUE) }
        return known + missing
    }

    /** 6.1 の制限（最小サイズ・タイトルバーが最低 48dp は画面内）に収まるよう補正する */
    private fun clamp(p: PanelState): PanelState {
        if (screenWidth <= 0f || screenHeight <= 0f) return p
        val width = p.widthDp.coerceIn(PanelMetrics.MIN_WIDTH, max(PanelMetrics.MIN_WIDTH, screenWidth))
        val height = p.heightDp.coerceIn(PanelMetrics.MIN_HEIGHT, max(PanelMetrics.MIN_HEIGHT, screenHeight))
        val x = p.xDp.coerceIn(PanelMetrics.MIN_VISIBLE - width, screenWidth - PanelMetrics.MIN_VISIBLE)
        val y = p.yDp.coerceIn(0f, max(0f, screenHeight - PanelMetrics.TITLE_BAR_HEIGHT))
        return p.copy(xDp = x, yDp = y, widthDp = width, heightDp = height)
    }

    /** 重なり順を 0,1,2… に振り直す（相対順は維持） */
    private fun normalizeZ(list: List<PanelState>): List<PanelState> {
        val order = list.sortedBy { it.zOrder }.map { it.id }
        return list.map { it.copy(zOrder = order.indexOf(it.id)) }
    }

    /** 現在フォーカス中（開いている中で最前面）のパネルID */
    fun focusedId(list: List<PanelState> = _panels.value): String? =
        list.filter { it.isOpen }.maxByOrNull { it.zOrder }?.id

    private inline fun update(id: String, transform: (PanelState) -> PanelState) {
        _panels.value = _panels.value.map { if (it.id == id) transform(it) else it }
    }

    /** 最前面に持ってくる */
    private fun bringToFront(id: String) {
        val top = _panels.value.maxOfOrNull { it.zOrder } ?: 0
        update(id) { it.copy(zOrder = top + 1) }
        _panels.value = normalizeZ(_panels.value)
    }

    /** パネルを開く（開いていれば最前面に出すだけ） */
    fun openPanel(id: String) {
        if (!loaded || _panels.value.none { it.id == id }) return
        update(id) { clamp(it.copy(isOpen = true)) }
        bringToFront(id)
        persist()
    }

    /** パネルにフォーカスする（タッチ時） */
    fun focusPanel(id: String) {
        if (!loaded || focusedId() == id) return
        bringToFront(id)
        persist()
    }

    /** パネルを閉じる。フォーカスは次に新しくアクティブ化されたパネルへ自動的に移る */
    fun closePanel(id: String) {
        if (!loaded) return
        // 空のメモは閉じたら削除する
        if (PanelIds.isMemo(id)) {
            val memo = _memos.value[PanelIds.memoIdOf(id)]
            if (memo == null || memo.text.isBlank()) {
                deleteMemo(PanelIds.memoIdOf(id))
                return
            }
        }
        update(id) { it.copy(isOpen = false) }
        persist()
    }

    fun closeFocused() {
        focusedId()?.let { closePanel(it) }
    }

    fun closeAll() {
        if (!loaded) return
        _panels.value.filter { it.isOpen }.forEach { closePanel(it.id) }
    }

    /** ドラッグ中の移動（保存はドラッグ終了時） */
    fun movePanelBy(id: String, dxDp: Float, dyDp: Float) {
        update(id) { clamp(it.copy(xDp = it.xDp + dxDp, yDp = it.yDp + dyDp)) }
    }

    /** ドラッグ中のリサイズ（保存はドラッグ終了時） */
    fun resizePanelBy(id: String, dwDp: Float, dhDp: Float) {
        update(id) { clamp(it.copy(widthDp = it.widthDp + dwDp, heightDp = it.heightDp + dhDp)) }
    }

    /** ドラッグ終了時に保存 */
    fun onDragEnd() = persist()

    /** パネル配置を初回起動時の状態に戻す（メモは閉じた状態で残す） */
    fun resetLayout() {
        if (!loaded) return
        val memoPanels = _panels.value.filter { PanelIds.isMemo(it.id) }.mapIndexed { i, p ->
            memoPanelAt(p.id, i).copy(isOpen = false, zOrder = -1)
        }
        _panels.value = normalizeZ(defaultLayout() + memoPanels)
        persist()
    }

    private fun persist() {
        if (!loaded) return
        viewModelScope.launch {
            // 連続保存の順序を保つため直列化し、常に最新の状態を書き込む
            saveMutex.withLock { panelRepository.save(_panels.value) }
        }
    }

    // ---- メモ ----

    /** メモパネルの初期配置（少しずつずらして重ねる） */
    private fun memoPanelAt(panelId: String, index: Int): PanelState {
        val width = min(screenWidth - 32f, 240f)
        val offset = (index % 6) * 18f
        return clamp(
            PanelState(
                id = panelId,
                xDp = (screenWidth - width) / 2f - 40f + offset,
                yDp = screenHeight * 0.18f + offset,
                widthDp = width,
                heightDp = 200f,
                isOpen = true,
                zOrder = -1
            )
        )
    }

    /** 新しいメモを作ってパネルを開く */
    fun newMemo(text: String = "") {
        if (!loaded) return
        val now = System.currentTimeMillis()
        val memo = Memo(UUID.randomUUID().toString().take(8), text, now, now)
        _memos.value = _memos.value + (memo.id to memo)
        val panelId = PanelIds.memoPanelId(memo.id)
        val count = _panels.value.count { PanelIds.isMemo(it.id) }
        _panels.value = _panels.value + memoPanelAt(panelId, count)
        bringToFront(panelId)
        saveMemosNow()
        persist()
    }

    /** メモ本文の編集（保存は少し待ってまとめて行う） */
    fun editMemo(memoId: String, text: String) {
        val memo = _memos.value[memoId] ?: return
        if (memo.text == text) return
        _memos.value = _memos.value + (memoId to memo.copy(text = text, updatedAt = System.currentTimeMillis()))
        memoSaveJob?.cancel()
        memoSaveJob = viewModelScope.launch {
            delay(500)
            memoRepository.save(_memos.value.values.toList())
        }
    }

    /** メモを削除し、パネルも取り除く */
    fun deleteMemo(memoId: String) {
        _memos.value = _memos.value - memoId
        _panels.value = normalizeZ(_panels.value.filterNot { it.id == PanelIds.memoPanelId(memoId) })
        saveMemosNow()
        persist()
    }

    private fun saveMemosNow() {
        memoSaveJob?.cancel()
        memoSaveJob = viewModelScope.launch { memoRepository.save(_memos.value.values.toList()) }
    }

    // ---- ジェスチャー ----

    /** ジェスチャー検出時に、割り当てられたアクションを実行する */
    fun onGesture(type: GestureType) {
        perform(settings.value.gestures[type] ?: GestureAction.NONE)
    }

    private fun perform(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.OPEN_TERMINAL -> openPanel(PanelIds.TERMINAL)
            GestureAction.OPEN_DRAWER -> openPanel(PanelIds.DRAWER)
            GestureAction.OPEN_SETTINGS -> openPanel(PanelIds.SETTINGS)
            GestureAction.OPEN_MEDIA -> openPanel(PanelIds.MEDIA)
            GestureAction.OPEN_SCHEDULE -> openPanel(PanelIds.SCHEDULE)
            GestureAction.OPEN_HISTORY -> openPanel(PanelIds.HISTORY)
            GestureAction.OPEN_CLOCK -> openPanel(PanelIds.CLOCK)
            GestureAction.OPEN_CALENDAR -> openPanel(PanelIds.CALENDAR)
            GestureAction.OPEN_STATUS -> openPanel(PanelIds.STATUS)
            GestureAction.OPEN_NETWORK -> openPanel(PanelIds.NETWORK)
            GestureAction.OPEN_FAVORITES -> openPanel(PanelIds.FAVORITES)
            GestureAction.NEW_MEMO -> newMemo()
            GestureAction.CLOSE_FOCUSED -> closeFocused()
            GestureAction.CLOSE_ALL -> closeAll()
        }
    }

    // ---- 設定 ----

    fun setBlurEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setBlurEnabled(enabled) }
    fun setBlurRadius(dp: Float) = viewModelScope.launch { settingsRepository.setBlurRadius(dp) }
    fun setSmoke(percent: Float) = viewModelScope.launch { settingsRepository.setSmoke(percent) }
    fun setScheduleDays(days: Int) = viewModelScope.launch { settingsRepository.setScheduleDays(days) }
    fun setAppearance(a: Appearance) = viewModelScope.launch { settingsRepository.setAppearance(a) }
    fun setMediaSettings(m: MediaSettings) = viewModelScope.launch { settingsRepository.setMediaSettings(m) }

    fun setGestureAction(type: GestureType, action: GestureAction) = viewModelScope.launch {
        settingsRepository.setGestures(settings.value.gestures + (type to action))
    }

    fun resetGestures() = viewModelScope.launch {
        settingsRepository.setGestures(GestureType.defaultMapping())
    }

    // ---- アプリ・履歴 ----

    /** アプリを起動し、起動履歴に記録する */
    fun launchApp(app: AppEntry): Boolean {
        val ok = appRepository.launch(app)
        if (ok) {
            val record = LaunchRecord(app.key, app.label, System.currentTimeMillis())
            // 同じアプリは最新の1件だけ残す
            _launchHistory.value = (listOf(record) + _launchHistory.value.filterNot { it.appKey == app.key })
                .take(HistoryRepository.MAX_LAUNCHES)
            viewModelScope.launch { historyRepository.saveLaunches(_launchHistory.value) }
        }
        return ok
    }

    fun clearLaunchHistory() {
        _launchHistory.value = emptyList()
        viewModelScope.launch { historyRepository.saveLaunches(emptyList()) }
    }

    /** ターミナルを開いてコマンドを実行する（履歴パネルから） */
    fun runCommand(command: String) {
        openPanel(PanelIds.TERMINAL)
        terminal.execute(command)
    }

    fun clearCommandHistory() = terminal.clearHistory()

    /** 通知を開く（元の通知の操作が使えなければアプリを起動） */
    fun openNotification(record: NotificationRecord) {
        val pi = notificationHistory.contentIntentOf(record)
        if (pi != null && runCatching { PendingIntents.send(app, pi) }.isSuccess) return
        apps.value.firstOrNull { it.component.packageName == record.packageName }?.let { launchApp(it) }
    }

    fun clearNotifications() = notificationHistory.clear()

    suspend fun loadIcon(app: AppEntry, sizePx: Int): ImageBitmap? = appRepository.loadIcon(app, sizePx)

    // ---- メディア ----

    fun mediaPlayPause() = mediaRepository.playPause()
    fun mediaNext() = mediaRepository.next()
    fun mediaPrevious() = mediaRepository.previous()
    fun mediaSeek(positionMs: Long) = mediaRepository.seekTo(positionMs)
    fun openMediaPlayer() = mediaRepository.openPlayer()
    fun mediaSkipTo(id: Long) = mediaRepository.skipToQueueItem(id)

    // ---- EQ ----

    fun eqSetEnabled(enabled: Boolean) = audioEffects.setEnabled(enabled)
    fun eqSelectPreset(index: Int) = audioEffects.selectPreset(index)
    fun eqSetBand(band: Int, level: Int) = audioEffects.setBandLevel(band, level)
    fun eqReset() = audioEffects.reset()

    // ---- お気に入り ----

    /** お気に入りアプリ（アンインストール済みは除く） */
    fun favoriteApps(): List<AppEntry> {
        val byKey = apps.value.associateBy { it.key }
        return _favoriteKeys.value.mapNotNull { byKey[it] }
    }

    private fun saveFavorites(keys: List<String>) {
        _favoriteKeys.value = keys
        viewModelScope.launch { favoritesRepository.save(keys) }
    }

    fun addFavorite(app: AppEntry) {
        if (app.key !in _favoriteKeys.value) saveFavorites(_favoriteKeys.value + app.key)
    }

    fun removeFavorite(app: AppEntry) = saveFavorites(_favoriteKeys.value - app.key)

    fun toggleFavorite(app: AppEntry) {
        if (app.key in _favoriteKeys.value) removeFavorite(app) else addFavorite(app)
    }

    /** 並び替え（パネルに表示中のキーの順に保存。表示されていない＝未インストールのキーは末尾に残す） */
    fun reorderFavorites(keys: List<String>) {
        saveFavorites(keys + _favoriteKeys.value.filterNot { it in keys })
    }

    // ---- カメラ・ライト・ファイル ----

    fun openCamera(): Boolean = startActivitySafely(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    fun requestAllFilesAccess() {
        if (!startActivitySafely(Permissions.allFilesIntent(app))) startActivitySafely(Permissions.allFilesListIntent())
    }

    fun requestUsageAccess() {
        if (!startActivitySafely(Permissions.usageAccessIntent(app))) startActivitySafely(Permissions.usageAccessListIntent())
    }

    // ---- 稼働状況・通信 ----

    suspend fun systemSnapshot(): SystemSnapshot = systemStatus.snapshot()
    fun startNetwork() = networkMonitor.start()
    fun stopNetwork() = networkMonitor.stop()

    /** カレンダーパネル用：期間内の予定 */
    suspend fun loadEvents(start: Long, end: Long): List<CalendarEvent> = calendarRepository.loadRange(start, end)

    // ---- 予定 ----

    fun reloadEvents() {
        viewModelScope.launch {
            _events.value = calendarRepository.loadUpcoming(settings.value.scheduleDays)
            _eventsVersion.value++
        }
    }

    /** カレンダーアプリで予定を開く */
    fun openEvent(event: CalendarEvent) {
        runCatching { app.startActivity(calendarRepository.viewIntent(event)) }
    }

    /** 設定画面などを開く（Activity 外からなので NEW_TASK を付ける） */
    fun startActivitySafely(intent: Intent): Boolean =
        runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    override fun onCleared() {
        appRepository.close()
        mediaRepository.stop()
        calendarRepository.stopObserving()
        networkMonitor.stop()
        torch.close()
        super.onCleared()
    }
}
