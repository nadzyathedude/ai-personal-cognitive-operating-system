package com.example.assistant.security

import com.example.assistant.models.SessionInfo
import com.example.assistant.models.VerificationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecureSessionManager {

    private val _session = MutableStateFlow<SessionInfo?>(null)
    val session: StateFlow<SessionInfo?> = _session.asStateFlow()

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun createSession(sessionId: String, userId: String) {
        _session.value = SessionInfo(
            sessionId = sessionId,
            userId = userId,
            verified = false,
            limitedMode = true
        )
        _isLocked.value = true
    }

    fun onVerificationResult(result: VerificationResult) {
        _session.value = _session.value?.copy(
            verified = result.verified,
            limitedMode = !result.verified
        )
        _isLocked.value = !result.verified
    }

    fun isVerified(): Boolean = _session.value?.verified == true

    fun getUserId(): String? = _session.value?.userId

    fun getSessionId(): String? = _session.value?.sessionId

    fun endSession() {
        _session.value = null
        _isLocked.value = true
    }

    fun isInLimitedMode(): Boolean = _session.value?.limitedMode == true
}
