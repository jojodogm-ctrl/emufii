package com.mpds.online

import java.io.Closeable
import java.util.concurrent.CompletableFuture

internal interface RoomNativeNetwork : Closeable {
    val ready: CompletableFuture<Unit>
}
