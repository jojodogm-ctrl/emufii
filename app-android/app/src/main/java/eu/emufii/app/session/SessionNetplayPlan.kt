package eu.emufii.app.session

import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.library.Backend
import eu.emufii.app.netplay.NetplayNames

internal fun Session.netplayPlan(profileName: String?): NetplayPlan? {
    room?.let {
        return NetplayPlan(
            role = NetplayPlan.Role.Guest,
            ip = it.host,
            port = it.port,
            username = NetplayNames.usernameFor(backend, profileName),
            usernameOverDefaultOnly = backend == Backend.AZAHAR,
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
        // Per-emulator default: Dolphin listens on 2626, the others on 24872.
        port = port.toIntOrNull() ?: backend.defaultNetplayPort,
        username = NetplayNames.usernameFor(backend, profileName),
        usernameOverDefaultOnly = backend == Backend.AZAHAR,
        roomName = if (role == Session.Role.HOST) NetplayNames.roomName(code) else null,
        preferredGame = if (role == Session.Role.HOST) rom?.displayName else null,
        // ARMSX2 needs the same room code on both sides.
        password = if (backend == Backend.ARMSX2) code else null
    )
}
