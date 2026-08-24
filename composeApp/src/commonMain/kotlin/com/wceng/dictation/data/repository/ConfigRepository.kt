package com.wceng.dictation.data.repository

import com.wceng.dictation.core.model.AppConfig
import com.wceng.dictation.core.model.ConfigUpdate
import kotlinx.coroutines.flow.Flow

/**
 * 配置仓库(NiA 单一数据源模式):应用配置的唯一权威来源。
 * UI 与控制器只从这里读写,不感知存储实现;
 * 配置为响应式 Flow,保存后自动向所有收集者广播新值。
 */
interface ConfigRepository {
    val config: Flow<AppConfig>

    /** 只更新非 null 字段 */
    suspend fun save(update: ConfigUpdate)
}
