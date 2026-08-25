package com.wceng.dictation.platform

import org.koin.core.module.Module

/**
 * 平台相关 Koin 模块契约。
 * 每个平台(desktop/android/ios)在各自 sourceSet 提供 actual 实现。
 * commonMain 仅声明平台模块与核心服务的接口,Binding 与实现物由各平台给出。
 */
expect val platformModule: Module