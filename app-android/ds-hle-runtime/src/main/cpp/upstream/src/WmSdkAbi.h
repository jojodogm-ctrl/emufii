#pragma once
// SDK-bridge ABI codec for the captured MKDS ROM. Explicit little-endian fields;
// no host pointers in guest data. Used by the opt-in service and offline tests.
// Layout evidence: MKDS ARM7 0600537C (SetMPData), 06006CBC (PortRecv),
// the traced API81 callbacks, and named libntr WM structs. The captured older
// SDK does not fill the newer maxSend/maxRecv suffix; do not copy that suffix.
// Caller owns validation of ROM revision, guest allocations and callback lease.
#include <array>
#include <cstdint>
#include <optional>

namespace wmguest {
constexpr uint32_t MainBegin = 0x02000000, MainEnd = 0x02400000;
constexpr uint16_t Tag = 10, SetMPData = 0x0f, PortSend = 0x81, PortRecv = 0x82;
using RequestBytes = std::array<uint8_t, 32>;
using CallbackBytes = std::array<uint8_t, 256>;

inline uint16_t read16(const uint8_t* p) {
    return uint16_t(p[0]) | (uint16_t(p[1]) << 8);
}
inline uint32_t read32(const uint8_t* p) {
    return uint32_t(read16(p)) | (uint32_t(read16(p + 2)) << 16);
}
inline void put16(uint8_t* p, uint16_t v) {
    p[0] = uint8_t(v); p[1] = uint8_t(v >> 8);
}
inline void put32(uint8_t* p, uint32_t v) {
    put16(p, uint16_t(v)); put16(p + 2, uint16_t(v >> 16));
}
inline bool mainRange(uint32_t address, uint32_t length) {
    // Conservative prototype accepts canonical main RAM only, not mirrors.
    return address >= MainBegin && address < MainEnd && length <= MainEnd - address;
}
inline std::optional<uint32_t> decodeFifo(uint32_t word) {
    if ((word & 0x3f) != Tag) return std::nullopt; // also rejects PXI error bit
    const uint32_t p = word >> 6;
    if ((p & 3) || !mainRange(p, 32)) return std::nullopt;
    return p;
}
inline std::optional<uint32_t> encodeFifo(uint32_t address) {
    if ((address & 3) || !mainRange(address, 256)) return std::nullopt;
    return (address << 6) | Tag;
}

struct SendRequest {
    uint32_t payload = 0, length = 0;
    uint16_t destination = 0, port = 0, priority = 0;
    uint32_t callback = 0, argument = 0;
};
template<class OwnedPayload>
inline std::optional<SendRequest> decodeSendOwned(const RequestBytes& b,OwnedPayload owns) {
    if (read16(b.data()) != SetMPData) return std::nullopt;
    SendRequest r;
    r.payload = read32(b.data() + 4); r.length = read32(b.data() + 8);
    const uint32_t dest = read32(b.data() + 12), port = read32(b.data() + 16);
    const uint32_t priority = read32(b.data() + 20);
    // Narrower than general Nintendo WM: an eight-player prototype. No truncation
    // of malformed values into a different valid AID/port/priority.
    if (r.length == 0 || r.length > 512 || !owns(r.payload, r.length)
        || dest > 0xff || port > 15 || priority > 3) return std::nullopt;
    r.destination = uint16_t(dest); r.port = uint16_t(port); r.priority = uint16_t(priority);
    r.callback = read32(b.data() + 24); r.argument = read32(b.data() + 28);
    return r;
}
inline std::optional<SendRequest> decodeSend(const RequestBytes& b) {
    if (read16(b.data()) != SetMPData) return std::nullopt;
    SendRequest r;
    r.payload = read32(b.data() + 4); r.length = read32(b.data() + 8);
    const uint32_t dest = read32(b.data() + 12), port = read32(b.data() + 16);
    const uint32_t priority = read32(b.data() + 20);
    // Narrower than general Nintendo WM: an eight-player prototype. No truncation
    // of malformed values into a different valid AID/port/priority.
    if (r.length == 0 || r.length > 512 || !mainRange(r.payload, r.length)
        || dest > 0xff || port > 15 || priority > 3) return std::nullopt;
    r.destination = uint16_t(dest); r.port = uint16_t(port); r.priority = uint16_t(priority);
    r.callback = read32(b.data() + 24); r.argument = read32(b.data() + 28);
    return r;
}

struct SendResult {
    uint16_t error = 0, destination = 0, remaining = 0, sent = 0;
    // Sequenced ports use the SDK sequence (wire sequence << 1); raw ports FFFF.
    uint16_t sdkSequence = 0xffff;
};
// ABI-only report encoder, including SDK no-op/error/cleanup reports. This does
// not claim delivery. The stricter wrapper below checks a bus delivery outcome.
inline std::optional<CallbackBytes> encodeSendReport(const SendRequest& r, const SendResult& s) {
    if (r.port > 15 || r.length > 0xffff || ((s.remaining|s.sent)&~s.destination)
        || (s.remaining&s.sent)) return std::nullopt;
    CallbackBytes b{};
    put16(b.data(), PortSend); put16(b.data()+2,s.error);
    put16(b.data()+8,0x14); put16(b.data()+10,r.port);
    put16(b.data()+12,s.destination); put16(b.data()+14,s.remaining);
    put16(b.data()+16,s.sent); put32(b.data()+20,r.payload);
    put16(b.data()+24,uint16_t(r.length)); put16(b.data()+26,s.sdkSequence);
    put32(b.data()+28,r.callback); put32(b.data()+32,r.argument);
    return b;
}
template<class OwnedPayload>
inline std::optional<CallbackBytes> encodeSendResultOwned(const SendRequest& r,const SendResult& s,
                                                  std::optional<uint16_t> effective,OwnedPayload owns) {
    // This codec cannot prove delivery; require caller's bus receipt. It at least
    // rejects internally contradictory success masks. No fabricated success.
    if (r.port > 15 || r.length == 0 || r.length > 512
        || !owns(r.payload, r.length)
        || ((s.remaining | s.sent) & ~s.destination) || (s.remaining & s.sent)
        || (effective && ((*effective & ~s.destination) || ((s.remaining|s.sent)&~*effective)))
        || (s.error == 0 && (s.remaining || s.sent != effective.value_or(s.destination)))) return std::nullopt;
    return encodeSendReport(r,s);
}
inline std::optional<CallbackBytes> encodeSendResult(const SendRequest& r, const SendResult& s,
                                                  std::optional<uint16_t> effective = std::nullopt) {
    // This codec cannot prove delivery; require caller's bus receipt. It at least
    // rejects internally contradictory success masks. No fabricated success.
    if (r.port > 15 || r.length == 0 || r.length > 512
        || !mainRange(r.payload, r.length)
        || ((s.remaining | s.sent) & ~s.destination) || (s.remaining & s.sent)
        || (effective && ((*effective & ~s.destination) || ((s.remaining|s.sent)&~*effective)))
        || (s.error == 0 && (s.remaining || s.sent != effective.value_or(s.destination)))) return std::nullopt;
    return encodeSendReport(r,s);
}

struct Receive {
    uint32_t receiveBuffer = 0, payload = 0;
    uint16_t length = 0, source = 0, target = 0, port = 0, sdkSequence = 0xffff;
};

struct ReceiveSequence {
    uint16_t callback = 0xffff, nextCell = 1;
};
inline std::optional<ReceiveSequence> planReceiveSequence(
    uint16_t port, uint16_t wireSequence, uint16_t currentCell) {
    // MKDS 06006C40..06006C90: per receiver/source/sequenced-port cell. Odd
    // means uninitialized. The first callback retains the RAW wire sequence;
    // later callbacks receive the doubled value. Do not "fix" that SDK quirk.
    // This is a pure plan: caller owns epoch reset and commits nextCell only
    // once the corresponding receive is accepted, not on peek/backpressure.
    // The finite SDK duplicate window is NOT a unique message identity.
    if (port > 15) return std::nullopt;
    if (port < 8) return ReceiveSequence{0xffff, currentCell};
    const auto doubled = uint16_t(uint32_t(wireSequence) << 1);
    if (currentCell & 1) return ReceiveSequence{wireSequence, doubled};
    if (uint16_t(currentCell - doubled) < 256) return std::nullopt;
    return ReceiveSequence{doubled, doubled};
}

inline std::optional<CallbackBytes> encodeReceive(const Receive& r) {
    if (!r.length || r.length > 512 || r.source > 7 || r.target > 7
        || r.source == r.target || r.port > 15 || !mainRange(r.payload, r.length)
        || !mainRange(r.receiveBuffer, r.length)) return std::nullopt;
    CallbackBytes b{};
    put16(b.data(), PortRecv); put16(b.data() + 4, 0x15);
    put16(b.data() + 6, r.port); put32(b.data() + 8, r.receiveBuffer);
    put32(b.data() + 12, r.payload); put16(b.data() + 16, r.length);
    put16(b.data() + 18, r.source); put16(b.data() + 26, r.sdkSequence);
    put16(b.data() + 32, r.target);
    // This captured ARM9 supplies arg (+1C), NOT the newer connected bitmap (+22).
    // Payload/receiveBuffer must remain owned until that callback has returned.
    return b;
}
} // namespace wmguest
