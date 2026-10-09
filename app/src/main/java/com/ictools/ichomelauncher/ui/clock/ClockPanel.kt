package com.ictools.ichomelauncher.ui.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.ui.theme.IhlColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 時計パネル：時刻・日付・曜日。文字の大きさはパネルの幅に合わせる */
@Composable
fun ClockPanel() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // 秒の切り替わりに合わせて更新
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 60_000) { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(now)) }
    val day = remember(now / 60_000) { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now)) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        // 等幅 8 文字（HH:mm:ss）が幅に収まる大きさ
        val timeSize = (maxWidth.value / 5.2f).coerceIn(14f, 96f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(time, color = IhlColors.Accent, fontSize = timeSize.sp, lineHeight = (timeSize * 1.1f).sp)
            Text(date, fontSize = (timeSize * 0.32f).coerceAtLeast(11f).sp)
            Text(day, color = IhlColors.TextDim, fontSize = (timeSize * 0.26f).coerceAtLeast(10f).sp)
        }
    }
}
