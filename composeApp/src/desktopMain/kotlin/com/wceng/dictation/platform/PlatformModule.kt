package com.wceng.dictation.platform

import com.wceng.dictation.core.Recorder
import com.wceng.dictation.core.SoundFeedback
import com.wceng.dictation.core.TextInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 桌面平台 Koin 模块(commonMain expect 的 actual)。
 * 绑定平台核心服务接口 → 桌面 actual 实现,以及平台级协程作用域。
 */
actual val platformModule: Module = module {
    // 平台核心服务(expect 接口 -> actual 实现)
    single<Recorder> { DesktopRecorder() }
    single<TextInjector> { DesktopTextInjector() }
    single<SoundFeedback> { DesktopSoundPlayer() }
    // 新增平台抽象
    single<AudioDeviceManager> { DesktopAudioDeviceManager() }
    single<ClipboardManager> { DesktopClipboardManager() }
    // 注意:通知服务同时注册「具体类」与「接口」两个类型——
    // Main.kt 需要具体类调用 attach(),控制器依赖接口 show();
    // 若只按接口注册,koin.get<DesktopNotificationService>() 会抛 NoDefinitionFound
    single { DesktopNotificationService() }
    single<NotificationService> { get<DesktopNotificationService>() }
    single<AppLifecycle> { DesktopAppLifecycle() }
    // 应用级协程作用域:控制器与 Main 的后台任务共用同一生命周期
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
}