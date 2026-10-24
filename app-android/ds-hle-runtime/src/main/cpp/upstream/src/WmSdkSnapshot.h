// Bounded, explicit little-endian semantic snapshots. No native object images.
#ifndef MELONDS_WM_SDK_SNAPSHOT_H
#define MELONDS_WM_SDK_SNAPSHOT_H
#include <algorithm>
#include <array>
#include <cstdint>
#include <new>
#include <string>
#include <type_traits>
#include <vector>

namespace melonDS::wm_sdk::snapshot {
static_assert(sizeof(unsigned)==4,"snapshot schema uses 32-bit unsigned fields");
using Bytes = std::vector<std::uint8_t>;
enum class Status { Ok, NotQuiescent, Unsupported, InvalidImage, WrongVersion,
                    ModeMismatch, StaleIncarnation, ResourceLimit };
enum class Kind : std::uint16_t { Registry = 1, RemoteLocal = 2 };
#if defined(NIFI2_PRIVATE_WMSDK_WAN_WINDOW) && NIFI2_PRIVATE_WMSDK_WAN_WINDOW
// WAN64 has different fixed completion/send geometry from the former WAN32
// format (0x8001/0x8002). Reject those images before decoding any payload.
inline constexpr std::uint16_t Version = 0x8041;
#else
inline constexpr std::uint16_t Version = 1;
#endif
// Registry v2 contains the enlarged bounded medium; RemoteLocal keeps its
// variable-length v1 representation, including byte-identical <=8 cuts.
inline constexpr std::uint16_t versionFor(Kind kind) { return kind == Kind::Registry ? (Version==1?2:0x8042) : Version; }
// WAN64 can hold 512 maximum-sized payloads plus the fixed registry metadata.
// Keep the existing legacy image cap when the wide flag is absent or zero.
inline constexpr std::size_t MaxImageBytes = Version==1 ? 256 * 1024 : 1024 * 1024;
inline constexpr std::size_t HeaderBytes = 28;
struct Error { Status status; };
inline void require(bool ok, Status status = Status::InvalidImage) {
    if (!ok) throw Error{status};
}
// CRC detects accidental corruption; the durable manifest must additionally
// authenticate/hash the complete image. CRC is not a security boundary.
inline std::uint32_t crc32(const std::uint8_t* bytes, std::size_t size, bool blankChecksum = false) {
    std::uint32_t crc = 0xffffffffu;
    for (std::size_t i = 0; i < size; ++i) {
        crc ^= blankChecksum && i >= 24 && i < 28 ? 0 : bytes[i];
        for (unsigned bit = 0; bit < 8; ++bit)
            crc = (crc >> 1) ^ (0xedb88320u & (0u - (crc & 1u)));
    }
    return ~crc;
}
struct Writer {
    static constexpr bool Reading = false;
    Bytes bytes;
    template<class T> void operator()(T& value) {
        static_assert(std::is_unsigned_v<T> && !std::is_same_v<T, bool>);
        require(bytes.size() <= MaxImageBytes - sizeof(T), Status::ResourceLimit);
        auto n = static_cast<std::uint64_t>(value);
        for (unsigned i = 0; i < sizeof(T); ++i) {
            bytes.push_back(static_cast<std::uint8_t>(n)); n >>= 8;
        }
    }
    void operator()(bool& value) { auto b = static_cast<std::uint8_t>(value); (*this)(b); }
    template<class T, std::size_t N> void operator()(std::array<T,N>& value) {
        for (auto& x : value) (*this)(x);
    }
    void string(std::string& value, std::size_t max) {
        require(value.size() <= max, Status::ResourceLimit);
        auto n = static_cast<std::uint16_t>(value.size()); (*this)(n);
        for (auto c : value) { auto b = static_cast<std::uint8_t>(c); (*this)(b); }
    }
};
struct Reader {
    static constexpr bool Reading = true;
    const Bytes& bytes;
    std::size_t pos = 0;
    explicit Reader(const Bytes& b) : bytes(b) { require(b.size() <= MaxImageBytes); }
    template<class T> void operator()(T& value) {
        static_assert(std::is_unsigned_v<T> && !std::is_same_v<T, bool>);
        require(sizeof(T) <= bytes.size() - pos);
        std::uint64_t n = 0;
        for (unsigned i = 0; i < sizeof(T); ++i) n |= std::uint64_t(bytes[pos++]) << (i * 8);
        value = static_cast<T>(n);
    }
    void operator()(bool& value) { std::uint8_t b = 0; (*this)(b); require(b <= 1); value = b != 0; }
    template<class T, std::size_t N> void operator()(std::array<T,N>& value) {
        for (auto& x : value) (*this)(x);
    }
    void string(std::string& value, std::size_t max) {
        std::uint16_t n = 0; (*this)(n); require(n <= max && n <= bytes.size() - pos);
        value.assign(reinterpret_cast<const char*>(bytes.data() + pos), n); pos += n;
    }
    void end() const { require(pos == bytes.size()); }
};
template<class A, class E> void enumeration(A& a, E& e, unsigned max) {
    auto n = static_cast<std::uint8_t>(e); a(n); require(n <= max); e = static_cast<E>(n);
}
template<class A> void count(A& a, std::size_t& value, std::size_t max) {
    if constexpr (!A::Reading) require(value <= max);
    auto n = static_cast<std::uint32_t>(value); a(n); require(n <= max); value = n;
}
inline Bytes seal(Writer& payload, Kind kind, std::uint64_t incarnation) {
    require(incarnation != 0 && payload.bytes.size() <= MaxImageBytes - HeaderBytes);
    Writer out;
    std::array<std::uint8_t,8> magic{{'W','M','S','N','A','P',0,0}};
    auto version = versionFor(kind); auto type = static_cast<std::uint16_t>(kind);
    auto size = static_cast<std::uint32_t>(payload.bytes.size());
    std::uint32_t crc = 0;
    out(magic); out(version); out(type); out(size); out(incarnation); out(crc);
    out.bytes.insert(out.bytes.end(), payload.bytes.begin(), payload.bytes.end());
    // Checksum covers header (with a zero CRC field) and payload.
    crc = crc32(out.bytes.data(), out.bytes.size());
    for (unsigned i = 0; i < 4; ++i) out.bytes[24+i] = static_cast<std::uint8_t>(crc >> (8*i));
    return std::move(out.bytes);
}
inline std::uint64_t unseal(Reader& r, Kind expected) {
    std::array<std::uint8_t,8> magic{};
    std::uint16_t version = 0, type = 0;
    std::uint32_t size = 0, crc = 0;
    std::uint64_t incarnation = 0;
    r(magic); r(version); r(type); r(size); r(incarnation); r(crc);
    require((magic == std::array<std::uint8_t,8>{{'W','M','S','N','A','P',0,0}}));
    require(version == versionFor(expected), Status::WrongVersion);
    require(type == static_cast<std::uint16_t>(expected));
    require(incarnation && size == r.bytes.size() - HeaderBytes);
    require(crc32(r.bytes.data(), r.bytes.size(), true) == crc);
    return incarnation;
}
template<class F> Status attempt(F&& f) {
    try { f(); return Status::Ok; }
    catch (const Error& e) { return e.status; }
    catch (const std::bad_alloc&) { return Status::ResourceLimit; }
}
class MediumCodec;
class RegistryCodec;
class RemoteCodec;
} // namespace melonDS::wm_sdk::snapshot
#endif
