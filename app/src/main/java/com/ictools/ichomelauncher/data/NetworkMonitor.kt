package com.ictools.ichomelauncher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wi-Fi の状態 */
data class WifiStatus(
    val ssid: String?,        // 位置情報の権限・位置情報サービスが無いと null
    val rssi: Int,            // dBm
    val level: Int,           // 0〜maxLevel
    val maxLevel: Int,
    val linkMbps: Int,
    val txMbps: Int,
    val rxMbps: Int,
    val frequencyMHz: Int
)

/** モバイル回線の状態 */
data class MobileStatus(
    val carrier: String,
    val simReady: Boolean,
    val level: Int?,          // 0〜4
    val dbm: Int?,
    val networkType: String?  // 電話の状態の権限が無いと null
)

/** 通信状況のまとめ */
data class NetworkSnapshot(
    val defaultTransport: String = "none",  // 現在インターネットに使われている回線
    val validated: Boolean = false,         // インターネット接続を確認済みか
    val downKbps: Int = 0,                  // OS による推定帯域
    val upKbps: Int = 0,
    val wifi: WifiStatus? = null,           // 未接続なら null
    val mobile: MobileStatus? = null
)

/**
 * Wi-Fi／モバイルの接続状態を監視する。
 * 通信パネルが表示されている間だけ [start] し、閉じたら [stop] する。
 */
class NetworkMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(ConnectivityManager::class.java)
    private val wm = appContext.getSystemService(WifiManager::class.java)
    private val tm = appContext.getSystemService(TelephonyManager::class.java)

    private val _state = MutableStateFlow(NetworkSnapshot())
    val state: StateFlow<NetworkSnapshot> = _state.asStateFlow()

    private var started = false
    private var defaultCaps: NetworkCapabilities? = null
    private var wifiInfo: WifiInfo? = null
    private var signal: SignalStrength? = null
    private var displayInfo: TelephonyDisplayInfo? = null

    // 既定（インターネットに使われている）ネットワーク
    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            defaultCaps = caps
            publish()
        }

        override fun onLost(network: Network) {
            defaultCaps = null
            publish()
        }
    }

    // Wi-Fi（SSID を受け取るため位置情報付きで登録）
    private val wifiCallback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            wifiInfo = caps.transportInfo as? WifiInfo
            publish()
        }

        override fun onLost(network: Network) {
            wifiInfo = null
            publish()
        }
    }

    // 電波強度（権限不要）
    private val signalCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            signal = signalStrength
            publish()
        }
    }

    // 5G NSA などの表示用回線種別（電話の状態の権限がある場合のみ）
    private val displayCallback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
            displayInfo = info
            publish()
        }
    }
    private var displayRegistered = false

    fun start() {
        if (started) return
        started = true
        runCatching { cm.registerDefaultNetworkCallback(defaultCallback) }
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                wifiCallback
            )
        }
        runCatching { tm.registerTelephonyCallback(appContext.mainExecutor, signalCallback) }
        registerDisplayInfo()
        publish()
    }

    /** 権限が後から許可された場合に呼ぶ */
    fun refreshPermissions() {
        if (!started) return
        registerDisplayInfo()
        // SSID を取り直すため Wi-Fi のコールバックを登録し直す
        runCatching { cm.unregisterNetworkCallback(wifiCallback) }
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                wifiCallback
            )
        }
        publish()
    }

    private fun registerDisplayInfo() {
        if (displayRegistered || !Permissions.current(appContext).phoneState) return
        displayRegistered = runCatching { tm.registerTelephonyCallback(appContext.mainExecutor, displayCallback) }.isSuccess
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { cm.unregisterNetworkCallback(defaultCallback) }
        runCatching { cm.unregisterNetworkCallback(wifiCallback) }
        runCatching { tm.unregisterTelephonyCallback(signalCallback) }
        if (displayRegistered) runCatching { tm.unregisterTelephonyCallback(displayCallback) }
        displayRegistered = false
    }

    private fun publish() {
        val caps = defaultCaps
        val transport = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "other"
        }
        val wifi = wifiInfo?.let { w ->
            // 権限が無いと "<unknown ssid>" が返る
            val ssid = w.ssid?.takeIf { it != WifiManager.UNKNOWN_SSID }?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }
            WifiStatus(
                ssid = ssid,
                rssi = w.rssi,
                level = runCatching { wm.calculateSignalLevel(w.rssi) }.getOrDefault(0),
                maxLevel = runCatching { wm.maxSignalLevel }.getOrDefault(4),
                linkMbps = w.linkSpeed,
                txMbps = w.txLinkSpeedMbps,
                rxMbps = w.rxLinkSpeedMbps,
                frequencyMHz = w.frequency
            )
        }
        val mobile = runCatching {
            val s = signal
            MobileStatus(
                carrier = tm.networkOperatorName.orEmpty(),
                simReady = tm.simState == TelephonyManager.SIM_STATE_READY,
                level = s?.level,
                dbm = s?.cellSignalStrengths?.firstOrNull()?.dbm?.takeIf { it != Int.MAX_VALUE },
                networkType = if (Permissions.current(appContext).phoneState) networkTypeLabel() else null
            )
        }.getOrNull()
        _state.value = NetworkSnapshot(
            defaultTransport = transport,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            downKbps = caps?.linkDownstreamBandwidthKbps ?: 0,
            upKbps = caps?.linkUpstreamBandwidthKbps ?: 0,
            wifi = wifi,
            mobile = mobile
        )
    }

    /** 回線種別の表示名（5G NSA / LTE+ などの表示用種別を優先） */
    private fun networkTypeLabel(): String? = runCatching {
        when (displayInfo?.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> return@runCatching "5G NSA"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> return@runCatching "5G+"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> return@runCatching "LTE+"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> return@runCatching "LTE-A Pro"
        }
        when (tm.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "-"
            else -> "other"
        }
    }.getOrNull()
}
