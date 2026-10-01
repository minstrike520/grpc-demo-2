package com.example.grpcsim

/** Connection state of the app-level gRPC channel / main stream. */
sealed interface ConnState {
    data object Idle : ConnState
    data object Connecting : ConnState
    data object Connected : ConnState
    data class Disconnected(val reason: String?) : ConnState
}