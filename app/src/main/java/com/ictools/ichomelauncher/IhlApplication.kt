package com.ictools.ichomelauncher

import android.app.Application
import com.ictools.ichomelauncher.data.AudioEffects

/** アプリ全体の初期化。プロセス起動直後から EQ 用のブロードキャストを受け取れるようにする */
class IhlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AudioEffects.get(this).register()
    }
}
