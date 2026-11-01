package com.ictools.ichomelauncher.ui.html

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import java.io.ByteArrayInputStream

/** HTML パネルから呼ぶ操作 */
class HtmlActions(
    val add: (Uri) -> Unit,
    val select: (String) -> Unit,
    val remove: (HtmlEntry) -> Unit,
    val canRead: (HtmlEntry) -> Boolean,
    val read: suspend (HtmlEntry) -> String?,
    val openExternal: (Uri) -> Boolean,
    val vibrate: (Long) -> Unit,
    val dismissMessage: () -> Unit
)

/**
 * ファイルピッカーで選べる種類。
 * .html を text/html として扱わないファイルアプリもあるため、すべてのファイルを選べるようにする。
 */
private val HTML_MIME_TYPES = arrayOf("*/*")

/**
 * HTML パネル：ファイルピッカーで追加した HTML ファイルを WebView（JavaScript 有効）で表示する。
 * 一覧は保存され、上部のファイル名から表示するファイルを切り替えられる。≡ で一覧（追加・削除）を開く。
 */
@Composable
fun HtmlPanel(library: HtmlLibrary, message: String?, actions: HtmlActions) {
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

        // 追加に失敗したときなどのお知らせ（タップで閉じる）
        message?.let {
            Text(
                it,
                color = IhlColors.Accent,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().clickable(onClick = actions.dismissMessage).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // ---- 本体 ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                showList -> FileList(library, actions, onAdd = { picker.launch(HTML_MIME_TYPES) }, onPicked = { showList = false })
                current == null -> EmptyState { picker.launch(HTML_MIME_TYPES) }
                !actions.canRead(current) -> Unreadable(current, actions)
                else -> HtmlView(current, reloadKey, actions)
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
            "CSS・JavaScript・画像を1つのファイルにまとめた HTML を表示できます（別ファイルの相対パス参照は読み込めません）。localStorage・WebAudio・navigator.vibrate も使えます",
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
 * WebView で表示する（JavaScript・localStorage 有効）。
 * 中身を読み込んで https://ihl.local/<ID>/ という仮の場所のページとして表示する。
 * （content:// のまま開くとページに正しい「場所」が無く、localStorage などが使えないため）
 * ページ内の http(s) リンクは外部ブラウザで開く。この範囲では画面全体のピンチを無効にして、ページの拡大縮小を優先する。
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun HtmlView(entry: HtmlEntry, reloadKey: Int, actions: HtmlActions) {
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
    LaunchedEffect(entry.uri, reloadKey, webView) {
        val view = webView ?: return@LaunchedEffect
        error = null
        val html = actions.read(entry)
        if (html == null) {
            error = "ファイルを読めませんでした"
            return@LaunchedEffect
        }
        val base = "https://$VIRTUAL_HOST/${entry.key}/"
        view.loadDataWithBaseURL(base + "index.html", injectBridge(html), "text/html", "UTF-8", base + "index.html")
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { PinchExclusion.set(PINCH_KEY, it.boundsInRoot()) }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // 端末のファイル・content:// を直接読ませない（中身はアプリが読んで渡す）
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    // navigator.vibrate を端末の振動につなぐ（WebView は標準では振動 API に対応していない）
                    addJavascriptInterface(VibrateBridge(actions.vibrate), BRIDGE_NAME)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            val scheme = url.scheme?.lowercase()
                            // 仮の場所の中の移動（ページ内リンク以外）は行き先が無いので止める
                            if (url.host == VIRTUAL_HOST) return url.fragment == null
                            if (request.isForMainFrame && (scheme == "http" || scheme == "https" || scheme == "mailto" || scheme == "tel")) {
                                actions.openExternal(url)
                                return true
                            }
                            return false
                        }

                        // 仮の場所への読み込み（相対パスの CSS・JS・画像など）は外へ出さずに「見つからない」で返す
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            if (request.url.host != VIRTUAL_HOST) return null
                            return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
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

/** JavaScript から呼ぶ振動（WebView の裏のスレッドから呼ばれる） */
private class VibrateBridge(private val vibrate: (Long) -> Unit) {
    @JavascriptInterface
    fun vibrate(ms: Int) {
        vibrate(ms.toLong().coerceIn(1, 1000))
    }
}

/** 仮の場所（ページの origin）のホスト名 */
private const val VIRTUAL_HOST = "ihl.local"
private const val BRIDGE_NAME = "IHLNative"

/** navigator.vibrate を IHLNative.vibrate につなぐスクリプト（ページのスクリプトより先に動くよう head の先頭に入れる） */
private val BRIDGE_SCRIPT = """<script>(function(){try{var f=function(p){var ms=Array.isArray(p)?p.filter(function(_,i){return i%2===0}).reduce(function(a,b){return a+(b|0)},0):(p|0);if(ms>0)$BRIDGE_NAME.vibrate(Math.min(ms,1000));return true;};Object.defineProperty(navigator,'vibrate',{value:f,configurable:true,writable:true});}catch(e){}})();</script>"""

/** HTML の head の直後（無ければ doctype の後、それも無ければ先頭）に振動用のスクリプトを入れる */
private fun injectBridge(html: String): String {
    Regex("<head(\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html)?.let { m ->
        return html.substring(0, m.range.last + 1) + BRIDGE_SCRIPT + html.substring(m.range.last + 1)
    }
    Regex("<!doctype[^>]*>", RegexOption.IGNORE_CASE).find(html)?.let { m ->
        return html.substring(0, m.range.last + 1) + BRIDGE_SCRIPT + html.substring(m.range.last + 1)
    }
    return BRIDGE_SCRIPT + html
}

private const val PINCH_KEY = "html"
