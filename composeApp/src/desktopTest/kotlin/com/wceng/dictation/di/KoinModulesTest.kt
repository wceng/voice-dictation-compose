package com.wceng.dictation

import com.wceng.dictation.core.DictationController
import com.wceng.dictation.core.Recorder
import com.wceng.dictation.core.SoundFeedback
import com.wceng.dictation.core.TextInjector
import com.wceng.dictation.data.network.OkHttpSttNetworkDataSource
import com.wceng.dictation.data.network.SttNetworkDataSource
import com.wceng.dictation.data.repository.ConfigRepository
import com.wceng.dictation.data.repository.LocalUiPreferencesRepository
import com.wceng.dictation.data.repository.OfflineFirstConfigRepository
import com.wceng.dictation.data.repository.UiPreferencesRepository
import com.wceng.dictation.data.store.DictationPreferencesDataSource
import com.wceng.dictation.di.appModules
import com.wceng.dictation.platform.AudioDeviceManager
import com.wceng.dictation.platform.AppLifecycle
import com.wceng.dictation.platform.ClipboardManager
import com.wceng.dictation.platform.NotificationService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.nio.file.Files
import java.nio.file.Path

/**
 * Koin 依赖图冒烟测试:全部定义可实例化、接口绑定正确、单例语义与关闭联动。
 * 每例独立 @TempDir 子目录(DataStore 要求同一文件同时只能有一个活跃实例)。
 */
class KoinModulesTest {

    @TempDir
    lateinit var tempDir: Path

    private fun newDir(sub: String): Path =
        tempDir.resolve(sub).also { Files.createDirectories(it) }

    @Test
    fun allDefinitionsResolveWithCorrectBindings() = runBlocking {
        val koin = startKoin { modules(appModules(newDir("resolve"))) }.koin
        try {
            assertNotNull(koin.get<DictationPreferencesDataSource>())
            assertTrue(koin.get<ConfigRepository>() is OfflineFirstConfigRepository)
            // 接口绑定到桌面实现
            assertTrue(koin.get<SttNetworkDataSource>() is OkHttpSttNetworkDataSource)
            assertTrue(koin.get<Recorder>() !is SttNetworkDataSource)
            assertNotNull(koin.get<TextInjector>())
            assertNotNull(koin.get<SoundFeedback>())
            assertNotNull(koin.get<DictationController>())
            assertNotNull(koin.get<NotificationService>())
            // 具体类也必须可解析(Main.kt 用 koin.get<DesktopNotificationService>().attach 桥接托盘)
            assertNotNull(koin.get<com.wceng.dictation.platform.DesktopNotificationService>())
            assertSame(
                koin.get<NotificationService>(),
                koin.get<com.wceng.dictation.platform.DesktopNotificationService>()
            )
            assertNotNull(koin.get<AudioDeviceManager>())
            assertNotNull(koin.get<ClipboardManager>())
            assertNotNull(koin.get<AppLifecycle>())
            // UI 偏好仓库走同一数据源
            assertTrue(koin.get<UiPreferencesRepository>() is LocalUiPreferencesRepository)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun graphVerificationPassesStatically() {
        // 静态校验跨模块依赖:必须用 includes 合并成单一父模块再校验,
        // 逐个 module.verify()/verifyAll() 都看不到其他模块的定义
        module { includes(appModules(newDir("verify"))) }.verify()
        // 再经独立上下文实例化一遍(checkModules 的等价轻量版)
        val app = koinApplication { modules(appModules(newDir("verify-live"))) }
        try {
            assertNotNull(app.koin.get<DictationController>())
        } finally {
            app.close()
        }
    }

    @Test
    fun singletonsAreSharedAcrossResolutions() {
        val koin = startKoin { modules(appModules(newDir("singleton"))) }.koin
        try {
            assertSame(koin.get<DictationController>(), koin.get<DictationController>())
            assertSame(koin.get<NotificationService>(), koin.get<NotificationService>())
            assertSame(koin.get<DictationPreferencesDataSource>(), koin.get<DictationPreferencesDataSource>())
        } finally {
            stopKoin()
        }
    }

    @Test
    fun closingKoinReleasesDataStoreHandleForRebind() = runBlocking {
        val dir = newDir("rebind")
        val first = startKoin { modules(appModules(dir)) }.koin
        assertNotNull(first.get<DictationPreferencesDataSource>())
        stopKoin() // 关闭时自动调用 AutoCloseable 单例的 close()

        // 同目录能重新建实例 = 旧句柄确已释放(DataStore 单实例规则的回归保护)
        val second = startKoin { modules(appModules(dir)) }.koin
        try {
            assertEquals(
                com.wceng.dictation.core.model.ThemeMode.SYSTEM,
                second.get<UiPreferencesRepository>().themeMode.first()
            )
        } finally {
            // 注意:必须 stopKoin()(而非 koin.close())才会注销 GlobalContext,
            // 否则后续测试的 startKoin 会抛 AlreadyStarted
            stopKoin()
        }
    }
}
