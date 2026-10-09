package com.ictools.ichomelauncher.data

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context

/** 他アプリの PendingIntent（通知・メディアの「開く」）を送る */
object PendingIntents {

    /**
     * Android 14 以降は送信側が明示的に許可しないとアクティビティを起動できないため、
     * 前面にいるランチャーの権限で起動できるよう ActivityOptions を付けて送る。
     */
    @Suppress("DEPRECATION")
    fun send(context: Context, pendingIntent: PendingIntent) {
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
    }
}
