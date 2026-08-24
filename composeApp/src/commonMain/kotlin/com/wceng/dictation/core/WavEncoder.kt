package com.wceng.dictation.core

/**
 * 纯 Kotlin WAV 编码器(16kHz/16bit/单声道 PCM -> 标准 WAV 字节)。
 * 不依赖任何平台 API,可在 commonMain 直接测试,移动端复用。
 */
object WavEncoder {

    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16

    fun encode(pcm: ByteArray): ByteArray {
        val dataSize = pcm.size
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
        val byteRate = SAMPLE_RATE * blockAlign

        fun le32(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte()
        )

        fun le16(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte()
        )

        val header = ByteArray(44)
        var i = 0
        fun put(b: ByteArray) { b.copyInto(header, i); i += b.size }

        put("RIFF".encodeToByteArray())
        put(le32(36 + dataSize))
        put("WAVE".encodeToByteArray())
        put("fmt ".encodeToByteArray())
        put(le32(16))                 // fmt chunk 大小
        put(le16(1))                  // PCM
        put(le16(CHANNELS))
        put(le32(SAMPLE_RATE))
        put(le32(byteRate))
        put(le16(blockAlign))
        put(le16(BITS_PER_SAMPLE))
        put("data".encodeToByteArray())
        put(le32(dataSize))

        return header + pcm
    }
}
