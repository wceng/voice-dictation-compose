package com.wceng.dictation.platform

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import com.sun.jna.platform.win32.User32
import com.wceng.dictation.core.model.DictationState
import java.awt.Color
import java.awt.MouseInfo
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.awt.SystemTray as AwtSystemTray
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JWindow
import javax.swing.MenuElement
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities

/**
 * 系统托盘:AWT TrayIcon 图标 + Swing JPopupMenu 菜单。
 * 不用 AWT 原生 PopupMenu——JDK 的 Windows native 菜单缺 CJK 字形,中文会变空心方块;
 * Swing 渲染中文正常。独立 JPopupMenu 点击外部不自动关闭,
 * 用 JNativeHook 全局鼠标钩子在点击菜单窗口外时手动收起。
 */
class TrayManager(
    private val onToggle: () -> Unit,
    private val onCancel: () -> Unit,
    private val onShowWindow: () -> Unit,
    private val onQuit: () -> Unit
) {

    private var trayIcon: TrayIcon? = null
    private var popup: JPopupMenu? = null
    private var invoker: JWindow? = null
    private var statusItem: JMenuItem? = null
    private var toggleItem: JMenuItem? = null
    private var cancelItem: JMenuItem? = null
    private var mouseHookInstalled = false
    @Volatile private var disposed = false

    fun show() {
        if (!AwtSystemTray.isSupported()) {
            println("[Tray] 系统不支持托盘,仅保留主窗口")
            return
        }
        val menu = JPopupMenu()

        statusItem = JMenuItem("○ 待机中").apply { isEnabled = false }
        menu.add(statusItem)
        menu.addSeparator()

        toggleItem = JMenuItem("开始录音 / 停止并转写").apply {
            addActionListener { onToggle() }
        }
        menu.add(toggleItem)

        cancelItem = JMenuItem("取消录音").apply {
            addActionListener { onCancel() }
            isEnabled = false
        }
        menu.add(cancelItem)

        menu.add(JMenuItem("显示主窗口").apply { addActionListener { onShowWindow() } })
        menu.addSeparator()
        menu.add(JMenuItem("退出").apply { addActionListener { onQuit() } })

        popup = menu

        // JPopupMenu 需要一个可见组件作为锚点(invoker),0x0 隐藏窗口即可
        invoker = JWindow().apply {
            setSize(0, 0)
            setLocation(0, 0)
            isVisible = true
        }

        val icon = TrayIcon(createIcon(DictationState.IDLE), "Voice Dictation")
        icon.isImageAutoSize = true
        icon.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3 || e.isPopupTrigger) showPopupAtCursor()
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3 || e.isPopupTrigger) showPopupAtCursor()
            }
        })
        AwtSystemTray.getSystemTray().add(icon)
        trayIcon = icon
        // 鼠标钩子失败不致命:绝不能在这里抛异常杀死 main——
        // 托盘图标已添加,而窗口还没创建,AWT 线程会让 JVM 变成
        // "托盘在、界面死"的僵尸实例并一直占着单实例锁
        runCatching { installMouseHook() }
            .onFailure { System.err.println("[Tray] 鼠标钩子安装失败(仅影响点击托盘外关闭菜单): ${it.message}") }
        println("[Tray] 托盘图标已显示(右键点击打开菜单)")
    }

    /** 由 Main 的状态收集协程驱动;切到 EDT 更新 */
    fun setState(state: DictationState) {
        SwingUtilities.invokeLater {
            trayIcon?.setImage(createIcon(state))
            statusItem?.text = when (state) {
                DictationState.IDLE -> "○ 待机中"
                DictationState.RECORDING -> "● 录音中..."
                DictationState.TRANSCRIBING -> "⏳ 转写中..."
            }
            toggleItem?.text = when (state) {
                DictationState.RECORDING -> "停止录音并转写"
                else -> "开始录音 / 停止并转写"
            }
            cancelItem?.isEnabled = state == DictationState.RECORDING
        }
    }

    /** 气泡通知(系统禁用通知时静默失败) */
    fun notify(title: String, message: String, isError: Boolean) {
        SwingUtilities.invokeLater {
            try {
                trayIcon?.displayMessage(
                    title,
                    message,
                    if (isError) TrayIcon.MessageType.ERROR else TrayIcon.MessageType.INFO
                )
            } catch (e: Exception) {
                System.err.println("[Tray] 托盘通知失败: ${e.message}")
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        // 全局鼠标钩子随 Main 里的 GlobalScreen.unregisterNativeHook() 一并注销
        SwingUtilities.invokeLater {
            runCatching { trayIcon?.let { AwtSystemTray.getSystemTray().remove(it) } }
        }
    }

    private fun showPopupAtCursor() {
        val menu = popup ?: return
        val anchor = invoker ?: return
        SwingUtilities.invokeLater {
            val p = MouseInfo.getPointerInfo().location
            // invoker 位于屏幕 (0,0),相对坐标即屏幕坐标;show() 自动把菜单收回屏幕内
            menu.show(anchor, p.x, p.y)
            MenuSelectionManager.defaultManager().selectedPath = arrayOf<MenuElement>(menu)
        }
    }

    private fun installMouseHook() {
        if (mouseHookInstalled) return
        mouseHookInstalled = true
        GlobalScreen.addNativeMouseListener(object : NativeMouseListener {
            override fun nativeMousePressed(e: NativeMouseEvent) {
                val menu = popup ?: return
                if (!menu.isVisible) return
                val win = SwingUtilities.windowForComponent(menu) ?: return
                // JNativeHook 坐标是物理像素,窗口 bounds 是逻辑像素,按 DPI 换算
                val scale = dpiScale()
                val x = (e.x / scale).toInt()
                val y = (e.y / scale).toInt()
                if (!win.bounds.contains(x, y)) {
                    SwingUtilities.invokeLater { menu.isVisible = false }
                }
            }

            override fun nativeMouseReleased(e: NativeMouseEvent) {}
            override fun nativeMouseClicked(e: NativeMouseEvent) {}
        })
    }

    /** Windows 物理像素/逻辑像素换算系数 */
    private fun dpiScale(): Double = try {
        val physW = User32.INSTANCE.GetSystemMetrics(0) // SM_CXSCREEN
        val logicW = Toolkit.getDefaultToolkit().screenSize.width
        if (physW > 0 && logicW > 0) physW.toDouble() / logicW else 1.0
    } catch (e: Exception) {
        1.0
    }

    private fun createIcon(state: DictationState): BufferedImage {
        val size = 48
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        g.color = when (state) {
            DictationState.IDLE -> Color(0x555555)
            DictationState.RECORDING -> Color(0xE53935)
            DictationState.TRANSCRIBING -> Color(0xFB8C00)
        }
        g.fillOval(2, 2, size - 4, size - 4)

        g.color = Color.WHITE
        val cx = size / 2
        val cy = size / 2
        val mw = 18
        val mh = 24
        val mx = cx - mw / 2
        val my = cy - mh / 2 + 1
        g.fillRoundRect(mx, my, mw, mh, 6, 6)
        g.fillOval(cx - 9, my - 2, 18, 8)
        val sw = 20
        val sx = cx - sw / 2
        val sy = my + mh - 1
        g.fillRoundRect(sx, sy, sw, 4, 3, 3)
        g.fillRect(cx - 3, sy + 3, 6, 5)

        g.dispose()
        return img
    }
}
