package com.wceng.dictation.platform

import com.wceng.dictation.core.Recorder
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

/**
 * 桌面录音实现:JDK 自带 javax.sound,16kHz/16bit/单声道 PCM。
 * 不足 [minimumDurationMs] 的录音视为误触,stop() 返回空数组。
 */
class DesktopRecorder : Recorder {

    private var line: TargetDataLine? = null
    private var buffer: ByteArrayOutputStream? = null
    private var recordingThread: Thread? = null
    @Volatile private var recording = false
    private var startTimeMs = 0L

    override val isRecording: Boolean get() = recording

    override fun start(): Boolean {
        if (recording) return true
        val format = AudioFormat(16000f, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("[Recorder] 系统不支持该录音格式,请检查麦克风设备")
            return false
        }

        line = (AudioSystem.getLine(info) as TargetDataLine).apply {
            open(format)
            start()
        }
        buffer = ByteArrayOutputStream()
        recording = true
        startTimeMs = System.currentTimeMillis()

        recordingThread = Thread {
            val buf = ByteArray(4096)
            while (recording) {
                val n = line?.read(buf, 0, buf.size) ?: -1
                if (n > 0) buffer?.write(buf, 0, n)
            }
        }.apply {
            isDaemon = true
            start()
        }
        println("[Recorder] 开始录音...")
        return true
    }

    override fun stop(): ByteArray {
        if (!recording) return ByteArray(0)

        val elapsed = System.currentTimeMillis() - startTimeMs
        recording = false
        closeLine()
        val data = buffer?.toByteArray() ?: ByteArray(0)

        if (elapsed < minimumDurationMs) {
            println("[Recorder] 录音太短(${elapsed}ms < ${minimumDurationMs}ms),视为误触已取消")
            return ByteArray(0)
        }
        println("[Recorder] 停止录音,采集到 ${data.size} 字节 (${elapsed}ms)")
        return data
    }

    override fun cancel() {
        if (!recording) return
        recording = false
        closeLine()
        println("[Recorder] 用户取消录音,已丢弃 ${buffer?.size() ?: 0} 字节")
        buffer = null
    }

    private fun closeLine() {
        line?.stop()
        line?.close()
        recordingThread?.join(500)
    }

    companion object {
        /** 最短录音时长(ms),低于此值视为误触 */
        const val minimumDurationMs = 800L
    }
}
