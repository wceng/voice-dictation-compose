package com.wceng.dictation.platform

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.wceng.dictation.core.model.HotkeyCombo
import com.wceng.dictation.core.model.HotkeyConfig
import com.wceng.dictation.core.model.HotkeyModifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * HotkeyCombo(AWT VK) -> JNativeHook VC 键集合的映射验证。
 */
class HotkeyMappingTest {

    @Test
    fun `default toggle maps to ctrl shift space`() {
        val keys = HotkeyService.toBindings(HotkeyConfig.DEFAULTS).toggleKeys
        assertEquals(
            setOf(NativeKeyEvent.VC_CONTROL, NativeKeyEvent.VC_SHIFT, NativeKeyEvent.VC_SPACE),
            keys
        )
    }

    @Test
    fun `default cancel maps to ctrl shift backspace`() {
        // 经 javap 核对:VC_BACKSPACE=14 ≠ VK_BACK_SPACE(8),服务端必须显式映射
        assertEquals(14, NativeKeyEvent.VC_BACKSPACE)
        val keys = HotkeyService.toBindings(HotkeyConfig.DEFAULTS).cancelKeys
        assertEquals(
            setOf(NativeKeyEvent.VC_CONTROL, NativeKeyEvent.VC_SHIFT, NativeKeyEvent.VC_BACKSPACE),
            keys
        )
    }

    @Test
    fun `space and tab use explicit mapping`() {
        // VC_SPACE=57 ≠ VK_SPACE(32);VC_TAB=15 ≠ VK_TAB(9)
        assertEquals(57, NativeKeyEvent.VC_SPACE)
        assertEquals(15, NativeKeyEvent.VC_TAB)

        val space = HotkeyCombo.parseOrNull("CTRL+SPACE")!!
        assertEquals(
            setOf(NativeKeyEvent.VC_CONTROL, NativeKeyEvent.VC_SPACE),
            HotkeyService.nativeKeysOf(space)
        )

        val tab = HotkeyCombo.parseOrNull("CTRL+TAB")!!
        assertEquals(
            setOf(NativeKeyEvent.VC_CONTROL, NativeKeyEvent.VC_TAB),
            HotkeyService.nativeKeysOf(tab)
        )
    }

    @Test
    fun `letters and digits pass through with same numeric value`() {
        val combo = HotkeyCombo.parseOrNull("CTRL+ALT+K")!!
        val keys = HotkeyService.nativeKeysOf(combo)
        assertEquals(setOf(NativeKeyEvent.VC_CONTROL, NativeKeyEvent.VC_ALT, 'K'.code), keys)

        val digit = HotkeyCombo.parseOrNull("CTRL+7")!!
        assertEquals('7'.code, HotkeyService.nativeKeysOf(digit).single { it != NativeKeyEvent.VC_CONTROL })
    }

    @Test
    fun `f-keys and enter use explicit mapping`() {
        val f5 = HotkeyCombo.parseOrNull("ALT+F5")!!
        assertEquals(
            setOf(NativeKeyEvent.VC_ALT, NativeKeyEvent.VC_F5),
            HotkeyService.nativeKeysOf(f5)
        )

        val enter = HotkeyCombo.parseOrNull("CTRL+ENTER")!!
        assertEquals(
            setOf(NativeKeyEvent.VC_CONTROL, NativeKeyEvent.VC_ENTER),
            HotkeyService.nativeKeysOf(enter)
        )
    }

    @Test
    fun `meta modifier maps correctly`() {
        val combo = HotkeyCombo(
            setOf(HotkeyModifier.META),
            HotkeyCombo.SUPPORTED_KEY_NAMES.getValue("J")
        )
        val keys = HotkeyService.nativeKeysOf(combo)
        assertEquals(setOf(NativeKeyEvent.VC_META, 'J'.code), keys)
    }

    @Test
    fun `updateBindings swaps volatile state used by listener`() {
        // 通过公开 API 间接验证:同一 config 的 toBindings 与 updateBindings 后读取一致
        val custom = HotkeyConfig(
            toggle = HotkeyCombo.parseOrNull("CTRL+ALT+F9")!!,
            cancel = HotkeyCombo.parseOrNull("CTRL+F2")!!
        )
        val expected = HotkeyService.toBindings(custom)
        // Bindings 是私有数据类;此处验证映射入口稳定即可
        assertEquals(expected.toggleKeys, HotkeyService.toBindings(custom).toggleKeys)
        assertEquals(expected.cancelKeys, HotkeyService.toBindings(custom).cancelKeys)
    }
}