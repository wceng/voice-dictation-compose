# Voice Dictation (Compose Multiplatform 桌面版)

按一下 `Ctrl+Shift+Space` 开始录音,再按一下结束录音并自动把识别结果粘贴到当前光标处;
录音中按 `Ctrl+Shift+Backspace` 直接取消并丢弃音频。

识别由 OpenAI 兼容的云端 STT API 完成(OpenAI / OpenRouter / Groq 等),本地不跑任何模型。

> 本项目是 `voice-dictation`(Swing/AWT 版)的 Compose Desktop 重写版。
> 目前只启用桌面目标,预留了 commonMain 结构,手机版(Android/iOS)以后再开发。

## 功能

- 🎙️ 全局快捷键听写,无需切换窗口
- 🤚 **两种触发习惯**:「点按切换」按一下开始/停止(默认);「长按说话」按住录音、松开自动转写(push-to-talk),设置页随时切换
- 🎛️ **自定义全局热键**:设置页录制「开始/停止」与「取消」组合键,保存即时生效、重启保留,支持字母/数字/F1–F12/Space/Enter/Tab/Backspace
- ⌨️ 识别文本自动写入剪贴板并模拟 Ctrl+V 注入光标处
- 🔇 录音不足 0.8 秒视为误触自动取消
- 🖥️ 系统托盘三态图标(待机/录音中/转写中)+ 右键菜单
- ⚙️ **应用内设置界面**:API Key、接口地址、模型、语言,保存即生效无需重启
- 🌗 **亮色/暗色主题**:跟随系统(默认,实时跟随 Windows 深浅色)/ 强制亮色 / 强制暗色,选择持久化
- 🚀 **开机自启动**:一键开启/关闭,动态检测 exe 路径写入 HKCU Run,登录即自动运行
- 💾 配置持久化到 Jetpack DataStore(跨平台);未在应用内配置时回退读取环境变量
- 📜 最近转写历史(本地持久化,重启保留)
- ♻️ 网络错误与服务端 5xx 自动重试(指数退避);4xx 不重试
- 🚫 单实例锁,开机自启与手动启动不会双开

## 快捷键

| 默认快捷键 | 作用 |
| --- | --- |
| `Ctrl+Shift+Space` | 开始录音 / 停止并转写 |
| `Ctrl+Shift+Backspace` | 取消录音并丢弃 |

两组均可在**设置 → 全局热键**中自定义:点击「修改」后按下新组合即保存,
即时生效、重启保留。主键支持字母/数字/F1–F12/Space/Enter/Tab/Backspace,
组合必须包含 `Ctrl`/`Alt`/`Win` 之一(仅 Shift 不允许),且两组不得相同。
提示:`Win` 组合易与系统快捷键冲突;若与其他软件的热键重复,双方可能同时响应。

### 触发方式(设置 → 触发方式)

| 模式 | 手势 |
| --- | --- |
| 点按切换(默认) | 按一下热键开始录音,再按一次停止并转写 |
| 长按说话 | 按住热键说话,松开立即停止并转写;轻点(不足 300 毫秒)视为误触自动忽略,不发转写请求 |

两种模式只改变**全局热键**的手势语义;主窗口按钮与托盘菜单始终是普通的开始/停止控件。
长按过程中随时可按取消热键丢弃本次录音。切换即时生效、重启保留。

## 配置

配置存储在 **Jetpack DataStore** 中:`~%USERPROFILE%\.voice-dictation\config.preferences_pb`。

**优先级:应用内保存(DataStore) > 环境变量 > 内置默认值**,留空某项即回退下一级。
启动日志会标注每个字段实际来源(store/env/default),方便排查"改了却不生效"的问题。

| 键 | 说明 | 默认值 |
| --- | --- | --- |
| `OPENAI_API_KEY` | API Key(必填) | 无 |
| `OPENAI_BASE_URL` | OpenAI 兼容接口地址(/v1 结尾) | `https://openrouter.ai/api/v1` |
| `STT_MODEL` | 转写模型 | `qwen/qwen3-asr-flash-2026-02-10` |
| `STT_LANGUAGE` | 识别语言 | `zh` |

推荐直接在主窗口"设置"区填写后点保存——留空的项自动回退环境变量或默认值。

## 外观主题

主窗口"设置"卡的**外观**行可三选一:

| 选项 | 行为 |
| --- | --- |
| 跟随系统(默认) | 实时跟随 Windows 深浅色设置,切换无需重启 |
| 亮色 | 固定 Material 3 亮色配色 |
| 暗色 | 固定 Material 3 暗色配色 |

选择持久化到 DataStore(`ui_theme` 键),重启保留。
注意:系统原生标题栏与托盘菜单由 Windows/Swing 渲染,始终跟随系统设置,不受应用内强制模式影响。

## 开机自启动

设置卡的**开机自启动**开关:

- 开启:运行时**动态检测**当前 exe 路径,写入
  `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`,登录即自动运行。
- 关闭:从该注册表键移除。
- 偏好持久化到 DataStore(`autostart_enabled` 键),重启保留。
- 仅**已安装**版本(jpackage,exe 位于 Program Files)会同步注册表;开发运行(`gradlew run`)
  只存 DataStore,不写注册表,避免污染开发机。

## 开发

要求本机有 JDK 21+(如 Android Studio 自带的 JBR 即可)。

```bash
# 运行
gradlew.bat :composeApp:run

# 测试(WAV 编码 / 配置加载 / STT 重试逻辑)
gradlew.bat :composeApp:desktopTest

# 打包 Windows 安装包(exe/msi,内嵌 JRE,用户免装 JDK)
gradlew.bat :composeApp:packageDistributionForCurrentOS
```

### 项目结构(KMP 新默认单模块结构)

```
composeApp/src/
├── commonMain/kotlin/com/wceng/dictation/
│   ├── core/
│   │   ├── DictationController.kt  # 听写流程编排(IDLE/RECORDING/TRANSCRIBING)
│   │   ├── Recorder.kt             # 录音器接口(通用契约,跨平台可复用)
│   │   ├── WavEncoder.kt           # 纯 Kotlin PCM→WAV 编码
│   │   ├── Feedback.kt             # TextInjector / SoundFeedback 抽象
│   │   └── model/                  # 纯模型(AppConfig/HistoryItem/TranscriptionResult/DictationState)
│   ├── data/                       # 数据层接口(NiA:仓库=单一数据源 SSOT)
│   │   ├── repository/             # ConfigRepository + TranscriptionHistoryRepository + UiPreferencesRepository
│   │   └── network/                # SttNetworkDataSource
│   ├── platform/                   # 跨平台服务契约(各平台 realize;commonMain 只依赖接口)
│   │   ├── AudioDeviceManager.kt   # 麦克风枚举/选择
│   │   ├── ClipboardManager.kt     # 剪贴板读写
│   │   ├── NotificationService.kt  # 系统通知
│   │   ├── AppLifecycle.kt         # 单实例锁/启动参数/退出
│   │   └── PlatformModule.kt       # expect val platformModule(DI 平台装配点)
│   └── ui/
│       ├── DictationApp.kt         # 主窗口界面(跨平台可复用)
│       └── theme/Theme.kt          # DictationTheme(亮/暗 MaterialTheme 包装)
└── desktopMain/kotlin/com/wceng/dictation/
    ├── Main.kt                     # 入口:Koin 启动 + 托盘/热键/窗口(命令式 koin.get / 组合内 koinInject)
    ├── di/AppModule.kt             # Koin 模块:数据源→仓库→网络→平台→控制器(不含平台绑定)
    ├── data/
    │   ├── store/DictationPreferencesDataSource.kt    # 唯一 DataStore 持有者(原始存取)
    │   ├── repository/OfflineFirstConfigRepository.kt # 配置:store>env>默认值 + 来源标记
    │   ├── repository/LocalTranscriptionHistoryRepository.kt # 历史 JSON 持久化(上限50条)
    │   ├── repository/LocalUiPreferencesRepository.kt # UI 偏好(外观主题)持久化
    │   └── network/OkHttpSttNetworkDataSource.kt      # OpenAI 兼容转写客户端(含重试)
    └── platform/
        ├── PlatformModule.kt       # actual val platformModule: 把接口绑到下方 Desktop 实现
        ├── DesktopRecorder.kt      # javax.sound 录音
        ├── DesktopSoundPlayer.kt   # 合成提示音
        ├── DesktopTextInjector.kt  # 剪贴板+Ctrl+V 注入(Robot/xdotool)
        ├── DesktopAudioDeviceManager.kt  # Java Sound 麦克风枚举
        ├── DesktopClipboardManager.kt    # AWT 剪贴板
        ├── DesktopNotificationService.kt # 托盘气泡通知(attach 后绑定 TrayManager)
        ├── DesktopAppLifecycle.kt        # TCP 单实例锁 + 命令行参数
        ├── CrashGuard.kt           # 崩溃 fail-fast(未捕获异常直接退出,防僵尸进程)
        ├── HotkeyService.kt        # JNativeHook 全局热键(纯桌面)
        ├── TrayManager.kt          # 托盘图标+Swing 菜单(纯桌面)
        └── SingleInstanceLock.kt   # 单实例端口锁 + 命令通道(双开唤起主窗口;DesktopAppLifecycle 复用)
```

架构说明:commonMain 只声明跨平台服务的**契约接口**与 `expect val platformModule`,
具体的 `Desktop*` 实现全部位于 desktopMain 的 `platform/`。Koin 通过
expect/actual 平台模块把这套契约绑定到桌面实现——随后追加 `androidTarget()`/
iOS 目标时,在各自 sourceSet 提供一个 `actual val platformModule` 并实现接口即可,
commonMain 的控制器、数据层、UI 零改动。

将来开发手机版:在 `kotlin { }` 中追加 `androidTarget()` / iOS 目标,
只需为各平台提供 `actual val platformModule` + 对应实现(移动端无全局热键与跨应用注入,交互形态会不同)。

### 依赖注入(Koin)

对象图由 [Koin](https://insert-koin.io/)(支持 Compose Multiplatform)统一装配,
入口 `startKoin { modules(appModules()) }`:

- 命令式代码(托盘、热键、启动读取)用 `koin.get<T>()`;组合内用 koin-compose 的 `koinInject<T>()`
- **平台绑定收敛到 `expect val platformModule`**:在 `desktopMain/platform/PlatformModule.kt` 用
  `actual` 绑定 `Recorder→DesktopRecorder`、`TextInjector→DesktopTextInjector`、
  `SoundFeedback→DesktopSoundPlayer`、`AudioDeviceManager→DesktopAudioDeviceManager`、
  `ClipboardManager→DesktopClipboardManager`、`NotificationService→DesktopNotificationService`、
  `AppLifecycle→DesktopAppLifecycle`
- 核心三件(录音/注入/提示音)数据层与网络层定义仍在 `di/AppModule.kt`(commonMain 相关的仓库/网络/控制器)
- 控制器产生的通知经 `NotificationService` 转发给托盘(`Main.kt` 中 `attach` 到 `TrayManager`),
  解决"控制器先于托盘创建"的时序
- 退出时 `stopKoin()` 会自动调用 `AutoCloseable` 单例的 `close()`(含 DataStore 数据源)

仍保持手工装配的三处(有意为之,均纯桌面):`SingleInstanceLock`(进程级门卫,失败即退出)、
`TrayManager`(依赖 compose 可变状态与退出闭包,属 UI 外壳胶水)、
`HotkeyService.register`(companion 注册模式)。

## 已知差异与注意事项

- **崩溃 fail-fast**:任何线程的未捕获异常都会让进程立即退出(`CrashGuard`,main 最先安装)。
  这是刻意的——托盘/AWT 非守护线程会把崩溃后的 JVM 吊成"托盘在、界面死"的僵尸实例并占用单实例锁,干净退出优于半死状态
- **双开即唤起**:重复启动不新开进程,第二实例经本地 TCP 命令通道(`SingleInstanceLock`)请求运行中的实例显示并前置主窗口(从最小化还原、抢焦点)
- Linux 文本注入依赖 X11 + xdotool;Wayland 下不可用
- 托盘菜单中文用 Swing 渲染(AWT native 菜单在部分 JDK 上中文显示为方块)
- 控制台中文日志在某些 JDK 下可能乱码,不影响功能

## License

Apache-2.0(沿用 JetBrains 模板许可)
