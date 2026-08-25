package com.wceng.dictation.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * 应用生命周期与单实例管理抽象。
 * 桌面端：TCP 端口锁 + 进程参数解析；
 * Android：单 Task/LaunchMode + Intent；
 * iOS：UIApplicationDelegate + 单实例由系统保证。
 */
interface AppLifecycle {

    /** 尝试获取单实例锁，失败表示已有实例运行 */
    fun acquireInstanceLock(): Boolean

    /** 释放单实例锁（正常退出时调用） */
    fun releaseInstanceLock()

    /** 应用是否为首次启动（非二次启动激活） */
    val isFirstLaunch: Boolean

    /** 启动参数（桌面：命令行参数；Android：Intent extras；iOS：launchOptions） */
    val launchArgs: StateFlow<Map<String, String>>

    /** 请求退出应用 */
    fun requestExit()
}