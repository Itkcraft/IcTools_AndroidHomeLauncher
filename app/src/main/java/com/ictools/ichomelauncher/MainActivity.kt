package com.ictools.ichomelauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ictools.ichomelauncher.ui.LauncherScreen
import com.ictools.ichomelauncher.ui.theme.IhlTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.function.Consumer
import kotlin.math.roundToInt

/** ホーム Activity。壁紙の表示（テーマで透過）とウィンドウ背後のぼかし制御を行う */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    // ぼかしの有効／無効（省電力モード等で変化する）を監視するリスナー
    private val blurListener = Consumer<Boolean> { enabled -> viewModel.setBlurAvailable(enabled) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ステータスバー・ナビゲーションバーの裏まで描画し、バーは透過にする
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false

        // ホームアプリなので「戻る」で終了しないようにする
        onBackPressedDispatcher.addCallback(this) { }

        viewModel.setBlurAvailable(windowManager.isCrossWindowBlurEnabled)

        // 設定またはぼかし可否が変わったらウィンドウのぼかし半径を更新する
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.settings, viewModel.blurAvailable) { s, available ->
                    if (s.background.blurEnabled && available) s.background.blurRadiusDp else 0f
                }.collect { radiusDp -> applyBlur(radiusDp) }
            }
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            IhlTheme(settings.appearance) {
                LauncherScreen(viewModel, onOpenHomeSettings = ::openHomeSettings)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 設定画面で権限を変えて戻ってきた場合に反映する
        viewModel.refreshPermissions()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 登録時に現在の状態も1回通知される
        windowManager.addCrossWindowBlurEnabledListener(mainExecutor, blurListener)
    }

    override fun onDetachedFromWindow() {
        windowManager.removeCrossWindowBlurEnabledListener(blurListener)
        super.onDetachedFromWindow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // ホームボタンで再度呼ばれた場合：v0.1.0 では何もしない
    }

    /** ウィンドウ背後（＝壁紙）をぼかす。0 で無効 */
    private fun applyBlur(radiusDp: Float) {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, radiusDp, resources.displayMetrics)
        window.setBackgroundBlurRadius(px.roundToInt())
    }

    /** 「デフォルトのホームアプリ」設定画面を開く */
    private fun openHomeSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
}
