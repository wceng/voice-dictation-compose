package com.wceng.dictation.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * 音频输入设备管理：枚举、选择、默认设备监听。
 * 桌面端基于 Java Sound API；Android 基于 AudioManager；iOS 基于 AVAudioSession。
 */
interface AudioDeviceManager {

    /** 可用输入设备列表（名称、ID、是否默认） */
    val devices: List<AudioInputDevice>

    /** 当前选中的设备 ID，null 表示系统默认 */
    val selectedDeviceId: String?

    /** 选择设备，null = 使用系统默认 */
    fun selectDevice(deviceId: String?)

    /** 刷新设备列表（热插拔后调用） */
    fun refresh()
}

/** 音频输入设备信息 */
data class AudioInputDevice(
    val id: String,
    val name: String,
    val isDefault: Boolean
)