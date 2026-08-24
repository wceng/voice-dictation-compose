package com.wceng.dictation.data.repository

import com.wceng.dictation.core.model.HistoryItem
import com.wceng.dictation.data.store.DictationPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 转写历史仓库实现:持久化到 DataStore(JSON),重启不丢。
 * record 的读-改-写用互斥锁串行化,避免并发转写完成时丢记录。
 */
class LocalTranscriptionHistoryRepository(
    private val dataSource: DictationPreferencesDataSource
) : TranscriptionHistoryRepository {

    private val mutex = Mutex()

    override val history: Flow<List<HistoryItem>> = dataSource.history

    override suspend fun record(item: HistoryItem) {
        mutex.withLock {
            val updated = (dataSource.history.first() + item).takeLast(MAX_HISTORY)
            dataSource.setHistory(updated)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            dataSource.setHistory(emptyList())
        }
    }

    companion object {
        const val MAX_HISTORY = 50
    }
}
