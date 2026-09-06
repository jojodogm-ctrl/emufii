package eu.emufii.app.session

import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.library.Backend
import eu.emufii.app.netplay.NetplayNames

/**
 * Both roles point Azahar at the host's tunnel address: `netPlayCreateRoom` binds and
 * self-joins on the same address (PHASE0_AZAHAR.md).
 * pourquoi : docs/decisions/session.md § What each backend receives at launch
 */
internal fun Session.netplayPlan(profileName: String?): NetplayPlan? {
    // With a room on the VPS nobody hosts: both players join it, so the game no longer
    // depends on a phone being reachable, and the tunnel need not be up to dial.
    room?.let {
        return NetplayPlan(
            role = NetplayPlan.Role.Guest,
            ip = it.host,
            port = it.port,
            username = NetplayNames.usernameFor(backend, profileName),
            password = it.password
        )
    }
    if (hostIp.isBlank()) return null
    return NetplayPlan(
        role = when (role) {
            Session.Role.HOST -> NetplayPlan.Role.Host
            Session.Role.GUEST -> NetplayPlan.Role.Guest
        },
        ip = hostIp,
        // Otherwise the target emulator's, 2626 for Dolphin and 24872 for the others: a shared
        // default would send the Dolphin guest to a silent port.
        port = port.toIntOrNull() ?: backend.defaultNetplayPort,
        // Eden only: it ships one default nickname to everybody, and two players sharing one
        // cannot share a room.
        // pourquoi : docs/decisions/session.md § What each backend receives at launch
        username = NetplayNames.usernameFor(backend, profileName),
        roomName = if (role == Session.Role.HOST) NetplayNames.roomName(code) else null,
        preferredGame = if (role == Session.Role.HOST) rom?.displayName else null,
        // On PS2 the session code doubles as the room code: ARMSX2 requires one, identical on
        // both sides, and negotiates nothing.
        // pourquoi : docs/decisions/session.md § What each backend receives at launch
        password = if (backend == Backend.ARMSX2) code else null
    )
}
