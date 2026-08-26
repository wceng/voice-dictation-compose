package com.wceng.dictation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wceng.dictation.core.DictationController
import com.wceng.dictation.core.model.AppConfig
import com.wceng.dictation.core.model.ConfigSource
import com.wceng.dictation.core.model.ConfigUpdate
import com.wceng.dictation.core.model.HotkeyCombo
import com.wceng.dictation.core.model.HotkeyConfig
import com.wceng.dictation.data.repository.ConfigRepository
import com.wceng.dictation.data.repository.OfflineFirstConfigRepository
import com.wceng.dictation.data.repository.TranscriptionHistoryRepository
import com.wceng.dictation.data.repository.UiPreferencesRepository
import com.wceng.dictation.data.store.DictationPreferencesDataSource
import com.wceng.dictation.di.appModules
import com.wceng.dictation.platform.CrashGuard
import com.wceng.dictation.platform.DesktopNotificationService
import com.wceng.dictation.platform.HotkeyService
import com.wceng.dictation.platform.SingleInstanceLock
import com.wceng.dictation.platform.TrayManager
import com.wceng.dictation.ui.DictationApp
import com.wceng.dictation.ui.theme.DictationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

fun main() {
    // 崩溃 fail-fast:必须最先安装——托盘/AWT 的非守护线程会把主线程崩溃后的
    // JVM 吊成僵尸(托盘在、界面死、占着单实例锁),任何未捕获异常直接退出
    CrashGuard.install()

    // 主窗口可见性与 AWT 窗口引用:先于单实例锁声明,供唤起命令回调使用
    val windowVisible = mutableStateOf(true)
    val awtWindowRef = AtomicReference<java.awt.Frame?>(null)

    /** 显示并前置主窗口(托盘菜单、二次启动唤起共用同一入口) */
    fun showMainWindow() {
        windowVisible.value = true
        awtWindowRef.get()?.let { w ->
            SwingUtilities.invokeLater {
                w.isVisible = true
                // 从最小化还原:清除 ICONIFIED 标志位
                w.extendedState = w.extendedState and java.awt.Frame.ICONIFIED.inv()
                // Windows 前台锁:后台进程直接 SetForegroundWindow 会被拒。
                // 模拟一次 ALT 键击让系统视为"有近期用户输入",再配合
                // alwaysOnTop 短暂置顶,确保窗口真正到最前并拿到焦点。
                runCatching {
                    val robot = java.awt.Robot()
                    robot.keyPress(java.awt.event.KeyEvent.VK_ALT)
                    robot.keyRelease(java.awt.event.KeyEvent.VK_ALT)
                }
                runCatching { w.isAlwaysOnTop = true }
                w.toFront()
                w.requestFocusInWindow()
                runCatching { w.isAlwaysOnTop = false }
            }
        }
    }

    // 单实例保护:第二实例不再静默退出,而是经 TCP 命令通道请求第一实例唤起主窗口
    val lock = SingleInstanceLock { command ->
        if (command == SingleInstanceLock.CMD_SHOW) showMainWindow()
    }
    if (!lock.acquire()) {
        val sent = SingleInstanceLock.sendCommand(SingleInstanceLock.CMD_SHOW)
        println(
            "[Main] 检测到已有实例在运行," +
                if (sent) "已请求其显示主窗口,本次启动退出" else "唤起请求发送失败,本次启动退出"
        )
        return
    }

    // NiA 分层装配改由 Koin 容器持有:数据源 -> 仓库 -> 平台服务 -> 控制器。
    // 命令式代码(托盘/热键/启动读取)用 koin.get<T>(),组合内用 koinInject<T>()。
    val koin = startKoin { modules(appModules()) }.koin
    val dataSource = koin.get<DictationPreferencesDataSource>()
    val appScope = koin.get<CoroutineScope>()

    // 启动日志用初始配置(窗口尚未创建,短暂阻塞无碍;之后全部走 Flow 自动更新)
    val initialConfig = runBlocking { koin.get<ConfigRepository>().config.first() }
    // 初始主题同理:避免首帧以默认值闪现亮色
    val initialThemeMode = runBlocking { koin.get<UiPreferencesRepository>().themeMode.first() }
    // 初始自启动状态(用于托盘/热键注册前同步,仅此处阻塞一次)
    val initialAutostart = runBlocking { koin.get<UiPreferencesRepository>().autostart.first() }
    // 初始全局热键(注册钩子前读取,避免默认值覆盖用户自定义)
    val initialHotkeys: HotkeyConfig = runBlocking {
        koin.get<UiPreferencesRepository>().hotkeys.first()
    }.let { base ->
        // 隐藏调试开关:VD_DEBUG_HOTKEY=<规范串> 可在启动时强制初始热键,
        // 用于无 GUI 环境验证钩子绑定(如 VD_DEBUG_HOTKEY=CTRL+SHIFT+M)
        val override = System.getenv("VD_DEBUG_HOTKEY")?.let(HotkeyCombo::parseOrNull)
        if (override != null) {
            println("[Main][DBG] 覆盖初始热键为 ${override.canonical()}")
            HotkeyConfig(override, base.cancel)
        } else base
    }

    // JNativeHook 原生库随 jar 分发:启动时解压到用户临时目录(永远可写)再加载,
    // 避免安装到 Program Files 后运行时解压到只读的 jar 目录导致启动崩溃。
    // 开发运行同样走此逻辑,行为一致。
    runCatching {
        val dllName = "JNativeHook-2.2.2.x86_64.dll"
        val target = java.io.File(System.getProperty("java.io.tmpdir"), "voice-dictation-native/$dllName")
        Thread.currentThread().contextClassLoader
            .getResourceAsStream("jnativehook/$dllName")?.use { input ->
                target.parentFile.mkdirs()
                target.outputStream().use { output -> input.copyTo(output) }
                System.setProperty("jnativehook.lib.path", target.parentFile.absolutePath)
            }
    }

    var tray: TrayManager? = null

    // 控制器从容器解析(其 onNotify 已绑定 NotificationService,托盘就绪后 attach 真正接收器)
    val controller = koin.get<DictationController>()

    fun logConfig(c: AppConfig) {
        fun src(key: String) = when (c.sources[key]) {
            ConfigSource.STORE -> "store"
            ConfigSource.ENV -> "env"
            else -> "default"
        }
        println(
            "[Config] 模型=${c.model}(${src(OfflineFirstConfigRepository.KEY_MODEL)}) " +
                "语言=${c.language}(${src(OfflineFirstConfigRepository.KEY_LANG)}) " +
                "API=${c.baseUrl}(${src(OfflineFirstConfigRepository.KEY_URL)}) " +
                "Key=${if (c.configured) "已配置" else "未配置"}(${src(OfflineFirstConfigRepository.KEY_API)})"
        )
        println("[Config] 存储位置: ${dataSource.storageLocation}")
    }

    fun exitApp() {
        println("[Main] 用户退出")
        runCatching { HotkeyService.unregister() }
        runCatching { tray?.dispose() }
        // Koin 关闭时自动调用 AutoCloseable 单例的 close()(含 DataStore 数据源)
        runCatching { stopKoin() }
        lock.release()
        exitProcess(0)
    }

    tray = TrayManager(
        onToggle = { controller.toggle() },
        onCancel = { controller.cancelRecording() },
        onShowWindow = ::showMainWindow,
        onQuit = ::exitApp
    )

    // 托盘就绪:接通控制器通知(转发到托盘气泡,同时保留控制台日志)
    koin.get<DesktopNotificationService>().attach { title, message, isError ->
        println("[Notify] $title: $message")
        tray?.notify(title, message, isError)
    }

    tray.show()
    logConfig(initialConfig)

    if (!initialConfig.configured) {
        tray.notify(
            "缺少 API Key",
            "请在主窗口设置中填写",
            true
        )
    }

    // 状态 -> 托盘单向同步
    appScope.launch {
        controller.state.collect { tray.setState(it) }
    }

    val hotkeyService = HotkeyService(
        initialHotkeys,
        onToggle = { controller.toggle() },
        onCancel = { controller.cancelRecording() }
    )
    HotkeyService.register(hotkeyService)
    println("[Main] 快捷键: ${initialHotkeys.toggle.displayText()} 开始/停止并转写 | ${initialHotkeys.cancel.displayText()} 取消录音")

    // 设置页改键即时生效:收集仓库热键流,原子替换钩子绑定
    appScope.launch {
        koin.get<UiPreferencesRepository>().hotkeys.collect { config ->
            hotkeyService.updateBindings(config)
            println("[Main] 热键已更新: ${config.toggle.displayText()} / ${config.cancel.displayText()}")
        }
    }

    // 保存后无需手动刷新:仓库 Flow 自动向界面广播新值,这里只做通知与日志
    fun saveConfig(update: ConfigUpdate) {
        appScope.launch {
            val configRepo = koin.get<ConfigRepository>()
            configRepo.save(update)
            val fresh = configRepo.config.first()
            logConfig(fresh)
            tray.notify("设置已保存", if (fresh.configured) "配置已生效" else "API Key 仍为空", !fresh.configured)
        }
    }

    application {
        // 组合内经 koin-compose 的 koinInject 解析(与命令式 koin.get 指向同一批单例)
        val configRepo = koinInject<ConfigRepository>()
        val historyRepo = koinInject<TranscriptionHistoryRepository>()
        val themeRepo = koinInject<UiPreferencesRepository>()
        val themeMode = themeRepo.themeMode.collectAsState(initialThemeMode).value
        val autostart = themeRepo.autostart.collectAsState(initialAutostart).value
        val hotkeys = themeRepo.hotkeys.collectAsState(initialHotkeys).value

        // 同步校验热键供捕获框内联提示;合法则启动保存协程并返回 null
        fun hotkeyErrorOrNull(combo: HotkeyCombo, conflicting: HotkeyCombo?): String? {
            combo.validate()?.let { return it }
            if (conflicting != null && combo == conflicting) return "与另一组热键相同"
            return null
        }

        Window(
            onCloseRequest = { windowVisible.value = false },
            visible = windowVisible.value,
            state = rememberWindowState(width = 520.dp, height = 860.dp),
            title = "Voice Dictation"
        ) {
            // 记录 AWT 窗口引用:二次启动唤起时用于还原/前置窗口
            SideEffect { awtWindowRef.set(window) }
            DictationTheme(themeMode = themeMode) {
                // Surface 让窗口背景随 colorScheme.surface 切换(否则暗色下仍是白底)
                Surface(modifier = Modifier.fillMaxSize()) {
                    DictationApp(
                        state = controller.state.collectAsState().value,
                        history = historyRepo.history.collectAsState(emptyList()).value,
                        config = configRepo.config.collectAsState(initialConfig).value,
                        storageLocation = dataSource.storageLocation,
                        formatTimestamp = { ts ->
                            java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        },
                        themeMode = themeMode,
                        autostart = autostart,
                        hotkeys = hotkeys,
                        onToggle = { controller.toggle() },
                        onCancel = { controller.cancelRecording() },
                        onClearHistory = { controller.clearHistory() },
                        onSaveConfig = ::saveConfig,
                        onThemeModeChange = { mode ->
                            appScope.launch { themeRepo.setThemeMode(mode) }
                        },
                        onAutostartChange = { enabled ->
                            appScope.launch { themeRepo.setAutostart(enabled) }
                        },
                        onSaveToggleHotkey = { combo ->
                            hotkeyErrorOrNull(combo, hotkeys.cancel) ?: run {
                                appScope.launch { runCatching { themeRepo.setToggleHotkey(combo) } }
                                null
                            }
                        },
                        onSaveCancelHotkey = { combo ->
                            hotkeyErrorOrNull(combo, hotkeys.toggle) ?: run {
                                appScope.launch { runCatching { themeRepo.setCancelHotkey(combo) } }
                                null
                            }
                        }
                    )
                }
            }
        }
    }
}
