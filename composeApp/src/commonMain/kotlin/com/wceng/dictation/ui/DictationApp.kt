package com.wceng.dictation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    /** 当前处于捕获态的槽位:"toggle" / "cancel",null=无 */
    capturingSlot: String? = null,
    /** 捕获相关状态行:(消息, 是否错误);null=隐藏 */
    captureStatus: Pair<String, Boolean>? = null,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
    onClearHistory: () -> Unit,
    onSaveConfig: (ConfigUpdate) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onAutostartChange: (Boolean) -> Unit = {},
    /** 开始某槽位的钩子捕获:slot = "toggle" | "cancel" */
    onStartCapture: (String) -> Unit = {},
    onCancelCapture: () -> Unit = {},
    /** 恢复某槽位出厂默认:slot = "toggle" | "cancel" */
    onResetHotkey: (String) -> Unit = {}
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
                HotkeyRow(
                    label = "开始 / 停止并转写",
                    slot = "toggle",
                    current = hotkeys.toggle,
                    capturing = capturingSlot == "toggle",
                    onStartCapture = onStartCapture,
                    onCancelCapture = onCancelCapture,
                    onResetHotkey = onResetHotkey
                )
                HotkeyRow(
                    label = "取消录音",
                    slot = "cancel",
                    current = hotkeys.cancel,
                    capturing = capturingSlot == "cancel",
                    onStartCapture = onStartCapture,
                    onCancelCapture = onCancelCapture,
                    onResetHotkey = onResetHotkey
                )
                if (capturingSlot != null || captureStatus != null) {
                    val (text, isError) = captureStatus
                        ?: ("请按下新的组合键…(Esc 取消)" to false)
                    Text(
                        text,
                        fontSize = 11.sp,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
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

/**
 * 全局热键行:展示当前组合 + 「修改」武装钩子捕获 + 「默认」恢复出厂。
 * 捕获由 JNativeHook 全局钩子单发监听完成(见 HotkeyService.armOneShot),
 * 不依赖 Compose 焦点与键盘事件,免疫 IME 与布局差异。
 */
@Composable
private fun HotkeyRow(
    label: String,
    slot: String,
    current: HotkeyCombo,
    capturing: Boolean,
    onStartCapture: (String) -> Unit,
    onCancelCapture: () -> Unit,
    onResetHotkey: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (capturing) "请按下新的组合键…" else current.displayText(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (capturing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        OutlinedButton(
            onClick = { if (capturing) onCancelCapture() else onStartCapture(slot) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
        ) {
            Text(if (capturing) "取消" else "修改", fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "默认",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onResetHotkey(slot) }
        )
    }
}
