package com.wceng.dictation.platform

import com.wceng.dictation.core.SoundFeedback
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/**
 * 录音状态提示音: 开始=升调(A4→D5), 结束=降调(D5→A4)。
 * 程序化合成正弦波,无需音频资源;两音合成到一段连续 PCM 只开一次设备;
 * 全部异步播放,无音频设备时静默降级不影响主流程。
 */
class DesktopSoundPlayer : SoundFeedback {

    override fun playStart() = playSequence(880.0 to 0.09, 1174.0 to 0.09)

    override fun playStop() = playSequence(1174.0 to 0.09, 880.0 to 0.14)

    private fun playSequence(vararg tones: Pair<Double, Double>) {
        Thread {
            try {
                val data = synthesize(tones)
                val clip = AudioSystem.getClip()
                clip.open(format, data, 0, data.size)
                clip.start()
                val totalSec = tones.sumOf { it.second } + GAP_SEC * (tones.size - 1)
                Thread.sleep((totalSec * 1000).toLong() + 100)
                clip.close()
            } catch (e: Exception) {
                System.err.println("[Sound] 提示音播放失败: ${e.message}")
            }
        }.apply { isDaemon = true }.start()
    }

    private fun synthesize(tones: Array<out Pair<Double, Double>>): ByteArray {
        val out = ByteArrayOutputStream()
        val gapBytes = (SAMPLE_RATE * GAP_SEC).toInt() * 2

        tones.forEachIndexed { index, (freq, durationSec) ->
            if (index > 0) out.write(ByteArray(gapBytes))
            val n = (SAMPLE_RATE * durationSec).toInt()
            for (i in 0 until n) {
                val t = i / SAMPLE_RATE
                val v = (0.5 * Math.sin(2 * Math.PI * freq * t) * Short.MAX_VALUE).toInt().toShort()
                out.write(v.toInt() and 0xFF)
                out.write((v.toInt() shr 8) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    companion object {
        private const val SAMPLE_RATE = 44100f
        private const val GAP_SEC = 0.05
        private val format = AudioFormat(SAMPLE_RATE, 16, 1, true, false)
    }
}
