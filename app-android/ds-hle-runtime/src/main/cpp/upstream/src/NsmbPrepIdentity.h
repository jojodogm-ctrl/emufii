#pragma once
#include <cstdint>
namespace melonDS::nsmb_prep {
enum class BootKind : std::uint32_t { Download=1, Cartridge=2 };
inline constexpr const char* Profile="nsmb-eu-free-preparation-v2";
inline constexpr const char* CartridgeProfile="nsmb-eu-cartridge-preparation-v1";
inline constexpr const char* GeneratedProfile="nsmb-eu-generated-cartridge-v1";
inline constexpr const char* RomSha="dd2e519a9e46134e213e6e63dd2b478d9a258627769b777baddb2316dc21b81d";
// 2026-09-20: FreeBIOS/generated settings, original receiver, natural round and
// Continue -> matched scene5 at journal10140, drained actual receipts.
// Private GAME data, not firmware; never embed this pair in a public release.
inline constexpr const char* SetSha="5bcfcbcd461aeafbf7c10c095a4ec6966af9e465c46998793a148fe3f7acb6d9";
// 2026-09-21: two actual cartridges, natural match -> Continue -> scene5,
// journal7500, ten future rows, drained real receipts. Private restore-test
// cohort only; pinning is not public gameplay certification.
inline constexpr const char* CartridgeSetSha="0d8c0d4fa62d8b256130d068b9814d6a48578d9345d3e6a095e10270b32fa96d";
// Selected-seat imports use these same immutable cohort members. This lets a
// browser receive only its own core/metadata without weakening the set pin.
struct Part {const char* name;unsigned bytes;const char* sha;};
inline constexpr Part CartridgeParts[]={
    {"nsmb-cart-prep-p1.bin",19244091,"7ea4520651d2d6da1e19646c278387a190fc8b352d7945a1e0d160b58058a6fc"},
    {"nsmb-cart-prep-p1.meta",19442,"f894a673e8d76252e70fdbf841fbd5d26bf8da84f398a6ebb29c4743fc19ff78"},
    {"nsmb-cart-prep-p2.bin",19244091,"38954b2ec4e16db52f866b949f2d30410f92d4541c30d96e8e06a5b3bd3941bc"},
    {"nsmb-cart-prep-p2.meta",19442,"c315c28daf2a40290312c4337d168a54dd9cb5202db4f27a98a89665d40c57f2"}
};
}
