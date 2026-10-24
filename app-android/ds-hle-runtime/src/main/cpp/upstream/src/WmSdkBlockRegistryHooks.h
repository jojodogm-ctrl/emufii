#pragma once
// Relative native SDK sites, after the entire registry/dependency graph passes.
// Commit points are before RestoreIrq: a later nested registration must not be
// revoked by an older removal which is only now returning to its caller.
namespace melonDS::wm_sdk::sharing::sdk300::response::registry {
enum class HookKind {Enter,RegisterCommit,RemoveHeadCommit,RemoveNextCommit,Exit};
struct Hook {unsigned node,offset;HookKind kind;std::uint32_t expected;bool restoreIrqCall=false;};
inline constexpr std::array<Hook,10> Hooks{{
    {0,0x00,HookKind::Enter,0xe92d40f0},
    {1,0x00,HookKind::Enter,0xe92d41f0},
    {0,0x48,HookKind::RemoveHeadCommit,0xe1a00004},
    {0,0x80,HookKind::RemoveNextCommit,0,true},
    {1,0xb4,HookKind::RegisterCommit,0xe1a00004},
    {0,0x30,HookKind::Exit,0xe8bd80f0},
    {0,0x58,HookKind::Exit,0xe8bd80f0},
    {0,0x8c,HookKind::Exit,0xe8bd80f0},
    {0,0xb0,HookKind::Exit,0xe8bd80f0},
    {1,0xc0,HookKind::Exit,0xe8bd81f0},
}};
} // namespace melonDS::wm_sdk::sharing::sdk300::response::registry
