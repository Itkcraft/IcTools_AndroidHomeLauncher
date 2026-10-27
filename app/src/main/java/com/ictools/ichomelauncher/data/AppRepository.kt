package com.ictools.ichomelauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/** ドロワー・ターミナルで扱うアプリ1件分の情報 */
data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    val installTime: Long = 0,      // 初回インストール日時（取得できなければ 0）
    val isSystem: Boolean = false   // プリインストール（システム）アプリか
) {
    /** 一覧の key・履歴の保存に使う一意な文字列（再起動後も変わらない） */
    val key: String get() = "${component.flattenToString()}#$userSerial"
}

/** インストール済みアプリ（MAIN + LAUNCHER）の取得・起動・変更監視 */
class AppRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val ownPackage = appContext.packageName
    private val userManager = appContext.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())

    /** ラベル順に並んだアプリ一覧 */
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    // 最後に取得したアクティビティ情報（アイコン読み込みに使う）
    @Volatile
    private var activityInfos: Map<String, LauncherActivityInfo> = emptyMap()

    // アイコンのメモリキャッシュ（最大 300 件。キーはアプリ＋大きさ）
    private val iconCache = LruCache<String, ImageBitmap>(300)

    // アプリのインストール／アンインストール／更新を検知して一覧を更新する
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = changed()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = changed()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = changed()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = changed()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = changed()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun changed() {
        iconCache.evictAll()
        refresh()
    }

    /** アプリ一覧を取り直す（全ユーザープロファイル分。自分自身は除外） */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val infos = runCatching {
                launcherApps.profiles.flatMap { user -> launcherApps.getActivityList(null, user) }
            }.getOrDefault(emptyList())
                .filter { it.componentName.packageName != ownPackage }

            val collator = Collator.getInstance()
            val pm = appContext.packageManager
            val pairs = infos.map { info ->
                val pkg = info.componentName.packageName
                val installTime = runCatching {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).firstInstallTime
                }.getOrDefault(0L)
                val isSystem = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                AppEntry(
                    label = info.label.toString(),
                    component = info.componentName,
                    user = info.user,
                    userSerial = userManager.getSerialNumberForUser(info.user),
                    installTime = installTime,
                    isSystem = isSystem
                ) to info
            }
            activityInfos = pairs.associate { (entry, info) -> entry.key to info }
            _apps.value = pairs.map { it.first }.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }

    /** アプリのアイコンを読み込む（キャッシュ付き・IO スレッド） */
    suspend fun loadIcon(app: AppEntry, sizePx: Int): ImageBitmap? {
        val cacheKey = "${app.key}@$sizePx"
        iconCache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            val info = activityInfos[app.key] ?: return@withContext null
            runCatching {
                info.getBadgedIcon(0).toBitmap(sizePx, sizePx).asImageBitmap()
            }.getOrNull()?.also { iconCache.put(cacheKey, it) }
        }
    }

    /** アプリを起動する。成功したら true */
    fun launch(app: AppEntry): Boolean = runCatching {
        launcherApps.startMainActivity(app.component, app.user, null, null)
    }.isSuccess

    /**
     * アンインストール画面を開く（OS の確認ダイアログが出る）。
     * システムアプリはアンインストールできないため false を返す（呼び出し側でアプリ情報を案内する）。
     */
    fun uninstall(app: AppEntry): Boolean {
        if (app.isSystem) return false
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (app.user != Process.myUserHandle()) intent.putExtra(Intent.EXTRA_USER, app.user)
        return runCatching { appContext.startActivity(intent) }.isSuccess
    }

    /** アプリ情報（設定アプリ）を開く */
    fun openAppInfo(app: AppEntry): Boolean = runCatching {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }.isSuccess

    /** 監視を解除する */
    fun close() {
        launcherApps.unregisterCallback(callback)
    }
}
