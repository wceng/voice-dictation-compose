package com.wceng.dictation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wceng.dictation.core.model.AppConfig
import com.wceng.dictation.core.model.ConfigUpdate
import com.wceng.dictation.core.model.DictationState
import com.wceng.dictation.core.model.HistoryItem
import com.wceng.dictation.core.model.HotkeyCombo
import com.wceng.dictation.core.model.HotkeyConfig
import com.wceng.dictation.core.model.HotkeyModifier
import com.wceng.dictation.core.model.ThemeMode

private val statusColor = mapOf(
    DictationState.IDLE to Color(0xFF9E9E9E),
    DictationState.RECORDING to Color(0xFFE53935),
    DictationState.TRANSCRIBING to Color(0xFFFB8C00)
)

private val statusText = mapOf(
    DictationState.IDLE to "待机中",
    DictationState.RECORDING to "录音中...",
    DictationState.TRANSCRIBING to "转写中..."
)

/**
 * 主窗口界面(纯 Compose,跨平台复用):
 * 状态卡 / 开始-取消按钮 / 设置区 / 转写历史。
 */
@Composable
fun DictationApp(
    state: DictationState,
    history: List<HistoryItem>,
    config: AppConfig?,
    storageLocation: String,
    formatTimestamp: (Long) -> String,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    autostart: Boolean = false,
    hotkeys: HotkeyConfig = HotkeyConfig.DEFAULTS,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
    onClearHistory: () -> Unit,
    onSaveConfig: (ConfigUpdate) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onAutostartChange: (Boolean) -> Unit = {},
    /** 返回 null 表示受理成功;非空为内联展示的错误消息 */
    onSaveToggleHotkey: (HotkeyCombo) -> String? = { null },
    onSaveCancelHotkey: (HotkeyCombo) -> String? = { null }
) {
    var apiKey by remember(config) { mutableStateOf(config?.apiKey.orEmpty()) }
    var baseUrl by remember(config) { mutableStateOf(config?.baseUrl.orEmpty()) }
    var model by remember(config) { mutableStateOf(config?.model.orEmpty()) }
    var language by remember(config) { mutableStateOf(config?.language.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ===== 状态卡 =====
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(statusColor[state] ?: Color.Gray, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Text(statusText[state] ?: "待机中", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }

        // ===== 操作按钮 =====
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onToggle,
                enabled = state != DictationState.TRANSCRIBING
            ) {
                Text(if (state == DictationState.RECORDING) "停止并转写" else "开始录音")
            }
            OutlinedButton(
                onClick = onCancel,
                enabled = state == DictationState.RECORDING
            ) {
                Text("取消录音")
            }
        }
        Text(
            "快捷键: ${hotkeys.toggle.displayText()} 开始/停止 · ${hotkeys.cancel.displayText()} 取消",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ===== 设置区 =====
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("设置", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("接口地址 (OpenAI 兼容 /v1)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型") },
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text("语言") },
                        modifier = Modifier.weight(1f)
                    )
                }
                // ===== 外观:亮色/暗色三档 =====
                Text("外观", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "亮色",
                        ThemeMode.DARK to "暗色"
                    )
                    options.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                        ) {
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
                // ===== 开机自启动 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("开机自启动", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = autostart, onCheckedChange = onAutostartChange)
                }
                // ===== 全局热键 =====
                Text("全局热键", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HotkeyCaptureField(
                    label = "开始 / 停止并转写",
                    current = hotkeys.toggle,
                    defaultCombo = HotkeyConfig.DEFAULTS.toggle,
                    onSave = onSaveToggleHotkey
                )
                HotkeyCaptureField(
                    label = "取消录音",
                    current = hotkeys.cancel,
                    defaultCombo = HotkeyConfig.DEFAULTS.cancel,
                    onSave = onSaveCancelHotkey
                )
                Button(onClick = {
                    onSaveConfig(
                        ConfigUpdate(
                            apiKey = apiKey.takeIf { it.isNotBlank() },
                            baseUrl = baseUrl.takeIf { it.isNotBlank() },
                            model = model.takeIf { it.isNotBlank() },
                            language = language.takeIf { it.isNotBlank() }
                        )
                    )
                }) {
                    Text("保存设置")
                }
                Text(
                    "存储位置: $storageLocation\n留空项回退到环境变量或默认值",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ===== 历史 =====
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("最近转写", fontWeight = FontWeight.SemiBold)
                    if (history.isNotEmpty()) {
                        Text(
                            "清空",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onClearHistory() }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (history.isEmpty()) {
                    Text(
                        "暂无记录",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    history.asReversed().forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider()
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(formatTimestamp(item.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.text, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/** 纯修饰键(按下后还需主键才能构成组合) */
private val modifierKeys = setOf(
    Key.CtrlLeft, Key.CtrlRight,
    Key.ShiftLeft, Key.ShiftRight,
    Key.AltLeft, Key.AltRight,
    Key.MetaLeft, Key.MetaRight
)

/**
 * 可作主键的白名单:Compose [Key] 常量 -> 规范名(与 HotkeyCombo.SUPPORTED_KEY_NAMES 对齐)。
 * 刻意不使用 Key.keyCode 数值——Compose 与 AWT/JNativeHook 的键码体系互不相同,
 * 按常量对象做身份识别、经规范名查表取存储码,从根上消除换算错误。
 */
private val supportedKeys: Map<Key, String> = buildMap {
    put(Key.A, "A"); put(Key.B, "B"); put(Key.C, "C"); put(Key.D, "D"); put(Key.E, "E")
    put(Key.F, "F"); put(Key.G, "G"); put(Key.H, "H"); put(Key.I, "I"); put(Key.J, "J")
    put(Key.K, "K"); put(Key.L, "L"); put(Key.M, "M"); put(Key.N, "N"); put(Key.O, "O")
    put(Key.P, "P"); put(Key.Q, "Q"); put(Key.R, "R"); put(Key.S, "S"); put(Key.T, "T")
    put(Key.U, "U"); put(Key.V, "V"); put(Key.W, "W"); put(Key.X, "X"); put(Key.Y, "Y")
    put(Key.Z, "Z")
    put(Key.Zero, "0"); put(Key.One, "1"); put(Key.Two, "2"); put(Key.Three, "3")
    put(Key.Four, "4"); put(Key.Five, "5"); put(Key.Six, "6"); put(Key.Seven, "7")
    put(Key.Eight, "8"); put(Key.Nine, "9")
    put(Key.F1, "F1"); put(Key.F2, "F2"); put(Key.F3, "F3"); put(Key.F4, "F4")
    put(Key.F5, "F5"); put(Key.F6, "F6"); put(Key.F7, "F7"); put(Key.F8, "F8")
    put(Key.F9, "F9"); put(Key.F10, "F10"); put(Key.F11, "F11"); put(Key.F12, "F12")
    put(Key.Spacebar, "SPACE")
    put(Key.Backspace, "BACKSPACE")
    put(Key.Enter, "ENTER")
    put(Key.Tab, "TAB")
}

/**
 * 全局热键捕获行:展示当前组合 + 「修改」进入捕获态 + 「默认」恢复出厂。
 * 捕获态下整行持有焦点,Esc 取消、失焦自动取消;
 * 保存回调返回 null 视为成功并退出捕获态,非空作为错误消息内联展示。
 */
@Composable
private fun HotkeyCaptureField(
    label: String,
    current: HotkeyCombo,
    defaultCombo: HotkeyCombo,
    onSave: (HotkeyCombo) -> String?
) {
    var capturing by remember { mutableStateOf(false) }
    // 本次捕获期间子树是否已实际持有过焦点(防止进入瞬态误判为失焦)
    var focusSeen by remember { mutableStateOf(false) }
    // (消息, 是否错误);null 表示无状态行
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(capturing) {
        if (capturing) {
            focusSeen = false
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (capturing) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                // 不能用 isFocused 判断失焦:点击行内按钮后焦点在子孙节点上,
                // 列会收到瞬时的 isFocused=false,导致刚进捕获态就被取消。
                // 正确语义:整棵子树彻底无焦点(hasFocus=false)、且本捕获期间
                // 确实持有过焦点之后,才视为用户主动离开。
                if (!capturing) return@onFocusChanged
                if (state.hasFocus) {
                    focusSeen = true
                } else if (focusSeen) {
                    capturing = false
                    status = null
                    focusSeen = false
                }
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!capturing) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        val key = event.key
                        when {
                            key == Key.Escape -> {
                                capturing = false
                                status = null
                                true
                            }
                            key in modifierKeys -> {
                                status = "已捕获修饰键,请再按主键" to false
                                true
                            }
                            else -> {
                                // 按 Key 常量对象识别主键,经规范名取存储码;
                                // 不使用 keyCode 数值(Compose 与 AWT 键码体系不同)
                                val keyName = supportedKeys[key]
                                when {
                                    keyName == null -> status = "不支持的按键" to true
                                    else -> {
                                        val mods = buildSet {
                                            if (event.isCtrlPressed) add(HotkeyModifier.CTRL)
                                            if (event.isShiftPressed) add(HotkeyModifier.SHIFT)
                                            if (event.isAltPressed) add(HotkeyModifier.ALT)
                                            if (event.isMetaPressed) add(HotkeyModifier.META)
                                        }
                                        val combo = HotkeyCombo(
                                            mods,
                                            HotkeyCombo.SUPPORTED_KEY_NAMES.getValue(keyName)
                                        )
                                        val structural = combo.validate()
                                        when {
                                            structural != null -> status = structural to true
                                            else -> {
                                                val err = onSave(combo)
                                                if (err == null) {
                                                    capturing = false
                                                    status = "已保存" to false
                                                } else {
                                                    status = err to true
                                                }
                                            }
                                        }
                                    }
                                }
                                true
                            }
                        }
                    }
                    // 吞掉对应抬起事件,避免泄漏给窗口其他组件
                    KeyEventType.KeyUp -> true
                    else -> false
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(current.displayText(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            OutlinedButton(
                onClick = {
                    if (capturing) {
                        capturing = false
                        status = null
                    } else {
                        capturing = true
                        status = null
                    }
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
            ) {
                Text(if (capturing) "取消" else "修改", fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "默认",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val err = onSave(defaultCombo)
                    status = if (err == null) "已恢复默认" to false else err to true
                    if (err == null) capturing = false
                }
            )
        }
        if (capturing || status != null) {
            val (text, isError) = status ?: ("请按下新的组合键(Esc 取消)" to false)
            Text(
                text,
                fontSize = 11.sp,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}
