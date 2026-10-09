package com.ictools.ichomelauncher.ui.favorites

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.SmallCutShape

private val ROW_HEIGHT = 42.dp
private val ICON_SIZE = 30.dp

/** お気に入りパネル：アイコン付き一覧。タップで起動、長押ししてドラッグで並び替え */
@Composable
fun FavoritesPanel(
    favorites: List<AppEntry>,
    loadIcon: suspend (AppEntry, Int) -> ImageBitmap?,
    onLaunch: (AppEntry) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    if (favorites.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Text("no favorites", color = IhlColors.TextDim)
            Text(
                "Apps パネルでアプリを長押し、またはターミナルで fav add <アプリ名> で追加できます",
                color = IhlColors.TextDim, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
        return
    }
    // ドラッグ中はこの並びを動かし、指を離したら保存する
    var items by remember(favorites) { mutableStateOf(favorites) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    fun finishDrag() {
        if (draggingKey != null) onReorder(items.map { it.key })
        draggingKey = null
        dragOffset = 0f
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 22.dp)) {
        items.forEach { app ->
            key(app.key) {
                val dragging = draggingKey == app.key
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .background(if (dragging) IhlColors.Line else Color.Transparent, SmallCutShape)
                        .pointerInput(app.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingKey = app.key
                                    dragOffset = 0f
                                },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { finishDrag() }
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val index = items.indexOfFirst { it.key == app.key }
                                // 半行以上動いたら隣と入れ替える
                                if (dragOffset > rowPx / 2 && index < items.lastIndex) {
                                    items = items.toMutableList().apply { add(index + 1, removeAt(index)) }
                                    dragOffset -= rowPx
                                } else if (dragOffset < -rowPx / 2 && index > 0) {
                                    items = items.toMutableList().apply { add(index - 1, removeAt(index)) }
                                    dragOffset += rowPx
                                }
                            }
                        }
                        .clickable { onLaunch(app) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app, loadIcon)
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("≡", color = IhlColors.TextDim)
                }
            }
        }
        Text("長押ししてドラッグで並び替え", color = IhlColors.TextDim, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun AppIcon(app: AppEntry, loadIcon: suspend (AppEntry, Int) -> ImageBitmap?) {
    val sizePx = with(LocalDensity.current) { ICON_SIZE.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.key) { value = loadIcon(app, sizePx) }
    Box(Modifier.size(ICON_SIZE)) {
        val bitmap = icon
        if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(IhlColors.Line, SmallCutShape))
    }
}
