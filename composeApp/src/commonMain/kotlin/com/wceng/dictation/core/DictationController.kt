package com.wceng.dictation.core

import com.wceng.dictation.core.model.AppConfig
import com.wceng.dictation.core.model.DictationState
import com.wceng.dictation.core.model.HistoryItem
import com.wceng.dictation.core.model.TranscriptionResult
import com.wceng.dictation.data.network.SttNetworkDataSource
import com.wceng.dictation.data.repository.ConfigRepository
import com.wceng.dictation.data.repository.TranscriptionHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 听写流程控制器(NiA 分层:编排者,不持有业务数据)。
 * 配置与历史全部来自仓库(单一数据源);热键、托盘、UI 经由它操作,
 * 状态以 StateFlow 单向驱动界面与托盘。
 */
class DictationController(
    private val recorder: Recorder,
    private val sttNetwork: SttNetworkDataSource,
    private val injector: TextInjector,
    private val sound: SoundFeedback,
    private val scope: CoroutineScope,
    configRepository: ConfigRepository,
    private val historyRepository: TranscriptionHistoryRepository,
    private val onNotify: (title: String, message: String, isError: Boolean) -> Unit = { _, _, _ -> }
) {

    private val _state = MutableStateFlow(DictationState.IDLE)
    val state: StateFlow<DictationState> = _state.asStateFlow()

    /** 仓库配置的常驻镜像:热键触发时无需挂起即可同步取值 */
    private val config: StateFlow<AppConfig> =
        configRepository.config.stateIn(scope, SharingStarted.Eagerly, AppConfig.DEFAULTS)

    /** 防止上一次转写未完成时又触发新一轮 */
    private val transcribeMutex = Mutex()

    /** 热键/托盘共用:录音中=停止并转写,否则=开始录音 */
    fun toggle() {
        when (_state.value) {
            DictationState.RECORDING -> stopAndTranscribe()
            DictationState.IDLE -> startRecording()
            DictationState.TRANSCRIBING -> Unit
        }
    }

    fun startRecording() {
        if (_state.value != DictationState.IDLE) return
        if (!config.value.configured) {
            onNotify("尚未配置", "请先在设置中填写 API Key", true)
            return
        }
        if (recorder.start()) {
            sound.playStart()
            _state.value = DictationState.RECORDING
        } else {
            onNotify("录音失败", "未找到可用的麦克风设备", true)
        }
    }

    fun stopAndTranscribe() {
        if (_state.value != DictationState.RECORDING) return
        val pcm = recorder.stop()
        if (pcm.isEmpty()) {
            _state.value = DictationState.IDLE
            return
        }
        sound.playStop()
        _state.value = DictationState.TRANSCRIBING

        // 转写开始前取一次配置快照;中途改设置只影响下一次听写
        val configSnapshot = config.value
        scope.launch {
            transcribeMutex.withLock {
                val wav = WavEncoder.encode(pcm)
                when (val result = sttNetwork.transcribe(configSnapshot, wav)) {
                    is TranscriptionResult.Success -> {
                        if (result.text.isBlank()) {
                            onNotify("未识别到语音", "请靠近麦克风再试", false)
                        } else {
                            injector.inject(result.text)
                            historyRepository.record(
                                HistoryItem(result.text, System.currentTimeMillis(), true)
                            )
                        }
                    }
                    is TranscriptionResult.Failure -> onNotify("转写失败", result.reason, true)
                }
                _state.value = DictationState.IDLE
            }
        }
    }

    fun cancelRecording() {
        if (_state.value != DictationState.RECORDING) return
        recorder.cancel()
        _state.value = DictationState.IDLE
    }

    fun clearHistory() {
        scope.launch { historyRepository.clear() }
    }
}
