package com.wceng.dictation.platform

import com.wceng.dictation.platform.AudioDeviceManager
import com.wceng.dictation.platform.AudioInputDevice
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Line
import javax.sound.sampled.TargetDataLine

/**
 * 桌面音频输入设备管理：基于 Java Sound API 的 Mixer / TargetDataLine 枚举。
 * 桌面端通常只有一个默认输入设备，热插拔后调用 refresh() 重新枚举。
 */
class DesktopAudioDeviceManager : AudioDeviceManager {

    private var _devices: List<AudioInputDevice> = emptyList()
    override val devices: List<AudioInputDevice> get() = _devices

    private var _selectedDeviceId: String? = null
    override val selectedDeviceId: String? get() = _selectedDeviceId

    override fun selectDevice(deviceId: String?) {
        _selectedDeviceId = deviceId
    }

    override fun refresh() {
        val list = mutableListOf<AudioInputDevice>()
        var isFirst = true

        AudioSystem.getMixerInfo().forEach { info ->
            val mixer = AudioSystem.getMixer(info)
            val hasInput = mixer.targetLineInfo.any {
                it is Line.Info && it.lineClass == TargetDataLine::class.java
            }
            if (hasInput) {
                list.add(
                    AudioInputDevice(
                        id = info.name,
                        name = info.name.trim(),
                        isDefault = isFirst
                    )
                )
                isFirst = false
            }
        }
        // 无可用设备时仍保持空列表,UI 显示"未检测到麦克风"
        _devices = list
        // 之前选择的设备已失效时回退到默认
        if (_selectedDeviceId?.let { s -> list.none { it.id == s } } != false) {
            _selectedDeviceId = list.firstOrNull()?.id
        }
    }

    /** 首启动自动枚举一次 */
    init {
        refresh()
    }
}