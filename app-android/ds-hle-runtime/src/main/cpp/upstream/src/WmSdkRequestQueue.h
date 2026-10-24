// Independently written, owner-thread-only request staging. No guest pointers
// are dereferenced here; submitted request words are immutable owned snapshots.
#ifndef MELONDS_WM_SDK_REQUEST_QUEUE_H
#define MELONDS_WM_SDK_REQUEST_QUEUE_H
#include <array>
#include <cstddef>
#include <cstdint>
namespace melonDS::wm_sdk {
template<std::size_t WordCount> struct RequestOf {
    static_assert(WordCount==12 || WordCount==16);
    static constexpr std::uint64_t Bytes=WordCount*4;
    std::uint32_t address = 0;
    std::array<std::uint32_t,WordCount> words{};
    std::uint64_t dwell = 0;
};
template<std::size_t WordCount> class RequestQueueOf {
    using Request=RequestOf<WordCount>;
    std::array<Request,16> requests{};
    unsigned head = 0, count = 0;
public:
    enum class Result { Ok, Full, Overlap };
    unsigned size() const { return count; }
    Result push(const Request& request) {
        for (unsigned i=0;i<count;++i) {
            const auto a=requests[(head+i)%requests.size()].address;
            if (std::uint64_t(a)<std::uint64_t(request.address)+Request::Bytes
                && std::uint64_t(request.address)<std::uint64_t(a)+Request::Bytes) return Result::Overlap;
        }
        if (count==requests.size()) return Result::Full;
        requests[(head+count)%requests.size()]=request; ++count;
        return Result::Ok;
    }
    bool pop(Request& request) {
        if (!count) return false;
        request=requests[head]; requests[head]={}; head=(head+1)%16u; --count;
        return true;
    }
};
// Existing runtime/checkpoint v1 stays exactly twelve words. A separately
// identified newer ABI can use the sixteen-word queue without dropping its
// StartMP temporary parameters at +30..3F. This alone admits no new title.
using Request=RequestOf<12>;
using RequestQueue=RequestQueueOf<12>;
using ExtendedRequest=RequestOf<16>;
using ExtendedRequestQueue=RequestQueueOf<16>;
}
#endif
