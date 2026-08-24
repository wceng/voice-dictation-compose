package com.wceng.dictation.core

/**
 * 录音器抽象。
 * 实现方负责采集设备管理与最短录音时长过滤;
 * stop() 返回原始 PCM 字节(不含 WAV 头),空数组表示无可转写内容。
 */
interface Recorder {
    val isRecording: Boolean

    /** 开始录音;设备不可用时返回 false */
    fun start(): Boolean

    fun stop(): ByteArray

    /** 停止并丢弃音频,不转写 */
    fun cancel()
}
