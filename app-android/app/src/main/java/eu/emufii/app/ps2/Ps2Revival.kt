package eu.emufii.app.ps2

import eu.emufii.app.psp.PspServer
import eu.emufii.app.psp.PspServerPick

/**
 * PS2 revival servers. Each one is a DNS resolver that points the game at the project's own
 * machines, so one address serves the whole catalogue it has taken over.
 *
 * Served as [PspServerPick] so the launch dialog's picker shows both consoles the same way;
 * `host` carries the resolver address. No project publishes a player count, so `players` is null.
 */
object Ps2Revival {

    /** Keyed by the ids `scripts/ps2-online.mjs` writes into the compat base. */
    val servers = linkedMapOf(
        "psrewired" to PspServer("PSRewired", "67.222.156.250", "", "", ""),
        "ps2online" to PspServer("PS2 Online Revival", "45.7.228.197", "", "", ""),
        "mholdschool" to PspServer("MH Oldschool", "34.75.107.68", "", "", ""),
    )

    val DEFAULT_DNS: String get() = servers.values.first().host

    @Volatile
    var chosenDns: String? = null

    /** Only the resolvers the compat base says serve this game, in its order. */
    fun picks(ids: List<String>): List<PspServerPick> =
        ids.mapNotNull { servers[it] }.map { PspServerPick(it, null) }

    /** A mistyped entry would send every lookup into the void with nothing pointing back here. */
    fun isUsable(dns: String): Boolean =
        dns.split('.').let { parts ->
            parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
        }
}
