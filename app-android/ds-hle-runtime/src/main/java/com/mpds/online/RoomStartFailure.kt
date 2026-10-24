package com.mpds.online

/** Only locally selected stages cross the UI/log boundary, never exception text. */
internal class RoomStartFailure(val stage: Stage, cause: Throwable) : Exception(stage.code, cause) {
    enum class Stage(val code: String) {
        ENGINE("game-engine-open"), ASSETS("game-asset-transfer"),
        PREPARATION("game-preparation-import"), BARRIER("game-start-barrier"),
        CONFIGURE("game-native-configuration"), NETWORK("game-network-connect"),
        BOOT("game-native-boot"), DISPLAY("game-display-start")
    }
    companion object {
        fun code(error: Throwable): String =
            (error as? RoomStartFailure)?.stage?.code ?: "game-start-failed"
    }
}
