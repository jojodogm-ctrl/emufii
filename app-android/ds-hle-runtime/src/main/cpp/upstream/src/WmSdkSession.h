// Independently written, bounded in-process discovery/association model.
// No guest pointers, NDS access, radio timing, or network backend.
#ifndef MELONDS_WM_SDK_SESSION_H
#define MELONDS_WM_SDK_SESSION_H
#include <algorithm>
#include <array>
#include <cstdint>
#include <mutex>
#include "WmSdkTransport.h"

namespace melonDS::wm_sdk {
class SessionRegistry final : public Transport {
public:
    using Bus = wm_prototype::MessageBus;
    static constexpr unsigned MaxSavedPlayers = 8;
private:
    friend class snapshot::RegistryCodec;
    struct Endpoint {
        Token token = 0, parent = 0;
        Mac mac{};
        Bss bss{};
        bool advertising = false, parentNotified = false, childNotified = false, fault = false;
        std::uint16_t maxChildren = 0, aid = 0;
        std::array<Event, 16> events{};
        unsigned count = 0;
        unsigned medium = 8;
        Bus::Session mp{};
        Token mpDomain = 0;
        std::uint64_t frame = 0;
        bool frameKnown = false;
        SavedRoomResult saved{};
        // Fixed, endpoint-generation-owned retention for one opt-in send.
        // Single terminal-send snapshots remain unsupported. Bulk retirement
        // below is preserved by the registry's explicit RCP1 extension.
        Bus::Message terminalSend{};
        Bus::CompletionResult terminalCompletion{};
        bool retainReceipts = false;
        std::array<Bus::Completion,MaxStopMpReceipts> retired{};
        unsigned retiredCount = 0;
    };
    struct SavedRoom {
        Token owner = 0;
        SavedRoomOffer offer{};
        std::array<Token,MaxSavedPlayers> seats{};
        std::array<unsigned,MaxSavedPlayers> aids{}; // actual validated per-seat offers
        SavedRoomState state = SavedRoomState::Pending;
        SavedRoomReason reason = SavedRoomReason::None;
    };
    std::mutex mutex;
    // Namespace admission gate. Packet adapters must dispatch through
    // dispatchInIncarnation; bare Transport calls are trusted owner calls.
    std::mutex incarnationMutex;
    std::uint64_t transportIncarnation = 1;
    std::array<Endpoint, 8> endpoints{};
    // One bus per parent slot; never reconstruct on reuse (epochs must not repeat).
    std::array<Bus, 8> media{};
    Token nextToken = 1;
    std::array<SavedRoom,8> savedRooms{};
    std::uint64_t lastSavedEpoch = 0; // bounded replay protection, never reset
    const bool frameDelivery;

    // Capacity is independent of admission: only reviewed profile validators
    // may authorize a roster, and every array index/AID stays within the bus.
    static bool validSavedOffer(const SavedRoomOffer& o) {
        return o.players >= 2 && o.players <= MaxSavedPlayers &&
            o.seat >= 1 && o.seat <= o.players && o.savedAid < MaxSavedPlayers && saved_room::valid(o);
    }
    static unsigned savedAidForSeat(const SavedRoomOffer& base, unsigned seat) {
        if (!validSavedOffer(base) || seat < 1 || seat > base.players) return MaxSavedPlayers;
        auto o = base; o.seat = seat;
        // Recover the exact pinned mapping through the profile validator. Do
        // not index MPDS's 3x4 table using another profile's player count.
        for (unsigned aid = 0; aid < MaxSavedPlayers; ++aid) {
            o.savedAid = aid;
            if (validSavedOffer(o)) return aid;
        }
        return MaxSavedPlayers; // legacy snapshot reconstruction fails closed
    }
    Endpoint* find(Token t) {
        if (t) for (auto& e : endpoints) if (e.token == t) return &e;
        return nullptr;
    }
    bool reserved(const Endpoint& e) const {
        return e.saved.room || savedRooms[&e-endpoints.data()].owner;
    }
    static bool pristine(const Endpoint& e) {
        return !e.parent && !e.advertising && !e.fault && !e.count && e.medium == 8
            && !e.retainReceipts && !e.retiredCount;
    }
    void rejectSaved(Token room, SavedRoomReason reason) {
        auto* owner = find(room);
        if (owner) {
            auto& r = savedRooms[owner-endpoints.data()];
            if (r.owner == room) { r.state = SavedRoomState::Rejected; r.reason = reason; }
        }
        for (auto& e : endpoints) if (e.token && e.saved.room == room) {
            stopMpLocked(e);
            e.parent = 0; e.aid = 0;
            e.saved.state = SavedRoomState::Rejected; e.saved.reason = reason;
            e.saved.members = 0; e.saved.session = {};
        }
    }
    static void push(Endpoint& e, const Event& event) {
        // Teardown cannot silently lose a notification. A consumer encountering
        // this fault must stop the experiment; admission checks prevent it.
        if (e.count == e.events.size()) e.fault = true;
        else e.events[e.count++] = event;
    }
    static void purge(Endpoint& e, Token peer) {
        unsigned n = 0;
        for (unsigned i = 0; i < e.count; ++i)
            if (e.events[i].peer != peer) e.events[n++] = e.events[i];
        e.count = n;
    }
    void unlink(Endpoint& child, bool notifyParent, bool notifyChild, std::uint16_t reason) {
        stopMpLocked(child);
        auto* parent = find(child.parent);
        if (parent) {
            purge(*parent, child.token);
            purge(child, parent->token);
            if (notifyParent && child.parentNotified)
                push(*parent, {Event::Kind::Disconnected, child.token, true, child.aid, reason, child.mac, {}, {}});
            if (notifyChild)
                push(child, {child.childNotified ? Event::Kind::Disconnected : Event::Kind::ConnectFailed,
                             parent->token, false, child.aid, reason, parent->mac, {}, {}});
        }
        child.parent = 0;
        child.aid = 0;
        child.parentNotified = child.childNotified = false;
    }
    void stopMpLocked(Endpoint& e) {
        if (e.medium >= media.size()) return;
        auto& bus = media[e.medium];
        bus.leave(e.mp);
        // Drain ALL retired bus credits as before. Only an explicitly armed
        // send keeps its exact terminal result, including failure/cancellation;
        // leave does not convert an unacknowledged send into Delivered.
        for (;;) {
            const auto result = bus.poll_completion(e.mp);
            if (result.status != Bus::Status::Ok) break;
            if(e.retainReceipts) {
                if(e.retiredCount==e.retired.size())e.fault=true;
                else e.retired[e.retiredCount++]=result.completion;
            } else if (isTerminal(e, result.completion.message)) e.terminalCompletion = result;
        }
        e.mp = {}; e.medium = 8; e.mpDomain = 0;
    }
    static bool isTerminal(const Endpoint& e, const Bus::Message& message) {
        return e.terminalSend.send_id && e.terminalSend.send_id == message.send_id &&
            e.terminalSend.source == message.source;
    }
    static void clearTerminal(Endpoint& e) {
        e.terminalSend = {}; e.terminalCompletion = {};
    }
    Bus::SendResult sendLocked(Endpoint* e, unsigned mask, unsigned port,
        const std::array<std::uint8_t,512>& data, unsigned length, Bus::Sequence wireSequence, bool block=false) {
        if (!e || e->medium >= media.size()) return {Bus::Status::SourceNotReady,{}};
        if (frameDelivery && (!e->frameKnown || e->frame == UINT64_MAX))
            return {Bus::Status::SourceNotReady,{}};
        // Opt-in HLE model: immutable sends become eligible in the next
        // emulated frame, not immediately according to host thread scheduling.
        if(block)return media[e->medium].send_block(e->mp,mask,data,length,frameDelivery ? e->frame+1 : 0);
        return media[e->medium].send(e->mp, mask, port, data, length, wireSequence,
                                    frameDelivery ? e->frame+1 : 0);
    }
public:
    explicit SessionRegistry(bool clocked = false) : frameDelivery(clocked) {}
    class SnapshotQuiescence {
        friend class SessionRegistry;
        SessionRegistry* owner;
        std::unique_lock<std::mutex> admission, state;
        explicit SnapshotQuiescence(SessionRegistry& r)
            : owner(&r), admission(r.incarnationMutex), state(r.mutex) {}
    public:
        SnapshotQuiescence(SnapshotQuiescence&&) = default;
        SnapshotQuiescence& operator=(SnapshotQuiescence&&) = delete;
        SnapshotQuiescence(const SnapshotQuiescence&) = delete;
    };
    // Caller must park ALL owner threads at the coordinated semantic cut first.
    // This guard freezes this registry only, not core state or broker IO/queues.
    // Same-thread lexical lifetime; do not call normal registry methods inside.
    SnapshotQuiescence quiesceForSnapshot() { return SnapshotQuiescence(*this); }
    bool supportsMediumSnapshot() const override { return true; }
    snapshot::Status exportSnapshot(const SnapshotQuiescence&, snapshot::Bytes&);
    snapshot::Status importSnapshot(const SnapshotQuiescence&, const snapshot::Bytes&, std::uint64_t freshIncarnation);
    static snapshot::Status validateSnapshot(const snapshot::Bytes&);
    std::uint64_t incarnation() {
        std::lock_guard<std::mutex> lock(incarnationMutex); return transportIncarnation;
    }
    // Bind an adapter's queued work to the incarnation at admission. The check
    // and complete operation share the import gate, so an old packet cannot
    // pass a check before restore and execute afterward. No nested dispatch,
    // incarnation query or snapshot acquisition inside action.
    template<class F> bool dispatchInIncarnation(std::uint64_t expected, F&& action) {
        std::lock_guard<std::mutex> lock(incarnationMutex);
        if (!expected || expected != transportIncarnation) return false;
        action(*this); return true;
    }
    SavedRoomResult lookupSavedRoom(Token t, const SavedRoomOffer& offer) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        if (!e) return {SavedRoomState::Rejected, SavedRoomReason::InvalidEndpoint};
        if (!validSavedOffer(offer)) return {SavedRoomState::Rejected, SavedRoomReason::InvalidOffer};
        if (reserved(*e) || !pristine(*e)) return {SavedRoomState::Rejected, SavedRoomReason::Busy};
        for (const auto& r : savedRooms) if (r.owner && r.offer.epoch == offer.epoch) {
            if (!saved_room::matches(r.offer, offer)) return {SavedRoomState::Rejected, SavedRoomReason::Mismatch};
            if (r.state == SavedRoomState::Rejected) return {SavedRoomState::Rejected, r.reason};
            if (r.state == SavedRoomState::Ready) return {SavedRoomState::Rejected, SavedRoomReason::Duplicate};
            return {SavedRoomState::Pending, SavedRoomReason::None,
                r.seats[0] == r.owner ? r.owner : 0, offer.epoch};
        }
        if (offer.epoch <= lastSavedEpoch) return {SavedRoomState::Rejected, SavedRoomReason::Stale};
        return {SavedRoomState::Pending, SavedRoomReason::None, 0, offer.epoch};
    }
    SavedRoomResult adoptSavedRoom(Token t, Token room, const SavedRoomOffer& offer) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t); auto* owner = find(room);
        if (!e || !owner) return {SavedRoomState::Rejected, SavedRoomReason::InvalidEndpoint};
        if (e->saved.room) {
            rejectSaved(e->saved.room, SavedRoomReason::Duplicate);
            return e->saved;
        }
        auto& r = savedRooms[owner-endpoints.data()];
        // A parent cannot also be a seat in another pending/admitted room.
        if ((owner->saved.room && owner->saved.room != room) ||
            (savedRooms[e-endpoints.data()].owner && t != room))
            return {SavedRoomState::Rejected, SavedRoomReason::Busy};
        if (owner->saved.room == room && owner->saved.state == SavedRoomState::Rejected)
            return {SavedRoomState::Rejected, owner->saved.reason, room, owner->saved.epoch};
        if (r.owner && r.state == SavedRoomState::Rejected)
            return {SavedRoomState::Rejected, r.reason, room, r.offer.epoch};
        if (!pristine(*e) || (!r.owner && !pristine(*owner))) {
            if (r.owner) rejectSaved(room, SavedRoomReason::Busy);
            return {SavedRoomState::Rejected, SavedRoomReason::Busy};
        }
        if (!validSavedOffer(offer) || ((t == room) != (offer.seat == 1))) {
            e->saved = {SavedRoomState::Rejected, SavedRoomReason::InvalidOffer, room, offer.epoch};
            if (r.owner) rejectSaved(room, SavedRoomReason::InvalidOffer);
            return e->saved;
        }
        if (!r.owner) {
            if (offer.epoch <= lastSavedEpoch) {
                e->saved = {SavedRoomState::Rejected, SavedRoomReason::Stale, room, offer.epoch};
                return e->saved;
            }
            lastSavedEpoch = offer.epoch;
            r = {}; r.owner = room; r.offer = offer;
        }
        e->saved = {SavedRoomState::Pending, SavedRoomReason::None, room, offer.epoch};
        if (!saved_room::matches(r.offer, offer)) {
            rejectSaved(room, SavedRoomReason::Mismatch); return e->saved;
        }
        if (r.state != SavedRoomState::Pending || r.seats[offer.seat-1]) {
            rejectSaved(room, SavedRoomReason::Duplicate); return e->saved;
        }
        r.seats[offer.seat-1] = t;
        r.aids[offer.seat-1] = offer.savedAid;
        for (unsigned i=0; i<offer.players; ++i) if (!r.seats[i]) return e->saved;
        // Complete real roster under one registry lock. No Connected events:
        // transport membership is not evidence that any guest callback occurred.
        const auto medium = static_cast<unsigned>(owner-endpoints.data());
        for (unsigned i=0; i<offer.players; ++i) {
            auto* peer = find(r.seats[i]);
            if (!peer || !pristine(*peer)) {
                rejectSaved(room, SavedRoomReason::Disconnected); return e->saved;
            }
            auto joined = media[medium].join(r.aids[i]);
            if (joined.status != Bus::Status::Ok) {
                rejectSaved(room, SavedRoomReason::Capacity); return e->saved;
            }
            clearTerminal(*peer);
            peer->medium = medium; peer->mp = joined.session; peer->mpDomain = room;
        }
        for (unsigned i=0; i<offer.players; ++i) {
            auto& peer = *find(r.seats[i]);
            peer.parent = i ? room : 0; peer.aid = peer.mp.aid;
            media[medium].make_ready(peer.mp);
            peer.saved = {SavedRoomState::Ready, SavedRoomReason::None, room, offer.epoch,
                static_cast<std::uint16_t>((1u << offer.players)-1), peer.mp};
        }
        r.state = SavedRoomState::Ready;
        return e->saved;
    }
    SavedRoomResult pollSavedRoom(Token t) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        return e ? e->saved : SavedRoomResult{SavedRoomState::Rejected, SavedRoomReason::InvalidEndpoint};
    }
    bool advanceFrame(Token t, std::uint64_t frame) override {
        if (!frameDelivery) return true;
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        if (!e || (e->frameKnown && frame < e->frame)) return false;
        e->frame = frame; e->frameKnown = true;
        return true;
    }
    Token open(const Mac& mac) override {
        std::lock_guard<std::mutex> lock(mutex);
        for (const auto& e : endpoints) if (e.token && e.mac == mac) return 0;
        if (!nextToken) return 0; // never recycle an exhausted generation
        for (auto& e : endpoints) if (!e.token) {
            e = {}; e.mac = mac; e.token = nextToken++;
            return e.token;
        }
        return 0;
    }
    void close(Token t) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        if (!e) return;
        if (reserved(*e)) rejectSaved(e->saved.room ? e->saved.room : t, SavedRoomReason::Disconnected);
        savedRooms[e-endpoints.data()] = {};
        stopMpLocked(*e);
        if (e->parent) unlink(*e, true, false, 3);
        for (auto& c : endpoints) if (c.parent == t) unlink(c, false, true, 3);
        *e = {};
    }
    Bus::SessionResult startMp(Token t) override { return *tryStartMp(t,true); }
    std::optional<Bus::SessionResult> tryStartMp(Token t,bool admit) {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        if (!e || reserved(*e) || e->fault || e->retiredCount || e->medium < media.size()) return Bus::SessionResult{};
        auto* parent = e->parent ? find(e->parent) : e;
        if (!parent || !parent->advertising || (e->parent && !e->childNotified)) return Bus::SessionResult{};
        if(!admit)return std::nullopt;
        const unsigned medium = static_cast<unsigned>(parent-endpoints.data());
        auto result = media[medium].join(e->parent ? e->aid : 0);
        if (result.status != Bus::Status::Ok) return result;
        clearTerminal(*e);
        e->medium = medium; e->mp = result.session; e->mpDomain = parent->token;
        media[medium].make_ready(e->mp);
        return result;
    }
    void stopMp(Token t) override {
        std::lock_guard<std::mutex> lock(mutex);
        if (auto* e = find(t)) {
            clearTerminal(*e); // Explicit owner reset also clears a retired receipt.
            if (reserved(*e)) rejectSaved(e->saved.room ? e->saved.room : t, SavedRoomReason::Disconnected);
            else stopMpLocked(*e);
            // Explicit legacy Stop deliberately discards its own receipts.
            // Retention protects involuntary unlink, not this reset contract.
            e->retiredCount=0;e->retired={};
        }
    }
    bool enableReceiptRetention(Token t) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e=find(t);
        if(!e || reserved(*e) || e->fault || e->medium<media.size() || e->terminalSend.send_id)return false;
        e->retainReceipts=true;return true;
    }
    std::optional<std::vector<Bus::Completion>> stopMpWithReceipts(Token t) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        // Saved-cohort rejection affects other owners too; unsupported here.
        if (!e || reserved(*e) || e->fault) return std::nullopt;
        static_assert(Bus::MaxCompletionsPerEndpoint + 1 <= MaxStopMpReceipts,
                      "stop receipts exceed owner bound");
        std::vector<Bus::Completion> receipts;
        // Allocate before any mutation. Bus capacity plus one retained terminal
        // receipt bounds every push, including completions made ready by leave.
        receipts.reserve(MaxStopMpReceipts);
        for(unsigned i=0;i<e->retiredCount;++i)receipts.push_back(e->retired[i]);
        if (e->terminalCompletion.status == Bus::Status::Ok)
            receipts.push_back(e->terminalCompletion.completion);
        if (e->medium < media.size()) {
            auto& bus = media[e->medium];
            bus.leave(e->mp);
            for (;;) {
                const auto result = bus.poll_completion(e->mp);
                if (result.status != Bus::Status::Ok) break;
                receipts.push_back(result.completion);
            }
            e->mp = {}; e->medium = 8; e->mpDomain = 0;
        }
        clearTerminal(*e);
        e->retiredCount=0;e->retired={};
        return receipts;
    }
    // Participation is independent of whether a peer has sent application data.
    std::optional<std::uint16_t> mpReadyMask(Token t, Bus::Session expected) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e=find(t);
        if(!e || !expected.epoch || e->medium>=media.size() || e->mp!=expected)return std::nullopt;
        std::uint16_t mask=0;
        for(const auto& peer:endpoints)
            if(peer.token && peer.token!=t && peer.medium==e->medium && peer.mpDomain==e->mpDomain
               && peer.mp.epoch && (!e->mp.aid || !peer.mp.aid))mask|=std::uint16_t(1u<<peer.mp.aid);
        return mask;
    }
    // Read-only broker fence. A delayed packet must never be submitted using
    // the endpoint's NEW current epoch after a stop/restart on the same socket.
    Bus::SessionResult currentMp(Token t) {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e=find(t);
        if(!e || e->medium>=media.size())return {Bus::Status::SourceNotReady,{}};
        return {Bus::Status::Ok,e->mp};
    }
    struct MpIdentity { Token domain=0; Bus::Session session{}; };
    std::optional<MpIdentity> currentMpIdentity(Token t) {
        std::lock_guard<std::mutex> lock(mutex);auto* e=find(t);
        if(!e||e->medium>=media.size())return std::nullopt;
        return MpIdentity{e->mpDomain,e->mp};
    }
    Token associationDomain(Token t) {
        std::lock_guard<std::mutex> lock(mutex);auto* e=find(t);
        return e?(e->parent?e->parent:e->token):0;
    }
    bool liveReceiptSessions(Token t,Token domain,const Bus::DeliveryToken& receipt) {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e=find(t);
        if(!e || e->medium>=media.size() || e->mpDomain!=domain || e->mp!=receipt.recipient)return false;
        for(const auto& source:endpoints)
            if(source.token && source.medium==e->medium && source.mpDomain==domain && source.mp==receipt.source)return true;
        return false;
    }
    Bus::SendResult send(Token t, unsigned mask, unsigned port, const std::array<std::uint8_t,512>& data,
                         unsigned length, Bus::Sequence wireSequence = 0xffff) override {
        std::lock_guard<std::mutex> lock(mutex);
        return sendLocked(find(t), mask, port, data, length, wireSequence);
    }
    Bus::SendResult sendBlock(Token t,unsigned mask,const std::array<std::uint8_t,512>& data,unsigned length) override {
        std::lock_guard<std::mutex> lock(mutex);
        return sendLocked(find(t),mask,Bus::BlockPort,data,length,0xffff,true);
    }
    Bus::SendResult sendTerminal(Token t, unsigned mask, unsigned port, const std::array<std::uint8_t,512>& data,
                                 unsigned length, Bus::Sequence wireSequence = 0xffff) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        if (!e || e->medium >= media.size()) return {Bus::Status::SourceNotReady,{}};
        if (e->retainReceipts || e->terminalSend.send_id) return {Bus::Status::Backpressure,{}};
        const auto result = sendLocked(e, mask, port, data, length, wireSequence);
        if (result.status == Bus::Status::Ok) e->terminalSend = result.message;
        return result;
    }
    Bus::DeliveryResult take(Token t, Token& domain) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        if (!e || e->medium >= media.size()) return {Bus::Status::SourceNotReady,{}};
        domain = e->mpDomain;
        if (frameDelivery && !e->frameKnown) return {Bus::Status::SourceNotReady,{}};
        return media[e->medium].take_delivery(e->mp, frameDelivery ? e->frame : UINT64_MAX);
    }
    Bus::Status acknowledge(Token t, Token domain, const Bus::DeliveryToken& token) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        if (!e || e->medium >= media.size() || e->mpDomain != domain) return Bus::Status::StaleSession;
        if (frameDelivery && !e->frameKnown) return Bus::Status::SourceNotReady;
        return media[e->medium].acknowledge(e->mp, token, frameDelivery ? e->frame : 0);
    }
    Bus::CompletionResult completion(Token t) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        if(e && e->fault)return {Bus::Status::SourceNotReady,{}};
        if(e && e->retiredCount) {
            const auto result=e->retired[0];
            for(unsigned i=1;i<e->retiredCount;++i)e->retired[i-1]=e->retired[i];
            e->retired[--e->retiredCount]={};return {Bus::Status::Ok,result};
        }
        if (e && e->terminalCompletion.status == Bus::Status::Ok) {
            const auto result = e->terminalCompletion;
            clearTerminal(*e);
            return result;
        }
        if (!e || e->medium >= media.size()) return {Bus::Status::SourceNotReady,{}};
        if (frameDelivery && !e->frameKnown) return {Bus::Status::SourceNotReady,{}};
        const auto result = media[e->medium].poll_completion(e->mp, frameDelivery ? e->frame : UINT64_MAX);
        if (result.status == Bus::Status::Ok && isTerminal(*e, result.completion.message)) clearTerminal(*e);
        return result;
    }
    bool advertise(Token t, const Bss& bss, std::uint16_t maxChildren) override { return *tryAdvertise(t,bss,maxChildren,true); }
    std::optional<bool> tryAdvertise(Token t,const Bss& bss,std::uint16_t maxChildren,bool admit) {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        if (!e || reserved(*e) || e->parent || e->fault || maxChildren < 1 || maxChildren > 7
            || !std::equal(e->mac.begin(), e->mac.end(), bss.begin() + 4)
            || get16(bss.data() + 0x36) < 1 || get16(bss.data() + 0x36) > 13
            || get16(bss.data() + 0x3c) < 16 || get16(bss.data() + 0x3c) > 128) return false;
        // Updating an already admitted announcement must not suspend an MP
        // owner which still owes real receive callbacks/ACKs during departure.
        if(!admit&&!e->advertising)return std::nullopt;
        e->bss = bss; e->maxChildren = maxChildren; e->advertising = true;
        return true;
    }
    Advertisement scan(Token t, std::uint16_t channel, const Mac& filter) override { return *tryScan(t,channel,filter,true); }
    std::optional<Advertisement> tryScan(Token t,std::uint16_t channel,const Mac& filter,bool admit) {
        std::lock_guard<std::mutex> lock(mutex);
        if (!find(t)||channel<1||channel>13)return Advertisement{};
        if(!admit)return std::nullopt;
        const bool broadcast = std::all_of(filter.begin(), filter.end(), [](auto b) { return b == 255; });
        for (const auto& e : endpoints)
            if (e.token != t && e.advertising && get16(e.bss.data() + 0x36) == channel
                && (broadcast || filter == e.mac)) return Advertisement{e.token, e.bss};
        return Advertisement{};
    }
    // Both endpoints reserve a callback slot before membership is installed.
    // The token is the generation captured by a real scan, not just a MAC.
    bool connect(Token childToken, Token parentToken, const Bss& observed, const ConnectData& data) override { return *tryConnect(childToken,parentToken,observed,data,true); }
    std::optional<bool> tryConnect(Token childToken,Token parentToken,const Bss& observed,const ConnectData& data,bool admit) {
        std::lock_guard<std::mutex> lock(mutex);
        auto* c = find(childToken); auto* p = find(parentToken);
        if (!c || !p || reserved(*c) || reserved(*p) || c == p || c->parent || c->advertising || !p->advertising
            || c->fault || p->fault || !(p->bss[0x4b] & 1)
            || c->count != 0 || p->count >= p->events.size()
            || !std::equal(observed.begin() + 4, observed.begin() + 10, p->mac.begin())
            || !std::equal(observed.begin() + 0x44, observed.begin() + 0x4a, p->bss.begin() + 0x44)
            || get16(observed.data() + 0x36) != get16(p->bss.data() + 0x36)) return false;
        // Do not let reconnect erase an earlier disconnect which ARM9 has not
        // consumed yet, even if the MAC/AID happen to be reused.
        for (unsigned i = 0; i < p->count; ++i) if (p->events[i].peer == childToken) return false;
        unsigned mask = 0, count = 0;
        for (const auto& e : endpoints) if (e.parent == parentToken) { mask |= 1u << e.aid; ++count; }
        if (count >= p->maxChildren) return false;
        std::uint16_t aid = 1;
        while (aid < 8 && (mask & (1u << aid))) ++aid;
        if (aid == 8) return false;
        if(!admit)return std::nullopt;
        clearTerminal(*c);
        c->parent = parentToken; c->aid = aid;
        push(*p, {Event::Kind::Connected, childToken, true, aid, 0, c->mac, data, p->bss});
        push(*c, {Event::Kind::Connected, parentToken, false, aid, 0, p->mac, {}, p->bss});
        return true;
    }
    std::uint16_t disconnect(Token t, std::uint16_t mask) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        if (!e) return 0;
        if (reserved(*e)) {
            const auto removed = static_cast<std::uint16_t>(mask &
                (e->aid ? 1u : (e->saved.members & ~1u)));
            if (mask) rejectSaved(e->saved.room ? e->saved.room : t, SavedRoomReason::Disconnected);
            return removed;
        }
        if (e->parent) {
            if (!(mask & 1)) return 0;
            unlink(*e, true, false, 3);
            return 1;
        }
        std::uint16_t removed = 0;
        for (auto& c : endpoints) if (c.parent == t && (mask & (1u << c.aid))) {
            removed |= std::uint16_t(1u << c.aid);
            unlink(c, false, true, 3);
        }
        return removed;
    }
    // Caller checks its own callback lease/FIFO BEFORE consuming this event.
    bool pop(Token t, Event& event, bool& fault) override {
        std::lock_guard<std::mutex> lock(mutex);
        auto* e = find(t);
        fault = !e || e->fault;
        if (fault || !e->count) return false;
        event = e->events[0];
        for (unsigned i = 1; i < e->count; ++i) e->events[i-1] = e->events[i];
        --e->count;
        if (event.kind == Event::Kind::Connected) {
            auto* c = event.toParent ? find(event.peer) : e;
            if (c) { if (event.toParent) c->parentNotified = true; else c->childNotified = true; }
        }
        return true;
    }
};
} // namespace melonDS::wm_sdk
#include "WmSdkSnapshotRegistry.h"
#endif
