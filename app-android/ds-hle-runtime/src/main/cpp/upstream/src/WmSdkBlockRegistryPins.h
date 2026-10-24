#pragma once
// Common SDK registry roles; checksums/typed links only, no guest code or absolute address.
namespace melonDS::wm_sdk::sharing::sdk300::response::registry {
inline constexpr Relocation Relocations[]{
    {12,Dependency::Shared,13},
    {20,Dependency::Response,43},
    {36,Dependency::Shared,14},
    {68,Dependency::Response,42},
    {76,Dependency::Shared,14},
    {128,Dependency::Shared,14},
    {164,Dependency::Shared,14},
    {20,Dependency::Shared,13},
    {28,Dependency::Response,43},
    {44,Dependency::Response,42},
    {52,Dependency::Response,43},
    {160,Dependency::Shared,21},
    {176,Dependency::Shared,20},
    {184,Dependency::Shared,14},
};
inline constexpr std::array<NodePin,2> Nodes{{
    {180,0xe3a1bc6eu,0,7,0x498c1d67u}, // Remove
    {196,0x2d5dfcd8u,7,7,0xcb3800ffu}, // Register
}};
} // namespace melonDS::wm_sdk::sharing::sdk300::response::registry
