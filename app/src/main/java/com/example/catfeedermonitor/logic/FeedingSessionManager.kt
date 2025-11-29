package com.example.catfeedermonitor.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FeedingState {
    IDLE,      // 无人/等待
    VERIFYING, // 发现目标，确认中
    RECORDING  // 正在进食 (已抓拍，计时中)
}

class FeedingSessionManager(
    private val onCaptureTriggered: (String) -> Unit,
    // NEW: 新增一个回调，当猫吃完离开时触发，返回 (猫名, 时长毫秒)
    private val onSessionEnded: (String, Long) -> Unit,
    private val logManager: LogManager
) {
    private val _currentState = MutableStateFlow(FeedingState.IDLE)
    val currentState: StateFlow<FeedingState> = _currentState.asStateFlow()

    private val _statusMessage = MutableStateFlow("状态: 等待中...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // 确认相关
    private var verifyingCat: String? = null
    private var verificationStartTime: Long = 0
    private val VERIFICATION_DURATION = 2000L

    // 会话相关
    private var currentSessionCat: String? = null
    private var sessionStartTime: Long = 0 // NEW: 记录开始时间
    private var lastSeenTime: Long = 0
    private val SESSION_TIMEOUT = 5000L

    fun processDetections(detections: List<DetectionResult>) {
        val topDetection = detections.maxByOrNull { it.score }
        val detectedCat = topDetection?.label

        when (_currentState.value) {
            FeedingState.IDLE -> {
                if (detectedCat != null) {
                    startVerification(detectedCat)
                } else {
                    _statusMessage.value = "状态: 等待中..."
                }
            }

            FeedingState.VERIFYING -> {
                if (detectedCat == null) {
                    resetToIdle("目标丢失")
                    return
                }
                if (detectedCat != verifyingCat) {
                    startVerification(detectedCat)
                    return
                }
                val elapsed = System.currentTimeMillis() - verificationStartTime
                _statusMessage.value = "确认中: $detectedCat (${elapsed / 100}%)"
                if (elapsed >= VERIFICATION_DURATION) {
                    startSession(detectedCat)
                }
            }

            FeedingState.RECORDING -> {
                // 计算当前已持续时长（用于显示）
                val currentDuration = System.currentTimeMillis() - sessionStartTime
                val durationSec = currentDuration / 1000

                if (detectedCat != null) {
                    if (detectedCat == currentSessionCat) {
                        lastSeenTime = System.currentTimeMillis()
                        _statusMessage.value = "状态: $currentSessionCat 进食中 (${durationSec}s)..."
                    } else {
                        // 换猫了：先结束当前会话，再开始新的验证
                        finishSession()
                        startVerification(detectedCat)
                    }
                } else {
                    val timeSinceLastSeen = System.currentTimeMillis() - lastSeenTime
                    if (timeSinceLastSeen > SESSION_TIMEOUT) {
                        finishSession()
                    } else {
                        // 倒计时显示
                        val remaining = (SESSION_TIMEOUT - timeSinceLastSeen) / 1000
                        _statusMessage.value = "状态: $currentSessionCat 离开? (缓冲 ${remaining}s) | 已吃 ${durationSec}s"
                    }
                }
            }
        }
    }

    private fun startVerification(catName: String) {
        _currentState.value = FeedingState.VERIFYING
        verifyingCat = catName
        verificationStartTime = System.currentTimeMillis()
        _statusMessage.value = "发现: $catName，确认中..."
        logManager.info("FeedingSession", "Verifying: $catName")
    }

    private fun startSession(catName: String) {
        _currentState.value = FeedingState.RECORDING
        currentSessionCat = catName
        lastSeenTime = System.currentTimeMillis()
        sessionStartTime = System.currentTimeMillis() // NEW: 记下开始时间

        // 触发抓拍（还是为了留照片）
        onCaptureTriggered(catName)

        _statusMessage.value = "状态: $catName 开始进食 (计时开始)"
        logManager.info("FeedingSession", "Session started: $catName")
    }

    private fun finishSession() {
        val cat = currentSessionCat
        val startTime = sessionStartTime

        // NEW: 计算总时长并回调
        if (cat != null && startTime > 0) {
            val totalDuration = lastSeenTime - startTime // 用最后看到的时间算，去掉缓冲期
            // 如果时长太短（比如小于3秒），可能只是路过，可以选择不保存，这里我们先都保存
            if (totalDuration > 1000) {
                onSessionEnded(cat, totalDuration)
                logManager.info("FeedingSession", "Session ended: $cat, duration: ${totalDuration}ms")
            } else {
                logManager.info("FeedingSession", "Session ignored (too short): $cat, duration: ${totalDuration}ms")
            }
        }

        resetToIdle("进食结束")
    }

    private fun resetToIdle(reason: String? = null) {
        _currentState.value = FeedingState.IDLE
        verifyingCat = null
        currentSessionCat = null
        sessionStartTime = 0
        if (reason != null) {
            _statusMessage.value = "状态: $reason -> 等待中"
        }
    }
}