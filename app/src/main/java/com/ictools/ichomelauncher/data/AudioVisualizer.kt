package com.ictools.ichomelauncher.data

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 出力ミックス（セッション0）の音を Visualizer で解析し、波形またはスペクトラムを返す。
 * マイク入力は使わない（Android の仕様上 RECORD_AUDIO 権限が必要なだけ）。
 * 表示中かつ再生中の間だけ [start] し、それ以外は [stop] して電池消費を抑える。
 */
class AudioVisualizer {

    companion object {
        private const val TAG = "IhlVisualizer"
        const val SPECTRUM_BARS = 32
    }

    private var visualizer: Visualizer? = null

    /**
     * 解析を開始する。[onData] には 0〜1 に正規化した値が届く
     * （波形：サンプル列、0.5 が無音／スペクトラム：低音→高音のバー）。
     * 開始できなければ例外メッセージを返す。
     */
    fun start(spectrum: Boolean, onData: (FloatArray) -> Unit): String? {
        stop()
        return try {
            val v = Visualizer(0)
            v.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = min(max(512, range[0]), range[1])
            // 最大レートの半分（多くの端末で約 10 回／秒）
            val rate = Visualizer.getMaxCaptureRate() / 2
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform != null) onData(toWaveform(waveform))
                }

                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) onData(toSpectrum(fft))
                }
            }, rate, !spectrum, spectrum)
            v.enabled = true
            visualizer = v
            null
        } catch (e: Throwable) {
            Log.w(TAG, "start failed", e)
            stop()
            e.javaClass.simpleName
        }
    }

    fun stop() {
        visualizer?.let {
            runCatching { it.enabled = false }
            runCatching { it.release() }
        }
        visualizer = null
    }

    /** 8bit 符号なし波形（128 が無音）を 0〜1 に */
    private fun toWaveform(bytes: ByteArray): FloatArray =
        FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }

    /** FFT（実部・虚部の組）から、対数間隔でまとめたバーの高さ 0〜1 を作る */
    private fun toSpectrum(fft: ByteArray): FloatArray {
        val n = fft.size / 2
        val mags = FloatArray(n)
        for (k in 1 until n) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            mags[k] = hypot(re, im)
        }
        val bars = FloatArray(SPECTRUM_BARS)
        for (b in 0 until SPECTRUM_BARS) {
            // 低音側を細かく、高音側をまとめる
            val from = max(1, (n.toDouble().pow(b.toDouble() / SPECTRUM_BARS)).toInt())
            val to = max(from + 1, (n.toDouble().pow((b + 1).toDouble() / SPECTRUM_BARS)).toInt())
            var peak = 0f
            for (k in from until min(to, n)) peak = max(peak, mags[k])
            // dB 風に圧縮して 0〜1 へ
            bars[b] = (ln(1f + peak) / ln(1f + 128f)).coerceIn(0f, 1f)
        }
        return bars
    }
}
