// Mario Party DS room initialization. No NDS instances, network, or guest writes.
// The checked snapshots are initialization assets, NOT runtime recovery images.
#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace melonDS::mpds_room {
inline constexpr char Profile[] = "mpds-us-debugmenu-room-v1";
inline constexpr char PatchedSha[] = "07b4ac7550e5ed876dd83578d49441712bbbd8127cdd60155e4d1a87604a5af7";
inline constexpr char StockSha[] = "28ce638914e3304eab95167cd17dc1ef893d226c18f9395683d5cbdf2e35ee2f";
// Deliberately false until the MPDS ABI + saved-session ownership transfer and
// actual shared minigames are validated. Never route this profile to MKDS hooks.
inline constexpr bool GameplaySupported = false;
enum class RomKind { Other, Patched, UnsupportedParty, Invalid };
struct PreparedRom { RomKind kind; bool patchApplied; };
std::string sha256(const void* data, std::size_t size);
// Exact known USA stock -> embedded patch -> exact full output SHA256. Only
// swaps the caller's buffer AFTER validation. Never modifies the source file.
PreparedRom prepareRom(std::vector<std::uint8_t>& rom);

struct Plan {
    unsigned players = 0, seat = 0;
    unsigned localConsoles = 1;
    unsigned savedAid = 0; // association order, not necessarily seat-1
    std::string snapshotName, snapshotSha, setSha;
    std::size_t snapshotSize = 0;
};
// Separates network roster size from the ONE locally emulated console. The set
// digest binds all seat hashes in order; a selected snapshot alone is not a set.
Plan plan(unsigned players, unsigned seat);
void verifySnapshot(const Plan& expected, const std::vector<std::uint8_t>& bytes);
struct Offer {
    std::string profile, romSha, setSha;
    std::uint64_t epoch = 0; // fresh, agreed room incarnation, never from a state
    unsigned players = 0, seat = 0, bufferTicks = 0;
    unsigned savedAid = 0;
};
// Pure future ROOM INIT admission contract. No transport receipt, no peer
// synthesis, and no assertion that a guest WM session has been imported.
Offer offer(const Plan& plan, std::uint64_t epoch, unsigned bufferTicks);
void verifyRoster(const std::vector<Offer>& peers);
// Read-only native-app preflight: patches only an in-memory copy, reads only
// the selected seat's state, emits JSON, creates no emulator/socket/output ROM.
int inspectCommand(int argc, char** argv);
} // namespace melonDS::mpds_room
