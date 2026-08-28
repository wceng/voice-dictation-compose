package com.wceng.dictation.platform

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.wceng.dictation.core.model.HotkeyCombo
import com.wceng.dictation.core.model.HotkeyConfig
import com.wceng.dictation.core.model.HotkeyModifier
import com.wceng.dictation.core.model.TriggerMode
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
 * 触发方式 [TriggerMode]:
 * - 点按切换:组合首次按下触发一次(门闩挡住自动重复),松开复位后可再次触发;
 * - 长按说话:按下开始([onPressed]),组合断开(任一成员抬起)时结算——
 *   按住时长 ≥ [ACCIDENTAL_RELEASE_MS] 视为有效说话调 [onReleased],
 *   否则视为误触调 [onCancelled](不发转写请求);
 *   长按期间按取消组合会终结本次会话,随后的松开不再产生任何回调。
 *
 * 钩子事件在 JNativeHook 自有线程回调;所有跨线程可变状态均为 @Volatile,
 * 更新与判定之间可能的理论交错以"模式判定兜底"自愈(见 nativeKeyReleased)。
 */
class HotkeyService(
    initialConfig: HotkeyConfig,
    initialMode: TriggerMode = TriggerMode.CLICK_TOGGLE,
    private val onPressed: () -> Unit,
    private val onReleased: () -> Unit,
    private val onCancelled: () -> Unit,
    /** 计时源可注入:生产用 nanoTime,测试用可控时钟(测误触阈值边界) */
    private val timeSource: () -> Long = System::nanoTime
) : NativeKeyListener {

    @Volatile private var bindings: HotkeyBindings = toBindings(initialConfig)
    @Volatile private var mode: TriggerMode = initialMode

    private val pressedKeys = mutableSetOf<Int>()
    /** 点按模式:本次按压已触发的门闩 */
    @Volatile private var toggleActive = false
    /** 长按模式:有效按压进行中 */
    @Volatile private var holdActive = false
    /** 本次长按已被取消组合终止:松开主键时不再结算(防双重回调) */
    @Volatile private var holdConsumed = false
    @Volatile private var pressNanos = 0L
    /** 取消组合:按住期间只触发一次的门闩(两种模式通用) */
    @Volatile private var cancelActive = false

    /**
     * 运行期切换热键绑定与触发模式(设置页保存后由 Main 的收集协程调用)。
     * 同时重置瞬时状态:模式/组合切换瞬间的残留按压不得形成幽灵触发或卡死。
     */
    fun updateBindings(config: HotkeyConfig, mode: TriggerMode) {
        bindings = toBindings(config)
        this.mode = mode
        pressedKeys.clear()
        toggleActive = false
        holdActive = false
        holdConsumed = false
        cancelActive = false
    }

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        pressedKeys.add(e.keyCode)

        val b = bindings
        if (!toggleActive && !holdActive && pressedKeys.containsAll(b.toggleKeys)) {
            // "组合首次按下"两模式共用同一判定;自动重复事件被门闩吸收
            if (mode == TriggerMode.HOLD_TO_TALK) {
                holdActive = true
                holdConsumed = false
                pressNanos = timeSource()
            } else {
                toggleActive = true
            }
            onPressed()
        }
        if (!cancelActive && pressedKeys.containsAll(b.cancelKeys)) {
            cancelActive = true
            // 长按会话中按取消组合:标记本次按压已终结,松开主键时不再结算
            if (holdActive) holdConsumed = true
            onCancelled()
        }
    }

    override fun nativeKeyReleased(e: NativeKeyEvent) {
        pressedKeys.remove(e.keyCode)
        val b = bindings
        if (!pressedKeys.containsAll(b.toggleKeys)) {
            // 长按结算:mode 判定兜底——updateBindings 清 holdActive 与事件线程的
            // 写入交错时,残留值在点按模式下永不触发回调
            if (holdActive && mode == TriggerMode.HOLD_TO_TALK) {
                val consumed = holdConsumed
                holdActive = false
                holdConsumed = false
                if (!consumed) {
                    val heldMs = (timeSource() - pressNanos) / 1_000_000
                    if (heldMs < ACCIDENTAL_RELEASE_MS) onCancelled() else onReleased()
                }
            }
            toggleActive = false
        }
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

        /** 长按模式误触阈值:按住不足此时长即松开 → 取消录音而非转写 */
        internal const val ACCIDENTAL_RELEASE_MS = 300L

        /** 屏蔽 JNativeHook 冗余调试日志 */
        private fun quietLogging() {
            val logger = Logger.getLogger(GlobalScreen::class.java.packageName)
            logger.level = Level.WARNING
            logger.useParentHandlers = false
        }
    }
}