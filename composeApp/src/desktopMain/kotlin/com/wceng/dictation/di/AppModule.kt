package com.wceng.dictation.di

import com.wceng.dictation.core.DictationController
import com.wceng.dictation.core.Recorder
import com.wceng.dictation.core.SoundFeedback
import com.wceng.dictation.core.TextInjector
import com.wceng.dictation.data.network.OkHttpSttNetworkDataSource
import com.wceng.dictation.data.network.SttNetworkDataSource
import com.wceng.dictation.data.repository.ConfigRepository
import com.wceng.dictation.data.repository.LocalTranscriptionHistoryRepository
import com.wceng.dictation.data.repository.LocalUiPreferencesRepository
import com.wceng.dictation.data.repository.OfflineFirstConfigRepository
import com.wceng.dictation.data.repository.TranscriptionHistoryRepository
import com.wceng.dictation.data.repository.UiPreferencesRepository
import com.wceng.dictation.data.store.DictationPreferencesDataSource
import com.wceng.dictation.platform.DesktopRecorder
import com.wceng.dictation.platform.DesktopSoundPlayer
import com.wceng.dictation.platform.DesktopTextInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import java.nio.file.Path

/**
 * 通知路由:解决「控制器先于托盘创建」的时序——控制器产生的通知先落到这里,
 * 托盘就绪后 attach 真正的接收器(语义等同旧 Main.kt 的可空 tray 变量桥接)。
 */
class NotifierRouter {
    @Volatile
    private var sink: ((title: String, message: String, isError: Boolean) -> Unit)? = null

    fun attach(sink: (title: String, message: String, isError: Boolean) -> Unit) {
        this.sink = sink
    }

    fun notify(title: String, message: String, isError: Boolean) {
        sink?.invoke(title, message, isError)
    }
}

/**
 * 数据存储模块:目录参数化——生产走默认 ~/.voice-dictation,
 * 测试传入 @TempDir 子目录以满足 DataStore 单实例规则。
 */
fun dataStoreModule(dir: Path = DictationPreferencesDataSource.defaultDir()): Module = module {
    single { DictationPreferencesDataSource(dir) }
}

val repositoryModule = module {
    single<ConfigRepository> { OfflineFirstConfigRepository(get()) }
    single<TranscriptionHistoryRepository> { LocalTranscriptionHistoryRepository(get()) }
    single<UiPreferencesRepository> { LocalUiPreferencesRepository(get()) }
}

val networkModule = module {
    single<SttNetworkDataSource> { OkHttpSttNetworkDataSource() }
}

val platformModule = module {
    single<Recorder> { DesktopRecorder() }
    single<TextInjector> { DesktopTextInjector() }
    single<SoundFeedback> { DesktopSoundPlayer() }
    // 应用级协程作用域:控制器与 Main 的后台任务共用同一生命周期
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { NotifierRouter() }
}

val controllerModule = module {
    single {
        DictationController(
            recorder = get(),
            sttNetwork = get(),
            injector = get(),
            sound = get(),
            scope = get(),
            configRepository = get(),
            historyRepository = get(),
            onNotify = { title, message, isError ->
                get<NotifierRouter>().notify(title, message, isError)
            }
        )
    }
}

/** 组合入口:生产与测试共用同一份图定义 */
fun appModules(dataSourceDir: Path = DictationPreferencesDataSource.defaultDir()): List<Module> =
    listOf(dataStoreModule(dataSourceDir), repositoryModule, networkModule, platformModule, controllerModule)
