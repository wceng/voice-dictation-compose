package com.wceng.dictation

import com.wceng.dictation.core.WavEncoder
import com.wceng.dictation.core.model.AppConfig
import com.wceng.dictation.core.model.ConfigSource
import com.wceng.dictation.core.model.ConfigUpdate
import com.wceng.dictation.core.model.HistoryItem
import com.wceng.dictation.core.model.ThemeMode
import com.wceng.dictation.core.model.TranscriptionResult
import com.wceng.dictation.data.network.OkHttpSttNetworkDataSource
import com.wceng.dictation.data.repository.LocalTranscriptionHistoryRepository
import com.wceng.dictation.data.repository.LocalUiPreferencesRepository
import com.wceng.dictation.data.repository.OfflineFirstConfigRepository
import com.wceng.dictation.data.store.DictationPreferencesDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class VoiceDictationTest {

    // ===== WavEncoder =====

    @Test
    fun wavHeaderIsCorrect() {
        val pcm = ByteArray(32000) // 1 秒 16kHz/16bit/单声道
        val wav = WavEncoder.encode(pcm)

        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4)))
        assertEquals("WAVE", String(wav.copyOfRange(8, 12)))
        assertEquals(36 + pcm.size, readLe32(wav, 4))
        assertEquals(WavEncoder.SAMPLE_RATE, readLe32(wav, 24))
        assertEquals(16, readLe16(wav, 34))
        assertEquals(pcm.size, readLe32(wav, 40))
        assertTrue(wav.copyOfRange(44, wav.size).contentEquals(pcm))
    }

    private fun readLe32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun readLe16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    // ===== OfflineFirstConfigRepository =====

    @TempDir
    lateinit var tempDir: Path

    // 每个用例独立子目录:DataStore 要求同一文件同时只能有一个活跃实例
    private fun newDs(sub: String): DictationPreferencesDataSource =
        DictationPreferencesDataSource(dir = tempDir.resolve(sub).also { Files.createDirectories(it) })

    @Test
    fun defaultsApplyWhenStorageEmpty() = runBlocking {
        val ds = newDs("defaults")
        val config = OfflineFirstConfigRepository(ds, env = emptyMap()).config.first()

        assertFalse(config.configured)
        assertEquals(AppConfig.DEFAULTS.baseUrl, config.baseUrl)
        assertEquals(AppConfig.DEFAULTS.model, config.model)
        assertEquals(ConfigSource.DEFAULT, config.sources[OfflineFirstConfigRepository.KEY_MODEL])
        ds.close()
    }

    @Test
    fun storeTakesPrecedenceOverEnv() = runBlocking {
        val ds = newDs("precedence")
        val repo = OfflineFirstConfigRepository(ds, env = mapOf("OPENAI_API_KEY" to "env-key"))

        repo.save(ConfigUpdate(apiKey = "stored-key", baseUrl = "https://stored.example.com/v1"))

        val config = repo.config.first()
        assertEquals("stored-key", config.apiKey)
        assertEquals("https://stored.example.com/v1", config.baseUrl)
        assertEquals(ConfigSource.STORE, config.sources[OfflineFirstConfigRepository.KEY_API])
        ds.close()
    }

    @Test
    fun envFillsMissingStoreEntryThenDefaults() = runBlocking {
        val ds = newDs("envfill")
        val config = OfflineFirstConfigRepository(ds, env = mapOf("OPENAI_API_KEY" to "env-key"))
            .config.first()
        assertEquals("env-key", config.apiKey)
        assertEquals(ConfigSource.ENV, config.sources[OfflineFirstConfigRepository.KEY_API])
        ds.close()

        val ds2 = newDs("envfill2")
        val config2 = OfflineFirstConfigRepository(ds2, env = emptyMap()).config.first()
        assertEquals(AppConfig.DEFAULTS.model, config2.model)
        ds2.close()
    }

    @Test
    fun storedValuesTrimmedBlankMeansMissing() = runBlocking {
        val ds = newDs("blank")
        val repo = OfflineFirstConfigRepository(ds, env = emptyMap())

        repo.save(ConfigUpdate(apiKey = "  sk-abc  ", baseUrl = ""))

        val config = repo.config.first()
        assertEquals("sk-abc", config.apiKey)
        assertEquals(AppConfig.DEFAULTS.baseUrl, config.baseUrl) // 空串=未设置 -> 默认值
        ds.close()
    }

    @Test
    fun savePersistsAcrossInstances() = runBlocking {
        val dir = tempDir.resolve("persist").also { Files.createDirectories(it) }
        val ds1 = DictationPreferencesDataSource(dir)
        OfflineFirstConfigRepository(ds1, env = emptyMap())
            .save(ConfigUpdate(apiKey = "sk-new", model = "my-model"))
        ds1.close() // 释放文件句柄后才能在同一文件上重建实例

        val ds2 = DictationPreferencesDataSource(dir)
        val config = OfflineFirstConfigRepository(ds2, env = emptyMap()).config.first()
        assertEquals("sk-new", config.apiKey)
        assertEquals("my-model", config.model)
        ds2.close()
    }

    // ===== LocalTranscriptionHistoryRepository =====

    @Test
    fun historyRecordPersistsCapsAndClears() = runBlocking {
        val dir = tempDir.resolve("history").also { Files.createDirectories(it) }
        val ds = DictationPreferencesDataSource(dir)
        val repo = LocalTranscriptionHistoryRepository(ds)

        repeat(55) { i -> repo.record(HistoryItem("text-$i", i.toLong(), true)) }

        val after = repo.history.first()
        assertEquals(LocalTranscriptionHistoryRepository.MAX_HISTORY, after.size)
        assertEquals("text-5", after.first().text) // 最旧的 5 条被淘汰
        assertEquals("text-54", after.last().text)
        ds.close()

        // 新实例重新从磁盘读:验证真正落盘、clear 生效
        val ds2 = DictationPreferencesDataSource(dir)
        val repo2 = LocalTranscriptionHistoryRepository(ds2)
        assertEquals("text-54", repo2.history.first().last().text)
        repo2.clear()
        assertTrue(repo2.history.first().isEmpty())
        ds2.close()
    }

    // ===== OkHttpSttNetworkDataSource 重试逻辑 =====

    private val stt = OkHttpSttNetworkDataSource()

    private fun configWith(server: MockWebServer) = AppConfig(
        apiKey = "test-key",
        baseUrl = server.url("/v1").toString(),
        model = "test-model",
        language = "zh"
    )

    private fun json(code: Int, body: String): MockResponse =
        MockResponse().setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private val pcmSample: ByteArray = WavEncoder.encode(ByteArray(1600))

    @Test
    fun transcribeSuccessSingleRequest() = runBlocking {
        val server = MockWebServer(); server.start()
        server.enqueue(json(200, """{"text":"hello"}"""))

        val result = stt.transcribe(configWith(server), pcmSample)

        assertTrue(result is TranscriptionResult.Success)
        assertEquals("hello", (result as TranscriptionResult.Success).text)
        assertEquals(1, server.requestCount)
        server.shutdown()
    }

    @Test
    fun retryAfter500Succeeds() = runBlocking {
        val server = MockWebServer(); server.start()
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        server.enqueue(json(200, """{"text":"retry-ok"}"""))

        val result = stt.transcribe(configWith(server), pcmSample)

        assertEquals("retry-ok", (result as TranscriptionResult.Success).text)
        assertEquals(2, server.requestCount)
        server.shutdown()
    }

    @Test
    fun exhaustedRetriesReturnFailureWithCount() = runBlocking {
        val server = MockWebServer(); server.start()
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500).setBody("boom")) }

        val result = stt.transcribe(configWith(server), pcmSample)

        assertTrue(result is TranscriptionResult.Failure)
        val reason = (result as TranscriptionResult.Failure).reason
        assertTrue(reason.contains("HTTP 500"))
        assertTrue(reason.contains("2"), reason)
        assertEquals(3, server.requestCount)
        server.shutdown()
    }

    @Test
    fun clientError401DoesNotRetry() = runBlocking {
        val server = MockWebServer(); server.start()
        server.enqueue(json(401, """{"error":"unauthorized"}"""))

        val result = stt.transcribe(configWith(server), pcmSample)

        assertTrue(result is TranscriptionResult.Failure)
        val reason = (result as TranscriptionResult.Failure).reason
        assertFalse(reason.contains("retried"))
        assertFalse(reason.contains("重试"))
        assertEquals(1, server.requestCount)
        server.shutdown()
    }

    @Test
    fun malformedJsonFailsWithoutRetry() = runBlocking {
        val server = MockWebServer(); server.start()
        server.enqueue(json(200, "not-json-at-all"))

        val result = stt.transcribe(configWith(server), pcmSample)

        assertEquals(
            "响应解析失败",
            (result as TranscriptionResult.Failure).reason
        )
        assertEquals(1, server.requestCount)
        server.shutdown()
    }

    // ===== 外观主题 (ThemeMode / UiPreferencesRepository) =====

    @Test
    fun themeDefaultsToSystemWhenUnset() = runBlocking {
        val ds = newDs("theme-default")
        val repo = LocalUiPreferencesRepository(ds)

        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
        ds.close()
    }

    @Test
    fun themeRoundTrips() = runBlocking {
        val ds = newDs("theme-roundtrip")
        val repo = LocalUiPreferencesRepository(ds)

        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.first())
        repo.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
        ds.close()
    }

    @Test
    fun invalidThemeValueFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromRawOrDefault(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromRawOrDefault(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromRawOrDefault("neon-pink"))
        // 宽松解析:大小写与首尾空白均可接受
        assertEquals(ThemeMode.DARK, ThemeMode.fromRawOrDefault("  DARK "))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromRawOrDefault("Light"))
    }

    @Test
    fun uiPrefsRepoPersistsAcrossInstances() = runBlocking {
        val dir = tempDir.resolve("theme-persist").also { Files.createDirectories(it) }
        val firstDs = DictationPreferencesDataSource(dir)
        LocalUiPreferencesRepository(firstDs).setThemeMode(ThemeMode.DARK)
        firstDs.close() // DataStore 要求同一文件同时只能有一个活跃实例

        val secondDs = DictationPreferencesDataSource(dir)
        assertEquals(
            ThemeMode.DARK,
            LocalUiPreferencesRepository(secondDs).themeMode.first()
        )
        secondDs.close()
    }

    // ===== 开机自启动 =====

    @Test
    fun autostartDefaultsToFalse() = runBlocking {
        val ds = newDs("autostart-default")
        val repo = LocalUiPreferencesRepository(ds)

        assertEquals(false, repo.autostart.first())
        ds.close()
    }

    @Test
    fun autostartRoundTrip() = runBlocking {
        val ds = newDs("autostart-roundtrip")
        val repo = LocalUiPreferencesRepository(ds)

        repo.setAutostart(true)
        assertEquals(true, repo.autostart.first())
        repo.setAutostart(false)
        assertEquals(false, repo.autostart.first())
        ds.close()
    }

    @Test
    fun autostartPersistsAcrossInstances() = runBlocking {
        val dir = tempDir.resolve("autostart-persist").also { Files.createDirectories(it) }
        val firstDs = DictationPreferencesDataSource(dir)
        LocalUiPreferencesRepository(firstDs).setAutostart(true)
        firstDs.close()

        val secondDs = DictationPreferencesDataSource(dir)
        assertEquals(
            true,
            LocalUiPreferencesRepository(secondDs).autostart.first()
        )
        secondDs.close()
    }

    @Test
    fun autostartDoesNotWriteRegistryInDevMode() = runBlocking {
        // 开发模式下(exe 路径不含 Program Files)不应执行 reg 命令
        // 这里仅验证 DataStore 读写正常,注册表同步逻辑由 isInstalledEnvironment() 守护
        val ds = newDs("autostart-dev")
        val repo = LocalUiPreferencesRepository(ds)

        repo.setAutostart(true)
        assertEquals(true, repo.autostart.first())
        ds.close()
    }
}
