package com.wceng.dictation.data.repository

import com.wceng.dictation.core.model.HistoryItem
import kotlinx.coroutines.flow.Flow

/**
 * 转写历史仓库(NiA 单一数据源模式):历史的唯一权威来源。
 * 持久化细节由实现决定(UI 不感知),新记录追加在末尾,超出上限自动淘汰最旧的。
 */
interface TranscriptionHistoryRepository {
    val history: Flow<List<HistoryItem>>

    suspend fun record(item: HistoryItem)

    suspend fun clear()
}
