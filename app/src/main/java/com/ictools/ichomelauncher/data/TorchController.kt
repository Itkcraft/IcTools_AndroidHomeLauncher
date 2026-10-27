package com.ictools.ichomelauncher.data

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ライト（カメラのフラッシュ）の点灯・消灯。カメラ権限は不要 */
class TorchController(context: Context) {

    private val cameraManager = context.applicationContext.getSystemService(CameraManager::class.java)

    // フラッシュ付きのカメラ（背面を優先）
    private val cameraId: String? = runCatching {
        val ids = cameraManager.cameraIdList.filter {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        ids.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: ids.firstOrNull()
    }.getOrNull()

    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    // 他アプリ（クイック設定など）での切り替えも反映する
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) _isOn.value = enabled
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) _isOn.value = false
        }
    }

    init {
        runCatching { cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
    }

    val available: Boolean get() = cameraId != null

    /** 点灯／消灯する。失敗したらエラーメッセージを返す */
    fun set(on: Boolean): String? {
        val id = cameraId ?: return "no flash available"
        return runCatching { cameraManager.setTorchMode(id, on) }
            .exceptionOrNull()?.let { "light unavailable (camera in use?)" }
    }

    fun close() {
        runCatching { cameraManager.unregisterTorchCallback(callback) }
    }
}
