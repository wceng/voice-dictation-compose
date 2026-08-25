package com.wceng.dictation.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Hotkey 模型:规范串编解码、展示文本、结构校验、成对冲突校验。
 */
class HotkeyTest {

    @Test
    fun `canonical roundtrip for representative combos`() {
        val samples = listOf(
            "CTRL+SHIFT+SPACE",
            "CTRL+SHIFT+BACKSPACE",
            "ALT+F5",
            "META+A",
            "CTRL+ALT+ENTER"
        )
        samples.forEach { raw ->
            val combo = HotkeyCombo.parseOrNull(raw)
            assertNotNull(combo, "$raw 应可解析")
            assertEquals(raw, combo!!.canonical(), "$raw 编解码应往返一致")
        }
    }

    @Test
    fun `parseOrNull rejects malformed input`() {
        listOf(
            "",              // 空
            "A",             // 无修饰键
            "SPACE",         // 纯主键
            "CTRL+A+B",      // A 不是修饰键
            "CTRL",          // 只有修饰键
            "CTRL+CTRL+A",   // 重复修饰键
            "F1+F2",         // F1 不是修饰键
            "CTRL+F13",      // 主键不在白名单
            "HYPER+A"        // 未知修饰键
        ).forEach { raw ->
            assertNull(HotkeyCombo.parseOrNull(raw), "\"$raw\" 应解析失败")
        }
    }

    @Test
    fun `displayText formats modifiers in fixed order`() {
        val combo = HotkeyCombo(
            setOf(HotkeyModifier.META, HotkeyModifier.SHIFT, HotkeyModifier.CTRL),
            HotkeyCombo.SUPPORTED_KEY_NAMES.getValue("K")
        )
        assertEquals("Ctrl+Shift+Meta+K", combo.displayText())
    }

    @Test
    fun `validate rejects missing real modifier`() {
        val noModifier = HotkeyCombo(emptySet(), HotkeyCombo.SUPPORTED_KEY_NAMES.getValue("A"))
        assertEquals("组合键必须包含 Ctrl、Alt 或 Win 之一", noModifier.validate())

        val shiftOnly = HotkeyCombo(setOf(HotkeyModifier.SHIFT), HotkeyCombo.SUPPORTED_KEY_NAMES.getValue("S"))
        assertEquals("组合键必须包含 Ctrl、Alt 或 Win 之一", shiftOnly.validate())
    }

    @Test
    fun `validate rejects unsupported main key`() {
        val combo = HotkeyCombo(setOf(HotkeyModifier.CTRL), keyCode = 999)
        assertEquals("不支持的按键", combo.validate())
    }

    @Test
    fun `valid combo passes validation`() {
        val combo = HotkeyCombo.parseOrNull("CTRL+ALT+T")!!
        assertNull(combo.validate())
    }

    @Test
    fun `validatePair rejects identical combos`() {
        val same = HotkeyCombo.parseOrNull("CTRL+SHIFT+SPACE")!!
        val config = HotkeyConfig(toggle = same, cancel = same)
        assertNotNull(config.validatePair())
    }

    @Test
    fun `defaults match historical behavior`() {
        assertEquals("CTRL+SHIFT+SPACE", HotkeyConfig.DEFAULTS.toggle.canonical())
        assertEquals("CTRL+SHIFT+BACKSPACE", HotkeyConfig.DEFAULTS.cancel.canonical())
    }

    @Test
    fun `supported key whitelist covers expected vk codes`() {
        assertEquals(65, HotkeyCombo.SUPPORTED_KEY_NAMES["A"])       // VK_A
        assertEquals(48, HotkeyCombo.SUPPORTED_KEY_NAMES["0"])       // VK_0
        assertEquals(112, HotkeyCombo.SUPPORTED_KEY_NAMES["F1"])     // VK_F1
        assertEquals(123, HotkeyCombo.SUPPORTED_KEY_NAMES["F12"])    // VK_F12
        assertEquals(32, HotkeyCombo.SUPPORTED_KEY_NAMES["SPACE"])   // VK_SPACE
        assertEquals(8, HotkeyCombo.SUPPORTED_KEY_NAMES["BACKSPACE"])// VK_BACK_SPACE
        assertEquals(10, HotkeyCombo.SUPPORTED_KEY_NAMES["ENTER"])   // VK_ENTER
        assertEquals(9, HotkeyCombo.SUPPORTED_KEY_NAMES["TAB"])      // VK_TAB
    }
}