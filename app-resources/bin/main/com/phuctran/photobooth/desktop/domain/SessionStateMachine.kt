package com.phuctran.photobooth.desktop.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionState {
    IDLE,
    SELECTING,
    SELECTING_QUANTITY,
    PAYMENT_PENDING,
    PREPARING,
    LIVE_VIEW,
    COUNTDOWN,
    CAPTURING,
    SELECTING_PHOTOS,
    EDITING,
    COMPOSING,
    PRINT_PENDING,
    PRINTING,
    DELIVERY,
    RECOVERY,
    OUT_OF_SERVICE,
    ADMIN
}

class SessionStateMachine {
    private val _currentState = MutableStateFlow(SessionState.IDLE)
    val currentState: StateFlow<SessionState> = _currentState.asStateFlow()

    fun transitionTo(newState: SessionState) {
        _currentState.value = newState
    }

    fun reset() {
        _currentState.value = SessionState.IDLE
    }
}
