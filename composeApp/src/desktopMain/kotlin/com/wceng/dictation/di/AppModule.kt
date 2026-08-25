package com.wceng.dictation.di

import com.wceng.dictation.core.DictationController
import com.wceng.dictation.data.network.OkHttpSttNetworkDataSource
import com.wceng.dictation.data.network.SttNetworkDataSource
import com.wceng.dictation.data.repository.ConfigRepository
import com.wceng.dictation.data.repository.LocalTranscriptionHistoryRepository
import com.wceng.dictation.data.repository.LocalUiPreferencesRepository
import com.wceng.dictation.data.repository.OfflineFirstConfigRepository
import com.wceng.dictation.data.repository.TranscriptionHistoryRepository
import com.wceng.dictation.data.repository.UiPreferencesRepository
import com.wceng.dictation.data.store.DictationPreferencesDataSource
import com.wceng.dictation.platform.NotificationService
import com.wceng.dictation.platform.platformModule
import org.koin.core.module.Module
import org.koin.dsl.module
import java.nio.file.Path

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
                get<NotificationService>().show(title, message, isError)
            }
        )
    }
}

/** 组合入口:生产与测试共用同一份图定义 */
fun appModules(dataSourceDir: Path = DictationPreferencesDataSource.defaultDir()): List<Module> =
    listOf(dataStoreModule(dataSourceDir), repositoryModule, networkModule, platformModule, controllerModule)