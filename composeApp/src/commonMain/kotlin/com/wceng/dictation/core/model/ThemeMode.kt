package com.wceng.dictation.core.model

/**
 * 外观主题模式:
 * - [SYSTEM] 跟随操作系统深浅色设置(默认),系统切换时窗口实时跟随;
 * - [LIGHT]/[DARK] 强制固定亮/暗色。
 */
enum class ThemeMode(val raw: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        /** 宽松解析存储值;空值或非法值回退到 [default](通常为 SYSTEM) */
        fun fromRawOrDefault(value: String?, default: ThemeMode = SYSTEM): ThemeMode =
            entries.firstOrNull { it.raw == value?.trim()?.lowercase() } ?: default
    }
}
