package com.example.catfeedermonitor.logic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FeedingState {
    IDLE,      // 无人/等待
    VERIFYING, // 发现目标，确认中（防误触）
    RECORDING  // 正在进食 (已抓拍，持续监测直到猫离开)
}

class FeedingSessionManager(
    private val onCaptureTriggered: (String) -> Unit
) {
    private val _currentState = MutableStateFlow(FeedingState.IDLE)
    val currentState: StateFlow<FeedingState> = _currentState.asStateFlow()

    private val _statusMessage = MutableStateFlow("状态: 等待中...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // 确认相关
    private var verifyingCat: String? = null
    private var verificationStartTime: Long = 0
    private val VERIFICATION_DURATION = 2000L // 2秒确认即可，稍微快点

    // 会话相关
    private var currentSessionCat: String? = null
    private var lastSeenTime: Long = 0
    private val SESSION_TIMEOUT = 5000L // 如果猫消失超过5秒，认为它走了

    fun processDetections(detections: List<DetectionResult>) {
        val topDetection = detections.maxByOrNull { it.score }
        val detectedCat = topDetection?.label

        when (_currentState.value) {
            // 1. 空闲状态：发现猫 -> 去确认
            FeedingState.IDLE -> {
                if (detectedCat != null) {
                    startVerification(detectedCat)
                } else {
                    _statusMessage.value = "状态: 等待中..."
                }
            }

            // 2. 确认状态：
            //    - 猫还在：检查时间是否够了
            //    - 猫变了：重新开始确认新的猫
            //    - 猫没了：回到 IDLE
            FeedingState.VERIFYING -> {
                if (detectedCat == null) {
                    resetToIdle("目标丢失")
                    return
                }

                if (detectedCat != verifyingCat) {
                    // 换猫了，重新确认新的猫
                    startVerification(detectedCat)
                    return
                }

                // 还是同一只猫，检查确认时间
                val elapsed = System.currentTimeMillis() - verificationStartTime
                _statusMessage.value = "确认中: $detectedCat (${elapsed / 100}%)"

                if (elapsed >= VERIFICATION_DURATION) {
                    startSession(detectedCat)
                }
            }

            // 3. 记录状态（进食中）：
            //    - 同一只猫还在：更新最后见面时间（续命）
            //    - 不同的猫来了：立即结束当前会话，开始新猫的确认
            //    - 猫不见了：检查是否超时，超时则结束
            FeedingState.RECORDING -> {
                if (detectedCat != null) {
                    if (detectedCat == currentSessionCat) {
                        // 同一只猫还在吃，更新时间，保持状态
                        lastSeenTime = System.currentTimeMillis()
                        _statusMessage.value = "状态: $currentSessionCat 正在进食..."
                    } else {
                        // 突然变成了另一只猫！(比如 Sunny 挤走了 Putong)
                        // 立即结束上一个会话，开始新猫的验证
                        finishSession()
                        startVerification(detectedCat)
                    }
                } else {
                    // 画面里没猫了，检查消失了多久
                    val timeSinceLastSeen = System.currentTimeMillis() - lastSeenTime
                    if (timeSinceLastSeen > SESSION_TIMEOUT) {
                        finishSession() // 认为猫已经吃完走了
                    } else {
                        _statusMessage.value = "状态: $currentSessionCat 暂时离开 (${(SESSION_TIMEOUT - timeSinceLastSeen)/1000}s)"
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
    }

    private fun startSession(catName: String) {
        _currentState.value = FeedingState.RECORDING
        currentSessionCat = catName
        lastSeenTime = System.currentTimeMillis()

        // 触发抓拍
        onCaptureTriggered(catName)

        _statusMessage.value = "状态: $catName 正在进食 (已记录)"
    }

    private fun finishSession() {
        // 会话结束
        resetToIdle("进食结束")
    }

    private fun resetToIdle(reason: String? = null) {
        _currentState.value = FeedingState.IDLE
        verifyingCat = null
        currentSessionCat = null
        if (reason != null) {
            _statusMessage.value = "状态: $reason -> 等待中"
        }
    }
}