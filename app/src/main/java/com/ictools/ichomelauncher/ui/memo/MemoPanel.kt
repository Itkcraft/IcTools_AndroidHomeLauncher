package com.ictools.ichomelauncher.ui.memo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ictools.ichomelauncher.data.Memo
import com.ictools.ichomelauncher.ui.common.shortTime
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.IhlTextStyle
import kotlinx.coroutines.delay

/** メモパネルの中身：本文の編集欄と、更新時刻・削除ボタン */
@Composable
fun MemoPanel(memo: Memo, onEdit: (String) -> Unit, onDelete: () -> Unit) {
    // 編集中のカーソル位置を保つため、本文は画面側でも保持する
    var value by remember(memo.id) { mutableStateOf(TextFieldValue(memo.text)) }
    // 削除は2回押しで確定（誤操作防止）
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3000)
            confirmDelete = false
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 4.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (value.text.isEmpty()) Text("memo...", color = IhlColors.TextDim)
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    onEdit(it.text)
                },
                textStyle = IhlTextStyle,
                cursorBrush = SolidColor(IhlColors.Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxSize()
            )
        }
        // フッター（右下のリサイズハンドルと重ならないよう右側に余白）
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shortTime(memo.updatedAt), color = IhlColors.TextDim, modifier = Modifier.weight(1f))
            Text(
                if (confirmDelete) "del?" else "del",
                color = if (confirmDelete) IhlColors.Accent else IhlColors.TextDim,
                modifier = Modifier
                    .clickable { if (confirmDelete) onDelete() else confirmDelete = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
