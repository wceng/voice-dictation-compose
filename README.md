# Voice Dictation (Compose Multiplatform 桌面版)

按一下 `Ctrl+Shift+Space` 开始录音,再按一下结束录音并自动把识别结果粘贴到当前光标处;
录音中按 `Ctrl+Shift+Backspace` 直接取消并丢弃音频。

识别由 OpenAI 兼容的云端 STT API 完成(OpenAI / OpenRouter / Groq 等),本地不跑任何模型。

> 本项目是 `voice-dictation`(Swing/AWT 版)的 Compose Desktop 重写版。
> 目前只启用桌面目标,预留了 commonMain 结构,手机版(Android/iOS)以后再开发。

## 功能

- 🎙️ 全局快捷键听写,无需切换窗口
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

| 快捷键 | 作用 |
| --- | --- |
| `Ctrl+Shift+Space` | 开始录音 / 停止并转写 |
| `Ctrl+Shift+Backspace` | 取消录音并丢弃 |

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
│   │   ├── Recorder.kt             # 录音器抽象
│   │   ├── WavEncoder.kt           # 纯 Kotlin PCM→WAV 编码
│   │   ├── Feedback.kt             # 文本注入/提示音抽象
│   │   └── model/                  # 纯模型(AppConfig/HistoryItem/TranscriptionResult/DictationState)
│   ├── data/                       # 数据层接口(NiA 模式:仓库=单一数据源 SSOT)
│   │   ├── repository/             # ConfigRepository + TranscriptionHistoryRepository + UiPreferencesRepository
│   │   └── network/                # SttNetworkDataSource
│   └── ui/
│       ├── DictationApp.kt         # 主窗口界面(跨平台可复用)
│       └── theme/Theme.kt          # DictationTheme(亮/暗 MaterialTheme 包装)
└── desktopMain/kotlin/com/wceng/dictation/
    ├── Main.kt                     # 入口:Koin 启动 + 托盘/热键/窗口(命令式 koin.get / 组合内 koinInject)
    ├── di/AppModule.kt             # Koin 模块:数据源→仓库→平台服务→控制器 + NotifierRouter
    ├── data/
    │   ├── store/DictationPreferencesDataSource.kt    # 唯一 DataStore 持有者(原始存取)
    │   ├── repository/OfflineFirstConfigRepository.kt # 配置:store>env>默认值 + 来源标记
    │   ├── repository/LocalTranscriptionHistoryRepository.kt # 历史 JSON 持久化(上限50条)
    │   ├── repository/LocalUiPreferencesRepository.kt # UI 偏好(外观主题)持久化
    │   └── network/OkHttpSttNetworkDataSource.kt      # OpenAI 兼容转写客户端(含重试)
    └── platform/
        ├── DesktopRecorder.kt      # javax.sound 录音
        ├── DesktopSoundPlayer.kt   # 合成提示音
        ├── DesktopTextInjector.kt  # 剪贴板+Ctrl+V 注入(Robot/xdotool)
        ├── HotkeyService.kt        # JNativeHook 全局热键
        ├── TrayManager.kt          # 托盘图标+Swing 菜单
        └── SingleInstanceLock.kt   # 单实例端口锁
```

将来开发手机版:在 `kotlin { }` 中追加 `androidTarget()` / iOS 目标,
commonMain 的核心逻辑与 UI 无需改动,只需为各平台实现 `Recorder`、
`TextInjector` 等接口(移动端没有全局热键与跨应用注入,交互形态会不同)。

### 依赖注入(Koin)

对象图由 [Koin](https://insert-koin.io/)(支持 Compose Multiplatform)统一装配,
定义集中在 `di/AppModule.kt`,入口 `startKoin { modules(appModules()) }`:

- 命令式代码(托盘、热键、启动读取)用 `koin.get<T>()`;组合内用 koin-compose 的 `koinInject<T>()`
- 接口绑定:`Recorder→DesktopRecorder`、`SttNetworkDataSource→OkHttpSttNetworkDataSource` 等
- 控制器产生的通知经 `NotifierRouter` 单例转发给托盘(解决"控制器先于托盘创建"的时序)
- 退出时 `stopKoin()` 会自动调用 `AutoCloseable` 单例的 `close()`(含 DataStore 数据源)

仍保持手工装配的三处(有意为之):`SingleInstanceLock`(进程级门卫,失败即退出)、
`TrayManager`(依赖 compose 可变状态与退出闭包,属 UI 外壳胶水)、
`HotkeyService.register`(companion 注册模式)。

## 已知差异与注意事项

- Linux 文本注入依赖 X11 + xdotool;Wayland 下不可用
- 托盘菜单中文用 Swing 渲染(AWT native 菜单在部分 JDK 上中文显示为方块)
- 控制台中文日志在某些 JDK 下可能乱码,不影响功能

## License

Apache-2.0(沿用 JetBrains 模板许可)
