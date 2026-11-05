package com.ictools.ichomelauncher.ui.favorites

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ictools.ichomelauncher.data.AppEntry
import com.ictools.ichomelauncher.data.CategoryData
import com.ictools.ichomelauncher.ui.theme.IhlColors
import com.ictools.ichomelauncher.ui.theme.SmallCutShape
import kotlin.math.max
import kotlin.math.roundToInt

private val GRID_CELL_MIN_WIDTH = 76.dp
private val GRID_CELL_HEIGHT = 86.dp
private val LIST_ROW_HEIGHT = 42.dp

/**
 * お気に入りパネル：グリッド／リスト表示。タップで起動、長押ししてドラッグで並び替え。
 *
 * 並び替えのドラッグは各セルではなく一覧全体（座標が並び替えで変わらない領域）で検出する。
 * セル単位で検出すると、入れ替えでセルが動いた分だけ指の座標がずれて「飛ぶ」「元に戻る」ため。
 */
@Composable
fun FavoritesPanel(
    favorites: List<AppEntry>,
    grid: Boolean,
    onToggleGrid: () -> Unit,
    showCategoryTabs: Boolean,
    categories: CategoryData,
    loadIcon: suspend (AppEntry, Int) -> ImageBitmap?,
    onLaunch: (AppEntry) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    // ---- 種類のタブ（お気に入りにある種類だけ。設定で非表示にできる） ----
    var selectedTab by rememberSaveable { mutableStateOf<String?>(null) }  // null は「すべて」
    val present = remember(favorites, categories) {
        val inUse = favorites.map { categories.of(it) }.toSet()
        categories.all.filter { it in inUse } + inUse.filterNot { it in categories.all }
    }
    val tabsEnabled = showCategoryTabs && present.size > 1
    // 切り替えの順番：すべて → 各種類（端まで行ったら反対側に戻る）
    val tabs: List<String?> = remember(present) { listOf<String?>(null) + present }
    val tab = selectedTab?.takeIf { tabsEnabled && it in present }
    // 直前の切り替えの向き（アニメーションの向き用。+1 は次へ、-1 は前へ）
    var direction by remember { mutableIntStateOf(1) }

    fun move(step: Int) {
        if (!tabsEnabled) return
        // スワイプの検出は長く動き続けるため、その時点の選択を状態から読み直す
        val current = selectedTab?.takeIf { it in present }
        val i = tabs.indexOf(current).coerceAtLeast(0)
        direction = step
        selectedTab = tabs[(i + step + tabs.size) % tabs.size]
    }

    val shown = remember(favorites, categories, tab) {
        if (tab == null) favorites else favorites.filter { categories.of(it) == tab }
    }

    Column(Modifier.fillMaxSize().padding(start = 6.dp, top = 2.dp, end = 6.dp)) {
        // ---- ヘッダー：件数　< 種類 >　表示切り替え ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} apps", color = IhlColors.TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (tabsEnabled) {
                Text("<", color = IhlColors.Accent, modifier = Modifier.clickable { move(-1) }.padding(horizontal = 8.dp, vertical = 4.dp))
                Text(
                    tab ?: "すべて",
                    color = IhlColors.Accent,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 56.dp, max = 140.dp)
                )
                Text(">", color = IhlColors.Accent, modifier = Modifier.clickable { move(1) }.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    if (grid) "grid" else "list",
                    color = IhlColors.TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable(onClick = onToggleGrid).padding(4.dp)
                )
            }
        }
        if (favorites.isEmpty()) {
            Text("no favorites", color = IhlColors.TextDim)
            Text(
                "Apps パネルでアプリを長押し、またはターミナルで fav add <アプリ名> で追加できます",
                color = IhlColors.TextDim, fontSize = 11.sp, lineHeight = 15.sp
            )
            return@Column
        }
        val swipeThreshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // 左右のスワイプで種類を切り替える（タップ・縦スクロール・長押しの並び替えとは別に検出）
                .pointerInput(tabsEnabled, tabs) {
                    if (!tabsEnabled) return@pointerInput
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            when {
                                total <= -swipeThreshold -> move(1)
                                total >= swipeThreshold -> move(-1)
                            }
                        }
                    ) { change, amount ->
                        change.consume()
                        total += amount
                    }
                }
        ) {
            val columns = if (grid) max(1, (maxWidth / GRID_CELL_MIN_WIDTH).toInt()) else 1
            val cellW = maxWidth / columns
            val cellH = if (grid) GRID_CELL_HEIGHT else LIST_ROW_HEIGHT
            // 種類を切り替えたら横にスライドして入れ替える（並び替えの状態も作り直す）
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val d = direction
                    (slideInHorizontally(tween(220)) { w -> w * d } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> -w * d } + fadeOut(tween(220)))
                },
                label = "favorites-tab"
            ) { t ->
                val list = if (t == tab) shown else if (t == null) favorites else favorites.filter { categories.of(it) == t }
                ReorderableGrid(list, columns, cellW, cellH, grid, loadIcon, onLaunch, onReorder)
            }
        }
    }
}

/** 種類を切り替えるスワイプの距離 */
private val SWIPE_THRESHOLD = 56.dp

@Composable
private fun ReorderableGrid(
    favorites: List<AppEntry>,
    columns: Int,
    cellW: Dp,
    cellH: Dp,
    grid: Boolean,
    loadIcon: suspend (AppEntry, Int) -> ImageBitmap?,
    onLaunch: (AppEntry) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    val density = LocalDensity.current
    val cellWpx = with(density) { cellW.toPx() }
    val cellHpx = with(density) { cellH.toPx() }

    // 表示中の並び（ドラッグ中はここだけを動かし、指を離したら保存する）
    var order by remember { mutableStateOf(favorites) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }   // 指の位置（一覧全体の座標）
    var grabOffset by remember { mutableStateOf(Offset.Zero) } // 指とセル左上のずれ

    // 保存された並びが変わったら反映（ドラッグ中は上書きしない）
    LaunchedEffect(favorites) { if (dragKey == null) order = favorites }

    fun cellTopLeft(index: Int) = Offset((index % columns) * cellWpx, (index / columns) * cellHpx)

    /** 座標にあるセルの番号（範囲外は端に寄せる） */
    fun indexAt(pos: Offset, clamp: Boolean): Int? {
        val col = (pos.x / cellWpx).toInt().coerceIn(0, columns - 1)
        val row = (pos.y / cellHpx).toInt()
        val i = row * columns + col
        return when {
            clamp -> i.coerceIn(0, order.lastIndex)
            pos.y < 0 || i !in order.indices -> null
            else -> i
        }
    }

    fun finishDrag() {
        if (dragKey != null) onReorder(order.map { it.key })
        dragKey = null
    }

    val rows = (order.size + columns - 1) / columns
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(cellH * rows + 24.dp)
                // タップで起動
                .pointerInput(columns, cellWpx, cellHpx) {
                    detectTapGestures(onTap = { pos -> indexAt(pos, clamp = false)?.let { onLaunch(order[it]) } })
                }
                // 長押し → ドラッグで並び替え
                .pointerInput(columns, cellWpx, cellHpx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            val i = indexAt(pos, clamp = false) ?: return@detectDragGesturesAfterLongPress
                            dragKey = order[i].key
                            dragPos = pos
                            grabOffset = pos - cellTopLeft(i)
                        },
                        onDragEnd = { finishDrag() },
                        onDragCancel = { finishDrag() }
                    ) { change, amount ->
                        val key = dragKey ?: return@detectDragGesturesAfterLongPress
                        change.consume()
                        dragPos += amount
                        // 指の下のセルへ移動（指の位置は一覧全体の座標なので並び替えでずれない）
                        val from = order.indexOfFirst { it.key == key }
                        val to = indexAt(dragPos, clamp = true) ?: return@detectDragGesturesAfterLongPress
                        if (from >= 0 && to != from) {
                            order = order.toMutableList().apply { add(to, removeAt(from)) }
                        }
                    }
                }
        ) {
            order.forEachIndexed { index, app ->
                key(app.key) {
                    val dragging = app.key == dragKey
                    val target = cellTopLeft(index)
                    // 他のセルは新しい位置へなめらかに移動、ドラッグ中のセルは指に追従
                    val animated by animateIntOffsetAsState(
                        IntOffset(target.x.roundToInt(), target.y.roundToInt()),
                        label = "favorite-cell"
                    )
                    val offset = if (dragging) {
                        val p = dragPos - grabOffset
                        IntOffset(p.x.roundToInt(), p.y.roundToInt())
                    } else {
                        animated
                    }
                    Box(
                        Modifier
                            .offset { offset }
                            .size(cellW, cellH)
                            .zIndex(if (dragging) 1f else 0f)
                            .padding(2.dp)
                            .background(if (dragging) IhlColors.Line else Color.Transparent, SmallCutShape)
                    ) {
                        if (grid) GridCell(app, loadIcon) else ListCell(app, loadIcon)
                    }
                }
            }
            Text(
                "長押ししてドラッグで並び替え",
                color = IhlColors.TextDim,
                fontSize = 10.sp,
                modifier = Modifier.offset { IntOffset(0, (rows * cellHpx).roundToInt() + 4) }
            )
        }
    }
}

@Composable
private fun GridCell(app: AppEntry, loadIcon: suspend (AppEntry, Int) -> ImageBitmap?) {
    Column(
        Modifier.fillMaxSize().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        AppIcon(app, 42.dp, loadIcon)
        Spacer(Modifier.height(4.dp))
        Text(
            app.label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ListCell(app: AppEntry, loadIcon: suspend (AppEntry, Int) -> ImageBitmap?) {
    Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, 30.dp, loadIcon)
        Spacer(Modifier.width(10.dp))
        Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("≡", color = IhlColors.TextDim)
    }
}

@Composable
private fun AppIcon(app: AppEntry, size: Dp, loadIcon: suspend (AppEntry, Int) -> ImageBitmap?) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.key, sizePx) { value = loadIcon(app, sizePx) }
    Box(Modifier.size(size)) {
        val bitmap = icon
        if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(IhlColors.Line, SmallCutShape))
    }
}
