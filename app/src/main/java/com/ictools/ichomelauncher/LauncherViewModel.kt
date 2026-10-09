package com.ictools.ichomelauncher

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.data.AppRepository
import com.ictools.ichomelauncher.data.LauncherSettings
import com.ictools.ichomelauncher.data.PanelIds
import com.ictools.ichomelauncher.data.PanelRepository
import com.ictools.ichomelauncher.data.PanelState
import com.ictools.ichomelauncher.data.SettingsRepository
import com.ictools.ichomelauncher.gesture.GestureAction
import com.ictools.ichomelauncher.gesture.GestureType
import com.ictools.ichomelauncher.ui.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

/** パネル配置の制約値（dp） */
object PanelMetrics {
    const val TITLE_BAR_HEIGHT = 30f   // タイトルバーの高さ
    const val MIN_WIDTH = 160f         // 最小幅
    const val MIN_HEIGHT = 120f        // 最小高さ
    const val MIN_VISIBLE = 48f        // タイトルバーが画面内に残る最小幅
}

/** ランチャー全体の状態（パネル・設定・アプリ一覧・ターミナル）を管理する ViewModel */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val panelRepository = PanelRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val appRepository = AppRepository(application, viewModelScope)

    /** インストール済みアプリ一覧 */
    val apps: StateFlow<List<AppEntry>> = appRepository.apps

    /** 背景・ジェスチャー設定 */
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    /** 端末／現在の状態でウィンドウぼかしが使えるか（Activity から更新される） */
    private val _blurAvailable = MutableStateFlow(true)
    val blurAvailable: StateFlow<Boolean> = _blurAvailable.asStateFlow()

    // ---- パネル ----

    private val _panels = MutableStateFlow<List<PanelState>>(emptyList())

    /** 全パネルの状態（閉じているものも含む） */
    val panels: StateFlow<List<PanelState>> = _panels.asStateFlow()

    private var screenWidth = 0f
    private var screenHeight = 0f
    private var loadStarted = false
    private var loaded = false
    private val saveMutex = Mutex()

    // ---- ターミナル ----

    /** ターミナルのセッション（ログ・履歴・コマンド実行） */
    val terminal = TerminalSession(
        apps = { appRepository.apps.value },
        launchApp = { appRepository.launch(it) },
        openPanel = { openPanel(it) },
        closePanel = { closePanel(it) }
    )

    fun setBlurAvailable(available: Boolean) {
        _blurAvailable.value = available
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

    /** 初回起動時の配置：ターミナルのみ画面中央下寄りに開く */
    private fun defaultLayout(): List<PanelState> {
        val w = screenWidth
        val h = screenHeight
        fun centeredX(width: Float) = (w - width) / 2f

        val termW = min(w - 32f, 360f)
        val termH = min(280f, h * 0.4f)
        val termY = min(h * 0.55f, h - termH - 64f)

        val drawerW = min(w - 32f, 320f)
        val drawerH = min(460f, h * 0.6f)

        val settingsW = min(w - 32f, 340f)
        val settingsH = min(540f, h * 0.65f)

        return listOf(
            PanelState(PanelIds.DRAWER, centeredX(drawerW), h * 0.12f, drawerW, drawerH, isOpen = false, zOrder = 0),
            PanelState(PanelIds.SETTINGS, centeredX(settingsW), h * 0.08f, settingsW, settingsH, isOpen = false, zOrder = 1),
            PanelState(PanelIds.TERMINAL, centeredX(termW), termY, termW, termH, isOpen = true, zOrder = 2)
        ).map { clamp(it) }
    }

    /** 保存データに欠けているパネルを初期配置で補い、未知のIDは捨てる */
    private fun mergeWithDefaults(saved: List<PanelState>): List<PanelState> {
        val defaults = defaultLayout()
        val known = saved.filter { it.id in PanelIds.ALL }.distinctBy { it.id }
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
        if (!loaded) return
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
        update(id) { it.copy(isOpen = false) }
        persist()
    }

    fun closeFocused() {
        focusedId()?.let { closePanel(it) }
    }

    fun closeAll() {
        if (!loaded) return
        _panels.value = _panels.value.map { it.copy(isOpen = false) }
        persist()
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

    /** パネル配置を初回起動時の状態に戻す */
    fun resetLayout() {
        if (!loaded) return
        _panels.value = normalizeZ(defaultLayout())
        persist()
    }

    private fun persist() {
        if (!loaded) return
        viewModelScope.launch {
            // 連続保存の順序を保つため直列化し、常に最新の状態を書き込む
            saveMutex.withLock { panelRepository.save(_panels.value) }
        }
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
            GestureAction.CLOSE_FOCUSED -> closeFocused()
            GestureAction.CLOSE_ALL -> closeAll()
        }
    }

    // ---- 設定 ----

    fun setBlurEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setBlurEnabled(enabled) }
    fun setBlurRadius(dp: Float) = viewModelScope.launch { settingsRepository.setBlurRadius(dp) }
    fun setSmoke(percent: Float) = viewModelScope.launch { settingsRepository.setSmoke(percent) }

    fun setGestureAction(type: GestureType, action: GestureAction) = viewModelScope.launch {
        settingsRepository.setGestures(settings.value.gestures + (type to action))
    }

    fun resetGestures() = viewModelScope.launch {
        settingsRepository.setGestures(GestureType.defaultMapping())
    }

    // ---- アプリ ----

    fun launchApp(app: AppEntry): Boolean = appRepository.launch(app)

    suspend fun loadIcon(app: AppEntry, sizePx: Int): ImageBitmap? = appRepository.loadIcon(app, sizePx)

    override fun onCleared() {
        appRepository.close()
        super.onCleared()
    }
}
