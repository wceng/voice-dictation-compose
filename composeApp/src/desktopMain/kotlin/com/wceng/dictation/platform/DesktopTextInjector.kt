package com.wceng.dictation.platform

import com.wceng.dictation.core.TextInjector
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent

/**
 * 将识别文本注入当前焦点窗口: 写剪贴板 -> 模拟 Ctrl+V。
 * Windows 走 java.awt.Robot;Linux/macOS 走 xdotool(X11)。
 */
class DesktopTextInjector : TextInjector {

    private val isWindows = System.getProperty("os.name").startsWith("Windows")

    override fun inject(text: String) {
        if (text.isBlank()) return
        println("[Inject] 注入文字: $text")
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            Thread.sleep(80)
            if (isWindows) pasteWithRobot() else pasteWithXdotool()
        } catch (e: Exception) {
            System.err.println("[Inject] 注入失败: ${e.message}")
        }
    }

    private fun pasteWithRobot() {
        val robot = Robot()
        try {
            robot.keyPress(KeyEvent.VK_CONTROL)
            robot.keyPress(KeyEvent.VK_V)
            Thread.sleep(50)
        } finally {
            robot.keyRelease(KeyEvent.VK_V)
            robot.keyRelease(KeyEvent.VK_CONTROL)
        }
    }

    private fun pasteWithXdotool() {
        ProcessBuilder("xdotool", "key", "--delay", "50", "ctrl+v")
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }
}
