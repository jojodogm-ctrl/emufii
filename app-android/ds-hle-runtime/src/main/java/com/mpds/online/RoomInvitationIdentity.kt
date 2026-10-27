package com.mpds.online

import com.mpds.online.RoomInvitation.Companion.check
import com.mpds.online.RoomInvitation.Companion.digest
import com.mpds.online.RoomInvitation.Companion.game
import com.mpds.online.RoomInvitation.Companion.hash
import com.mpds.online.RoomInvitation.Companion.hex
import com.mpds.online.RoomInvitation.Companion.number
import com.mpds.online.RoomInvitation.Companion.obj

/** Exact admission metadata policy from the web identity modules. Generated
 * preparation is descriptor admission only: runner must verify actual boot bytes
 * and manifests before import, just as the browser preparation admissions do.
 */
internal object RoomInvitationIdentity {
    private const val NSMB_ROM = "dd2e519a9e46134e213e6e63dd2b478d9a258627769b777baddb2316dc21b81d"
    private const val NSMB_PIPE = "nsmb-a2dp-cartridge-preparation-rows10-v1"
    private const val NSMB_BOOT = "3ab3644a786183c758f9789f52e51325482c9771973672fa585af493279981e3"
    private const val NSMB_SET = "0d8c0d4fa62d8b256130d068b9814d6a48578d9345d3e6a095e10270b32fa96d"
    private const val EXP_PROFILE = "experimental-sdk-room-v2"
    // Wide-window (SDK500) metadata carries 64 send slots: bound each meta by 64 KiB.
    internal const val NSMB_GENERATED_MAX_BOOT = 38619610L

    fun validate(r: Map<String, Any?>, selected: RoomDirectory.Snapshot) {
        val game = game(selected); val players = selected.settings.players; val seat = selected.seat
        var snapshot: Any? = ""; var aid: Any? = -1.0
        val rom: Any?; val bytes: Long; val set: Any?
        if (game == "nsmb") {
            val generated = r["profile"] == "nsmb-eu-generated-cartridge-v1" &&
                r["sdkPipeline"] == NSMB_PIPE && r["romSha256"] == NSMB_ROM &&
                r["authorityTransport"] == "n2s1-loopback-datagram-v2" && r["bufferTicks"] == 10.0 &&
                players == 2 && r["savedAid"] == (seat - 1).toDouble() && r["snapshotSha256"] == "" &&
                hash(r["setSha256"]) && hash(r["bootSha256"]) && inRange(r["bootBytes"], 357, NSMB_GENERATED_MAX_BOOT)
            check(generated || r["profile"] == "nsmb-eu-cartridge-preparation-v1" &&
                r["sdkPipeline"] == NSMB_PIPE && r["bootSha256"] == NSMB_BOOT &&
                r["bootBytes"] == 38527066.0 && r["bufferTicks"] == 10.0, "invitation-profile")
            rom = NSMB_ROM; bytes = 33554432; set = if (generated) r["setSha256"] else NSMB_SET
            aid = (seat - 1).toDouble()
        } else if (game == "experimental") {
            val settings = selected.settings
            val boot = if (settings.guestBoot == RoomDirectory.GuestBoot.CARTRIDGE) "cartridge" else "download-play"
            check(r["profile"] == EXP_PROFILE && r["sdkPipeline"] == "experimental-wm-freedownload-v2" &&
                number(r["romBytes"]) == settings.romBytes && r["guestBoot"] == boot, "invitation-profile")
            rom = settings.romSha256; bytes = settings.romBytes!!
            set = hex(digest("$EXP_PROFILE\n$rom\n$bytes\n$players\n$boot\n".toByteArray()))
        } else {
            val pin = obj(RoomInvitationPins.games[game])
            rom = pin["rom"]; bytes = if (game == "mpds") 23288696 else number(pin["bytes"])
            val generated = game == "mkds" && r["mkdsMenu"] == true &&
                r["profile"] == "mkds-menu-amce-generated-v1" && r["sdkPipeline"] == pin["pipeline"] &&
                r["romSha256"] == rom && r["authorityTransport"] == "n2s1-loopback-datagram-v2" &&
                r["bufferTicks"] == 8.0 && inRange(r["savedAid"], 0, players - 1L) &&
                ((seat == 1) == (r["savedAid"] == 0.0)) && hash(r["setSha256"]) &&
                hash(r["snapshotSha256"]) && hash(r["bootSha256"]) &&
                inRange(r["bootBytes"], 92L + 144L * players + 2, 92L + 144L * players + 33554432L + 65536L)
            if (generated) {
                set = r["setSha256"]; snapshot = r["snapshotSha256"]; aid = r["savedAid"]
            } else {
                check(r["profile"] == pin["profile"] && r["sdkPipeline"] == pin["pipeline"], "invitation-profile")
                if (game == "mpds" || game == "mkds" && r["mkdsMenu"] == true) {
                    val group = obj(obj(pin[if (game == "mpds") "cohorts" else "menu"])[players.toString()])
                    val part = obj((group["seats"] as List<*>)[seat - 1])
                    set = group["set"]; snapshot = part["core"]; aid = part["aid"]
                    if (game == "mpds") check(r["shareAssets"] == false && r["sendROM"] != false ||
                        r["transferRomSha256"] == rom && r["transferRomBytes"] == 23288696.0 ||
                        r["transferRomSha256"] == pin["stock"] && r["transferRomBytes"] == 33554432.0, "invitation-assets")
                    else check(r["bootSha256"] == part["boot"] && r["bootBytes"] == 19516863.0 &&
                        r["bufferTicks"] == 8.0, "invitation-profile")
                } else set = if (game == "mkds") obj(pin["cold"])[players.toString()] else pin["set"]
            }
        }
        check(r["romSha256"] == rom && r["setSha256"] == set && r["snapshotSha256"] == snapshot &&
            r["savedAid"] == aid, "invitation-identity")
        if (r["shareAssets"] == false && r["sendROM"] != false)
            check(r["transferRomSha256"] == "" && r["transferRomBytes"] == 0.0, "invitation-assets")
        else if (game != "mpds") check(r["transferRomSha256"] == rom && number(r["transferRomBytes"]) == bytes, "invitation-assets")
    }

    private fun inRange(v: Any?, min: Long, max: Long) = v is Double && v.isFinite() &&
        v >= min.toDouble() && v <= max.toDouble() && v == v.toLong().toDouble()
}
