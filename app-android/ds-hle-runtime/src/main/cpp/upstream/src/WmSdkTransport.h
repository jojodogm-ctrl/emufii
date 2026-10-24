// Semantic WM transport boundary. No NDS/guest pointers, scheduling or IO.
// A replay implementation must return observed results, not construct peers.
#ifndef MELONDS_WM_SDK_TRANSPORT_H
#define MELONDS_WM_SDK_TRANSPORT_H
#include <array>
#include <cstdint>
#include <optional>
#include <exception>
#include <vector>
#include "WmSdkMessageBus.h"
#include "WmSdkSavedRoom.h"

namespace melonDS::wm_sdk {
using Token = std::uint64_t;
using Mac = std::array<std::uint8_t, 6>;
using Bss = std::array<std::uint8_t, 192>;
using ConnectData = std::array<std::uint8_t, 24>;
// Compiled SDK window capability, before any MP/game data. This is neither a
// saved association nor a delivery receipt. Unsupported adapters reject.
enum class SharingWindowStatus : std::uint8_t { Pending, Ready, Rejected };
// Negotiated control deferral; no operation or guest callback was completed.
struct AssociationPending : std::exception {
    const char* what() const noexcept override { return "association cohort pending"; }
};
enum class SharingDrainState : std::uint8_t { Idle, Pending, Ready, Rejected };
inline std::uint16_t get16(const std::uint8_t* p) { return std::uint16_t(p[0] | (p[1] << 8)); }
inline void put16(std::uint8_t* p, std::uint16_t v) { p[0] = std::uint8_t(v); p[1] = std::uint8_t(v >> 8); }

struct Advertisement { Token parent = 0; Bss bss{}; };
struct Event {
    enum class Kind { Connected, Disconnected, ConnectFailed } kind = Kind::Connected;
    Token peer = 0;
    bool toParent = false;
    std::uint16_t aid = 0, reason = 0;
    Mac mac{};
    ConnectData data{};
    Bss bss{};
};

namespace net { class Remote; }
class Transport {
public:
    using Bus = wm_prototype::MessageBus;
    virtual ~Transport() = default;
    // Trusted owner discovery only. Tapes/local media must not impersonate a
    // broker-confirmed remote checkpoint boundary.
    virtual net::Remote* checkpointRemote() { return nullptr; }
    // Component support only; never authorizes a whole-room/core checkpoint.
    // Remote's live stream cannot currently establish a coordinated cut.
    virtual bool supportsMediumSnapshot() const { return false; }
    // Owner-provided scheduling metadata, not a guest SDK operation. A tape
    // already binds each operation to its actual frame; replay needs no medium
    // clock because it returns that frame's recorded results. No guest pointer.
    virtual bool advanceFrame(Token, std::uint64_t) { return true; }
    // Owner-verified port readiness for future arrivals in an active session.
    // No queued eligibility, guest SDK state, ACK or completion is changed.
    virtual bool setSubframePorts(Token, std::uint16_t) { return false; }
    // A delayed scan keeps ownership of the original request. nullopt is
    // pending, never an empty advertisement or a successful SDK callback.
    // Local/replay adapters retain their existing synchronous implementation.
    virtual std::optional<Advertisement> pollScan(Token token,std::uint16_t channel,
                                                 const Mac& filter,std::uint64_t) {
        return scan(token,channel,filter);
    }
    virtual Token open(const Mac& mac) = 0;
    virtual void close(Token token) = 0;
    virtual SharingWindowStatus claimSharingWindow(Token,unsigned,unsigned) {
        return SharingWindowStatus::Rejected;
    }
    // Local owner cleanup after a deferred native request completes/cancels.
    // A Pending reply admitted no wire operation; there is no remote undo.
    virtual void cancelAssociationWait(Token) {}
    // Private negotiated fence. Masks describe owned descriptors, never guest
    // pointers or delivery evidence. The default cannot impersonate a fence.
    virtual SharingDrainState sharingDrain(Token,bool,std::uint16_t,std::uint16_t,bool) {
        return SharingDrainState::Rejected;
    }
    // Explicit saved-room opt-in, independent of guest connect/start callbacks.
    // room is the already-open real seat-1 endpoint. Every real seat must offer.
    // Pending never installs membership. Ready includes the fresh MP session;
    // do not call startMp to import it. Duplicate offers reject the entire room;
    // use pollSavedRoom for retries. A rejected attempt is terminal for these
    // endpoint handles: close/reopen before retrying. First offers for new rooms
    // require strictly increasing nonzero offer epochs per registry lifetime.
    // These defaults keep existing MKDS transports/tapes unchanged and fail closed.
    virtual SavedRoomResult adoptSavedRoom(Token, Token, const SavedRoomOffer&) {
        return {SavedRoomState::Rejected, SavedRoomReason::Unsupported};
    }
    virtual SavedRoomResult pollSavedRoom(Token) {
        return {SavedRoomState::Rejected, SavedRoomReason::Unsupported};
    }
    // Read-only discovery by the full offer identity. Pending with room==0 means
    // no matching real parent has offered yet; Pending with room!=0 resolves
    // that parent's actual token for adoptSavedRoom. Lookup never offers a seat
    // or exposes a session. Poll freely; rejected/full/stale rooms fail closed.
    virtual SavedRoomResult lookupSavedRoom(Token, const SavedRoomOffer&) {
        return {SavedRoomState::Rejected, SavedRoomReason::Unsupported};
    }
    virtual bool advertise(Token token, const Bss& bss, std::uint16_t maxChildren) = 0;
    virtual Advertisement scan(Token token, std::uint16_t channel, const Mac& filter) = 0;
    virtual bool connect(Token child, Token parent, const Bss& observed, const ConnectData& data) = 0;
    virtual std::uint16_t disconnect(Token token, std::uint16_t mask) = 0;
    // A false pop still has a meaningful fault output; event is valid only on true.
    virtual bool pop(Token token, Event& event, bool& fault) = 0;
    virtual Bus::SessionResult startMp(Token token) = 0;
    virtual void stopMp(Token token) = 0;
    // Atomic owner stop plus ALL unconsumed, genuine post-leave receipts.
    // nullopt means unsupported/rejected with NO mutation, not an empty stop.
    // A present empty vector means stopped with no outstanding receipts.
    // Preserve message identity, source epoch, outcome and prior ACK mask.
    static constexpr std::size_t MaxStopMpReceipts = wire_window::StopReceipts;
    virtual std::optional<std::vector<Bus::Completion>> stopMpWithReceipts(Token) {
        return std::nullopt;
    }
    // Opt in before MP starts: retain genuine receipts when a peer tears this
    // endpoint down. Retention lasts until close; snapshots must account for it
    // or reject the cut. Legacy endpoints keep their existing stop semantics.
    virtual bool enableReceiptRetention(Token) { return false; }
    // Read-only participation query, NOT a DATA receipt or radio-poll ACK.
    // Match the caller's live MP generation; unsupported/stale returns nullopt.
    virtual std::optional<std::uint16_t> mpReadyMask(Token, Bus::Session) { return std::nullopt; }
    // Explicit subscription opt-in. A remote adapter may perform ONE control
    // exchange per MP generation, then consume real change notifications.
    // Local registries can answer directly. This never acknowledges game data.
    virtual std::optional<std::uint16_t> watchMpReadyMask(Token t, Bus::Session expected) {
        return mpReadyMask(t,expected);
    }
    virtual Bus::SendResult send(Token token, unsigned mask, unsigned port,
        const std::array<std::uint8_t,512>& data, unsigned length,
        Bus::Sequence wireSequence = 0xffff) = 0;
    // Explicit negotiated emulator extension, independent of guest ports.
    // Unsupported adapters reject without sending. The runtime must negotiate
    // a complete v5 cohort before calling, and own/drain every real receipt.
    virtual Bus::SendResult sendBlock(Token,unsigned,const std::array<std::uint8_t,512>&,unsigned) {
        return {Bus::Status::SourceNotReady,{}};
    }
    // Opt-in terminal send: atomically admit a send and retain its REAL result
    // for completion(), even if the peer disconnects before the owner polls.
    // At most one armed/unconsumed terminal send per endpoint (Backpressure on
    // another); ordinary send/completion behavior is otherwise unchanged.
    // Ok means admission only. Boot requires the matching source/send_id,
    // Delivered, and acknowledged == targets; disconnect never implies ACK.
    // Explicit stopMp/reset, close, successful reconnect/startMp, and snapshot
    // import discard the opt-in/receipt. A receipt is volatile and polled once.
    // Armed/retained receipts are not snapshot-compatible. Unsupported adapters
    // fail closed WITHOUT sending; Remote requires the explicit broker opcode
    // (an old broker rejects it, never silently downgrades to ordinary send).
    virtual Bus::SendResult sendTerminal(Token, unsigned, unsigned,
        const std::array<std::uint8_t,512>&, unsigned, Bus::Sequence = 0xffff) {
        return {Bus::Status::SourceNotReady,{}};
    }
    // domain is an in/out argument, including Empty/SourceNotReady results.
    virtual Bus::DeliveryResult take(Token token, Token& domain) = 0;
    virtual Bus::Status acknowledge(Token token, Token domain, const Bus::DeliveryToken& delivery) = 0;
    virtual Bus::CompletionResult completion(Token token) = 0;
};
} // namespace melonDS::wm_sdk
#endif
