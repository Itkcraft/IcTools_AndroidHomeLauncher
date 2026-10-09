package com.ictools.ichomelauncher.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.SmallCutShape
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** ワイヤーフレーム風のボタン */
@Composable
fun WireButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .border(1.dp, if (enabled) IhlColors.Border else IhlColors.Line, SmallCutShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = if (enabled) IhlColors.Text else IhlColors.TextDim)
    }
}

/** 権限が無いときにパネル内へ出す案内 */
@Composable
fun PermissionPrompt(
    message: String,
    buttons: List<Pair<String, () -> Unit>>,
    note: String? = null
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(message)
        buttons.forEach { (label, action) -> WireButton(label, onClick = action) }
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            Text(note, color = IhlColors.TextDim, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

/** 一覧用の短い時刻表示（今日なら時刻、今年なら月日、それ以外は年月日） */
fun shortTime(time: Long, now: Long = System.currentTimeMillis()): String {
    val a = Calendar.getInstance().apply { timeInMillis = time }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    val pattern = when {
        a.get(Calendar.YEAR) != b.get(Calendar.YEAR) -> "yyyy/MM/dd"
        a.get(Calendar.DAY_OF_YEAR) != b.get(Calendar.DAY_OF_YEAR) -> "MM/dd HH:mm"
        else -> "HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
}

/** ミリ秒を m:ss 形式に */
fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
