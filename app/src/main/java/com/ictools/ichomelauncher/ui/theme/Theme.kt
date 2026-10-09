package com.ictools.ichomelauncher.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ワイヤーフレーム風の配色 */
object IhlColors {
    val PanelBackground = Color.Black.copy(alpha = 0.7f)  // パネル背景（不透明度 70%）
    val Border = Color(0xFF8A8A8A)                        // 枠線（通常）
    val BorderFocused = Color(0xFFE6E6E6)                 // 枠線（フォーカス中）
    val Text = Color(0xFFE6E6E6)                          // 本文
    val TextDim = Color(0xFF9A9A9A)                       // 補足・プレースホルダ
    val Accent = Color.White
    val Line = Color(0x55FFFFFF)                          // 区切り線
}

/** パネルの形：右上と左下を斜めにカット */
val PanelShape = CutCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 0.dp, bottomStart = 12.dp)

/** ボタン等の小さい部品用の形 */
val SmallCutShape = CutCornerShape(topStart = 0.dp, topEnd = 6.dp, bottomEnd = 0.dp, bottomStart = 6.dp)

/** 等幅フォントの基本文字スタイル */
val IhlTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    color = IhlColors.Text
)

/** アプリ全体のテーマ（Material3 部品の配色も黒基調に揃える） */
@Composable
fun IhlTheme(content: @Composable () -> Unit) {
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
