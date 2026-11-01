package com.ictools.ichomelauncher.ui.html

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ictools.ichomelauncher.data.HtmlEntry
import com.ictools.ichomelauncher.data.HtmlLibrary
import com.ictools.ichomelauncher.gesture.PinchExclusion
import com.ictools.ichomelauncher.ui.common.WireButton
import com.ictools.ichomelauncher.ui.theme.IhlColors
import kotlinx.coroutines.delay

/** HTML パネルから呼ぶ操作 */
class HtmlActions(
    val add: (Uri) -> Unit,
    val select: (String) -> Unit,
    val remove: (HtmlEntry) -> Unit,
    val canRead: (HtmlEntry) -> Boolean,
    val openExternal: (Uri) -> Boolean
)

/** ファイルピッカーで選べる種類 */
private val HTML_MIME_TYPES = arrayOf("text/html", "application/xhtml+xml")

/**
 * HTML パネル：ファイルピッカーで追加した HTML ファイルを WebView（JavaScript 有効）で表示する。
 * 一覧は保存され、上部のファイル名から表示するファイルを切り替えられる。≡ で一覧（追加・削除）を開く。
 */
@Composable
fun HtmlPanel(library: HtmlLibrary, actions: HtmlActions) {
    var showList by rememberSaveable { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            actions.add(it)
            showList = false
        }
    }
    val current = library.current

    Column(Modifier.fillMaxSize()) {
        // ---- 上部：表示中のファイル（タップで切り替え）・再読み込み・追加・一覧 ----
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                Text(
                    (current?.name ?: "no file") + if (library.entries.size > 1) " ▾" else "",
                    color = if (current != null) IhlColors.Accent else IhlColors.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = library.entries.isNotEmpty()) { menu = true }.padding(vertical = 6.dp)
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    library.entries.forEach { e ->
                        DropdownMenuItem(
                            text = { Text((if (e.uri == library.selected) "> " else "  ") + e.name) },
                            onClick = {
                                menu = false
                                showList = false
                                actions.select(e.uri)
                            }
                        )
                    }
                }
            }
            HeaderButton("⟳", enabled = current != null && !showList) { reloadKey++ }
            HeaderButton("+") { picker.launch(HTML_MIME_TYPES) }
            HeaderButton(if (showList) "×" else "≡") { showList = !showList }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(IhlColors.Line))

        // ---- 本体 ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                showList -> FileList(library, actions, onAdd = { picker.launch(HTML_MIME_TYPES) }, onPicked = { showList = false })
                current == null -> EmptyState { picker.launch(HTML_MIME_TYPES) }
                !actions.canRead(current) -> Unreadable(current, actions)
                else -> HtmlView(current.uri, reloadKey, actions.openExternal)
            }
        }
    }
}

@Composable
private fun HeaderButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) IhlColors.Accent else IhlColors.Line,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("HTML ファイルがありません")
        Spacer(Modifier.height(8.dp))
        WireButton("+ HTML ファイルを追加", onClick = onAdd)
        Spacer(Modifier.height(8.dp))
        Text(
            "CSS・JavaScript・画像を1つのファイルにまとめた HTML がおすすめです（別ファイルの相対パス参照は読み込めません）",
            color = IhlColors.TextDim, fontSize = 10.sp, lineHeight = 13.sp
        )
    }
}

/** ファイルが移動・削除されて読めなくなった場合 */
@Composable
private fun Unreadable(entry: HtmlEntry, actions: HtmlActions) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("「${entry.name}」を開けません", color = IhlColors.Accent)
        Text("ファイルが移動・削除されたか、アクセス許可が失われた可能性があります", color = IhlColors.TextDim, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(8.dp))
        WireButton("一覧から削除", onClick = { actions.remove(entry) })
    }
}

/** 一覧：タップで表示を切り替え、del（2回押し）で削除 */
@Composable
private fun FileList(library: HtmlLibrary, actions: HtmlActions, onAdd: () -> Unit, onPicked: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        items(library.entries, key = { it.uri }) { e ->
            var confirm by remember { mutableStateOf(false) }
            LaunchedEffect(confirm) {
                if (confirm) {
                    delay(3000)
                    confirm = false
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (e.uri == library.selected) "> " else "  ") + e.name,
                    color = if (e.uri == library.selected) IhlColors.Accent else IhlColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            actions.select(e.uri)
                            onPicked()
                        }
                        .padding(vertical = 8.dp)
                )
                Text(
                    if (confirm) "del?" else "del",
                    color = if (confirm) IhlColors.Accent else IhlColors.TextDim,
                    modifier = Modifier
                        .clickable { if (confirm) actions.remove(e) else confirm = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            WireButton("+ HTML ファイルを追加", onClick = onAdd)
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * WebView で表示する（JavaScript 有効）。
 * ページ内の http(s) リンクは外部ブラウザで開く。この範囲では画面全体のピンチを無効にして、ページの拡大縮小を優先する。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlView(uri: String, reloadKey: Int, openExternal: (Uri) -> Boolean) {
    // WebViewClient は一度だけ作るため、ファイルごとに作り直さない状態にする（切り替え時は LaunchedEffect で消す）
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // アプリが裏に回ったら WebView も止める（JavaScript のタイマーなど）
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            PinchExclusion.remove(PINCH_KEY)
            webView?.destroy()
            webView = null
        }
    }
    // ファイルの切り替え・再読み込み
    LaunchedEffect(uri, reloadKey, webView) {
        val view = webView ?: return@LaunchedEffect
        error = null
        if (view.url == uri && reloadKey > 0) view.reload() else view.loadUrl(uri)
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { PinchExclusion.set(PINCH_KEY, it.boundsInRoot()) }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // ローカルファイルへの直接アクセスは許可せず、ピッカーで選んだ content:// だけを読む
                    settings.allowFileAccess = false
                    settings.allowContentAccess = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme?.lowercase()
                            if (request.isForMainFrame && (scheme == "http" || scheme == "https" || scheme == "mailto" || scheme == "tel")) {
                                openExternal(request.url)
                                return true
                            }
                            return false
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                            if (request.isForMainFrame) error = err.description?.toString() ?: "error ${err.errorCode}"
                        }
                    }
                    webView = this
                }
            }
        )
        error?.let {
            Text(
                "読み込めませんでした：$it",
                color = IhlColors.Accent,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).background(IhlColors.PanelBackground).padding(6.dp)
            )
        }
    }
}

private const val PINCH_KEY = "html"
