package com.wceng.dictation.data.repository

import com.wceng.dictation.core.model.ThemeMode
import com.wceng.dictation.data.store.DictationPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * UI 偏好仓库实现:直接委托 DataStore 数据源,flow 去重避免无效重组。
 */
class LocalUiPreferencesRepository(
    private val dataSource: DictationPreferencesDataSource
) : UiPreferencesRepository {

    override val themeMode: Flow<ThemeMode> =
        dataSource.themeMode.distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataSource.setThemeMode(mode)
    }
}
