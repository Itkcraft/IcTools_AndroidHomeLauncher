package com.ictools.ichomelauncher.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.IhlTextStyle

/** ターミナルパネルの中身：上部に出力ログ、下部にプロンプトと入力欄 */
@Composable
fun TerminalPanel(session: TerminalSession) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    // 履歴の参照位置（-1 は履歴を参照していない状態）
    var historyIndex by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val lines = session.lines

    // 新しい行が追加されたら最下部へスクロール
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    fun run() {
        session.execute(input.text)
        input = TextFieldValue("")
        historyIndex = -1
    }

    Column(Modifier.fillMaxSize().padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 4.dp)) {
        // ---- 出力ログ ----
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            items(lines.size) { index ->
                Text(lines[index])
            }
        }
        // ---- 入力行（右下のリサイズハンドルと重ならないよう右側に余白） ----
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 履歴呼び出しボタン（押すたびに1つ前の入力へ）
            Text(
                "↑",
                color = if (session.history.isEmpty()) IhlColors.TextDim else IhlColors.Accent,
                modifier = Modifier
                    .clickable {
                        val history = session.history
                        if (history.isEmpty()) return@clickable
                        historyIndex = if (historyIndex < 0) history.lastIndex else (historyIndex - 1).coerceAtLeast(0)
                        val text = history[historyIndex]
                        input = TextFieldValue(text, selection = TextRange(text.length))
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Text(TerminalSession.PROMPT, color = IhlColors.Accent, modifier = Modifier.padding(end = 6.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = IhlTextStyle,
                cursorBrush = SolidColor(IhlColors.Accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onSend = { run() }, onDone = { run() }),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
