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

/** VC 修饰键 -> 模型枚举(供从钩子原始键集合反解组合) */
private val vcModifierMap = mapOf(
    NativeKeyEvent.VC_CONTROL to HotkeyModifier.CTRL,
    NativeKeyEvent.VC_SHIFT to HotkeyModifier.SHIFT,
    NativeKeyEvent.VC_ALT to HotkeyModifier.ALT,
    NativeKeyEvent.VC_META to HotkeyModifier.META
)

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
         *
         * ⚠️ 两套编码几乎完全不同(VC 是 Set-1 扫描码/HID 体系):
         * 字母 A=30..Z=54、数字 1=2..0=11、SPACE=57、BACKSPACE=14、TAB=15、
         * ENTER=28、F1=62..F12=73——白名单内没有任何键可透传,
         * 全部必须经 [MAIN_KEY_VK_TO_VC] 显式映射(常量引用,勿手抄数值)。
         */
        fun nativeKeysOf(combo: HotkeyCombo): Set<Int> {
            val modifierMap = mapOf(
                HotkeyModifier.CTRL to NativeKeyEvent.VC_CONTROL,
                HotkeyModifier.SHIFT to NativeKeyEvent.VC_SHIFT,
                HotkeyModifier.ALT to NativeKeyEvent.VC_ALT,
                HotkeyModifier.META to NativeKeyEvent.VC_META
            )
            val mainVc = MAIN_KEY_VK_TO_VC[combo.keyCode]
                ?: error("未映射的主键 VK=${combo.keyCode},请补全 MAIN_KEY_VK_TO_VC")
            return combo.modifiers.map { modifierMap.getValue(it) }.toSet() + mainVc
        }

        /** VK -> VC 主键全量显式映射;缺失会抛错而非静默失配 */
        private val MAIN_KEY_VK_TO_VC: Map<Int, Int> = mapOf(
            // 字母(Set-1 扫描码: A=30 .. Z=54)
            KeyEvent.VK_A to NativeKeyEvent.VC_A,
            KeyEvent.VK_B to NativeKeyEvent.VC_B,
            KeyEvent.VK_C to NativeKeyEvent.VC_C,
            KeyEvent.VK_D to NativeKeyEvent.VC_D,
            KeyEvent.VK_E to NativeKeyEvent.VC_E,
            KeyEvent.VK_F to NativeKeyEvent.VC_F,
            KeyEvent.VK_G to NativeKeyEvent.VC_G,
            KeyEvent.VK_H to NativeKeyEvent.VC_H,
            KeyEvent.VK_I to NativeKeyEvent.VC_I,
            KeyEvent.VK_J to NativeKeyEvent.VC_J,
            KeyEvent.VK_K to NativeKeyEvent.VC_K,
            KeyEvent.VK_L to NativeKeyEvent.VC_L,
            KeyEvent.VK_M to NativeKeyEvent.VC_M,
            KeyEvent.VK_N to NativeKeyEvent.VC_N,
            KeyEvent.VK_O to NativeKeyEvent.VC_O,
            KeyEvent.VK_P to NativeKeyEvent.VC_P,
            KeyEvent.VK_Q to NativeKeyEvent.VC_Q,
            KeyEvent.VK_R to NativeKeyEvent.VC_R,
            KeyEvent.VK_S to NativeKeyEvent.VC_S,
            KeyEvent.VK_T to NativeKeyEvent.VC_T,
            KeyEvent.VK_U to NativeKeyEvent.VC_U,
            KeyEvent.VK_V to NativeKeyEvent.VC_V,
            KeyEvent.VK_W to NativeKeyEvent.VC_W,
            KeyEvent.VK_X to NativeKeyEvent.VC_X,
            KeyEvent.VK_Y to NativeKeyEvent.VC_Y,
            KeyEvent.VK_Z to NativeKeyEvent.VC_Z,
            // 数字行(1=2 .. 0=11)
            KeyEvent.VK_0 to NativeKeyEvent.VC_0,
            KeyEvent.VK_1 to NativeKeyEvent.VC_1,
            KeyEvent.VK_2 to NativeKeyEvent.VC_2,
            KeyEvent.VK_3 to NativeKeyEvent.VC_3,
            KeyEvent.VK_4 to NativeKeyEvent.VC_4,
            KeyEvent.VK_5 to NativeKeyEvent.VC_5,
            KeyEvent.VK_6 to NativeKeyEvent.VC_6,
            KeyEvent.VK_7 to NativeKeyEvent.VC_7,
            KeyEvent.VK_8 to NativeKeyEvent.VC_8,
            KeyEvent.VK_9 to NativeKeyEvent.VC_9,
            // 其余
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

        /** VC -> VK 反查表(由正向映射自动生成,永不失同步) */
        private val VC_TO_VK: Map<Int, Int> = MAIN_KEY_VK_TO_VC.entries.associate { (k, v) -> v to k }

        /**
         * 从钩子按住的原始 VC 键集合反解组合;主键不在白名单时返回 null。
         */
        fun comboFromHeldKeys(held: Set<Int>): HotkeyCombo? {
            val modifiers = held.mapNotNull { vcModifierMap[it] }.toSet()
            val mainVc = held.firstOrNull { it !in vcModifierMap } ?: return null
            val vk = VC_TO_VK[mainVc] ?: return null
            return HotkeyCombo(modifiers, vk)
        }

        /** 主动取消进行中的捕获(等价于按下 Esc)由外部按钮触发 */
        @Volatile private var activeFinish: ((HotkeyCombo?) -> Unit)? = null

        /**
         * 单发捕获:武装后下一次「修饰键 + 主键」的物理按键经钩子原样回传。
         * 相比 Compose 键事件,钩子层拿到的是与触发判定同域的 VC 码,
         * 天然免疫 IME、焦点竞争与键盘布局差异。ESC 取消,超时返回 null。
         * 回调经 EDT 派发,可安全触碰 Compose 状态。
         */
        fun armOneShot(onDone: (HotkeyCombo?) -> Unit) {
            val held = mutableSetOf<Int>()
            var done = false
            lateinit var listener: NativeKeyListener

            fun finish(result: HotkeyCombo?) {
                if (done) return
                done = true
                activeFinish = null
                runCatching { GlobalScreen.removeNativeKeyListener(listener) }
                javax.swing.SwingUtilities.invokeLater { onDone(result) }
            }

            listener = object : NativeKeyListener {
                override fun nativeKeyPressed(e: NativeKeyEvent) {
                    if (done) return
                    if (e.keyCode == NativeKeyEvent.VC_ESCAPE) return finish(null)
                    held.add(e.keyCode)
                    if (held.any { it !in vcModifierMap }) finish(comboFromHeldKeys(held.toSet()))
                }

                override fun nativeKeyReleased(e: NativeKeyEvent) {}
            }

            activeFinish = ::finish
            GlobalScreen.addNativeKeyListener(listener)
            java.util.Timer("hotkey-capture-timeout", true).schedule(object : java.util.TimerTask() {
                override fun run() = finish(null)
            }, CAPTURE_TIMEOUT_MS)
        }

        /** 主动取消进行中的捕获(等价于按下 Esc),无会话时静默 */
        fun cancelOneShot() {
            activeFinish?.invoke(null)
        }

        private const val CAPTURE_TIMEOUT_MS = 8_000L

        /** 屏蔽 JNativeHook 冗余调试日志 */
        private fun quietLogging() {
            val logger = Logger.getLogger(GlobalScreen::class.java.packageName)
            logger.level = Level.WARNING
            logger.useParentHandlers = false
        }
    }
}