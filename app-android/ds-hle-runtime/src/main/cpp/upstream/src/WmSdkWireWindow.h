#pragma once
#include <cstddef>

// Transport credits, independent of a game's payload or SDK DataSharing depth.
// Private coherent-build experiment only: peers, broker and snapshot codecs
// must use the same capacity. Default wire/snapshot contracts remain unchanged.
namespace melonDS::wm_sdk::wire_window {
#if defined(NIFI2_PRIVATE_WMSDK_WAN_WINDOW) && NIFI2_PRIVATE_WMSDK_WAN_WINDOW
inline constexpr std::size_t Credits=64;
inline constexpr std::size_t StopReceipts=65;
inline constexpr bool Wide=true;
#else
inline constexpr std::size_t Credits=8;
inline constexpr std::size_t StopReceipts=32;
inline constexpr bool Wide=false;
#endif
static_assert(Credits+1<=StopReceipts);
}
