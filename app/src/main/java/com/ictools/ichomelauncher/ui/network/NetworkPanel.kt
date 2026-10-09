package com.ictools.ichomelauncher.ui.network

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ictools.ichomelauncher.data.NetworkSnapshot
import com.ictools.ichomelauncher.data.PermissionState
import com.ictools.ichomelauncher.ui.common.WireButton
import com.ictools.ichomelauncher.ui.theme.IhlColors

/** 通信パネルから呼ぶ操作 */
class NetworkActions(
    val start: () -> Unit,
    val stop: () -> Unit,
    val onPermissionResult: () -> Unit,
    val openLocationSettings: () -> Unit,
    val openAppInfo: () -> Unit
)

/** 通信パネル：Wi-Fi／モバイルの接続状態・電波強度・リンク速度・SSID。表示中だけ監視する */
@Composable
fun NetworkPanel(snapshot: NetworkSnapshot, permissions: PermissionState, actions: NetworkActions) {
    DisposableEffect(Unit) {
        actions.start()
        onDispose { actions.stop() }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        actions.onPermissionResult()
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        actions.onPermissionResult()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 22.dp)) {
        // ---- 現在の接続 ----
        Row {
            Text("ACTIVE", Modifier.width(64.dp), color = IhlColors.Accent)
            Text(snapshot.defaultTransport + if (snapshot.validated) "  (internet ok)" else "")
        }
        if (snapshot.downKbps > 0 || snapshot.upKbps > 0) {
            Text("  est. ↓${formatKbps(snapshot.downKbps)} ↑${formatKbps(snapshot.upKbps)}", color = IhlColors.TextDim, fontSize = 11.sp)
        }

        // ---- Wi-Fi ----
        Spacer(Modifier.height(10.dp))
        Text("Wi-Fi", color = IhlColors.Accent)
        val wifi = snapshot.wifi
        if (wifi == null) {
            Text("  not connected", color = IhlColors.TextDim)
        } else {
            val ssid = wifi.ssid
            Text("  SSID   ${ssid ?: "(hidden)"}")
            Text("  signal ${bars(wifi.level, wifi.maxLevel)} ${wifi.rssi} dBm")
            Text("  link   ${wifi.linkMbps} Mbps (tx ${wifi.txMbps} / rx ${wifi.rxMbps})")
            Text("  band   ${band(wifi.frequencyMHz)}")
            if (ssid == null) {
                Spacer(Modifier.height(4.dp))
                when {
                    !permissions.fineLocation -> {
                        Reason("SSID の表示には「正確な位置情報」の許可が必要です（Android の仕様。位置情報は保存・送信しません）")
                        WireButton("許可する", onClick = {
                            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        })
                    }
                    !permissions.locationEnabled -> {
                        Reason("SSID の表示には端末の位置情報サービス（GPS）を ON にする必要があります")
                        WireButton("位置情報の設定を開く", onClick = actions.openLocationSettings)
                    }
                }
            }
        }

        // ---- モバイル ----
        Spacer(Modifier.height(10.dp))
        Text("Mobile", color = IhlColors.Accent)
        val mobile = snapshot.mobile
        if (mobile == null || !mobile.simReady) {
            Text("  no SIM", color = IhlColors.TextDim)
        } else {
            Text("  carrier ${mobile.carrier.ifEmpty { "-" }}")
            val level = mobile.level
            Text("  signal  ${if (level != null) bars(level, 4) else "-"} ${mobile.dbm?.let { "$it dBm" } ?: ""}")
            Text("  type    ${mobile.networkType ?: "-"}")
            if (!permissions.phoneState) {
                Spacer(Modifier.height(4.dp))
                Reason("回線種別（5G/LTE など）の表示には「電話」（電話の状態の読み取り）の許可が必要です。電話の発信や通話の確認はしません")
                Row {
                    WireButton("許可する", onClick = { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) })
                    Spacer(Modifier.width(6.dp))
                    WireButton("アプリ情報", onClick = actions.openAppInfo)
                }
            }
        }
    }
}

@Composable
private fun Reason(text: String) {
    Text(text, color = IhlColors.TextDim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
}

/** 電波の強さを棒で表す（例：▮▮▮▯） */
private fun bars(level: Int, max: Int): String {
    val m = max.coerceAtLeast(1)
    val l = level.coerceIn(0, m)
    return "▮".repeat(l) + "▯".repeat(m - l)
}

private fun band(freq: Int): String = when {
    freq <= 0 -> "-"
    freq < 3000 -> "2.4GHz ($freq MHz)"
    freq < 5925 -> "5GHz ($freq MHz)"
    else -> "6GHz ($freq MHz)"
}

private fun formatKbps(kbps: Int): String = if (kbps >= 1000) "%.1fMbps".format(kbps / 1000f) else "${kbps}kbps"
