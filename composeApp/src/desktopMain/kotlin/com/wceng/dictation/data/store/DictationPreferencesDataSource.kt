package com.wceng.dictation.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wceng.dictation.core.model.HistoryItem
import com.wceng.dictation.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * 偏好数据源(NiA 模式):应用内唯一的 DataStore 持有者。
 *
 * 只做原始存取,不含业务规则(回退优先级、来源标记在仓库层);
 * 历史记录以 JSON 串存储,编解码在本层完成,损坏时容错为空列表。
 */
class DictationPreferencesDataSource(
    dir: Path = defaultDir()
) : AutoCloseable {

    private val storeFile: Path = dir.resolve(FILE_NAME)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        Files.createDirectories(storeFile.parent)
        storeFile.toFile()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** 存储内的配置键值(空串原样暴露,"留空=未设置"的判断交给仓库层) */
    val configValues: Flow<Map<String, String>> = store.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.value is String }
            .associate { it.key.name to (it.value as String) }
            .filterKeys { it in CONFIG_PREF_KEYS }
    }

    /** 批量写入/更新配置项;值为空串表示显式置空(= 回退到环境变量/默认值) */
    suspend fun updateConfig(transform: (MutableMap<String, String>) -> Unit) {
        store.edit { prefs ->
            val mutable = CONFIG_PREF_KEYS.associateWith { prefs[stringPreferencesKey(it)].orEmpty() }
                .toMutableMap()
            transform(mutable)
            mutable.forEach { (name, value) -> prefs[stringPreferencesKey(name)] = value }
        }
    }

    /** 转写历史(新→旧顺序由调用方维护);JSON 损坏时返回空列表并打日志 */
    val history: Flow<List<HistoryItem>> = store.data
        .map { prefs -> prefs[HISTORY_KEY]?.let(::decodeHistory).orEmpty() }
        .catch { e ->
            System.err.println("[Store] 历史读取失败(${e.message}),按空历史处理")
            emit(emptyList())
        }

    /** 外观主题偏好;未设置或存储值非法时回退 SYSTEM(跟随系统) */
    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        ThemeMode.fromRawOrDefault(prefs[THEME_KEY])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { prefs -> prefs[THEME_KEY] = mode.raw }
    }

    /** 开机自启动偏好;未设置时默认 false(关闭) */
    val autostart: Flow<Boolean> = store.data.map { prefs ->
        prefs[AUTOSTART_KEY]?.toBoolean() ?: false
    }

    suspend fun setAutostart(enabled: Boolean) {
        store.edit { prefs -> prefs[AUTOSTART_KEY] = enabled.toString() }
    }

    suspend fun setHistory(items: List<HistoryItem>) {
        store.edit { prefs ->
            if (items.isEmpty()) prefs.remove(HISTORY_KEY)
            else prefs[HISTORY_KEY] = json.encodeToString(items)
        }
    }

    private fun decodeHistory(raw: String): List<HistoryItem>? = try {
        json.decodeFromString<List<HistoryItem>>(raw)
    } catch (e: SerializationException) {
        System.err.println("[Store] 历史 JSON 解析失败,已忽略: ${e.message}")
        null
    }

    /**
     * 取消内部作用域,释放该文件上的 DataStore 活跃句柄。
     * DataStore 要求同一文件同时只能有一个活跃实例;应用退出或测试重建实例前调用。
     */
    /** 存储位置(设置界面展示用) */
    val storageLocation: String get() = storeFile.toString()

    override fun close() {
        scope.cancel()
    }

    companion object {
        private const val DIR_NAME = ".voice-dictation"
        private const val FILE_NAME = "config.preferences_pb"

        // 配置项的 preference 存储键(小写);与环境变量名的对应关系见仓库层映射
        const val KEY_API = "openai_api_key"
        const val KEY_URL = "openai_base_url"
        const val KEY_MODEL = "stt_model"
        const val KEY_LANG = "stt_language"

        val CONFIG_PREF_KEYS = setOf(KEY_API, KEY_URL, KEY_MODEL, KEY_LANG)

        // UI 偏好键:不参与后端配置的「存储>环境变量>默认值」优先级体系
        const val KEY_UI_THEME = "ui_theme"
        const val KEY_AUTOSTART = "autostart_enabled"
        private val THEME_KEY = stringPreferencesKey(KEY_UI_THEME)
        private val AUTOSTART_KEY = stringPreferencesKey(KEY_AUTOSTART)

        private val HISTORY_KEY = stringPreferencesKey("transcription_history_json")

        fun defaultDir(): Path =
            Path.of(System.getProperty("user.home") ?: ".", DIR_NAME)
    }
}
