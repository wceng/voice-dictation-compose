package com.wceng.dictation.platform

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.wceng.dictation.core.model.HotkeyConfig
import com.wceng.dictation.core.model.TriggerMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HotkeyService 触发方式行为矩阵:直驱 nativeKeyPressed/Released 模拟钩子事件,
 * 不经 GlobalScreen 注册(单测环境无法加载原生库)。
 *
 * 键序均使用真实 VC 常量(默认热键 CTRL+SHIFT+SPACE / CTRL+SHIFT+BACKSPACE);
 * 时间源注入 FakeClock,可精确验证 300ms 误触阈值边界。
 */
class HotkeyServiceBehaviorTest {

    /** 可控时间源:now 单位为纳秒,与 System.nanoTime 同语义 */
    private class FakeClock {
        var now = 1_000_000_000L
        val time: () -> Long = { now }
        fun advanceMs(ms: Long) { now += ms * 1_000_000 }
    }

    private class Harness(mode: TriggerMode) {
        val clock = FakeClock()
        /** 回调事件序列:"press" | "release" | "cancel",按发生顺序追加 */
        val log = mutableListOf<String>()
        val service = HotkeyService(
            initialConfig = HotkeyConfig.DEFAULTS,
            initialMode = mode,
            onPressed = { log.add("press") },
            onReleased = { log.add("release") },
            onCancelled = { log.add("cancel") },
            timeSource = clock.time
        )
    }

    private fun pressEvent(vc: Int) = NativeKeyEvent(
        NativeKeyEvent.NATIVE_KEY_PRESSED, 0, 0, vc, NativeKeyEvent.CHAR_UNDEFINED
    )

    private fun releaseEvent(vc: Int) = NativeKeyEvent(
        NativeKeyEvent.NATIVE_KEY_RELEASED, 0, 0, vc, NativeKeyEvent.CHAR_UNDEFINED
    )

    // ===== 点按切换(回归:与历史行为一致) =====

    @Test
    fun `click mode fires once per cycle and retriggers after release`() {
        val h = Harness(TriggerMode.CLICK_TOGGLE)

        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SHIFT))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        assertEquals(listOf("press"), h.log)

        // 组合完全按住期间 Windows 自动重复的 KeyPressed 不应追加触发
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        assertEquals(listOf("press"), h.log)

        // 松开任一成员即结束本次周期,且松开不产生 stop/cancel 回调
        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SPACE))
        assertEquals(listOf("press"), h.log)

        // 重新按下主键 → 新的触发周期
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        assertEquals(listOf("press", "press"), h.log)
    }

    @Test
    fun `click mode ignores partial combos`() {
        val h = Harness(TriggerMode.CLICK_TOGGLE)

        // 只按修饰键不成组合
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_CONTROL))

        assertTrue(h.log.isEmpty())
    }

    // ===== 长按说话 =====

    @Test
    fun `hold mode starts on press and releases after threshold`() {
        val h = Harness(TriggerMode.HOLD_TO_TALK)

        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SHIFT))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        assertEquals(listOf("press"), h.log)

        // 自动重复不追加、不复位计时起点
        h.clock.advanceMs(400)
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        h.clock.advanceMs(100)   // 距按下共 500ms ≥ 阈值

        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SPACE))
        assertEquals(listOf("press", "release"), h.log)
    }

    @Test
    fun `hold mode cancels accidental taps below threshold`() {
        val h = Harness(TriggerMode.HOLD_TO_TALK)

        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SHIFT))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        h.clock.advanceMs(HotkeyService.ACCIDENTAL_RELEASE_MS - 50)

        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SHIFT))
        assertEquals(listOf("press", "cancel"), h.log)
    }

    @Test
    fun `hold mode threshold boundary is inclusive for valid speech`() {
        val h = Harness(TriggerMode.HOLD_TO_TALK)

        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SHIFT))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        h.clock.advanceMs(HotkeyService.ACCIDENTAL_RELEASE_MS)   // 恰好等于阈值 → 有效

        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SPACE))
        assertEquals(listOf("press", "release"), h.log)
    }

    @Test
    fun `hold mode broken combo then reformed starts new session`() {
        val h = Harness(TriggerMode.HOLD_TO_TALK)

        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SHIFT))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        h.clock.advanceMs(800)
        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SPACE))   // 结算第一轮

        // 组合重新构成 → 新会话
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        h.clock.advanceMs(100)
        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SHIFT))   // 新会话误触取消
        assertEquals(listOf("press", "release", "press", "cancel"), h.log)
    }

    // ===== 长按期间取消组合键 =====

    @Test
    fun `cancel combo during hold consumes the session`() {
        val h = Harness(TriggerMode.HOLD_TO_TALK)

        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SHIFT))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        h.clock.advanceMs(500)

        // 取消组合(CTRL+SHIFT+BACKSPACE):共有的修饰键已按住,只补 BACKSPACE
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_BACKSPACE))
        assertEquals(listOf("press", "cancel"), h.log)

        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_BACKSPACE))
        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SPACE))
        // 会话已被取消终结:后续松开不再结算
        assertEquals(listOf("press", "cancel"), h.log)

        // 且不会污染下一次按压
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        h.clock.advanceMs(600)
        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SPACE))
        assertEquals(listOf("press", "cancel", "press", "release"), h.log)
    }

    @Test
    fun `cold cancel combo still works and rearms after release`() {
        val h = Harness(TriggerMode.HOLD_TO_TALK)

        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SHIFT))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_BACKSPACE))
        assertEquals(listOf("cancel"), h.log)

        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_BACKSPACE))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_BACKSPACE))
        assertEquals(listOf("cancel", "cancel"), h.log)
    }

    // ===== 运行期模式/绑定切换 =====

    @Test
    fun `updateBindings mid-hold prevents ghost callbacks`() {
        val h = Harness(TriggerMode.HOLD_TO_TALK)
        h.service.updateBindings(HotkeyConfig.DEFAULTS, TriggerMode.HOLD_TO_TALK)

        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SHIFT))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        h.clock.advanceMs(1_000)

        // 切换为点按:残留的长按状态必须被重置(pressedKeys 一并清空,
        // 用户须先完全松开当前组合再重新按下——绑定变更后的常规再武装语义)
        h.service.updateBindings(HotkeyConfig.DEFAULTS, TriggerMode.CLICK_TOGGLE)
        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SPACE))
        assertTrue(h.log.none { it != "press" })

        // 物理现实:用户继续把剩余修饰键松开,不得产生任何回调
        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SHIFT))
        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_CONTROL))
        assertEquals(listOf("press"), h.log)

        // 全新按压表现为标准点按周期:单次触发、松开无结算回调
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_CONTROL))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SHIFT))
        h.service.nativeKeyPressed(pressEvent(NativeKeyEvent.VC_SPACE))
        assertEquals(listOf("press", "press"), h.log)
        h.service.nativeKeyReleased(releaseEvent(NativeKeyEvent.VC_SPACE))
        assertEquals(listOf("press", "press"), h.log)
    }
}
