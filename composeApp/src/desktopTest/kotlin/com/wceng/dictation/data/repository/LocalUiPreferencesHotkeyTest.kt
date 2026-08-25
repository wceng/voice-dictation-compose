package com.wceng.dictation.data.repository

import com.wceng.dictation.core.model.HotkeyCombo
import com.wceng.dictation.core.model.HotkeyConfig
import com.wceng.dictation.core.model.ThemeMode
import com.wceng.dictation.data.store.DictationPreferencesDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * UiPreferencesRepository 热键部分:默认值、保存往返、非法输入拒绝。
 * 每例独立 @TempDir(DataStore 单实例规则)。
 */
class LocalUiPreferencesHotkeyTest {

    @TempDir
    lateinit var tempDir: Path

    private fun newRepo(sub: String): LocalUiPreferencesRepository {
        val dir = tempDir.resolve(sub).also { Files.createDirectories(it) }
        return LocalUiPreferencesRepository(DictationPreferencesDataSource(dir))
    }

    @Test
    fun `defaults returned when nothing persisted`() = runBlocking {
        val repo = newRepo("defaults")
        assertEquals(HotkeyConfig.DEFAULTS, repo.hotkeys.first())
    }

    @Test
    fun `custom toggle persists and round-trips`() = runBlocking {
        val repo = newRepo("roundtrip")
        val custom = HotkeyCombo.parseOrNull("CTRL+ALT+F9")!!

        repo.setToggleHotkey(custom)

        val config = repo.hotkeys.first()
        assertEquals(custom, config.toggle)
        // 取消键不受影响
        assertEquals(HotkeyConfig.DEFAULTS.cancel, config.cancel)
    }

    @Test
    fun `custom cancel persists and round-trips`() = runBlocking {
        val repo = newRepo("cancel")
        val custom = HotkeyCombo.parseOrNull("ALT+Q")!!

        repo.setCancelHotkey(custom)

        assertEquals(custom, repo.hotkeys.first().cancel)
    }

    @Test
    fun `setter rejects combos without real modifier`() = runBlocking {
        val repo = newRepo("reject")
        val invalid = HotkeyCombo(emptySet(), 'A'.code)

        val ex = runCatching { repo.setToggleHotkey(invalid) }.exceptionOrNull()

        assertTrue(ex is IllegalArgumentException)
        assertEquals("组合键必须包含 Ctrl、Alt 或 Win 之一", ex?.message)
    }

    @Test
    fun `theme mode flow unaffected by hotkey changes`() = runBlocking {
        val repo = newRepo("isolation")
        repo.setToggleHotkey(HotkeyCombo.parseOrNull("CTRL+ALT+Z")!!)
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }
}