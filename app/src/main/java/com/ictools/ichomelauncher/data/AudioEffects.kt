package com.ictools.ichomelauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 保存する EQ 設定 */
@Serializable
data class EqSettings(
    val enabled: Boolean = false,
    val preset: Int = -1,                 // -1 はカスタム（バンドごとの値を使う）
    val levels: List<Int> = emptyList()   // バンドごとのレベル（ミリベル）。バンド数が変わったら無視
)

/** EQ のバンド1つ分の情報 */
data class EqBand(val centerHz: Int)

/** パネル表示用の EQ 状態 */
data class EqState(
    val settings: EqSettings = EqSettings(),
    val sessions: Map<String, Int> = emptyMap(),   // パッケージ名 → オーディオセッションID
    val bands: List<EqBand> = emptyList(),         // 端末の EQ のバンド（一度でも EQ を作れたら分かる）
    val levelRange: IntRange = -1500..1500,        // ミリベル
    val presets: List<String> = emptyList(),
    val error: String? = null
)

/**
 * 再生アプリが送るオーディオエフェクト用ブロードキャストを受け取り、そのセッションに Equalizer をかける。
 * Android 8 以降はこのブロードキャストをマニフェスト登録では受け取れないため、プロセス起動中に実行時登録する。
 */
class AudioEffects private constructor(context: Context) {

    companion object {
        private const val TAG = "IhlAudioEffects"

        @Volatile
        private var instance: AudioEffects? = null

        fun get(context: Context): AudioEffects =
            instance ?: synchronized(this) {
                instance ?: AudioEffects(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("eq_settings")

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    // セッションIDごとの Equalizer
    private val equalizers = mutableMapOf<Int, Equalizer>()
    private var registered = false
    private var loaded = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: return
            if (session <= 0) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> onOpen(pkg, session)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> onClose(pkg, session)
            }
        }
    }

    /** ブロードキャストの受信を開始する（多重登録はしない） */
    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        runCatching {
            // 他アプリから送られるため EXPORTED で登録する
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            registered = true
        }.onFailure { Log.w(TAG, "register failed", it) }
        scope.launch {
            val raw = appContext.ihlDataStore.data.first()[key]
            val saved = raw?.let { runCatching { json.decodeFromString<EqSettings>(it) }.getOrNull() } ?: EqSettings()
            loaded = true
            _state.value = _state.value.copy(settings = saved)
            applyAll()
        }
    }

    private fun onOpen(pkg: String, session: Int) {
        _state.value = _state.value.copy(sessions = _state.value.sessions + (pkg to session))
        applyAll()
    }

    private fun onClose(pkg: String, session: Int) {
        equalizers.remove(session)?.let { runCatching { it.release() } }
        val sessions = _state.value.sessions.filterNot { it.key == pkg && it.value == session }
        _state.value = _state.value.copy(sessions = sessions)
    }

    /** 指定パッケージが EQ 用のセッションを通知しているか */
    fun sessionOf(packageName: String): Int? = _state.value.sessions[packageName]

    /** 全セッションに現在の設定を適用する（無効なら Equalizer を解放） */
    private fun applyAll() {
        if (!loaded) return
        val settings = _state.value.settings
        val active = _state.value.sessions.values.toSet()
        // 終わったセッションの Equalizer を解放
        equalizers.keys.filter { it !in active }.forEach { equalizers.remove(it)?.let { e -> runCatching { e.release() } } }

        var error: String? = null
        for (session in active) {
            if (!settings.enabled) {
                equalizers.remove(session)?.let { runCatching { it.release() } }
                continue
            }
            val eq = equalizers[session] ?: runCatching { Equalizer(0, session) }
                .onFailure { error = "EQ を作成できませんでした（${it.javaClass.simpleName}）" }
                .getOrNull()?.also { equalizers[session] = it } ?: continue
            runCatching {
                readDeviceInfo(eq)
                eq.enabled = true
                if (settings.preset >= 0 && settings.preset < eq.numberOfPresets) {
                    eq.usePreset(settings.preset.toShort())
                } else if (settings.levels.size == eq.numberOfBands.toInt()) {
                    settings.levels.forEachIndexed { i, level -> eq.setBandLevel(i.toShort(), level.toShort()) }
                }
            }.onFailure { error = "EQ を適用できませんでした（${it.javaClass.simpleName}）" }
        }
        _state.value = _state.value.copy(error = error)
    }

    /** 端末の EQ のバンド・範囲・プリセットを読む（初回のみ） */
    private fun readDeviceInfo(eq: Equalizer) {
        if (_state.value.bands.isNotEmpty()) return
        val bands = (0 until eq.numberOfBands).map { EqBand(eq.getCenterFreq(it.toShort()) / 1000) }
        val range = eq.bandLevelRange
        val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
        // カスタム値が未設定なら現在値（通常は 0）で埋める
        val levels = _state.value.settings.levels.takeIf { it.size == bands.size }
            ?: (0 until bands.size).map { eq.getBandLevel(it.toShort()).toInt() }
        _state.value = _state.value.copy(
            bands = bands,
            levelRange = range[0].toInt()..range[1].toInt(),
            presets = presets,
            settings = _state.value.settings.copy(levels = levels)
        )
    }

    // ---- 設定の変更（即時適用・保存） ----

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    /** プリセットを選ぶ。選んだプリセットのバンド値をカスタム値の初期値として取り込む */
    fun selectPreset(index: Int) {
        val eq = equalizers.values.firstOrNull()
        val levels = if (eq != null && index >= 0) runCatching {
            eq.usePreset(index.toShort())
            (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() }
        }.getOrNull() else null
        update { it.copy(preset = index, levels = levels ?: it.levels) }
    }

    /** バンドの値を変える（カスタムに切り替わる） */
    fun setBandLevel(band: Int, level: Int) = update { s ->
        val levels = s.levels.toMutableList()
        while (levels.size <= band) levels.add(0)
        levels[band] = level
        s.copy(preset = -1, levels = levels)
    }

    fun reset() = update { EqSettings(enabled = it.enabled, preset = -1, levels = it.levels.map { 0 }) }

    private fun update(transform: (EqSettings) -> EqSettings) {
        val next = transform(_state.value.settings)
        _state.value = _state.value.copy(settings = next)
        applyAll()
        scope.launch(Dispatchers.IO) {
            val raw = json.encodeToString(next)
            appContext.ihlDataStore.edit { it[key] = raw }
        }
    }
}
