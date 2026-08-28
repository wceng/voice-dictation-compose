package com.wceng.dictation.data.repository

import com.wceng.dictation.core.model.HotkeyCombo
import com.wceng.dictation.core.model.HotkeyConfig
import com.wceng.dictation.core.model.ThemeMode
import com.wceng.dictation.core.model.TriggerMode
import com.wceng.dictation.data.store.DictationPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    override val autostart: Flow<Boolean> =
        dataSource.autostart.distinctUntilChanged()

    override suspend fun setAutostart(enabled: Boolean) {
        dataSource.setAutostart(enabled)
        // 仅在已安装环境同步 HKCU Run
        if (isInstalledEnvironment()) syncRegistryRun(enabled)
    }

    override val hotkeys: Flow<HotkeyConfig> = combine(
        dataSource.hotkeyToggle,
        dataSource.hotkeyCancel,
        ::HotkeyConfig
    ).distinctUntilChanged()

    override suspend fun setToggleHotkey(combo: HotkeyCombo) {
        combo.validate()?.let { throw IllegalArgumentException(it) }
        dataSource.setHotkeyToggle(combo)
    }

    override suspend fun setCancelHotkey(combo: HotkeyCombo) {
        combo.validate()?.let { throw IllegalArgumentException(it) }
        dataSource.setHotkeyCancel(combo)
    }

    override val triggerMode: Flow<TriggerMode> =
        dataSource.triggerMode.distinctUntilChanged()

    override suspend fun setTriggerMode(mode: TriggerMode) {
        dataSource.setTriggerMode(mode)
    }

    /** 运行时动态判断:是否为 jpackage 安装版(exe 在 Program Files) */
    private fun isInstalledEnvironment(): Boolean = try {
        val cmd = ProcessHandle.current().info().command().orElse("")
        cmd.contains("Program Files", ignoreCase = true)
    } catch (_: Exception) { false }

    /** 同步 HKCU Run —— 仅安装版生效;开发模式仅存 DataStore */
    private fun syncRegistryRun(enabled: Boolean) = runCatching {
        // 运行时动态获取真实 exe 路径(含空格已自动处理)
        val exePath = ProcessHandle.current().info()
            .command().orElseThrow { IllegalStateException("无法获取 exe 路径") }
        val runKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        // 用 ProcessBuilder 传参数数组,避免 Runtime.exec(String) 按空格误拆含空格的路径
        val args = if (enabled) {
            listOf("reg", "add", runKey, "/v", "VoiceDictation", "/t", "REG_SZ", "/d", "\"$exePath\"", "/f")
        } else {
            listOf("reg", "delete", runKey, "/v", "VoiceDictation", "/f")
        }
        ProcessBuilder(args).start().waitFor()
    }.onFailure { e -> System.err.println("[Autostart] Registry sync failed: ${e.message}") }
}
