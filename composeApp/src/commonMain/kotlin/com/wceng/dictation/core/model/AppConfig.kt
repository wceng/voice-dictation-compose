package com.wceng.dictation.core.model

/**
 * 应用配置(纯数据,跨平台复用)。
 *
 * 加载优先级: 本地存储(DataStore) > 环境变量 > 默认值;
 * [sources] 记录每个字段实际来源,用于启动日志与设置界面提示。
 */
data class AppConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val language: String,
    val sources: Map<String, ConfigSource> = emptyMap()
) {
    /** 是否已完成必要配置(有 API Key) */
    val configured: Boolean get() = apiKey.isNotBlank()

    companion object {
        /** 与旧版 voice-dictation 的 start.bat 保持一致(OpenRouter + Qwen ASR) */
        val DEFAULTS = AppConfig(
            apiKey = "",
            baseUrl = "https://openrouter.ai/api/v1",
            model = "qwen/qwen3-asr-flash-2026-02-10",
            language = "zh"
        )
    }
}

/** 配置项实际取值来源 */
enum class ConfigSource { STORE, ENV, DEFAULT }

/** 设置界面提交的一次保存动作,null 表示该项保持不变 */
data class ConfigUpdate(
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val model: String? = null,
    val language: String? = null
)
