package eu.emufii.app.wg

sealed interface WgState {
    data object Idle : WgState

    data class Starting(val code: String) : WgState

    data class Online(val code: String, val ip: String) : WgState

    data class Offline(val code: String) : WgState

    data object Stopping : WgState

    data class Error(val message: String) : WgState
}
