package com.mpds.online

/** Derive transfer identity exclusively from the authenticated invitation.
 * These are metadata checks; the receiver must hash every actual file before use.
 */
internal object RoomAssetManifest {
    data class Entry(val kind: String, val bytes: Long, val sha256: String) {
        fun metadata(reuse: Boolean = false): ByteArray {
            require(kind in listOf("rom", "seat", "boot") && bytes > 0 && RoomInvitation.hash(sha256))
            return ("{\"kind\":\"$kind\",\"bytes\":$bytes,\"sha256\":\"$sha256\"" +
                (if (reuse) ",\"reuse\":true}" else "}")).toByteArray(Charsets.UTF_8)
        }
    }
    fun entries(admission: RoomInvitation.Admission, host: Boolean = false): List<Entry> {
        check(admission.isCurrent()) { "asset-cancelled" }
        val r = admission.room
        require(r["shareAssets"] == true && RoomInvitation.number(r["seat"]) in
            (if(host)1L else 2L)..RoomInvitation.number(r["players"]))
        val game = r["game"] as? String ?: "mpds"
        val romHash = r["transferRomSha256"] as? String
        require(RoomInvitation.hash(romHash))
        val romBytes = RoomInvitation.number(r["transferRomBytes"], 1, 536870912)
        val maxRom = if (game == "experimental" && r["guestBoot"] == "cartridge") 536870912L else 67108864L
        require(romBytes <= maxRom)
        val out = mutableListOf(Entry("rom", romBytes, romHash!!))
        if (game == "mpds") {
            val hash = r["snapshotSha256"] as? String
            require(RoomInvitation.hash(hash))
            out.add(Entry("seat", if (RoomInvitation.number(r["players"]) == 4L) 19244109 else 19244105, hash!!))
        } else if (game == "nsmb" || game == "mkds" && r["mkdsMenu"] == true) {
            val hash = r["bootSha256"] as? String
            require(RoomInvitation.hash(hash))
            out.add(Entry("boot", RoomInvitation.number(r["bootBytes"], 1, RoomInvitationIdentity.NSMB_GENERATED_MAX_BOOT), hash!!))
        }
        return out.toList()
    }
}
