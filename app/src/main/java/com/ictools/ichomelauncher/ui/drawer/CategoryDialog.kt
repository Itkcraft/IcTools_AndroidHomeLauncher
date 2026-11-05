package com.ictools.ichomelauncher.ui.drawer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.ui.common.WireButton
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.IhlTextStyle
import com.ictools.ichomelauncher.ui.theme.SmallCutShape

/**
 * アプリの種類を選ぶダイアログ。
 * 一覧から選ぶ／「自動」に戻す／新しい種類を作る（作った種類をそのアプリに設定する）。
 */
@Composable
fun CategoryDialog(
    app: AppEntry,
    current: String,
    isOverridden: Boolean,
    categories: List<String>,
    onSelect: (String?) -> Unit,   // null は「自動に戻す」
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IhlColors.PanelBackground.copy(alpha = 0.95f),
        title = { Text("種類：${app.label}", color = IhlColors.Accent, fontSize = 14.sp) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                // 自動に戻す
                Option(
                    label = "自動（${app.autoCategory}）",
                    selected = !isOverridden,
                    onClick = { onSelect(null) }
                )
                categories.forEach { c ->
                    Option(label = c, selected = isOverridden && c == current, onClick = { onSelect(c) })
                }
                // 新しい種類
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .border(1.dp, IhlColors.Border, SmallCutShape)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        if (newName.isEmpty()) Text("新しい種類", color = IhlColors.TextDim)
                        BasicTextField(
                            value = newName,
                            onValueChange = { newName = it.take(20) },
                            singleLine = true,
                            textStyle = IhlTextStyle,
                            cursorBrush = SolidColor(IhlColors.Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (newName.isNotBlank()) onSelect(newName.trim()) }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    WireButton("追加", enabled = newName.isNotBlank(), onClick = { onSelect(newName.trim()) })
                }
            }
        },
        confirmButton = {
            Text("閉じる", color = IhlColors.TextDim, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
        }
    )
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        (if (selected) "(●) " else "( ) ") + label,
        color = if (selected) IhlColors.Accent else IhlColors.Text,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp)
    )
}
