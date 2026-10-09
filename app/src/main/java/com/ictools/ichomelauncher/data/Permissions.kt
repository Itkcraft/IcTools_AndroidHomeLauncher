package com.ictools.ichomelauncher.data

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.ictools.ichomelauncher.service.IhlNotificationListener

/** 各権限の許可状態 */
data class PermissionState(
    val notificationAccess: Boolean = false, // 通知へのアクセス（メディア・通知履歴）
    val calendar: Boolean = false,           // カレンダー（スケジュール・カレンダー）
    val recordAudio: Boolean = false,        // マイク（音楽波形の Visualizer に必要。録音はしない）
    val allFiles: Boolean = false,           // すべてのファイルへのアクセス（ターミナルのファイル操作）
    val usageStats: Boolean = false,         // 使用状況へのアクセス（稼働状況の最近使ったアプリ）
    val fineLocation: Boolean = false,       // 正確な位置情報（Wi-Fi の SSID）
    val locationEnabled: Boolean = false,    // 端末の位置情報サービスが ON か（SSID に必要）
    val phoneState: Boolean = false          // 電話の状態（モバイル回線種別）
)

/** 権限の確認と、許可用の設定画面を開くための Intent */
object Permissions {

    fun listenerComponent(context: Context) = ComponentName(context, IhlNotificationListener::class.java)

    /** すべての権限状態をまとめて取得する */
    fun current(context: Context) = PermissionState(
        notificationAccess = hasNotificationAccess(context),
        calendar = granted(context, Manifest.permission.READ_CALENDAR),
        recordAudio = granted(context, Manifest.permission.RECORD_AUDIO),
        allFiles = Environment.isExternalStorageManager(),
        usageStats = hasUsageStats(context),
        fineLocation = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
        locationEnabled = runCatching { context.getSystemService(LocationManager::class.java).isLocationEnabled }.getOrDefault(false),
        phoneState = granted(context, Manifest.permission.READ_PHONE_STATE)
    )

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** 「通知へのアクセス」が許可されているか */
    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** カレンダーの読み取りが許可されているか */
    fun hasCalendar(context: Context): Boolean = granted(context, Manifest.permission.READ_CALENDAR)

    /** 「使用状況へのアクセス」が許可されているか（AppOps での確認が公式の方法のため非推奨 API を使う） */
    @Suppress("DEPRECATION")
    fun hasUsageStats(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    /** このアプリの「通知へのアクセス」設定画面 */
    fun notificationAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())

    /** 「通知へのアクセス」一覧画面（詳細画面が開けない端末向け） */
    fun notificationAccessListIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** アプリ情報画面（権限の手動許可・制限付き設定の解除用） */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    /** 「すべてのファイルへのアクセス」設定画面 */
    fun allFilesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri(context))

    /** 「すべてのファイルへのアクセス」一覧画面（予備） */
    fun allFilesListIntent(): Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    /** 「使用状況へのアクセス」設定画面（このアプリを直接開く。非対応端末では一覧） */
    fun usageAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri(context))

    fun usageAccessListIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** 位置情報サービスの設定画面 */
    fun locationSettingsIntent(): Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
}
