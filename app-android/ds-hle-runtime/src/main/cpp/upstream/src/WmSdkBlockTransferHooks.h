#pragma once
namespace melonDS::wm_sdk::sharing::sdk300::response::transfer {
enum class HookKind {QueueCommit,Activate,CompleteEnter,CompleteCommit,CompleteExit};
struct Hook {unsigned node,offset;HookKind kind;std::uint32_t expected;};
inline constexpr std::array<Hook,6> Hooks{{
    {44,0x70,HookKind::QueueCommit,0xe1a00004u},
    {1,0x1c0,HookKind::Activate,0xe5980000u},
    {10,0x0,HookKind::CompleteEnter,0xe92d4010u},
    {10,0x54,HookKind::CompleteCommit,0xe351000bu},
    {10,0x34,HookKind::CompleteExit,0x18bd8010u},
    {10,0xd8,HookKind::CompleteExit,0xe8bd8010u},
}};
}
