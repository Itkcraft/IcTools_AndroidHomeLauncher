package com.ictools.ichomelauncher.ui.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.IhlTextStyle
import com.ictools.ichomelauncher.ui.theme.SmallCutShape

private val ICON_SIZE = 32.dp

/** ドロワーパネルの中身：検索欄とアプリ一覧（タップで起動） */
@Composable
fun DrawerPanel(
    apps: List<AppEntry>,
    loadIcon: suspend (AppEntry, Int) -> ImageBitmap?,
    onLaunch: (AppEntry) -> Unit,
    favoriteKeys: Set<String>,
    onToggleFavorite: (AppEntry) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        // ---- 検索欄 ----
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, IhlColors.Border, SmallCutShape)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (query.isEmpty()) Text("search...", color = IhlColors.TextDim)
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
        Spacer(Modifier.height(6.dp))
        Text("${filtered.size} apps", color = IhlColors.TextDim)
        Spacer(Modifier.height(4.dp))

        // ---- アプリ一覧 ----
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(filtered, key = { it.key }) { app ->
                AppRow(
                    app = app,
                    loadIcon = loadIcon,
                    isFavorite = app.key in favoriteKeys,
                    onClick = { onLaunch(app) },
                    onToggleFavorite = { onToggleFavorite(app) }
                )
            }
        }
    }
}

/** アプリ1行分：アイコン＋アプリ名。長押しでお気に入りの登録・解除メニュー */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    loadIcon: suspend (AppEntry, Int) -> ImageBitmap?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val sizePx = with(LocalDensity.current) { ICON_SIZE.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.key) { value = loadIcon(app, sizePx) }
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(ICON_SIZE)) {
            val bitmap = icon
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(IhlColors.Line, SmallCutShape))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (isFavorite) Text("★", color = IhlColors.TextDim)
        Box {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "お気に入りから外す" else "お気に入りに追加") },
                    onClick = {
                        menu = false
                        onToggleFavorite()
                    }
                )
            }
        }
    }
}
