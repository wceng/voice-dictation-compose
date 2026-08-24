package com.wceng.dictation.core

/** 把识别文本送入当前焦点输入框(桌面: 剪贴板 + Ctrl+V) */
fun interface TextInjector {
    fun inject(text: String)
}

/** 录音开始/结束提示音 */
interface SoundFeedback {
    fun playStart()
    fun playStop()
}
