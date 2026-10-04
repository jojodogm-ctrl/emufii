package eu.emufii.app.wfc

object KaeruWfc {

    const val DNS_SERVER = "178.62.43.212"

    /** Answered via a protected socket so Kaeru needn't be routable through the tun. Clear of 10.67.x and 10.0.2.x. */
    const val SENTINEL_DNS = "10.66.53.53"

    const val TUN_ADDRESS = "10.66.53.2"

    const val DNS_PORT = 53

    const val MAX_DNS_MESSAGE = 4096
}
