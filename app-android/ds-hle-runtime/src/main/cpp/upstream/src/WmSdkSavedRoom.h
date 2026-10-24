// Header-only MPDS admission identity; no ROM loader, guest state or callbacks.
#pragma once
#include "MpdsRoomBootstrap.h"
#include "MpdsCompactPipeline.h"
#include "WmSdkMessageBus.h"
#include "NsmbPrepIdentity.h"
#include "MkdsMenuPrepIdentity.h"

namespace melonDS::wm_sdk {
using SavedRoomOffer = mpds_room::Offer;
enum class SavedRoomState : std::uint8_t { Pending, Ready, Rejected };
enum class SavedRoomReason : std::uint8_t {
    None, Unsupported, NotOffered, InvalidEndpoint, InvalidOffer, Busy,
    Duplicate, Mismatch, Stale, Disconnected, Capacity
};
struct SavedRoomResult {
    SavedRoomState state = SavedRoomState::Rejected;
    SavedRoomReason reason = SavedRoomReason::NotOffered;
    std::uint64_t room = 0, epoch = 0; // real parent token, agreed offer epoch
    std::uint16_t members = 0; // AID bitmap, populated only when Ready
    wm_prototype::MessageBus::Session session{}; // fresh transport identity
};
namespace saved_room {
// SHA256(Profile + LF + PatchedSha + LF + players + LF + each ordered
// snapshot SHA + LF), derived from MpdsRoomBootstrap.cpp's pinned asset sets.
// These pins intentionally require review together if the asset profile changes.
inline constexpr const char* SetSha[] = {
    "bcd3d30142fd92fa092645502d8b56d36c7ac86abce9b8b11be2a35f9e19d296",
    "789d97979f627e57799cb33d5597b2220835feb0d244426f200094895c9d211b",
    "54b4b7a1c1f557184493c3589fea2baad7e63bf9c2753740decede207e22b575"
};
inline constexpr unsigned Aid[3][4] = {{0,1,0,0},{0,2,1,0},{0,3,1,2}};
inline bool valid(const SavedRoomOffer& o) {
    // Generated profiles agree on a manifest hash, not a built-in asset pin.
    // Each endpoint must separately verify its bytes before making this offer.
    // Keep this identity equal to mkds_menu_prep::generated::Profile without
    // pulling its core/SDK decoder into the transport's header dependency cycle.
    if(o.profile=="mkds-menu-amce-generated-v1") return o.players>=2 && o.players<=8
        && o.seat>=1 && o.seat<=o.players && o.epoch && o.bufferTicks==8
        && o.savedAid<o.players && ((o.seat==1)==(o.savedAid==0))
        && o.romSha==mkds_menu_prep::RomSha && o.setSha.size()==64
        && o.setSha.find_first_not_of("0123456789abcdef")==std::string::npos;
    if(o.profile==nsmb_prep::GeneratedProfile) return o.players==2 && o.seat>=1 && o.seat<=2 && o.epoch
        && o.bufferTicks==10 && o.savedAid==o.seat-1 && o.romSha==nsmb_prep::RomSha
        && o.setSha.size()==64 && o.setSha.find_first_not_of("0123456789abcdef")==std::string::npos;
    if(o.profile==mkds_menu_prep::Profile) return mkds_menu_prep::supported(o.players) && o.seat>=1 && o.seat<=o.players && o.epoch
        && o.bufferTicks==8 && o.savedAid==mkds_menu_prep::set(o.players).seats[o.seat-1].aid && o.romSha==mkds_menu_prep::RomSha
        && o.setSha==mkds_menu_prep::set(o.players).sha;
    if(o.profile==nsmb_prep::CartridgeProfile) return o.players==2 && o.seat>=1 && o.seat<=2 && o.epoch
        && o.bufferTicks==10 && o.savedAid==o.seat-1 && o.romSha==nsmb_prep::RomSha
        && std::string(nsmb_prep::CartridgeSetSha).size()==64 && o.setSha==nsmb_prep::CartridgeSetSha;
    if(o.profile==nsmb_prep::Profile) return o.players==2 && o.seat>=1 && o.seat<=2 && o.epoch
        && o.bufferTicks==8 && o.savedAid==o.seat-1 && o.romSha==nsmb_prep::RomSha
        && std::string(nsmb_prep::SetSha).size()==64 && o.setSha==nsmb_prep::SetSha;
    return o.players >= 2 && o.players <= 4 && o.seat >= 1 && o.seat <= o.players &&
        o.epoch && o.bufferTicks >= 2 && o.bufferTicks <= 12 &&
        (o.profile == mpds_room::Profile || o.profile == mpds_room::compact::Profile
#ifdef NIFI2_PRIVATE_MPDS_EXTENDED_PIPELINE
            #if defined(NIFI2_PRIVATE_WMSDK_WAN_WINDOW) && NIFI2_PRIVATE_WMSDK_WAN_WINDOW
            || o.profile == "mpds-us-room-p16-w32-v1"
#else
            || o.profile == "mpds-us-debugmenu-room-p16-v1"
#endif
#endif
        ) && o.romSha == mpds_room::PatchedSha &&
        o.setSha == SetSha[o.players-2] && o.savedAid == Aid[o.players-2][o.seat-1];
}
inline bool matches(const SavedRoomOffer& a, const SavedRoomOffer& b) {
    return a.profile == b.profile && a.romSha == b.romSha && a.setSha == b.setSha &&
        a.epoch == b.epoch && a.players == b.players && a.bufferTicks == b.bufferTicks;
}
} // namespace saved_room
} // namespace melonDS::wm_sdk
