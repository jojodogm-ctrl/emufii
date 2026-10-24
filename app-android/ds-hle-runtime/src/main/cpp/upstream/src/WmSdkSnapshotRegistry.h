#ifndef MELONDS_WM_SDK_SNAPSHOT_REGISTRY_H
#define MELONDS_WM_SDK_SNAPSHOT_REGISTRY_H
#include "WmSdkSession.h"
#include "WmSdkSnapshotMedium.h"
#include <memory>

namespace melonDS::wm_sdk::snapshot {
class RegistryCodec {
public:
    struct State {
        bool clocked = false;
        Token nextToken = 1;
        std::uint64_t lastSavedEpoch = 0, incarnation = 0;
        std::array<SessionRegistry::Endpoint,8> endpoints{};
        std::array<SessionRegistry::SavedRoom,8> rooms{};
        std::array<MediumCodec::State,8> media{};
    };
    static std::unique_ptr<State> capture(const SessionRegistry& registry) {
        auto s = std::make_unique<State>();
        s->clocked = registry.frameDelivery; s->incarnation = registry.transportIncarnation;
        s->nextToken = registry.nextToken; s->lastSavedEpoch = registry.lastSavedEpoch;
        s->endpoints = registry.endpoints; s->rooms = registry.savedRooms;
        for (unsigned i = 0; i < 8; ++i) MediumCodec::capture(registry.media[i],s->media[i]);
        return s;
    }
    template<class A> static void seatFields(A& a, std::array<Token,SessionRegistry::MaxSavedPlayers>& seats,
                                            unsigned players) {
        require(players >= 2 && players <= seats.size());
        // Preserve the existing four-token layout byte-for-byte for <=4
        // players. Larger rosters require a separately reviewed valid offer.
        const unsigned stored = std::max(4u,players);
        for (unsigned seat = 0; seat < seats.size(); ++seat) {
            if (seat < stored) a(seats[seat]);
            else if constexpr (A::Reading) seats[seat] = 0;
            if (seat >= players) require(!seats[seat]);
        }
    }
    template<class A> static void aidFields(A& a, SessionRegistry::SavedRoom& room) {
        require(SessionRegistry::validSavedOffer(room.offer));
        for (unsigned seat = 0; seat < room.aids.size(); ++seat) {
            // Existing <=4 images omitted AIDs. Reconstruct only that legacy
            // representation using the exact profile pins. New larger rosters
            // carry the actual offered per-seat AIDs explicitly.
            if (room.offer.players > 4 && seat < room.offer.players) a(room.aids[seat]);
            else if constexpr (A::Reading)
                room.aids[seat] = room.seats[seat] ? SessionRegistry::savedAidForSeat(room.offer,seat+1) : 0;
            if (!room.seats[seat]) { require(!room.aids[seat]); continue; }
            auto offered = room.offer; offered.seat = seat+1; offered.savedAid = room.aids[seat];
            require(SessionRegistry::validSavedOffer(offered));
        }
    }
    template<class A> static void fields(A& a, State& s) {
        a(s.clocked); a(s.nextToken); a(s.lastSavedEpoch);
        for (auto& e : s.endpoints) {
            a(e.token); if (!e.token) continue;
            a(e.parent); a(e.mac); a(e.bss); a(e.advertising);
            a(e.parentNotified); a(e.childNotified); a(e.fault); a(e.maxChildren); a(e.aid);
            a(e.count); require(e.count <= e.events.size());
            for (unsigned i = 0; i < e.count; ++i) event(a,e.events[i]);
            a(e.medium); session(a,e.mp,true); a(e.mpDomain); a(e.frame); a(e.frameKnown); saved(a,e.saved);
        }
        for (auto& room : s.rooms) {
            a(room.owner); if (!room.owner) continue;
            offer(a,room.offer); seatFields(a,room.seats,room.offer.players);
            aidFields(a,room);
            enumeration(a,room.state,2); enumeration(a,room.reason,10);
        }
        for (auto& bus : s.media) MediumCodec::fields(a,bus);
        // Optional bounded trailer: legacy cuts remain byte-identical. Older
        // readers reject this at end(), rather than silently losing receipts.
        bool receipts=false;
        for(const auto& e:s.endpoints)receipts|=e.retainReceipts||e.retiredCount;
        if constexpr(A::Reading)receipts=a.pos<a.bytes.size();
        if(receipts){
            std::uint32_t tag=0x31504352; // RCP1
            a(tag);require(tag==0x31504352,Status::WrongVersion);
            bool present=false;
            for(auto& e:s.endpoints){
                a(e.retainReceipts);a(e.retiredCount);
                require(e.retiredCount<=e.retired.size());
                present|=e.retainReceipts||e.retiredCount;
                for(unsigned i=0;i<e.retiredCount;++i)completion(a,e.retired[i]);
            }
            require(present); // one canonical representation of empty state
        }
    }
    static void validate(const State& s) {
        const auto find = [&](Token t) -> const SessionRegistry::Endpoint* {
            if (t) for (const auto& e : s.endpoints) if (e.token == t) return &e;
            return nullptr;
        };
        std::array<std::array<bool,8>,8> joined{};
        for (unsigned i = 0; i < 8; ++i) {
            const auto& e = s.endpoints[i];
            require(!e.terminalSend.send_id&&e.terminalCompletion.status!=Bus::Status::Ok,Status::Unsupported);
            require(e.retiredCount<=e.retired.size());
            require(!(e.retainReceipts||e.retiredCount)||e.token);
            if(e.retiredCount){
                require(e.retainReceipts&&e.medium==8&&e.mp==Bus::Session{});
                for(unsigned j=0;j<e.retiredCount;++j){
                    const auto& c=e.retired[j];terminal(c);
                    // The endpoint can already be unlinked (aid reset to zero).
                    // Its old source epoch lives in these receipts, not e.mp.
                    require(c.message.source==e.retired[0].message.source);
                    for(unsigned k=0;k<j;++k)require(c.message.send_id!=e.retired[k].message.send_id);
                }
            }
            if (!e.token) continue;
            require(!s.nextToken || e.token < s.nextToken);
            for (unsigned j = 0; j < i; ++j) if (s.endpoints[j].token)
                require(e.token != s.endpoints[j].token && e.mac != s.endpoints[j].mac);
            require(e.aid < 8 && e.mp.aid < 8 && e.medium <= 8 && e.maxChildren <= 7);
            if (e.advertising) {
                require(!e.parent && e.maxChildren && std::equal(e.mac.begin(),e.mac.end(),e.bss.begin()+4));
                require(get16(e.bss.data()+0x36) >= 1 && get16(e.bss.data()+0x36) <= 13);
                require(get16(e.bss.data()+0x3c) >= 16 && get16(e.bss.data()+0x3c) <= 128);
            }
            if (e.parent) {
                const auto* p = find(e.parent);
                require(p && p != &e && !p->parent && e.aid);
                require(p->advertising || e.saved.state == SavedRoomState::Ready);
                for (unsigned j = 0; j < i; ++j) require(s.endpoints[j].parent != e.parent || s.endpoints[j].aid != e.aid);
            } else require(!e.aid && !e.parentNotified && !e.childNotified);
            require(e.frameKnown || !e.frame);
            if (e.medium < 8) {
                const auto& bus = s.media[e.medium].endpoints[e.mp.aid];
                const auto* domain = find(e.mpDomain);
                require(domain && domain == &s.endpoints[e.medium]);
                require(domain->advertising || e.saved.state == SavedRoomState::Ready);
                require(e.mp.aid == e.aid && e.mp.epoch && bus.live && bus.ready && bus.epoch == e.mp.epoch);
                require(e.mpDomain == (e.parent ? e.parent : e.token));
                require(!joined[e.medium][e.mp.aid]); joined[e.medium][e.mp.aid] = true;
            } else require(e.mp == Bus::Session{} && !e.mpDomain);
            if (e.saved.state == SavedRoomState::Ready)
                require(e.saved.session == e.mp && e.saved.room == e.mpDomain && e.medium < 8);
            if (e.saved.state == SavedRoomState::Pending) require(e.saved.room && find(e.saved.room));
            // Disconnect events and rejected saved records can name a closed
            // historical endpoint; their identities must remain representable.
            for (unsigned j = 0; j < e.count; ++j) {
                const auto& ev = e.events[j];
                require(!s.nextToken || ev.peer < s.nextToken);
                if (ev.kind == Event::Kind::Connected) {
                    const auto* peer = find(ev.peer); require(peer);
                    require(ev.toParent ? (peer->parent == e.token && peer->aid == ev.aid) : (e.parent == ev.peer && e.aid == ev.aid));
                }
            }
        }
        for (unsigned i = 0; i < 8; ++i) {
            const auto& parent=s.endpoints[i];
            if(parent.token && parent.advertising) {
                unsigned children=0;
                for(const auto& e:s.endpoints) if(e.parent==parent.token) ++children;
                require(children<=parent.maxChildren);
            }
            const auto& r = s.rooms[i];
            if (r.owner) {
                require(SessionRegistry::validSavedOffer(r.offer));
                require(r.owner == s.endpoints[i].token && r.offer.epoch <= s.lastSavedEpoch);
                require((r.state == SavedRoomState::Rejected) == (r.reason != SavedRoomReason::None));
                for (unsigned seat = 0; seat < r.seats.size(); ++seat) {
                    if (seat >= r.offer.players) { require(!r.seats[seat] && !r.aids[seat]); continue; }
                    if (r.state == SavedRoomState::Ready) require(r.seats[seat]);
                    if (!r.seats[seat]) { require(!r.aids[seat]); continue; }
                    auto offered = r.offer; offered.seat = seat+1; offered.savedAid = r.aids[seat];
                    require(SessionRegistry::validSavedOffer(offered));
                    for (unsigned j = 0; j < seat; ++j) require(r.seats[j] != r.seats[seat]);
                    if (!seat) require(r.seats[seat] == r.owner);
                    const auto* peer = find(r.seats[seat]);
                    if (r.state != SavedRoomState::Rejected) {
                        require(peer && peer->saved.room == r.owner && peer->saved.epoch == r.offer.epoch && peer->saved.state == r.state);
                        if (r.state == SavedRoomState::Ready) {
                            require(peer->aid == r.aids[seat]);
                            require(peer->saved.members == (1u << r.offer.players)-1);
                        }
                    }
                }
            }
            MediumCodec::validate(s.media[i]);
            for (unsigned aid = 0; aid < 8; ++aid) require(joined[i][aid] == s.media[i].endpoints[aid].live);
        }
        for (const auto& e : s.endpoints) if (e.token && e.saved.state != SavedRoomState::Rejected) {
            bool present = false;
            for (const auto& r : s.rooms) if (r.owner == e.saved.room)
                for (auto t : r.seats) if (t == e.token) present = true;
            require(present);
        }
    }
    static std::unique_ptr<State> decode(const Bytes& bytes) {
        auto s = std::make_unique<State>(); Reader r(bytes);
        s->incarnation = unseal(r,Kind::Registry); fields(r,*s); r.end(); validate(*s); return s;
    }
    static void install(SessionRegistry& r, State& s, std::uint64_t fresh) noexcept {
        // Everything that allocates/validates happened before this commit. All
        // buses are private to r and all access shares the held registry lock.
        static_assert(noexcept(r.endpoints.swap(s.endpoints)) && noexcept(r.savedRooms.swap(s.rooms)));
        r.endpoints.swap(s.endpoints); r.savedRooms.swap(s.rooms);
        for (unsigned i = 0; i < 8; ++i) MediumCodec::install(r.media[i],s.media[i]);
        r.nextToken = s.nextToken; r.lastSavedEpoch = s.lastSavedEpoch;
        r.transportIncarnation = fresh;
    }
};
} // namespace melonDS::wm_sdk::snapshot

namespace melonDS::wm_sdk {
inline snapshot::Status SessionRegistry::exportSnapshot(const SnapshotQuiescence& guard, snapshot::Bytes& bytes) {
    if (guard.owner != this || !guard.admission.owns_lock() || !guard.state.owns_lock()) return snapshot::Status::NotQuiescent;
    // Single terminal-send retention remains a distinct, unsupported contract.
    // Bulk receipt retention has an explicit bounded RCP1 extension below.
    for (const auto& e : endpoints)
        if (e.terminalSend.send_id || e.terminalCompletion.status == Bus::Status::Ok) return snapshot::Status::Unsupported;
    return snapshot::attempt([&] {
        auto state = snapshot::RegistryCodec::capture(*this); snapshot::Writer w;
        snapshot::RegistryCodec::fields(w,*state); snapshot::RegistryCodec::validate(*state);
        auto result = snapshot::seal(w,snapshot::Kind::Registry,state->incarnation); bytes.swap(result);
    });
}
inline snapshot::Status SessionRegistry::validateSnapshot(const snapshot::Bytes& bytes) {
    return snapshot::attempt([&] { (void)snapshot::RegistryCodec::decode(bytes); });
}
inline snapshot::Status SessionRegistry::importSnapshot(const SnapshotQuiescence& guard, const snapshot::Bytes& bytes,
                                                       std::uint64_t freshIncarnation) {
    if (guard.owner != this || !guard.admission.owns_lock() || !guard.state.owns_lock()) return snapshot::Status::NotQuiescent;
    for(const auto& medium:media)if(!snapshot::MediumCodec::supported(medium))return snapshot::Status::Unsupported;
    return snapshot::attempt([&] {
        auto s = snapshot::RegistryCodec::decode(bytes);
        snapshot::require(s->clocked == frameDelivery,snapshot::Status::ModeMismatch);
        snapshot::require(freshIncarnation > s->incarnation && freshIncarnation > transportIncarnation,
                          snapshot::Status::StaleIncarnation);
        snapshot::RegistryCodec::install(*this,*s,freshIncarnation);
    });
}
} // namespace melonDS::wm_sdk
#endif
