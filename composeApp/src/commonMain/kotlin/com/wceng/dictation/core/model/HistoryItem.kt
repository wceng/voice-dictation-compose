package com.wceng.dictation.core.model

import kotlinx.serialization.Serializable

/** 一条转写历史记录 */
@Serializable
data class HistoryItem(
    val text: String,
    val timestamp: Long,
    val pasted: Boolean
)
