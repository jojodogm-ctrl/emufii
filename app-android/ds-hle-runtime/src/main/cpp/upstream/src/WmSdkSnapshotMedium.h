#ifndef MELONDS_WM_SDK_SNAPSHOT_MEDIUM_H
#define MELONDS_WM_SDK_SNAPSHOT_MEDIUM_H
#include "WmSdkTransport.h"

namespace melonDS::wm_sdk::snapshot {
using Bus = wm_prototype::MessageBus;
template<class A> void session(A& a, Bus::Session& s, bool empty = false) {
    a(s.aid); a(s.epoch);
    require(s.aid < 8 && (s.epoch || (empty && s.aid == 0)));
}
template<class A> void message(A& a, Bus::Message& m) {
    session(a,m.source); a(m.send_id); a(m.sequence); a(m.port); a(m.targets); a(m.wire_sequence);
    require(m.send_id && m.port < 16 && m.targets && m.targets <= 255 &&
            (m.source.aid == 0 ? !(m.targets & 1) : m.targets == 1));
}
inline bool sameMessage(const Bus::Message& a, const Bus::Message& b) {
    return a.source == b.source && a.send_id == b.send_id && a.sequence == b.sequence &&
           a.port == b.port && a.targets == b.targets && a.wire_sequence == b.wire_sequence;
}
template<class A> void completion(A& a, Bus::Completion& c) {
    message(a,c.message); a(c.acknowledged);
    enumeration(a,c.outcome,2); enumeration(a,c.reason,4);
    require(!(c.acknowledged & ~c.message.targets));
}
inline void terminal(const Bus::Completion& c) {
    if (c.outcome == Bus::Outcome::Delivered)
        require(c.acknowledged == c.message.targets && c.reason == Bus::Reason::None);
    else {
        require(c.acknowledged != c.message.targets);
        if (c.outcome == Bus::Outcome::Cancelled)
            require(c.reason == Bus::Reason::SenderLeft || c.reason == Bus::Reason::SenderReset);
        else require(c.reason == Bus::Reason::RecipientLeft || c.reason == Bus::Reason::RecipientReset);
    }
}
template<class A> void delivery(A& a, Bus::Delivery& d) {
    session(a,d.token.source); session(a,d.token.recipient); a(d.token.send_id);
    require(d.token.send_id && (d.token.source.aid == 0 ? d.token.recipient.aid != 0 : d.token.recipient.aid == 0));
    a(d.sequence); a(d.port); a(d.wire_sequence); require(d.port < 16);
    auto n = static_cast<std::uint16_t>(d.payload.size()); a(n); require(n && n <= 512);
    auto bytes = d.payload.bytes();
    for (unsigned i = 0; i < n; ++i) a(bytes[i]);
    if constexpr (A::Reading) require(Bus::Payload::from_owned(bytes,n,d.payload));
}
template<class A> void event(A& a, Event& e) {
    enumeration(a,e.kind,2); a(e.peer); a(e.toParent); a(e.aid); a(e.reason);
    a(e.mac); a(e.data); a(e.bss); require(e.peer && e.aid > 0 && e.aid < 8);
}
template<class A> void offer(A& a, SavedRoomOffer& o) {
    a.string(o.profile,32); a.string(o.romSha,64); a.string(o.setSha,64);
    a(o.epoch); a(o.players); a(o.seat); a(o.bufferTicks); a(o.savedAid);
    require(o.players >= 2 && o.players <= 8 && o.seat >= 1 && o.seat <= o.players &&
            o.savedAid < 8 && saved_room::valid(o));
}
template<class A> void saved(A& a, SavedRoomResult& s) {
    enumeration(a,s.state,2); enumeration(a,s.reason,10);
    a(s.room); a(s.epoch); a(s.members); session(a,s.session,true);
    require((s.state == SavedRoomState::Rejected) == (s.reason != SavedRoomReason::None));
    if (s.state == SavedRoomState::Ready)
        require(s.room && s.epoch && s.session.epoch &&
                s.members >= 3 && s.members <= 255 && !(s.members & (s.members + 1u)) &&
                (s.members & (1u << s.session.aid)));
    else require(!s.members && s.session == Bus::Session{});
}

// Internal codec, invoked only with the owning registry frozen. Inactive slot
// remnants are canonicalized: only live semantic fields are encoded.
class MediumCodec {
public:
    struct State {
        std::array<Bus::Endpoint,8> endpoints{};
        std::array<Bus::SendSlot,Bus::MaxInflight> sends{};
        std::uint64_t next = 1;
    };
    // Fill caller-owned storage: a WAN64 medium is larger than the standalone
    // WASM broker's256KiB stack. A by-value aggregate creates a temporary even
    // when the registry's final State is already heap-owned.
    static bool supported(const Bus& bus) noexcept { return !bus.block_used_; }
    static void capture(const Bus& bus, State& s) {
        require(!bus.block_used_,Status::Unsupported);
        s.endpoints=bus.endpoints_;s.sends=bus.sends_;s.next=bus.next_send_id_;
    }
    static void install(Bus& bus, const State& s) noexcept {
        bus.block_used_ = false;
        bus.endpoints_ = s.endpoints; bus.sends_ = s.sends; bus.next_send_id_ = s.next;
    }
    template<class A> static void fields(A& a, State& s) {
        a(s.next);
        for (auto& e : s.endpoints) {
            a(e.epoch); a(e.live); a(e.ready); count(a,e.inflight,Bus::MaxDeliveriesPerEndpoint); a(e.next_sequence);
            for (auto& c : e.completions) {
                bool used = c.event.message.send_id != 0; a(used);
                if (!used) { if constexpr (A::Reading) c = {}; continue; }
                completion(a,c.event); a(c.ready); a(c.last_ack_at);
            }
        }
        for (auto& slot : s.sends) {
            a(slot.used); if (!slot.used) continue;
            message(a,slot.message); a(slot.pending); a(slot.taken); a(slot.eligible_at);
            count(a,slot.completion_index,Bus::MaxCompletionsPerEndpoint-1);
            // Epochs outside targets may be leftovers from slot reuse.
            for (unsigned i = 0; i < 8; ++i)
                if (slot.message.targets & (1u << i)) a(slot.recipient_epochs[i]);
            auto n = static_cast<std::uint16_t>(slot.payload.size()); a(n); require(n && n <= 512);
            auto bytes = slot.payload.bytes();
            for (unsigned i = 0; i < n; ++i) a(bytes[i]);
            if constexpr (A::Reading) require(Bus::Payload::from_owned(bytes,n,slot.payload));
        }
    }
    static void validate(const State& s) {
        std::array<unsigned,8> inflight{};
        std::array<std::array<bool,Bus::MaxCompletionsPerEndpoint>,8> referenced{};
        std::array<std::uint64_t,Bus::MaxInflight> ids{};
        unsigned countIds = 0;
        for (unsigned aid = 0; aid < 8; ++aid) {
            const auto& e = s.endpoints[aid];
            require(!e.live || e.epoch); require(!e.ready || e.live);
            for (const auto& slot : e.completions) {
                const auto& c = slot.event;
                if (!c.message.send_id) { require(!slot.ready); continue; }
                require(c.message.source.aid == aid && c.message.source.epoch <= e.epoch);
                require(!s.next || c.message.send_id < s.next);
                for (unsigned i = 0; i < countIds; ++i) require(ids[i] != c.message.send_id);
                ids[countIds++] = c.message.send_id;
                if (slot.ready) terminal(c);
                else require(c.outcome == Bus::Outcome::Delivered && c.reason == Bus::Reason::None && !c.acknowledged);
            }
        }
        for (const auto& slot : s.sends) {
            if (!slot.used) continue;
            const auto& m = slot.message; const auto& source = s.endpoints[m.source.aid];
            require(source.live && source.ready && source.epoch == m.source.epoch);
            require(slot.pending && !(slot.pending & ~m.targets) && !(slot.taken & ~slot.pending));
            const auto& c = source.completions[slot.completion_index];
            require(!c.ready && sameMessage(m,c.event.message));
            require(!referenced[m.source.aid][slot.completion_index]);
            referenced[m.source.aid][slot.completion_index] = true;
            for (unsigned aid = 0; aid < 8; ++aid) {
                const auto& dest = s.endpoints[aid];
                if (m.targets & (1u << aid)) require(slot.recipient_epochs[aid] && slot.recipient_epochs[aid] <= dest.epoch);
                if (!(slot.pending & (1u << aid))) continue;
                require(dest.live && dest.ready && slot.recipient_epochs[aid] == dest.epoch);
                ++inflight[aid];
            }
        }
        for (unsigned aid = 0; aid < 8; ++aid) {
            require(inflight[aid] == s.endpoints[aid].inflight);
            for (unsigned i = 0; i < Bus::MaxCompletionsPerEndpoint; ++i) {
                const auto& c = s.endpoints[aid].completions[i];
                require(referenced[aid][i] == (c.event.message.send_id && !c.ready));
            }
        }
    }
};
} // namespace melonDS::wm_sdk::snapshot
#endif
