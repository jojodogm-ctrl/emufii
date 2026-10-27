package com.mpds.online

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** A slow guest must not make the host close its room: the host lane waits minutes, the guest lane stays short. */
class LobbyWaitTest {
    @Test fun hostWaitsMinutesForGuestWhileGuestKeepsShortBound() {
        assertTrue(RoomLobbyChannel.gatewayWaitNanos(true) >= TimeUnit.MINUTES.toNanos(10))
        assertTrue(RoomLobbyChannel.gatewayWaitNanos(true) <= TimeUnit.MINUTES.toNanos(30))
        assertEquals(TimeUnit.SECONDS.toNanos(30), RoomLobbyChannel.gatewayWaitNanos(false))
    }
}
