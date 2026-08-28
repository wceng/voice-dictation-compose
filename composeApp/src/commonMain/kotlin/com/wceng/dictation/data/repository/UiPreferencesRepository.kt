package com.wceng.dictation.data.repository

import com.wceng.dictation.core.model.HotkeyCombo
import com.wceng.dictation.core.model.HotkeyConfig
import com.wceng.dictation.core.model.ThemeMode
import com.wceng.dictation.core.model.TriggerMode
import kotlinx.coroutines.flow.Flow

/**
 * UI 偏好仓库(NiA 单一数据源模式):外观等界面级偏好的唯一权威来源。
 * 与后端配置([ConfigRepository])分离 —— UI 偏好没有环境变量回退语义。
 */
interface UiPreferencesRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** 开机自启动偏好 */
    val autostart: Flow<Boolean>
    suspend fun setAutostart(enabled: Boolean)

    /** 全局热键配置对(开始/停止 与 取消) */
    val hotkeys: Flow<HotkeyConfig>
    suspend fun setToggleHotkey(combo: HotkeyCombo)
    suspend fun setCancelHotkey(combo: HotkeyCombo)

    /** 热键触发方式:点按切换 / 长按说话 */
    val triggerMode: Flow<TriggerMode>
    suspend fun setTriggerMode(mode: TriggerMode)
}
