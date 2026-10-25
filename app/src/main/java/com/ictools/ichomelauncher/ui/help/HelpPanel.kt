package com.ictools.ichomelauncher.ui.help

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.ui.terminal.CommandHelp
import com.ictools.ichomelauncher.ui.terminal.HelpEntry
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.IhlTextStyle
import com.ictools.ichomelauncher.ui.theme.SmallCutShape

/**
 * ヘルプパネル：ターミナルのコマンドをカテゴリのタブと検索で一覧表示する。
 * コマンドをタップするとターミナルを開いて入力欄に入れる（実行はしない）。
 */
@Composable
fun HelpPanel(onInsert: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val sections = CommandHelp.SECTIONS

    val entries: List<HelpEntry> = remember(tab, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            sections[tab].entries
        } else {
            // 検索中は全カテゴリから探す
            sections.flatMap { it.entries }.filter {
                it.usage.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 8.dp, top = 4.dp, end = 8.dp)) {
        // ---- 検索 ----
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, IhlColors.Border, SmallCutShape)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            if (query.isEmpty()) Text("search commands...", color = IhlColors.TextDim)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = IhlTextStyle,
                cursorBrush = SolidColor(IhlColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
        }
        // ---- カテゴリのタブ ----
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
            sections.forEachIndexed { i, s ->
                val selected = i == tab && query.isBlank()
                Text(
                    if (selected) "[${s.title}]" else " ${s.title} ",
                    color = if (selected) IhlColors.Accent else IhlColors.TextDim,
                    modifier = Modifier.clickable { tab = i; query = "" }.padding(end = 4.dp)
                )
            }
        }
        // ---- コマンド一覧 ----
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(entries) { e ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = e.insert.isNotEmpty()) { onInsert(e.insert) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(e.usage, color = IhlColors.Accent)
                    Text(e.description, fontSize = 11.sp, lineHeight = 14.sp)
                    e.example?.let { Text("e.g. $it", color = IhlColors.TextDim, fontSize = 10.sp) }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text("タップでターミナルの入力欄に入ります", color = IhlColors.TextDim, fontSize = 10.sp)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
