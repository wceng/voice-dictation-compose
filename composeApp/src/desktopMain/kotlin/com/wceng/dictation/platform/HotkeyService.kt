package com.wceng.dictation.platform

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 全局热键服务(JNativeHook):
 * - Ctrl+Shift+Space     开始录音 / 停止并转写
 * - Ctrl+Shift+Backspace 取消录音
 * 组合按住期间只触发一次,松开后可再次触发。
 */
class HotkeyService(
    private val onToggle: () -> Unit,
    private val onCancel: () -> Unit
) : NativeKeyListener {

    private val toggleKeys = setOf(
        NativeKeyEvent.VC_CONTROL,
        NativeKeyEvent.VC_SHIFT,
        NativeKeyEvent.VC_SPACE
    )

    private val cancelKeys = setOf(
        NativeKeyEvent.VC_CONTROL,
        NativeKeyEvent.VC_SHIFT,
        NativeKeyEvent.VC_BACKSPACE
    )

    private val pressedKeys = mutableSetOf<Int>()
    @Volatile private var toggleActive = false
    @Volatile private var cancelActive = false

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        pressedKeys.add(e.keyCode)

        if (!toggleActive && pressedKeys.containsAll(toggleKeys)) {
            toggleActive = true
            onToggle()
        }
        if (!cancelActive && pressedKeys.containsAll(cancelKeys)) {
            cancelActive = true
            onCancel()
        }
    }

    override fun nativeKeyReleased(e: NativeKeyEvent) {
        pressedKeys.remove(e.keyCode)
        if (toggleActive && !pressedKeys.containsAll(toggleKeys)) toggleActive = false
        if (cancelActive && !pressedKeys.containsAll(cancelKeys)) cancelActive = false
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

        /** 屏蔽 JNativeHook 冗余调试日志 */
        private fun quietLogging() {
            val logger = Logger.getLogger(GlobalScreen::class.java.packageName)
            logger.level = Level.WARNING
            logger.useParentHandlers = false
        }
    }
}
