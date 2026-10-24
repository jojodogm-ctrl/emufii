#pragma once
// Common GetBlock ABI: full normalized checksum and three typed calls.
// No guest code bodies or absolute addresses.
namespace melonDS::wm_sdk::sharing::sdk300::response::transfer {
inline constexpr unsigned ApiBytes=84;
inline constexpr std::uint32_t ApiCrc=0x4fc8f878u,ApiPrefixCrc=0xce13c066u;
inline constexpr std::array<Relocation,3> Relocations{{
    {48,0},
    {64,0},
    {72,44},
}};
}
