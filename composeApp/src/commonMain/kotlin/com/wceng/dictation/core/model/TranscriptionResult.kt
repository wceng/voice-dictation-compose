package com.wceng.dictation.core.model

/** 转写结果: 成功携带识别文本(可能为空), 失败携带可读原因 */
sealed interface TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult
    data class Failure(val reason: String) : TranscriptionResult
}
