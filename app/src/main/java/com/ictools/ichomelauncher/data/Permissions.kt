package com.ictools.ichomelauncher.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.ictools.ichomelauncher.service.IhlNotificationListener

/** 権限の確認と、許可用の設定画面を開くための Intent */
object Permissions {

    fun listenerComponent(context: Context) = ComponentName(context, IhlNotificationListener::class.java)

    /** 「通知へのアクセス」が許可されているか */
    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** カレンダーの読み取りが許可されているか */
    fun hasCalendar(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** このアプリの「通知へのアクセス」設定画面 */
    fun notificationAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())

    /** 「通知へのアクセス」一覧画面（詳細画面が開けない端末向け） */
    fun notificationAccessListIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** アプリ情報画面（権限の手動許可・制限付き設定の解除用） */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}
