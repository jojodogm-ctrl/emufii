#pragma once
#include <charconv>
#include <cstring>

namespace melonDS::wm_sdk {
inline constexpr int MaxRoomSeconds = 8 * 60 * 60;
struct BrokerOptions { int port = 0, seconds = 0, players = 0; };
// Parse fully before opening a socket. In particular, no std::stoi prefix
// acceptance ("7200junk"), overflow, sign or whitespace in a lifetime.
inline bool brokerOptions(int argc, const char* const* argv, BrokerOptions& result) {
    if (argc != 4 && argc != 5) return false;
    auto number = [](const char* text, int low, int high, int& value) {
        if (!text || !*text || std::strlen(text) > 10) return false;
        for (auto p = text; *p; ++p) if (*p < '0' || *p > '9') return false;
        int parsed = 0;
        auto read = std::from_chars(text, text + std::strlen(text), parsed);
        if (read.ec != std::errc{} || read.ptr != text + std::strlen(text) || parsed < low || parsed > high) return false;
        value = parsed; return true;
    };
    BrokerOptions parsed;
    if (!number(argv[1], 1024, 65535, parsed.port) ||
        !number(argv[2], 1, MaxRoomSeconds, parsed.seconds) ||
        !argv[3] || !*argv[3]) return false;
    parsed.players = 2;
    if (argc == 5 && !number(argv[4], 2, 8, parsed.players)) return false;
    result = parsed; return true;
}
}
