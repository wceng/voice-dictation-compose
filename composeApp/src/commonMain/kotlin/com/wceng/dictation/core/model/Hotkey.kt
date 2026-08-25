package com.wceng.dictation.core.model

/**
 * 全局热键修饰键(跨平台语义;桌面层映射到钩子库常量)。
 */
enum class HotkeyModifier { CTRL, SHIFT, ALT, META }

/**
 * 全局热键组合:修饰键 + 主键。
 *
 * [keyCode] 采用 AWT `KeyEvent.VK_*` 整数——Compose Desktop 的键盘事件
 * 在 JVM 目标上与其同源,捕获 UI 可直接透传,无需平台换算;
 * 主键必须在 [SUPPORTED_KEY_NAMES] 白名单内。
 */
data class HotkeyCombo(
    val modifiers: Set<HotkeyModifier>,
    val keyCode: Int
) {
    /** 规范存储串,如 "CTRL+SHIFT+SPACE";修饰键按固定顺序排列 */
    fun canonical(): String =
        (FIXED_MODIFIER_ORDER.filter { it in modifiers }.map { it.name } + keyName())
            .joinToString("+")

    /** 展示文本,如 "Ctrl+Shift+Space";主键名统一首字母大写(SPACE→Space、F5 保持 F5) */
    fun displayText(): String {
        val mods = FIXED_MODIFIER_ORDER.filter { it in modifiers }
            .joinToString("+") { it.name.lowercase().replaceFirstChar(Char::uppercase) }
        val prettyKey = keyName().lowercase().replaceFirstChar(Char::uppercase)
        return if (mods.isEmpty()) prettyKey else "$mods+$prettyKey"
    }

    private fun keyName(): String =
        SUPPORTED_KEY_NAMES.entries.firstOrNull { it.value == keyCode }?.key ?: keyCode.toString()

    /**
     * 结构合法性校验(不含成对冲突,见 [HotkeyConfig.validatePair])。
     * 返回错误消息;null 表示合法。
     */
    fun validate(): String? {
        if (keyCode !in SUPPORTED_KEY_NAMES.values) return "不支持的按键"
        if (modifiers.none { it == HotkeyModifier.CTRL || it == HotkeyModifier.ALT || it == HotkeyModifier.META })
            return "组合键必须包含 Ctrl、Alt 或 Win 之一"
        return null
    }

    companion object {
        /** 修饰键在规范串/展示文本中的固定顺序 */
        val FIXED_MODIFIER_ORDER = listOf(
            HotkeyModifier.CTRL, HotkeyModifier.SHIFT, HotkeyModifier.ALT, HotkeyModifier.META
        )

        /** 可作主键的白名单:名称 -> AWT VK 码 */
        val SUPPORTED_KEY_NAMES: Map<String, Int> = buildMap {
            ('A'..'Z').forEach { put(it.toString(), it.code) }   // VK_A=65 .. VK_Z=90
            ('0'..'9').forEach { put(it.toString(), it.code) }   // VK_0=48 .. VK_9=57
            (1..12).forEach { put("F$it", 111 + it) }            // VK_F1=112 .. VK_F12=123
            put("SPACE", 32)      // VK_SPACE
            put("BACKSPACE", 8)   // VK_BACK_SPACE
            put("ENTER", 10)      // VK_ENTER
            put("TAB", 9)         // VK_TAB
        }

        /**
         * 解析规范串;任何格式错误返回 null(调用方回退默认值)。
         * 要求:至少一个已知修饰键 + 一个白名单主键,无重复修饰键。
         */
        fun parseOrNull(raw: String): HotkeyCombo? {
            val parts = raw.trim().split("+").filter { it.isNotEmpty() }
            if (parts.size < 2) return null
            val keyCode = SUPPORTED_KEY_NAMES[parts.last()] ?: return null
            val modifiers = parts.dropLast(1).map { token ->
                HotkeyModifier.entries.firstOrNull { it.name == token } ?: return null
            }
            if (modifiers.toSet().size != modifiers.size) return null // 重复修饰键
            return HotkeyCombo(modifiers.toSet(), keyCode)
        }
    }
}

/**
 * 一对全局热键配置:开始/停止转写 与 取消录音。
 */
data class HotkeyConfig(
    val toggle: HotkeyCombo,
    val cancel: HotkeyCombo
) {
    /** 成对冲突校验;返回错误消息,null 表示合法 */
    fun validatePair(): String? =
        if (toggle == cancel) "开始与取消热键不能相同" else null

    companion object {
        /** 与历史行为一致的出厂默认值 */
        val DEFAULTS = HotkeyConfig(
            toggle = HotkeyCombo.parseOrNull("CTRL+SHIFT+SPACE")!!,
            cancel = HotkeyCombo.parseOrNull("CTRL+SHIFT+BACKSPACE")!!
        )
    }
}