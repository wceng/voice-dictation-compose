package com.wceng.dictation.data.repository

import com.wceng.dictation.core.model.AppConfig
import com.wceng.dictation.core.model.ConfigSource
import com.wceng.dictation.core.model.ConfigUpdate
import com.wceng.dictation.data.store.DictationPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 配置仓库实现(OfflineFirst 模式):
 * 本地 DataStore 为权威数据源,缺失项回退环境变量,再回退内置默认值;
 * 每个字段的实际来源记入 [AppConfig.sources],供启动日志与设置界面提示。
 */
class OfflineFirstConfigRepository(
    private val dataSource: DictationPreferencesDataSource,
    private val env: Map<String, String> = System.getenv()
) : ConfigRepository {

    override val config: Flow<AppConfig> = dataSource.configValues
        .map(::resolve)
        .distinctUntilChanged()

    override suspend fun save(update: ConfigUpdate) {
        // 留空写入空串 = 显式置空 = 回退到环境变量/默认值
        dataSource.updateConfig { values ->
            update.apiKey?.let { values[DictationPreferencesDataSource.KEY_API] = it.trim() }
            update.baseUrl?.let { values[DictationPreferencesDataSource.KEY_URL] = it.trim() }
            update.model?.let { values[DictationPreferencesDataSource.KEY_MODEL] = it.trim() }
            update.language?.let { values[DictationPreferencesDataSource.KEY_LANG] = it.trim() }
        }
    }

    private fun resolve(stored: Map<String, String>): AppConfig {
        val sources = LinkedHashMap<String, ConfigSource>()

        fun resolveValue(prefKey: String, default: String): String {
            val envName = ENV_BY_PREF.getValue(prefKey)
            stored[prefKey]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                sources[envName] = ConfigSource.STORE
                return it
            }
            env[envName]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                sources[envName] = ConfigSource.ENV
                return it
            }
            sources[envName] = ConfigSource.DEFAULT
            return default
        }

        val d = AppConfig.DEFAULTS
        return AppConfig(
            apiKey = resolveValue(DictationPreferencesDataSource.KEY_API, d.apiKey),
            baseUrl = resolveValue(DictationPreferencesDataSource.KEY_URL, d.baseUrl),
            model = resolveValue(DictationPreferencesDataSource.KEY_MODEL, d.model),
            language = resolveValue(DictationPreferencesDataSource.KEY_LANG, d.language),
            sources = sources
        )
    }

    companion object {
        // 环境变量名(sources 标记与启动日志使用)
        const val KEY_API = "OPENAI_API_KEY"
        const val KEY_URL = "OPENAI_BASE_URL"
        const val KEY_MODEL = "STT_MODEL"
        const val KEY_LANG = "STT_LANGUAGE"

        /** 存储键 -> 环境变量名 */
        private val ENV_BY_PREF = mapOf(
            DictationPreferencesDataSource.KEY_API to KEY_API,
            DictationPreferencesDataSource.KEY_URL to KEY_URL,
            DictationPreferencesDataSource.KEY_MODEL to KEY_MODEL,
            DictationPreferencesDataSource.KEY_LANG to KEY_LANG
        )
    }
}
