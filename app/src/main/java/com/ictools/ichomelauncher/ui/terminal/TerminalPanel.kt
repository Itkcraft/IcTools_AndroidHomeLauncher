package com.ictools.ichomelauncher.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.IhlTextStyle

/** ターミナルパネルの中身：上部に出力ログ、下部にプロンプトと入力欄（補完候補をグレーで重ねて表示） */
@Composable
fun TerminalPanel(session: TerminalSession) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    // 履歴の参照位置（-1 は履歴を参照していない状態）
    var historyIndex by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val lines = session.lines

    // 補完候補（入力が変わるたびに計算）
    val suggestion = remember(input.text, lines.size) { session.suggest(input.text) }

    // 新しい行が追加されたら最下部へスクロール
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    fun run() {
        session.execute(input.text)
        input = TextFieldValue("")
        historyIndex = -1
    }

    /** 補完候補を確定する */
    fun accept(): Boolean {
        val s = suggestion ?: return false
        input = TextFieldValue(s, selection = TextRange(s.length))
        return true
    }

    Column(Modifier.fillMaxSize().padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 4.dp)) {
        // ---- 出力ログ（操作付きの行はタップで実行） ----
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            items(lines.size) { index ->
                val line = lines[index]
                val action = line.action
                if (action != null) {
                    Text(
                        line.text,
                        color = IhlColors.Accent,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(onClick = action)
                    )
                } else {
                    Text(line.text)
                }
            }
        }
        // ---- 入力行（右下のリサイズハンドルと重ならないよう右側に余白） ----
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, end = 16.dp),
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
                onValueChange = {
                    input = it
                    historyIndex = -1
                },
                singleLine = true,
                textStyle = IhlTextStyle,
                cursorBrush = SolidColor(IhlColors.Accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onSend = { run() }, onDone = { run() }),
                modifier = Modifier
                    .weight(1f)
                    // 外付けキーボードの Tab で補完を確定
                    .onPreviewKeyEvent { e ->
                        if (e.key == Key.Tab && e.type == KeyEventType.KeyDown) accept() || true
                        else e.key == Key.Tab
                    },
                decorationBox = { inner ->
                    Box {
                        // ゴーストテキスト：入力済み部分は透明、続きをグレーで重ねる
                        val s = suggestion
                        if (s != null && s.length > input.text.length) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color.Transparent)) { append(input.text) }
                                    withStyle(SpanStyle(color = IhlColors.TextDim)) { append(s.substring(input.text.length)) }
                                },
                                style = IhlTextStyle,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        inner()
                    }
                }
            )
            // 補完の確定ボタン（ソフトキーボードの Tab の代わり）
            Text(
                "→",
                color = if (suggestion != null) IhlColors.Accent else IhlColors.TextDim,
                modifier = Modifier
                    .clickable(enabled = suggestion != null) { accept() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
