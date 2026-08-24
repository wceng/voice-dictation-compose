package com.wceng.dictation.data.repository

import com.wceng.dictation.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * UI 偏好仓库(NiA 单一数据源模式):外观等界面级偏好的唯一权威来源。
 * 与后端配置([ConfigRepository])分离 —— UI 偏好没有环境变量回退语义。
 */
interface UiPreferencesRepository {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
