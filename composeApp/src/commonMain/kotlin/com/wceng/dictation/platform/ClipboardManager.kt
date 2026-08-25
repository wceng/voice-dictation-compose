package com.wceng.dictation.platform

/**
 * 统一剪贴板读写抽象。
 * 桌面端基于 AWT Toolkit；Android 基于 ClipboardManager；iOS 基于 UIPasteboard。
 */
interface ClipboardManager {
    fun setText(text: String)
    fun getText(): String?
    fun hasText(): Boolean
}