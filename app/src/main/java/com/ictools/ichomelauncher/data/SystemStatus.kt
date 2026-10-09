package com.ictools.ichomelauncher.data

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** 最近使ったアプリ1件分 */
data class AppUsage(val packageName: String, val label: String, val foregroundMs: Long, val lastUsed: Long)

/** 稼働状況のスナップショット */
data class SystemSnapshot(
    val ramTotal: Long,
    val ramAvailable: Long,
    val lowMemory: Boolean,
    val batteryPercent: Int,
    val batteryTempC: Float?,
    val charging: Boolean,
    val chargeSource: String,      // AC / USB / Wireless / -
    val batteryStatus: String,     // charging / discharging / full / not charging
    val thermalStatus: Int,        // PowerManager.THERMAL_STATUS_*
    val thermalHeadroom: Float?,   // 1.0 で発熱制限の閾値（取得できない端末は null）
    val storageTotal: Long,
    val storageFree: Long,
    val uptimeMs: Long,
    val recentApps: List<AppUsage>? // 権限が無い場合は null
)

/** 稼働状況（RAM・バッテリー・発熱・ストレージ・稼働時間・最近使ったアプリ）を集める */
class SystemStatus(context: Context) {

    private val appContext = context.applicationContext

    suspend fun snapshot(): SystemSnapshot = withContext(Dispatchers.IO) {
        val am = appContext.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

        // バッテリーは sticky ブロードキャストから読む（レシーバー登録は不要）
        val battery: Intent? = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val pm = appContext.getSystemService(PowerManager::class.java)
        val headroom = runCatching { pm.getThermalHeadroom(10) }.getOrNull()?.takeIf { !it.isNaN() }

        val stat = StatFs(Environment.getDataDirectory().path)

        SystemSnapshot(
            ramTotal = mem.totalMem,
            ramAvailable = mem.availMem,
            lowMemory = mem.lowMemory,
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            batteryTempC = temp,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            chargeSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
                else -> "-"
            },
            batteryStatus = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                else -> "unknown"
            },
            thermalStatus = runCatching { pm.currentThermalStatus }.getOrDefault(PowerManager.THERMAL_STATUS_NONE),
            thermalHeadroom = headroom,
            storageTotal = stat.totalBytes,
            storageFree = stat.availableBytes,
            uptimeMs = SystemClock.elapsedRealtime(),
            recentApps = if (Permissions.hasUsageStats(appContext)) recentApps() else null
        )
    }

    /** 今日の使用時間が長い順のアプリ（最大 8 件） */
    private fun recentApps(): List<AppUsage> = runCatching {
        val usm = appContext.getSystemService(UsageStatsManager::class.java)
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val pm = appContext.packageManager
        usm.queryAndAggregateUsageStats(start, System.currentTimeMillis()).values
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(8)
            .map {
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, PackageManager.ApplicationInfoFlags.of(0))).toString()
                }.getOrDefault(it.packageName)
                AppUsage(it.packageName, label, it.totalTimeInForeground, it.lastTimeUsed)
            }
    }.getOrDefault(emptyList())
}

/** 発熱状態の表示名 */
fun thermalLabel(status: Int): String = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> "normal"
    PowerManager.THERMAL_STATUS_LIGHT -> "light"
    PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
    PowerManager.THERMAL_STATUS_SEVERE -> "severe"
    PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
    PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
    PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
    else -> "unknown"
}
