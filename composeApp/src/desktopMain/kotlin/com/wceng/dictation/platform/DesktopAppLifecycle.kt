package com.wceng.dictation.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面生命周期实现：基于 TCP 端口单实例锁 + 命令行参数解析。
 * 二次启动（通过锁失败）后 new-Isolated 命令行仍保留，此处仅记录首次启动。
 */
class DesktopAppLifecycle : AppLifecycle {

    private val lock = SingleInstanceLock()

    private val _launchArgs = MutableStateFlow<Map<String, String>>(emptyMap())
    override val launchArgs: StateFlow<Map<String, String>> = _launchArgs.asStateFlow()

    override fun acquireInstanceLock(): Boolean = lock.acquire()

    override fun releaseInstanceLock() {
        lock.release()
    }

    override val isFirstLaunch: Boolean
        get() = _isFirstLaunch

    private val _isFirstLaunch = true

    /** 把命令行参数解析为 "name=value" 键值对 */
    fun setArgs(vararg args: String) {
        if (args.isEmpty()) return
        _launchArgs.value = args.mapNotNull {
            val idx = it.indexOf('=')
            if (idx > 0) it.substring(0, idx) to it.substring(idx + 1) else null
        }.toMap()
    }

    override fun requestExit() {
        // 由 Main 负责真正的退出流程(注销热键/托盘/关闭 Koin)
    }
}