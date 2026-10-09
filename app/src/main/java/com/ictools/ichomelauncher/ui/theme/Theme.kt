package com.ictools.ichomelauncher.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.data.Appearance
import com.ictools.ichomelauncher.data.PanelShapeType

/**
 * ワイヤーフレーム風の配色。
 * ワイヤー色・パネル背景は設定で変えられるため Compose の状態として持ち、変更が即座に全画面へ反映されるようにする。
 */
object IhlColors {
    /** ワイヤー（枠線）の色 */
    var wire by mutableStateOf(Color(Appearance.DEFAULT_WIRE))
        private set

    /** パネル背景の色（不透明度は別） */
    var panelBase by mutableStateOf(Color(Appearance.DEFAULT_PANEL))
        private set

    /** パネル背景の不透明度 */
    var panelAlpha by mutableFloatStateOf(Appearance.DEFAULT_PANEL_ALPHA)
        private set

    val PanelBackground: Color get() = panelBase.copy(alpha = panelAlpha)
    val Border: Color get() = wire.copy(alpha = wire.alpha * 0.55f)     // 枠線（通常）
    val BorderFocused: Color get() = wire                                // 枠線（フォーカス中）
    val Line: Color get() = wire.copy(alpha = wire.alpha * 0.33f)       // 区切り線
    val Text = Color(0xFFE6E6E6)                                         // 本文
    val TextDim = Color(0xFF9A9A9A)                                      // 補足・プレースホルダ
    val Accent = Color.White

    internal fun apply(a: Appearance) {
        wire = Color(a.wireColor)
        panelBase = Color(a.panelColor)
        panelAlpha = a.panelAlpha
    }
}

/** パネルの形の設定（状態） */
object IhlShapes {
    var type by mutableStateOf(PanelShapeType.CUT)
        private set
    var cornerDp by mutableFloatStateOf(Appearance.DEFAULT_CORNER)
        private set

    internal fun apply(a: Appearance) {
        type = a.shape
        cornerDp = a.cornerDp
    }

    fun shape(corner: Float): Shape = when (type) {
        // 右上と左下を斜めにカット
        PanelShapeType.CUT -> CutCornerShape(topStart = 0.dp, topEnd = corner.dp, bottomEnd = 0.dp, bottomStart = corner.dp)
        PanelShapeType.ROUND -> RoundedCornerShape(corner.dp)
        PanelShapeType.SQUARE -> RectangleShape
    }
}

/** パネルの形（設定に従う） */
val PanelShape: Shape get() = IhlShapes.shape(IhlShapes.cornerDp)

/** ボタン等の小さい部品用の形（パネルの形の半分の大きさ） */
val SmallCutShape: Shape get() = IhlShapes.shape(IhlShapes.cornerDp / 2f)

/** 等幅フォントの基本文字スタイル */
val IhlTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    color = IhlColors.Text
)

/** アプリ全体のテーマ（Material3 部品の配色も黒基調に揃える） */
@Composable
fun IhlTheme(appearance: Appearance, content: @Composable () -> Unit) {
    // 見た目の設定を反映（変更時は即座に全体へ伝わる）
    SideEffect {
        IhlColors.apply(appearance)
        IhlShapes.apply(appearance)
    }
    val scheme = darkColorScheme(
        primary = IhlColors.Accent,
        onPrimary = Color.Black,
        surface = Color(0xFF0E0E0E),
        onSurface = IhlColors.Text,
        surfaceContainer = Color(0xFF0E0E0E),
        background = Color.Transparent
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalTextStyle provides IhlTextStyle,
            LocalContentColor provides IhlColors.Text,
            content = content
        )
    }
}
