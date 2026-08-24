package com.wceng.dictation.data.network

import com.wceng.dictation.core.model.AppConfig
import com.wceng.dictation.core.model.TranscriptionResult

/**
 * STT 网络数据源(NiA 模式):云端转写访问的唯一出口。
 * 配置由调用方传入——数据源不依赖任何仓库,重试等传输层策略封装在实现里。
 */
interface SttNetworkDataSource {
    suspend fun transcribe(config: AppConfig, wavBytes: ByteArray): TranscriptionResult
}
