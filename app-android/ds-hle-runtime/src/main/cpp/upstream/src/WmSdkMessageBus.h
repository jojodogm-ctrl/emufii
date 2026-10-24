#ifndef WM_MESSAGE_BUS_PROTOTYPE_H
#define WM_MESSAGE_BUS_PROTOTYPE_H

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <mutex>
#include "WmSdkSnapshot.h"
#include "WmSdkWireWindow.h"

// Independently written SDK message bus. Standard C++17 library only.
// No emulator, ROM ABI, guest memory, wire protocol, or Nintendo/libntr code.
//
// Contract:
// * Exactly eight AIDs: parent 0, children 1..7; ports are 0..15. Parent sends
//   to a nonempty child mask (at most seven recipients), never itself. A child
//   must specify mask 1 (parent only). Invalid routing is rejected, not coerced.
// * join creates a new, initially unready session. make_ready is one-way;
//   withdraw readiness with reset or leave. reset retires the old session and
//   creates a new unready one. AID epochs never repeat during this bus lifetime.
// * send accepts only host-owned std::array bytes, with an optional used length.
//   The bus copies 1..512 bytes and exposes read-only payload snapshots. No raw
//   pointer or guest address input exists. Empty sends are errors, not no-ops.
//   Keep the input array alive and unchanged until send returns. Status::Ok on
//   send means admission only. Always check status before reading result data.
// * Admission checks every destination and reserves ALL resources atomically:
//   one global send slot, one delivery credit per recipient, and one completion
//   slot at the source. Rejection changes no queue, send ID, or sequence. There
//   is no eviction, partial admission, or successful disconnected/unready send.
// * peek sees the oldest untaken delivery without changing it. take marks it
//   taken, but retains its payload and delivery credit until ACK/termination.
//   The caller retains the returned token/snapshot. ACK requires take first,
//   the exact recipient session, source session, and send ID. Repeated, mismatched,
//   or stale ACKs fail without an event. ACKs may arrive out of order.
// * Optional caller-supplied times are internal metadata only. send's eligibleAt
//   defaults to zero; peek/take select the original FIFO head before comparing
//   it to now, returning Empty without mutation if it is too early. No overtaking.
//   ACK records the maximum successful ACK time (default zero). Delivered events
//   for a current source become pollable only at that time. Failed/cancelled and
//   retired-source events remain immediately drainable for cleanup. Polling now
//   defaults to UINT64_MAX, preserving untimed callers. No clock is advanced here.
// * Completion is Delivered only after ALL intended recipients ACK. A source
//   leave/reset cancels its sends; an unacknowledged recipient leave/reset fails
//   the ENTIRE send and invalidates all remaining tokens. Prior ACKs are recorded
//   and cannot be undone. An already-ACKed recipient leaving has no further
//   effect. Completed events are terminal even if a session subsequently leaves.
// * Terminal events survive leave/reset/rejoin and are polled with their ORIGINAL
//   source session, including after leave. Reserved and unpolled completions both
//   consume source capacity across epochs. Drain old sessions to recover credit.
//   poll_completion returns the lowest send ID among time-eligible ready events
//   for that exact session; it does not wait for an earlier pending or future
//   completion. It removes that event.
// * Sequence is uint16_t, per source session and port, starts at zero, and wraps
//   modulo 65536. Send IDs are nonzero uint64_t, unique across this bus lifetime,
//   independent of sequence and epoch. ID/epoch exhaustion rejects, never reuses.
//   This is a logical sequence; wire/SDK sequence encoding belongs to the adapter.
// * Limits: EndpointCount * MaxCompletionsPerEndpoint active sends globally and
//   deliveries per recipient. Each source retains MaxCompletionsPerEndpoint
//   pending-or-unpolled completions across epochs (default8; private WAN32).
// * Fixed storage; no allocation, callbacks, worker threads, or external runtime
//   dependencies. Every operation uses one mutex with bounded scans/copies and
//   no user code under the lock. Each console calls/polls on its own thread;
//   the bus cannot enforce thread affinity. Mutex scheduling is not real-time.
//
// Limitations: in-process, volatile, bus-local identities (not authentication),
// no timeout/retry/redelivery, no persistence, no hardware timing or protocol
// compatibility claims. ACK means the caller explicitly asserts delivery; the
// bus cannot verify guest execution. Abandoned tokens/events deliberately cause
// backpressure until ACK, session termination, or completion polling. Delivery
// copies remain readable after termination but their tokens become invalid.
// Construct/destroy only when no callers are using the bus; object replacement
// starts a different identity domain. Caller-owned return-value copies are not
// included in the bus's fixed memory bound.
namespace wm_prototype {

class MessageBus {
public:
    static constexpr std::size_t EndpointCount = 8;
    static constexpr std::size_t PortCount = 16;
    // Emulator-private lane. Ordinary send() never accepts this port. It shares
    // actual admission credits, IDs, delivery ownership and terminal receipts.
    static constexpr unsigned BlockPort = 0xff;
    static constexpr std::size_t MaxPayload = 512;
    static constexpr std::size_t MaxCompletionsPerEndpoint = melonDS::wm_sdk::wire_window::Credits;
    // The room must admit eight messages from EACH endpoint, not twelve
    // shared by the full roster. The parent's receive credits cover fan-in.
    static constexpr std::size_t MaxInflight = EndpointCount * MaxCompletionsPerEndpoint;
    static constexpr std::size_t MaxDeliveriesPerEndpoint = MaxInflight;

    using Epoch = std::uint64_t;
    using SendId = std::uint64_t;
    using Mask = std::uint16_t;
    using Sequence = std::uint16_t;

    enum class Status {
        Ok, Empty, InvalidAid, StaleSession, AlreadyJoined, InvalidPort,
        InvalidTargets, InvalidPayload, SourceNotReady, MissingRecipient,
        RecipientNotReady, Backpressure, InvalidToken, IdExhausted
    };
    enum class Outcome { Delivered, Cancelled, Failed };
    enum class Reason { None, SenderLeft, SenderReset, RecipientLeft, RecipientReset };

    struct Session {
        std::uint8_t aid = 0;
        Epoch epoch = 0;
        friend bool operator==(Session a, Session b) noexcept {
            return a.aid == b.aid && a.epoch == b.epoch;
        }
        friend bool operator!=(Session a, Session b) noexcept { return !(a == b); }
    };
    struct SessionResult {
        Status status = Status::StaleSession;
        Session session{};
    };
    struct Message {
        Session source{};
        SendId send_id = 0;
        Sequence sequence = 0;
        std::uint8_t port = 0;
        Mask targets = 0;
        // Optional adapter metadata; logical sequence above keeps its original
        // per-bus contract. The guest SDK owns a different persistent counter.
        Sequence wire_sequence = 0xffff;
    };
    struct SendResult {
        Status status = Status::Backpressure;
        Message message{};
    };
    struct DeliveryToken {
        Session source{};
        Session recipient{};
        SendId send_id = 0;
    };
    class Payload {
    public:
        std::size_t size() const noexcept { return size_; }
        const std::array<std::uint8_t, MaxPayload>& bytes() const noexcept {
            return bytes_;
        }
        // Replay/import boundary: bounded owned bytes only, no object-layout or
        // guest-pointer deserialization. Failure leaves the output unchanged.
        static bool from_owned(const std::array<std::uint8_t, MaxPayload>& bytes,
                               std::size_t length, Payload& output) noexcept {
            if (length == 0 || length > MaxPayload) return false;
            Payload value;
            std::copy_n(bytes.begin(), length, value.bytes_.begin());
            value.size_ = static_cast<std::uint16_t>(length);
            output = value;
            return true;
        }
    private:
        friend class MessageBus;
        std::array<std::uint8_t, MaxPayload> bytes_{};
        std::uint16_t size_ = 0;
    };
    struct Delivery {
        DeliveryToken token{};
        Sequence sequence = 0;
        std::uint8_t port = 0;
        Payload payload{};
        Sequence wire_sequence = 0xffff;
    };
    struct DeliveryResult {
        Status status = Status::Empty;
        Delivery delivery{};
    };
    struct Completion {
        Message message{};
        Mask acknowledged = 0;
        Outcome outcome = Outcome::Delivered;
        Reason reason = Reason::None;
    };
    struct CompletionResult {
        Status status = Status::Empty;
        Completion completion{};
    };

    MessageBus() = default;
    MessageBus(const MessageBus&) = delete;
    MessageBus& operator=(const MessageBus&) = delete;

    SessionResult join(unsigned aid) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (aid >= EndpointCount) return {Status::InvalidAid, {}};
        auto& endpoint = endpoints_[aid];
        if (endpoint.live) return {Status::AlreadyJoined, {}};
        if (endpoint.epoch == (std::numeric_limits<Epoch>::max)())
            return {Status::IdExhausted, {}};
        ++endpoint.epoch;
        endpoint.live = true;
        endpoint.ready = false;
        endpoint.next_sequence.fill(0);
        endpoint.block_sequence = 0;
        return {Status::Ok, {static_cast<std::uint8_t>(aid), endpoint.epoch}};
    }

    Status make_ready(Session session) {
        std::lock_guard<std::mutex> lock(mutex_);
        const auto status = current(session);
        if (status != Status::Ok) return status;
        endpoints_[session.aid].ready = true;
        return Status::Ok;
    }

    Status leave(Session session) {
        std::lock_guard<std::mutex> lock(mutex_);
        const auto status = current(session);
        if (status != Status::Ok) return status;
        retire(session, false);
        endpoints_[session.aid].live = false;
        endpoints_[session.aid].ready = false;
        return Status::Ok;
    }

    SessionResult reset(Session session) {
        std::lock_guard<std::mutex> lock(mutex_);
        const auto status = current(session);
        if (status != Status::Ok) return {status, {}};
        auto& endpoint = endpoints_[session.aid];
        if (endpoint.epoch == (std::numeric_limits<Epoch>::max)())
            return {Status::IdExhausted, {}};
        retire(session, true);
        ++endpoint.epoch;
        endpoint.ready = false;
        endpoint.next_sequence.fill(0);
        endpoint.block_sequence = 0;
        return {Status::Ok, {session.aid, endpoint.epoch}};
    }

    template<std::size_t N>
    SendResult send(Session source, unsigned targets, unsigned port,
                    const std::array<std::uint8_t, N>& bytes,
                    std::size_t length = N, Sequence wire_sequence = 0xffff,
                    std::uint64_t eligibleAt = 0) {
        return sendOwned(source,targets,port,bytes,length,wire_sequence,eligibleAt,false);
    }

    template<std::size_t N>
    SendResult send_block(Session source,unsigned targets,const std::array<std::uint8_t,N>& bytes,
                          std::size_t length=N,std::uint64_t eligibleAt=0) {
        return sendOwned(source,targets,BlockPort,bytes,length,0xffff,eligibleAt,true);
    }
private:
    template<std::size_t N>
    SendResult sendOwned(Session source,unsigned targets,unsigned port,const std::array<std::uint8_t,N>& bytes,
                         std::size_t length,Sequence wire_sequence,std::uint64_t eligibleAt,bool block) {
        std::lock_guard<std::mutex> lock(mutex_);
        const auto status = current(source);
        if (status != Status::Ok) return {status, {}};
        if (block ? port != BlockPort : port >= PortCount) return {Status::InvalidPort, {}};
        if (targets == 0 || targets > 0xffu ||
            (source.aid == 0 ? (targets & 1u) != 0 : targets != 1u))
            return {Status::InvalidTargets, {}};
        if (length == 0 || length > N || length > MaxPayload)
            return {Status::InvalidPayload, {}};
        auto& sender = endpoints_[source.aid];
        if (!sender.ready) return {Status::SourceNotReady, {}};
        for (std::size_t aid = 0; aid < EndpointCount; ++aid) {
            if ((targets & bit(aid)) == 0) continue;
            if (!endpoints_[aid].live) return {Status::MissingRecipient, {}};
            if (!endpoints_[aid].ready) return {Status::RecipientNotReady, {}};
        }
        if (next_send_id_ == 0) return {Status::IdExhausted, {}};
        std::size_t free_send = MaxInflight;
        for (std::size_t i = 0; i < MaxInflight; ++i)
            if (!sends_[i].used) { free_send = i; break; }
        std::size_t free_completion = MaxCompletionsPerEndpoint;
        for (std::size_t i = 0; i < MaxCompletionsPerEndpoint; ++i)
            if (sender.completions[i].event.message.send_id == 0) {
                free_completion = i;
                break;
            }
        if (free_send == MaxInflight || free_completion == MaxCompletionsPerEndpoint)
            return {Status::Backpressure, {}};
        for (std::size_t aid = 0; aid < EndpointCount; ++aid)
            if ((targets & bit(aid)) != 0 &&
                endpoints_[aid].inflight == MaxDeliveriesPerEndpoint)
                return {Status::Backpressure, {}};

        // No mutation occurs before every admission check has passed.
        auto& slot = sends_[free_send];
        slot.message = {source, next_send_id_++, (block ? sender.block_sequence++ : sender.next_sequence[port]++),
                        static_cast<std::uint8_t>(port), static_cast<Mask>(targets),wire_sequence};
        slot.pending = static_cast<Mask>(targets);
        slot.taken = 0;
        slot.eligible_at = eligibleAt;
        slot.completion_index = free_completion;
        slot.payload.bytes_.fill(0);
        std::copy_n(bytes.begin(), length, slot.payload.bytes_.begin());
        slot.payload.size_ = static_cast<std::uint16_t>(length);
        for (std::size_t aid = 0; aid < EndpointCount; ++aid) {
            if ((targets & bit(aid)) == 0) continue;
            slot.recipient_epochs[aid] = endpoints_[aid].epoch;
            ++endpoints_[aid].inflight;
        }
        sender.completions[free_completion] =
            {{slot.message, 0, Outcome::Delivered, Reason::None}, false, 0};
        slot.used = true;
        if(block) block_used_ = true;
        return {Status::Ok, slot.message};
    }
public:

    DeliveryResult peek_delivery(Session recipient,
                                 std::uint64_t now = (std::numeric_limits<std::uint64_t>::max)()) {
        std::lock_guard<std::mutex> lock(mutex_);
        return delivery(recipient, false, now);
    }

    DeliveryResult take_delivery(Session recipient,
                                 std::uint64_t now = (std::numeric_limits<std::uint64_t>::max)()) {
        std::lock_guard<std::mutex> lock(mutex_);
        return delivery(recipient, true, now);
    }

    Status acknowledge(Session recipient, const DeliveryToken& token, std::uint64_t now = 0) {
        std::lock_guard<std::mutex> lock(mutex_);
        const auto status = current(recipient);
        if (status != Status::Ok) return status;
        if (token.recipient != recipient || token.send_id == 0)
            return Status::InvalidToken;
        for (auto& slot : sends_) {
            if (!slot.used || slot.message.send_id != token.send_id) continue;
            const Mask target = bit(recipient.aid);
            if (slot.message.source != token.source ||
                slot.recipient_epochs[recipient.aid] != recipient.epoch ||
                (slot.pending & target) == 0 || (slot.taken & target) == 0)
                return Status::InvalidToken;
            auto& completion = endpoints_[slot.message.source.aid].completions[slot.completion_index];
            completion.last_ack_at = (std::max)(completion.last_ack_at, now);
            slot.pending = static_cast<Mask>(slot.pending & ~target);
            slot.taken = static_cast<Mask>(slot.taken & ~target);
            --endpoints_[recipient.aid].inflight;
            if (slot.pending == 0) finish(slot, Outcome::Delivered, Reason::None);
            return Status::Ok;
        }
        return Status::InvalidToken;
    }

    CompletionResult poll_completion(Session source,
                                     std::uint64_t now = (std::numeric_limits<std::uint64_t>::max)()) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (source.aid >= EndpointCount) return {Status::InvalidAid, {}};
        // Historical sessions can drain their events, but never another epoch's.
        if (source.epoch == 0 || source.epoch > endpoints_[source.aid].epoch)
            return {Status::StaleSession, {}};
        const bool retired = current(source) != Status::Ok;
        CompletionSlot* selected = nullptr;
        for (auto& slot : endpoints_[source.aid].completions)
            if (slot.ready && slot.event.message.source == source &&
                (retired || slot.event.outcome != Outcome::Delivered || slot.last_ack_at <= now) &&
                (!selected || slot.event.message.send_id < selected->event.message.send_id))
                selected = &slot;
        if (!selected) return {Status::Empty, {}};
        CompletionResult result{Status::Ok, selected->event};
        *selected = CompletionSlot{};
        return result;
    }

private:
    friend class melonDS::wm_sdk::snapshot::MediumCodec;
    // A volatile extension generation is deliberately outside legacy snapshots.
    bool block_used_ = false;
    struct CompletionSlot {
        Completion event{}; // send_id != 0 reserves capacity even before ready.
        bool ready = false;
        std::uint64_t last_ack_at = 0;
    };
    struct Endpoint {
        Epoch epoch = 0;
        bool live = false;
        bool ready = false;
        std::size_t inflight = 0; // Includes taken, unacknowledged deliveries.
        std::array<Sequence, PortCount> next_sequence{};
        Sequence block_sequence = 0;
        std::array<CompletionSlot, MaxCompletionsPerEndpoint> completions{};
    };
    struct SendSlot {
        bool used = false;
        Message message{};
        Mask pending = 0;
        Mask taken = 0;
        std::uint64_t eligible_at = 0;
        std::size_t completion_index = 0;
        std::array<Epoch, EndpointCount> recipient_epochs{};
        Payload payload{};
    };

    static Mask bit(std::size_t aid) noexcept { return static_cast<Mask>(1u << aid); }

    // All private helpers are called with mutex_ held.
    Status current(Session session) const noexcept {
        if (session.aid >= EndpointCount) return Status::InvalidAid;
        const auto& endpoint = endpoints_[session.aid];
        return endpoint.live && session.epoch != 0 && endpoint.epoch == session.epoch
            ? Status::Ok : Status::StaleSession;
    }

    DeliveryResult delivery(Session recipient, bool take, std::uint64_t now) {
        const auto status = current(recipient);
        if (status != Status::Ok) return {status, {}};
        SendSlot* selected = nullptr;
        const Mask target = bit(recipient.aid);
        for (auto& slot : sends_)
            if (slot.used && (slot.pending & target) != 0 && (slot.taken & target) == 0 &&
                slot.recipient_epochs[recipient.aid] == recipient.epoch &&
                (!selected || slot.message.send_id < selected->message.send_id))
                selected = &slot;
        // Gate only after selecting the FIFO head, including across sources/ports.
        if (!selected || selected->eligible_at > now) return {Status::Empty, {}};
        if (take) selected->taken = static_cast<Mask>(selected->taken | target);
        return {Status::Ok, {{selected->message.source, recipient, selected->message.send_id},
                            selected->message.sequence, selected->message.port,
                            selected->payload,selected->message.wire_sequence}};
    }

    void finish(SendSlot& slot, Outcome outcome, Reason reason) {
        auto& completion = endpoints_[slot.message.source.aid].completions[slot.completion_index];
        completion.event.acknowledged =
            static_cast<Mask>(slot.message.targets & ~slot.pending);
        completion.event.outcome = outcome;
        completion.event.reason = reason;
        completion.ready = true; // Admission reserved this exact slot.
        for (std::size_t aid = 0; aid < EndpointCount; ++aid)
            if ((slot.pending & bit(aid)) != 0) --endpoints_[aid].inflight;
        slot.used = false;
    }

    void retire(Session session, bool resetting) {
        for (auto& slot : sends_) {
            if (!slot.used) continue;
            if (slot.message.source == session)
                finish(slot, Outcome::Cancelled, resetting ? Reason::SenderReset : Reason::SenderLeft);
            else if ((slot.pending & bit(session.aid)) != 0 &&
                     slot.recipient_epochs[session.aid] == session.epoch)
                finish(slot, Outcome::Failed, resetting ? Reason::RecipientReset : Reason::RecipientLeft);
        }
    }

    std::mutex mutex_;
    std::array<Endpoint, EndpointCount> endpoints_{};
    std::array<SendSlot, MaxInflight> sends_{};
    SendId next_send_id_ = 1;
};

} // namespace wm_prototype

#endif // WM_MESSAGE_BUS_PROTOTYPE_H
