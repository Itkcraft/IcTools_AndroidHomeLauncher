package com.ictools.ichomelauncher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ictools.ichomelauncher.data.NotificationHistory

/**
 * 通知リスナー。
 * 「通知へのアクセス」を許可すると OS が接続する。
 * ・通知履歴の記録
 * ・再生中メディアセッションの取得（MediaSessionManager がこのコンポーネントの許可を要求するため）
 */
class IhlNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationHistory.get(this).record(sbn)
    }
}
