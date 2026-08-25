package com.wceng.dictation.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * 桌面剪贴板实现：基于 AWT Toolkit。
 * Windows/Linux 的 X11 剪贴板读取需要持有 AWT 引用，首次调用会初始化图形环境。
 */
class DesktopClipboardManager : ClipboardManager {

    override fun setText(text: String) {
        runCatching {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }.onFailure { System.err.println("[Clipboard] 写入失败: ${it.message}") }
    }

    override fun getText(): String? {
        return runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return@runCatching null
            clipboard.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull()
    }

    override fun hasText(): Boolean = !getText().isNullOrEmpty()
}