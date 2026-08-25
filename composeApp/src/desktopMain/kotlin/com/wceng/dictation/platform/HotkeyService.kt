package com.wceng.dictation.platform

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.wceng.dictation.core.model.HotkeyCombo
import com.wceng.dictation.core.model.HotkeyConfig
import com.wceng.dictation.core.model.HotkeyModifier
import java.awt.event.KeyEvent
import java.util.logging.Level
import java.util.logging.Logger

/** 一对组合键的 VC 键集合;不可变,整体替换保证可见性与一致性 */
internal data class HotkeyBindings(val toggleKeys: Set<Int>, val cancelKeys: Set<Int>)

/**
 * 全局热键服务(JNativeHook)。
 *
 * 绑定来自 [HotkeyConfig](设置页可自定义),运行期经 [updateBindings] 热更新——
 * 单个 @Volatile 不可变对象整体替换,监听线程下一次按键即读到新绑定,
 * 无需注销/重注册 GlobalScreen。
 *
 * 组合按住期间只触发一次,松开后可再次触发(与历史行为一致)。
 */
class HotkeyService(
    initialConfig: HotkeyConfig,
    private val onToggle: () -> Unit,
    private val onCancel: () -> Unit
) : NativeKeyListener {

    @Volatile private var bindings: HotkeyBindings = toBindings(initialConfig)

    private val pressedKeys = mutableSetOf<Int>()
    @Volatile private var toggleActive = false
    @Volatile private var cancelActive = false

    /** 运行期切换热键绑定(设置页保存后由 Main 的收集协程调用) */
    fun updateBindings(config: HotkeyConfig) {
        bindings = toBindings(config)
    }

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        pressedKeys.add(e.keyCode)

        val b = bindings
        if (!toggleActive && pressedKeys.containsAll(b.toggleKeys)) {
            toggleActive = true
            onToggle()
        }
        if (!cancelActive && pressedKeys.containsAll(b.cancelKeys)) {
            cancelActive = true
            onCancel()
        }
    }

    override fun nativeKeyReleased(e: NativeKeyEvent) {
        pressedKeys.remove(e.keyCode)
        val b = bindings
        if (toggleActive && !pressedKeys.containsAll(b.toggleKeys)) toggleActive = false
        if (cancelActive && !pressedKeys.containsAll(b.cancelKeys)) cancelActive = false
    }

    companion object {
        fun register(service: HotkeyService) {
            quietLogging()
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(service)
        }

        fun unregister() {
            runCatching { GlobalScreen.unregisterNativeHook() }
        }

        /** 组合键 -> VC 键集合(修饰键 + 主键) */
        internal fun toBindings(config: HotkeyConfig): HotkeyBindings =
            HotkeyBindings(nativeKeysOf(config.toggle), nativeKeysOf(config.cancel))

        /**
         * HotkeyCombo(AWT VK 码) -> JNativeHook VC 键集合。
         * 经 javap 核对:仅字母与数字两套编码数值相同(可透传);
         * Space/Tab/Backspace/F1–F12/Enter 均不同,必须走显式映射表。
         */
        fun nativeKeysOf(combo: HotkeyCombo): Set<Int> {
            val modifierMap = mapOf(
                HotkeyModifier.CTRL to NativeKeyEvent.VC_CONTROL,
                HotkeyModifier.SHIFT to NativeKeyEvent.VC_SHIFT,
                HotkeyModifier.ALT to NativeKeyEvent.VC_ALT,
                HotkeyModifier.META to NativeKeyEvent.VC_META
            )
            return combo.modifiers.map { modifierMap.getValue(it) }.toSet() +
                (MAIN_KEY_VK_TO_VC[combo.keyCode] ?: combo.keyCode)
        }

        /** VK 与 VC 数值不同的主键映射;白名单中仅字母/数字可直接透传 */
        private val MAIN_KEY_VK_TO_VC: Map<Int, Int> = mapOf(
            KeyEvent.VK_SPACE to NativeKeyEvent.VC_SPACE,
            KeyEvent.VK_BACK_SPACE to NativeKeyEvent.VC_BACKSPACE,
            KeyEvent.VK_TAB to NativeKeyEvent.VC_TAB,
            KeyEvent.VK_F1 to NativeKeyEvent.VC_F1,
            KeyEvent.VK_F2 to NativeKeyEvent.VC_F2,
            KeyEvent.VK_F3 to NativeKeyEvent.VC_F3,
            KeyEvent.VK_F4 to NativeKeyEvent.VC_F4,
            KeyEvent.VK_F5 to NativeKeyEvent.VC_F5,
            KeyEvent.VK_F6 to NativeKeyEvent.VC_F6,
            KeyEvent.VK_F7 to NativeKeyEvent.VC_F7,
            KeyEvent.VK_F8 to NativeKeyEvent.VC_F8,
            KeyEvent.VK_F9 to NativeKeyEvent.VC_F9,
            KeyEvent.VK_F10 to NativeKeyEvent.VC_F10,
            KeyEvent.VK_F11 to NativeKeyEvent.VC_F11,
            KeyEvent.VK_F12 to NativeKeyEvent.VC_F12,
            KeyEvent.VK_ENTER to NativeKeyEvent.VC_ENTER
        )

        /** 屏蔽 JNativeHook 冗余调试日志 */
        private fun quietLogging() {
            val logger = Logger.getLogger(GlobalScreen::class.java.packageName)
            logger.level = Level.WARNING
            logger.useParentHandlers = false
        }
    }
}