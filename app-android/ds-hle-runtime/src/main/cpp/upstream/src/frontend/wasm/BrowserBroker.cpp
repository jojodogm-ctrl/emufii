// Clean-room provenance: our native broker dispatcher and public project
// transport interfaces; see docs/CLEANROOM.md. Reuses the ACTUAL SessionRegistry,
// MessageBus and WmSdkNet codecs. This is a carrier adapter, no game protocol.
#include "BrowserBroker.h"
#include "../../WmSdkNet.h"
#include "../../WmSdkSession.h"
#include <limits>
#if defined(NIFI2_PRIVATE_BROKER_FAULT_TRACE)
#include <cstdio>
#include <exception>
#endif

namespace melonDS::wm_sdk::browser {
using namespace net;
namespace checkpoint {
namespace snap = melonDS::wm_sdk::snapshot;
constexpr std::uint64_t MaxAge = 8ULL * 60 * 60 * 1000000;
constexpr std::array<std::uint8_t, 8> Magic{{'W','M','B','W','S','N','A','P'}};
// The old outer v1 format did not distinguish WAN32 from the default medium.
// New WAN64 geometry must fail before any payload or replacement is staged.
constexpr std::uint16_t Version = snap::Version==1 ? 1 : 0x8041;
struct Peer {
    Token token = 0;
    SavedRoomResult saved{};
    std::uint64_t lastSend = 0, lastWatch = 0;
    ReadyWatch watch{};
};
struct Pending { unsigned peer; std::uint64_t remaining, age; Bytes packet; };
struct Image {
    unsigned capacity = 0, rtt = 0;
    Broker::Handle nextHandle = 0;
    std::uint64_t incarnation = 0, operation = 0;
    std::vector<Peer> peers;
    std::deque<Pending> pending;
    std::map<std::pair<Token, Bus::SendId>, Bus::Message> originals;
    Bytes registry;
};
template<class F> snap::Status attempt(F action) {
    try { return snap::attempt(action); }
    catch (const Fault&) { return snap::Status::InvalidImage; }
}
struct Output : Writer {
    void blob(const Bytes& b, std::size_t max) {
        snap::require(b.size() <= max && bytes.size() + 4 + b.size() <= Broker::MaxSnapshotBytes,
            snap::Status::ResourceLimit);
        u32(static_cast<std::uint32_t>(b.size())); bytes.insert(bytes.end(), b.begin(), b.end());
    }
};
struct Input {
    const Bytes& bytes; std::size_t pos = 0;
    explicit Input(const Bytes& b) : bytes(b) { snap::require(b.size() >= 40 && b.size() <= Broker::MaxSnapshotBytes); }
    std::uint64_t number(unsigned width) {
        snap::require(width <= 8 && pos <= bytes.size() && width <= bytes.size() - pos);
        std::uint64_t n = 0;
        for (unsigned i = 0; i < width; ++i) n |= std::uint64_t(bytes[pos++]) << (8 * i);
        return n;
    }
    Bytes blob(std::size_t max) {
        const auto n = number(4); snap::require(n <= max && n <= bytes.size() - pos);
        Bytes b(bytes.begin() + pos, bytes.begin() + pos + n); pos += n; return b;
    }
};
// Same RegistryCodec/MediumCodec and cross-layer invariants as our native
// broker. This outer carrier image additionally preserves replay high-water
// marks and handle allocation; it is not the native socket broker container.
void validate(const Image& s) {
    snap::require(s.capacity >= 2 && s.capacity <= 8 && s.peers.size() == s.capacity &&
        s.rtt <= 1000 && s.incarnation && s.operation && s.pending.size() <= Bus::MaxInflight * s.capacity && s.originals.size() <= Bus::MaxInflight);
    snap::require(!s.nextHandle || s.nextHandle > s.capacity);
    auto registry = snap::RegistryCodec::decode(s.registry);
    snap::require(!registry->clocked && registry->incarnation == s.incarnation, snap::Status::ModeMismatch);
    const auto& endpoints = registry->endpoints;
    const auto endpoint = [&](Token t) {
        for (const auto& e : endpoints) if (t && e.token == t) return &e;
        snap::require(false); return &endpoints[0];
    };
    unsigned live = 0;
    for (const auto& e : registry->endpoints) if (e.token) { ++live; snap::require(!e.fault); }
    snap::require(live == s.capacity);
    std::map<std::pair<Token, std::uint64_t>, bool> external, receipts;
    std::size_t completions = 0;
    for (unsigned i = 0; i < s.capacity; ++i) {
        const auto& p = s.peers[i]; const auto* e = endpoint(p.token);
        snap::validateReadyWatch(p.watch,p.token,p.lastWatch);
        for (unsigned j = 0; j < i; ++j) snap::require(p.token != s.peers[j].token);
        Writer w; encodeSavedResult(w, p.saved); Reader r(w.bytes); (void)decodeSavedResult(r); r.end();
        const auto mapped=[&](const Bus::Message& actual){
            if (!actual.send_id) return; ++completions;
            const auto it = s.originals.find({p.token, actual.send_id}); snap::require(it != s.originals.end());
            const auto& m = it->second;
            Writer mw; mw.message(m); Reader mr(mw.bytes); (void)mr.message(); mr.end();
            snap::require(m.source == actual.source && m.port == actual.port && m.targets == actual.targets &&
                m.wire_sequence == actual.wire_sequence && m.send_id <= p.lastSend &&
                external.emplace(std::make_pair(p.token, m.send_id), true).second);
        };
        for(unsigned j=0;j<e->retiredCount;++j)mapped(e->retired[j].message);
        if (e->medium >= 8) continue;
        for (const auto& c : registry->media[e->medium].endpoints[e->aid].completions)mapped(c.event.message);
    }
    snap::require(completions == s.originals.size());
    std::array<std::uint64_t, 8> lastQueued{};
    for (const auto& d : s.pending) {
        snap::require(d.peer < s.capacity && d.remaining <= s.rtt * 500 && d.age <= MaxAge);
        const auto& p = s.peers[d.peer]; const auto* e = endpoint(p.token);
        Reader r(d.packet); const auto code = r.u8(); snap::require(r.u64() == p.token);
        if (code == Send) {
            const auto m = r.message(); const auto length = r.u16(); (void)r.array<512>(); r.end();
            snap::require(length && length <= 512 && e->medium < 8 && m.source == e->mp &&
                m.send_id <= p.lastSend && m.send_id > lastQueued[d.peer] &&
                external.emplace(std::make_pair(p.token, m.send_id), true).second);
            lastQueued[d.peer] = m.send_id;
        } else {
            snap::require(code == Ack); const auto domain = r.u64(); const auto ack = r.token(); r.end();
            snap::require(e->medium < 8 && domain == e->mpDomain && ack.recipient == e->mp);
            bool found = false;
            for (const auto& slot : registry->media[e->medium].sends)
                if (slot.used && slot.message.send_id == ack.send_id && slot.message.source == ack.source) {
                    const auto bit = 1u << ack.recipient.aid;
                    found = (slot.pending & bit) && (slot.taken & bit) &&
                        slot.recipient_epochs[ack.recipient.aid] == ack.recipient.epoch;
                }
            snap::require(found && receipts.emplace(std::make_pair(p.token, ack.send_id), true).second);
        }
    }
}
Bytes encode(const Image& s) {
    Output w; w.array(Magic); w.u16(Version); w.u8(s.capacity); w.u16(s.rtt);
    w.u32(s.nextHandle); w.u64(s.incarnation); w.u64(s.operation);
    for (const auto& p : s.peers) {
        w.u64(p.token); w.u64(p.lastSend); w.u64(p.lastWatch);
        Writer saved; encodeSavedResult(saved, p.saved); w.blob(saved.bytes, 64);
    }
    w.u16(static_cast<std::uint16_t>(s.pending.size()));
    for (const auto& d : s.pending) { w.u8(d.peer); w.u64(d.remaining); w.u64(d.age); w.blob(d.packet, 1024); }
    w.u16(static_cast<std::uint16_t>(s.originals.size()));
    for (const auto& o : s.originals) {
        w.u64(o.first.first); w.u64(o.first.second); Writer m; m.message(o.second); w.blob(m.bytes, 64);
    }
    w.blob(s.registry, snap::MaxImageBytes);
    if(std::any_of(s.peers.begin(),s.peers.end(),[](const auto& p){return p.watch.id!=0;})){
        w.u32(0x31594452);
        for(const auto& p:s.peers){Writer record;
            if(p.watch.id)encodeReadyWatch(record,p.watch);w.blob(record.bytes,64);}
    }
    snap::require(w.bytes.size() <= Broker::MaxSnapshotBytes - 4, snap::Status::ResourceLimit);
    w.u32(snap::crc32(w.bytes.data(), w.bytes.size())); return std::move(w.bytes);
}
std::unique_ptr<Image> decode(const Bytes& bytes) {
    Input r(bytes); auto s = std::make_unique<Image>();
    for (auto c : Magic) snap::require(r.number(1) == c);
    snap::require(r.number(2) == Version, snap::Status::WrongVersion);
    s->capacity = r.number(1); s->rtt = r.number(2);
    snap::require(s->capacity >= 2 && s->capacity <= 8 && s->rtt <= 1000);
    s->nextHandle = r.number(4); s->incarnation = r.number(8); s->operation = r.number(8);
    for (unsigned i = 0; i < s->capacity; ++i) {
        Peer p; p.token = r.number(8); p.lastSend = r.number(8); p.lastWatch = r.number(8);
        auto b = r.blob(64); Reader rd(b); p.saved = decodeSavedResult(rd); rd.end(); s->peers.push_back(p);
    }
    const auto pending = r.number(2); snap::require(pending <= Bus::MaxInflight * s->capacity);
    for (unsigned i = 0; i < pending; ++i) {
        Pending d; d.peer = r.number(1); d.remaining = r.number(8); d.age = r.number(8);
        d.packet = r.blob(1024); s->pending.push_back(std::move(d));
    }
    const auto originals = r.number(2); snap::require(originals <= Bus::MaxInflight);
    for (unsigned i = 0; i < originals; ++i) {
        const auto token = r.number(8), id = r.number(8); auto b = r.blob(64); Reader rd(b);
        const auto m = rd.message(); rd.end();
        snap::require(token && id && s->originals.emplace(std::make_pair(token, id), m).second);
    }
    s->registry = r.blob(snap::MaxImageBytes);
    if(r.pos+4<bytes.size()){
        snap::require(r.number(4)==0x31594452,snap::Status::WrongVersion);
        bool present=false;
        for(auto& p:s->peers){auto record=r.blob(64);
            if(!record.empty()){Reader rd(record);p.watch=decodeReadyWatch(rd);present=true;}}
        snap::require(present);
    }
    const auto crc = r.number(4);
    snap::require(r.pos == bytes.size() && crc == snap::crc32(bytes.data(), bytes.size() - 4));
    validate(*s); return s;
}
} // namespace checkpoint
struct Broker::Impl {
    struct Client {
        Handle handle = 0;
        Token token = 0;
        SavedRoomResult saved{};
        Bus::SendId terminal = 0, lastSend = 0;
        ReadyWatch watch{};
        std::uint64_t lastWatch = 0;
        bool prepared = false, released = false, fenced = false;
        std::deque<Bytes> output;
        Bytes drainSent;
        bool drainHadTargets=false;
    };
    struct Deferred {
        Handle handle;
        std::uint64_t queued, due;
        Bytes packet;
        std::uint64_t ageBase = 0;
    };
    // As on the native broker, the medium is unclocked. Remote retains all
    // next-frame / initialized-port delivery eligibility in the player module.
    SessionRegistry registry;
    std::array<Client, 8> clients{};
    std::array<Handle, 8> restoredHandles{};
    std::deque<Deferred> deferred;
    std::map<std::pair<Token, Bus::SendId>, Bus::Message> originals;
    const unsigned capacity;
    unsigned delayUs;
    bool deliveryHold = false;
    std::uint64_t holdSince = 0;
    Handle nextHandle = 1;
    std::uint64_t clock = 0;
    bool failed = false;
    bool requireSharingWindow = false;
    unsigned sharingVersion=3,sharingLead=64;
    bool sharingNegotiated = false;
    std::array<bool,8> sharingClaims{};
    struct Drain {
        Token target=0,domain=0;Bus::Session session{};std::uint64_t id=0;
        std::array<Token,8> members{};std::array<Bus::Session,8> epochs{};
        std::uint16_t offered=0;bool ready=false;
    };
    std::array<Drain,8> drains{};
    std::uint64_t nextDrain=0,drainRevision=1;
    bool domainDraining(Token domain)const{
        for(const auto& d:drains)if(d.target&&d.domain==domain)return true;return false;
    }
    bool drainWireBusy(const Drain& d){
        for(const auto& entry:originals){const auto& m=entry.second;
            if(entry.first.first==d.target)return true;
            if(m.source.aid<8&&d.members[m.source.aid]==entry.first.first&&d.epochs[m.source.aid]==m.source&&(m.targets&(1u<<d.session.aid)))return true;
        }
        for(const auto& packet:deferred){Reader r(packet.packet);const auto code=r.u8();const auto token=r.u64();
            if(code==Send||code==SendTerminal||code==BlockSend){const auto m=r.message(code==BlockSend);
                if(token==d.target||(m.source.aid<8&&d.members[m.source.aid]==token&&d.epochs[m.source.aid]==m.source&&(m.targets&(1u<<d.session.aid))))return true;
            }else if(code==Ack){const auto domain=r.u64();const auto receipt=r.token();
                if(domain==d.domain&&(receipt.source==d.session||receipt.recipient==d.session))return true;
            }
        }
        return false;
    }
    void refreshDrains(){
        for(auto& d:drains)if(d.target){
            const auto target=registry.currentMpIdentity(d.target);
            if(registry.associationDomain(d.target)!=d.domain||(target&&target->session!=d.session)){
                d={};if(++drainRevision==0)throw Fault("sharing drain revision exhausted");continue;
            }
            std::array<Token,8> members{};std::array<Bus::Session,8> epochs{};std::uint16_t mask=0;
            for(const auto& c:clients)if(c.token){const auto current=registry.currentMpIdentity(c.token);
                if(current&&current->domain==d.domain){const auto aid=current->session.aid;members[aid]=c.token;epochs[aid]=current->session;mask|=std::uint16_t(1u<<aid);}}
            bool changed=false;
            for(unsigned aid=0;aid<8;++aid)if(members[aid]!=d.members[aid]||epochs[aid]!=d.epochs[aid]){
                changed=true;
                d.offered&=std::uint16_t(~(1u<<aid));if(members[aid])d.ready=false;
            }
            if(changed&&++drainRevision==0)throw Fault("sharing drain revision exhausted");
            d.members=members;d.epochs=epochs;
            if(!d.ready&&(d.offered&mask)==mask&&!drainWireBusy(d)){
                d.ready=true;if(++drainRevision==0)throw Fault("sharing drain revision exhausted");
            }
        }
    }
    DrainNotice drainView(const Client& observer){
        const auto identity=registry.currentMpIdentity(observer.token);
        if(!identity)throw Fault("sharing drain inactive observer");
        DrainNotice n;n.endpoint=observer.token;n.domain=identity->domain;n.session=identity->session;n.revision=drainRevision;
        for(const auto& c:clients)if(c.token){const auto current=registry.currentMpIdentity(c.token);
            if(current&&current->domain==n.domain)n.live[current->session.aid]=current->session.epoch;}
        for(const auto& d:drains)if(d.target&&d.domain==n.domain)n.targets[d.session.aid]={d.target,d.session,d.id,d.ready};
        return n;
    }
    void publishDrains(){
        if(!requireSharingWindow)return;
        bool any=false;for(const auto& d:drains)any=any||bool(d.target);
        for(const auto& c:clients)any=any||c.drainHadTargets;
        if(!any)return;
        refreshDrains();
        for(auto& c:clients)if(c.token&&registry.currentMpIdentity(c.token)){
            const auto n=drainView(c);bool targets=false;for(const auto& t:n.targets)targets=targets||bool(t.token);
            if(!targets&&!c.drainHadTargets)continue;
            Writer w;w.u8(SharingDrainStatus);encodeDrainNotice(w,n);
            if(w.bytes!=c.drainSent){emit(c,w);c.drainSent=w.bytes;}c.drainHadTargets=targets;
        }
    }
    void drainControl(Client& c,Reader& r,Writer& w){
        const auto expected=r.session();const bool begin=r.boolean();const auto offered=r.u16();
        std::array<std::uint64_t,8> ids{};for(auto& id:ids)id=r.u64();r.end();
        const auto identity=registry.currentMpIdentity(c.token);
        if(!requireSharingWindow||!sharingClaims[unsigned(&c-clients.data())]||!identity||identity->session!=expected||offered>255)
            throw Fault("sharing drain identity/capability");
        refreshDrains();
        if(begin){bool found=false;for(const auto& d:drains)if(d.target==c.token)found=true;
            if(!found){auto slot=std::find_if(drains.begin(),drains.end(),[](const auto& d){return !d.target;});
                if(slot==drains.end()||nextDrain==UINT64_MAX)throw Fault("sharing drain capacity/identity");
                slot->target=c.token;slot->domain=identity->domain;slot->session=identity->session;slot->id=++nextDrain;
                if(++drainRevision==0)throw Fault("sharing drain revision exhausted");refreshDrains();
            }
        }
        for(unsigned aid=0;aid<8;++aid){
            if(bool(offered&(1u<<aid))!=bool(ids[aid]))throw Fault("sharing drain offer mask");
            if(!ids[aid])continue;
            for(auto& d:drains)if(d.id==ids[aid]&&d.target){
                if(d.domain!=identity->domain||d.session.aid!=aid||d.members[expected.aid]!=c.token||d.epochs[expected.aid]!=expected)
                    throw Fault("sharing drain foreign offer");
                d.offered|=std::uint16_t(1u<<expected.aid);
            }
        }
        refreshDrains();encodeDrainNotice(w,drainView(c));
    }
    enum class Cut { Disabled, Running, Preparing, Frozen, Releasing, Restoring };
    Cut cut = Cut::Disabled;
    std::uint64_t operation = 0, lastOperation = 0, incarnation = 1, frozenAt = 0;

    bool parked() const { return cut == Cut::Frozen || cut == Cut::Releasing || cut == Cut::Restoring; }
    bool cohort() const {
        for (unsigned i = 0; i < capacity; ++i) {
            if (!clients[i].handle || !clients[i].token) return false;
            for (unsigned j = 0; j < i; ++j) if (clients[i].token == clients[j].token) return false;
        }
        return true;
    }
    bool sharingReady() const {
        if(!requireSharingWindow||!cohort())return false;
        for(unsigned i=0;i<capacity;++i)if(!sharingClaims[i])return false;
        return true;
    }
    void marker(Client& c, Code code) {
        Writer w; w.bytes = snapshotMarker(code, operation, incarnation, c.token); emit(c, w);
    }
    void freeze() {
        if (!cohort()) throw Fault("browser snapshot cohort incomplete");
        cut = Cut::Frozen; frozenAt = clock;
        for (auto& c : clients) if (c.handle) marker(c, SnapshotFrozen);
    }
    static std::uint64_t age(const Deferred& d, std::uint64_t at) {
        if (at < d.queued || at - d.queued > UINT64_MAX - d.ageBase) throw Fault("browser snapshot clock range");
        return d.ageBase + at - d.queued;
    }
    void cutControl(Client& c, Code code, Reader& r) {
        const auto control = decodeSnapshotControl(r);
        if (!operation || control.operation != operation || control.incarnation != incarnation || control.endpoint != c.token)
            throw Fault("browser snapshot identity");
        if (code == PrepareSnapshot) {
            if (cut != Cut::Preparing || c.prepared) throw Fault("browser snapshot prepare not admitted");
            c.prepared = true;
            bool all = cohort(); for (unsigned i = 0; i < capacity; ++i) all = all && clients[i].prepared;
            if (all) freeze();
        } else {
            if ((cut != Cut::Frozen && cut != Cut::Releasing) || !c.prepared || c.released)
                throw Fault("browser snapshot release not admitted");
            // Native writes the complete cohort's markers before accepting
            // release. Here pop() is the carrier-admission frontier; merely
            // enqueueing a marker cannot acknowledge delivery to an owner.
            for (const auto& peer : clients) if (!peer.output.empty())
                throw Fault("browser snapshot freeze output pending");
            c.released = true; cut = Cut::Releasing;
            bool all = cohort(); for (unsigned i = 0; i < capacity; ++i) all = all && clients[i].released;
            if (all) {
                for (auto& peer : clients) if (peer.handle) marker(peer, SnapshotReleased);
                for (auto& d : deferred) {
                    d.ageBase = age(d, frozenAt); d.queued = clock;
                    d.due = clock + (d.due > frozenAt ? d.due - frozenAt : 0);
                }
                lastOperation = operation; cut = Cut::Running;
                for (auto& peer : clients) { peer.prepared = false; peer.released = false; }
            }
        }
    }

    Impl(unsigned players, unsigned rtt) : capacity(players), delayUs(rtt * 500) {
        if (players < 2 || players > 8 || rtt > 1000) throw Fault("browser broker configuration");
    }
    Client* find(Handle h) {
        for (auto& c : clients) if (h && c.handle == h) return &c;
        return nullptr;
    }
    void time(std::uint64_t now) {
        if (now > 9007199254740991ULL || now * 1000 < clock) throw Fault("browser broker clock");
        clock = now * 1000;
    }
    template<class F> bool attempt(F action) {
        if (failed) return false;
        try { action(); return true; }
        catch (...) {
#if defined(NIFI2_PRIVATE_BROKER_FAULT_TRACE)
            // Fixed internal reasons only; no packet bytes or endpoint secrets.
            try { throw; }
            catch (const std::exception& e) {
                std::fprintf(stderr, "[browser-broker-fault] reason=%s\n", e.what());
            }
            catch (...) { std::fprintf(stderr, "[browser-broker-fault] reason=unknown\n"); }
#endif
            // Never resume a partly applied exchange or silently drop output.
            // Destroying this disposable module is the only recovery boundary.
            failed = true;
            deferred.clear(); originals.clear();
            for (auto& c : clients) c.output.clear();
            return false;
        }
    }
    void emit(Client& c, const Writer& w) {
        if (w.bytes.empty() || w.bytes.size() > 1024 || c.output.size() >= 2 * Bus::MaxInflight)
            throw Fault("browser broker output capacity");
        c.output.push_back(c.fenced ? incarnatedPacket(w.bytes, incarnation) : w.bytes);
    }
    void purgeTerminal(Client& c) {
        if (!c.terminal) return;
        deferred.erase(std::remove_if(deferred.begin(), deferred.end(), [&](const Deferred& d) {
            return d.handle == c.handle && d.packet[0] == SendTerminal;
        }), deferred.end());
        for (auto i = originals.begin(); i != originals.end();)
            if (i->first.first == c.token && i->second.send_id == c.terminal) i = originals.erase(i);
            else ++i;
        c.terminal = 0;
    }
    void purge(Client& c) {
        c.terminal = 0; c.watch = {};
        deferred.erase(std::remove_if(deferred.begin(), deferred.end(), [&](const Deferred& d) {
            return d.handle == c.handle;
        }), deferred.end());
        for (auto i = originals.begin(); i != originals.end();)
            if (i->first.first == c.token) i = originals.erase(i); else ++i;
    }
    void packet(Client& c, const Bytes& bytes) {
        Reader r(bytes);
        const auto code = static_cast<Code>(r.u8());
        const auto index=static_cast<unsigned>(&c-clients.data());
        if(code==BlockSend&&(!requireSharingWindow||sharingVersion!=sharing_policy::BlockVersion||!sharingClaims[index]))
            throw Fault("block lane requires negotiated v5 cohort");
        const bool retiringData=(code==Send||code==SendTerminal||code==BlockSend)&&sharingNegotiated&&sharingClaims[index];
        // Idle scheduling, cleanup and genuine receipts remain possible after
        // a peer closes. A new association still requires the full cohort.
        const bool association=code==Advertise||code==Scan||code==Connect||code==Start;
        const bool data=code==Send||code==SendTerminal||code==BlockSend;
        if(requireSharingWindow&&!sharingReady()&&data&&!retiringData)
            throw Fault("sharing window cohort incomplete");
        if (code == PrepareSnapshot || code == ReleaseSnapshot) { cutControl(c, code, r); return; }
        if (parked() || c.prepared) throw Fault("browser snapshot owner parked");
        if (cut == Cut::Preparing && (code == SendTerminal || code == WatchMpReady))
            throw Fault("browser snapshot unsupported subscription/terminal");
        if (code == Send || code == SendTerminal || code == BlockSend || code == Ack) {
            if (!c.token || r.u64() != c.token) throw Fault("browser broker foreign token");
            Bus::SendId send = 0;
            if (code == Ack) {
                if (!r.u64()) throw Fault("browser broker ACK domain");
                (void)r.token();
            } else {
                const auto m = r.message(code==BlockSend);
                if((code==BlockSend)!=(m.port==Bus::BlockPort))throw Fault("block lane port/code binding");
                const auto n = r.u16();
                if (!n || n > 512 || m.send_id <= c.lastSend) throw Fault("browser broker send identity/size");
                (void)r.array<512>(); send = m.send_id;
                if(requireSharingWindow)for(const auto& d:drains)if(d.target&&m.source.aid<8&&d.members[m.source.aid]==c.token&&d.epochs[m.source.aid]==m.source&&
                    (d.offered&(1u<<m.source.aid))&&(c.token==d.target||(m.targets&(1u<<d.session.aid))))
                    throw Fault("sharing drain sender resumed after offer");
                if (code == SendTerminal && c.terminal) throw Fault("browser broker terminal reserved");
            }
            r.end();
            if (deferred.size() >= Bus::MaxInflight * capacity) throw Fault("browser broker input capacity");
            deferred.push_back({c.handle, clock, clock + delayUs, bytes});
            if (send) c.lastSend = send;
            if (code == SendTerminal) c.terminal = send;
            return;
        }
        const auto id = r.u64();
        if (!id) throw Fault("browser broker RPC identity");
        Writer w; w.u8(Reply); w.u64(id);
        if (code == Open) {
            const auto mac = r.array<6>(); r.end();
            if (c.token) throw Fault("browser broker already open");
            c.token = registry.open(mac); w.u64(c.token);
        } else {
            const auto token = r.u64();
            if (!token || token != c.token) throw Fault("browser broker foreign token");
            if(requireSharingWindow&&association&&!sharingClaims[index])
                throw Fault("sharing association without owner capability");
            const bool admit=!requireSharingWindow||sharingReady();
            const auto pending=[&](){
                Writer notice;notice.u8(ReplyAssociationPending);notice.u64(id);
                notice.u16(3);notice.u8(code);notice.u64(token);emit(c,notice);
            };
            switch (code) {
            case ClaimSharingWindow: {
                const auto version=r.u16(),depth=r.u16();r.end();
                if(!requireSharingWindow){w.u8(2);w.u16(3);w.u16(64);break;}
                if(version!=sharingVersion||depth!=sharingLead)throw Fault("sharing window capability mismatch");
                sharingClaims[index]=true;const bool ready=sharingReady()&&!domainDraining(registry.associationDomain(token));
                if(ready)sharingNegotiated=true;
                w.u8(ready?1:0);w.u16(sharingVersion);w.u16(sharingLead);break;
            }
            case SharingDrain: drainControl(c,r,w);break;
            case Close:
                r.end(); registry.close(token); purge(c); c.token = 0; c.saved = {};
                c.drainSent.clear();c.drainHadTargets=false;
                sharingClaims[static_cast<unsigned>(&c-clients.data())]=false;break;
            case Advertise: {
                const auto bss = r.array<192>(); const auto count = r.u16(); r.end();
                const auto result=registry.tryAdvertise(token,bss,count,admit);
                if(!result){pending();return;}w.boolean(*result);break;
            }
            case Scan: {
                const auto channel = r.u16(); const auto filter = r.array<6>(); r.end();
                const auto result=registry.tryScan(token,channel,filter,admit);
                if(!result){pending();return;}w.u64(result->parent);w.array(result->bss);break;
            }
            case Connect: {
                const auto parent = r.u64(); const auto bss = r.array<192>(); const auto data = r.array<24>(); r.end();
                const auto connected=registry.tryConnect(token,parent,bss,data,
                    admit&&!domainDraining(registry.associationDomain(parent)));
                if(!connected){pending();return;}
                if (*connected) { purgeTerminal(c); c.watch = {}; }
                w.boolean(*connected); break;
            }
            case Disconnect: {
                const auto mask = r.u16(); r.end(); w.u16(registry.disconnect(token, mask)); break;
            }
            case Start: {
                r.end();const auto result=registry.tryStartMp(token,
                    admit&&!domainDraining(registry.associationDomain(token)));
                if(!result){pending();return;}c.watch = {};w.status(result->status);
                if (result->status == Bus::Status::Ok) { purgeTerminal(c); w.session(result->session); }
                break;
            }
            case Stop: r.end(); registry.stopMp(token); purge(c);c.drainSent.clear();c.drainHadTargets=false;break;
            case RetainReceipts: r.end(); w.boolean(registry.enableReceiptRetention(token)); break;
            case StopReceipts: {
                const auto version=r.u16();r.end();
                if(version!=1 || registry.pollSavedRoom(token).room){w.u8(0);break;}
                // Retire accepted wire sends which never entered the bus. An
                // unready recipient cannot be allowed to prevent EndMP. These
                // exact-identity cancellations precede the Reply barrier; ACKs
                // are not accelerated and still drain through normal ticks.
                for(auto it=deferred.begin();it!=deferred.end();){
                    if(it->handle!=c.handle || (it->packet[0]!=Send && it->packet[0]!=SendTerminal && it->packet[0]!=BlockSend)){++it;continue;}
                    Reader pending(it->packet);pending.u8();
                    if(pending.u64()!=token)throw Fault("browser stop pending token");
                    Bus::Completion cancelled{};cancelled.message=pending.message(sharingVersion==sharing_policy::BlockVersion);
                    pending.u16();pending.array<512>();pending.end();
                    cancelled.outcome=Bus::Outcome::Cancelled;cancelled.reason=Bus::Reason::SenderLeft;
                    Writer notification;notification.u8(Complete);encodeCompletion(notification,cancelled);emit(c,notification);
                    if(c.terminal==cancelled.message.send_id)c.terminal=0;
                    it=deferred.erase(it);
                }
                if(std::any_of(deferred.begin(),deferred.end(),[&](const Deferred& d){return d.handle==c.handle;})){
                    w.u8(2);break;
                }
                auto receipts=registry.stopMpWithReceipts(token);
                if(!receipts){w.u8(0);break;}
                if(receipts->size()>Transport::MaxStopMpReceipts)throw Fault("browser stop receipt capacity");
                // Preserve1024-byte wire records: real terminal receipts use
                // existing Complete notifications before the final Reply barrier.
                // Remote already merges these exact identities with its inbox.
                const bool stream=receipts->size()>32;
                w.u8(1);w.u8(stream?0:static_cast<std::uint8_t>(receipts->size()));
                for(auto& receipt:*receipts){
                    auto i=originals.find({token,receipt.message.send_id});
                    if(i==originals.end())throw Fault("browser unknown stop receipt mapping");
                    receipt.message=i->second;originals.erase(i);
                    if(stream){Writer notification;notification.u8(Complete);encodeCompletion(notification,receipt);emit(c,notification);}
                    else encodeCompletion(w,receipt);
                }
                purge(c);break;
            }
            case MpReady: {
                const auto session = r.session(); r.end(); const auto mask = registry.mpReadyMask(token, session);
                w.boolean(bool(mask)); if (mask) w.u16(*mask); break;
            }
            case WatchMpReady: {
                const auto session = r.session(); const auto watch = r.u64(); r.end();
                if (!watch || watch <= c.lastWatch) throw Fault("browser broker watch identity");
                c.watch = {token, session, watch, 1, registry.mpReadyMask(token, session)};
                c.lastWatch = watch; encodeReadyWatch(w, c.watch); break;
            }
            case AdoptSaved: case PollSaved: case LookupSaved:
                savedControl(registry, code, token, r, w); break;
            default: throw Fault("browser broker unsupported control");
            }
        }
        r.end(); emit(c, w);
    }
    void completions(Client& c) {
        for (unsigned budget = 0; budget < 8; ++budget) {
            auto result = registry.completion(c.token);
            if (result.status != Bus::Status::Ok) break;
            const auto i = originals.find({c.token, result.completion.message.send_id});
            if (i == originals.end()) throw Fault("browser broker unknown completion");
            result.completion.message = i->second; originals.erase(i);
            Writer w; w.u8(Complete); encodeCompletion(w, result.completion); emit(c, w);
            if (c.terminal == result.completion.message.send_id) c.terminal = 0;
        }
    }
    void pump() {
        if (parked()) return;
        if(deliveryHold && clock-holdSince>60000000)throw Fault("broker delivery hold exceeded60 seconds");
        // Readiness is published before DATA, as in the native broker.
        for (auto& c : clients) if (c.handle && c.token) {
            const auto saved = registry.pollSavedRoom(c.token);
            if (saved.room && (saved.room != c.saved.room || saved.state != c.saved.state || saved.reason != c.saved.reason)) {
                Writer w; w.u8(SavedStatus); encodeSavedResult(w, saved); emit(c, w); c.saved = saved;
            }
            if (saved.room && saved.state == SavedRoomState::Rejected) purge(c);
        }
        std::array<bool, 8> blocked{};
        for (auto i = deferred.begin(); !deliveryHold && i != deferred.end();) {
            auto* c = find(i->handle);
            if (!c || !c->token) { i = deferred.erase(i); continue; }
            const auto slot = static_cast<std::size_t>(c - clients.data());
            if (blocked[slot] || i->due > clock) { blocked[slot] = true; ++i; continue; }
            Reader r(i->packet); const auto code = r.u8(); const auto token = r.u64();
            if (token != c->token) throw Fault("browser broker retired deferred token");
            if (code == Send || code == SendTerminal || code == BlockSend) {
                const auto m = r.message(code==BlockSend); const auto length = r.u16(); const auto bytes = r.array<512>(); r.end();
                const auto source = registry.currentMp(token);
                if (source.status == Bus::Status::SourceNotReady) {
                    if (code == SendTerminal) c->terminal = 0;
                    i = deferred.erase(i); continue;
                }
                if (source.status != Bus::Status::Ok || source.session != m.source) throw Fault("browser broker source epoch");
                const auto saved = registry.pollSavedRoom(token);
                if (saved.room && (saved.state != SavedRoomState::Ready || saved.session != m.source))
                    throw Fault("browser broker saved epoch");
                const auto result = code == BlockSend
                    ? registry.sendBlock(token,m.targets,bytes,length)
                    : code == SendTerminal
                    ? registry.sendTerminal(token, m.targets, m.port, bytes, length, m.wire_sequence)
                    : registry.send(token, m.targets, m.port, bytes, length, m.wire_sequence);
                if (result.status == Bus::Status::Backpressure || result.status == Bus::Status::MissingRecipient ||
                    result.status == Bus::Status::RecipientNotReady) {
                    if (age(*i, clock) > 5000000) throw Fault("browser broker send admission timeout");
                    blocked[slot] = true; ++i; continue;
                }
                if (result.status != Bus::Status::Ok || result.message.source != m.source || originals.size() >= Bus::MaxInflight ||
                    !originals.emplace(std::make_pair(token, result.message.send_id), m).second)
                    throw Fault("browser broker send mapping");
            } else if (code == Ack) {
                const auto domain = r.u64(); const auto receipt = r.token(); r.end();
                if (registry.liveReceiptSessions(token, domain, receipt)) {
                    const auto status = registry.acknowledge(token, domain, receipt);
                    if (status != Bus::Status::Ok && status != Bus::Status::InvalidToken && status != Bus::Status::StaleSession)
                        throw Fault("browser broker receive ACK");
                }
                // Stale receipts get NO credit, including a retired multicast.
            } else throw Fault("browser broker deferred code");
            i = deferred.erase(i);
        }
        for (auto& c : clients) if (c.handle && c.token) {
            if (c.watch.id) {
                const auto mask = registry.mpReadyMask(c.token, c.watch.session);
                if (mask != c.watch.mask) {
                    if (c.watch.revision == UINT64_MAX) throw Fault("browser broker watch exhausted");
                    c.watch.mask = mask; ++c.watch.revision;
                    Writer w; w.u8(MpReadyStatus); encodeReadyWatch(w, c.watch); emit(c, w);
                }
            }
            // Opt-in terminal completion must precede the disconnect event.
            if (c.terminal) completions(c);
            for (unsigned n = 0; n < 16; ++n) {
                Event event; bool fault = false;
                if (!registry.pop(c.token, event, fault)) {
                    if (fault) throw Fault("browser broker member queue");
                    break;
                }
                Writer w; w.u8(Member); w.event(event); emit(c, w);
            }
            for (unsigned n = 0; n < 8; ++n) {
                Token domain = 0; const auto result = registry.take(c.token, domain);
                if (result.status != Bus::Status::Ok) break;
                Writer w; w.u8(Receive); encodeDelivery(w, domain, result.delivery); emit(c, w);
            }
            completions(c);
            // SessionRegistry discards nonterminal retired-source completions.
            // Retire their adapter mappings too, preventing reconnect leaks.
            const auto current = registry.currentMp(c.token);
            for (auto i = originals.begin(); i != originals.end();)
                if (i->first.first == c.token && i->second.send_id != c.terminal &&
                    (current.status != Bus::Status::Ok || i->second.source != current.session)) i = originals.erase(i);
                else ++i;
        }
        publishDrains();
    }
};

Broker::Broker(unsigned players, unsigned rtt) : impl(std::make_unique<Impl>(players, rtt)) {}
Broker::Broker(unsigned players, SharingWindow64, unsigned rtt) : Broker(players,rtt) {
    if(Bus::MaxCompletionsPerEndpoint!=64)throw Fault("private sharing64 requires64 real wire credits");
    impl->requireSharingWindow=true;
}
Broker::Broker(unsigned players, SharingPipeline policy, unsigned rtt) : Broker(players,SharingWindow64{},rtt) {
    if(!sharing_policy::validLead(policy.lead))throw Fault("sharing pipeline lead");
    impl->sharingVersion=sharing_policy::PipelineVersion;impl->sharingLead=policy.lead;
}
Broker::Broker(unsigned players,SharingBlocks policy,unsigned rtt):Broker(players,SharingPipeline{policy.lead},rtt){
    impl->sharingVersion=sharing_policy::BlockVersion;
}
Broker::Broker(unsigned players, CoordinatedSnapshots, unsigned rtt) : Broker(players, rtt) { impl->cut = Impl::Cut::Running; }
Broker::~Broker() = default;
Broker::Handle Broker::attach() {
    if (impl->failed || !impl->nextHandle || (impl->cut != Impl::Cut::Disabled && impl->cut != Impl::Cut::Running)) return 0;
    for (unsigned n = 0; n < impl->capacity; ++n) if (!impl->clients[n].handle) {
        auto& c = impl->clients[n]; c = {}; c.handle = impl->nextHandle++;
        c.fenced = impl->incarnation != 1; return c.handle;
    }
    return 0;
}
bool Broker::detach(Handle h) {
    auto* c = impl->find(h);
    if (!c || impl->failed) return false;
    return impl->attempt([&] {
        if (impl->cut != Impl::Cut::Disabled && impl->cut != Impl::Cut::Running)
            throw Fault("browser snapshot participant lost");
        if (c->token) impl->registry.close(c->token);
        impl->sharingClaims[static_cast<unsigned>(c-impl->clients.data())]=false;
        impl->purge(*c); *c = {}; impl->pump();
    });
}
bool Broker::receive(Handle h, const Bytes& bytes, std::uint64_t now) {
    return impl->attempt([&] {
        impl->time(now); auto* c = impl->find(h);
        if (!c || bytes.empty() || bytes.size() > 1024) throw Fault("browser broker input");
        auto body = bytes;
        if (c->fenced) consumeIncarnation(body, impl->incarnation);
        if (!impl->registry.dispatchInIncarnation(impl->incarnation, [&](SessionRegistry&) {
            impl->packet(*c, body); impl->pump();
        })) throw Fault("browser broker retired incarnation");
    });
}
bool Broker::tick(std::uint64_t now) { return impl->attempt([&] { impl->time(now); impl->pump(); }); }
bool Broker::setDeliveryTiming(unsigned rtt, bool hold, std::uint64_t now) {
    return impl->attempt([&] {
        impl->time(now);
        if(rtt>1000 || (impl->cut!=Impl::Cut::Disabled&&impl->cut!=Impl::Cut::Running))
            throw Fault("broker delivery timing configuration");
        // Releasing an expired hold cannot bypass the same limit as tick().
        if(impl->deliveryHold&&impl->clock-impl->holdSince>60000000)
            throw Fault("broker delivery hold exceeded60 seconds");
        impl->delayUs=rtt*500;
        if(hold&&!impl->deliveryHold)impl->holdSince=impl->clock;
        impl->deliveryHold=hold;
        impl->pump();
    });
}
std::size_t Broker::pendingRecords() const { return impl->deferred.size(); }
const Broker::Bytes* Broker::peek(Handle h) const {
    const auto* c = impl->find(h);
    return !impl->failed && c && !c->output.empty() ? &c->output.front() : nullptr;
}
bool Broker::pop(Handle h) {
    auto* c = impl->find(h);
    if (!peek(h)) return false;
    c->output.pop_front(); return true;
}
bool Broker::failed() const { return impl->failed; }
snapshot::Status Broker::beginSnapshot(std::uint64_t op) {
    using S = snapshot::Status;
    if (impl->cut == Impl::Cut::Disabled) return S::Unsupported;
    if (impl->deliveryHold) return S::Unsupported;
    if (impl->failed || impl->cut != Impl::Cut::Running || !impl->cohort() || !op || op <= impl->lastOperation)
        return S::NotQuiescent;
    for (const auto& c : impl->clients) if (c.terminal) return S::Unsupported;
    impl->operation = op; impl->cut = Impl::Cut::Preparing; return S::Ok;
}
bool Broker::snapshotFrozen(std::uint64_t op) const {
    if (impl->failed || !op || impl->operation != op || impl->cut != Impl::Cut::Frozen || !impl->cohort()) return false;
    for (const auto& c : impl->clients) if (!c.output.empty()) return false;
    return true;
}
bool Broker::snapshotReleased(std::uint64_t op) const {
    return !impl->failed && op && impl->lastOperation == op && impl->cut == Impl::Cut::Running;
}
bool Broker::emptyPreparation(std::uint64_t op) {
    if (!snapshotFrozen(op) || !impl->deferred.empty() || !impl->originals.empty()) return false;
    for (const auto& c : impl->clients) if (c.terminal || c.watch.id) return false;
    try {
        // Export under the actual registry lock, including its terminal-state
        // rejection. Decoding uses our native codec, never a JS queue estimate.
        Bytes bytes;
        auto guard = impl->registry.quiesceForSnapshot();
        if (impl->registry.exportSnapshot(guard, bytes) != snapshot::Status::Ok) return false;
        const auto state = snapshot::RegistryCodec::decode(bytes);
        for (const auto& e : state->endpoints) if (e.token && (e.fault || e.count)) return false;
        for (const auto& medium : state->media) {
            for (const auto& send : medium.sends) if (send.used) return false;
            for (const auto& e : medium.endpoints) {
                if (e.inflight) return false;
                for (const auto& completion : e.completions)
                    if (completion.event.message.send_id || completion.ready) return false;
            }
        }
        return true;
    } catch (...) { return false; }
}
snapshot::Status Broker::exportSnapshot(std::uint64_t op, Bytes& output) {
    using S = snapshot::Status;
    if (!snapshotFrozen(op)) return S::NotQuiescent;
    for (const auto& c : impl->clients) {
        if (c.terminal) return S::Unsupported;
        if (!c.output.empty()) return S::NotQuiescent;
    }
    return checkpoint::attempt([&] {
        checkpoint::Image s;
        s.capacity = impl->capacity; s.rtt = impl->delayUs / 500; s.nextHandle = impl->nextHandle;
        s.incarnation = impl->incarnation; s.operation = op; s.originals = impl->originals;
        for (unsigned i = 0; i < s.capacity; ++i) {
            const auto& c = impl->clients[i]; s.peers.push_back({c.token, c.saved, c.lastSend, c.lastWatch,c.watch});
        }
        for (const auto& d : impl->deferred) {
            const auto* c = impl->find(d.handle); snapshot::require(c != nullptr);
            s.pending.push_back({static_cast<unsigned>(c - impl->clients.data()),
                d.due > impl->frozenAt ? d.due - impl->frozenAt : 0, Impl::age(d, impl->frozenAt), d.packet});
        }
        {
            auto guard = impl->registry.quiesceForSnapshot();
            const auto status = impl->registry.exportSnapshot(guard, s.registry);
            snapshot::require(status == S::Ok, status);
        }
        checkpoint::validate(s); auto bytes = checkpoint::encode(s); output.swap(bytes);
    });
}
snapshot::Status Broker::validateSnapshot(const Bytes& bytes) {
    return checkpoint::attempt([&] { (void)checkpoint::decode(bytes); });
}
snapshot::Status Broker::restoreSnapshot(const Bytes& bytes, std::uint64_t fresh, std::uint64_t op,
                                       std::unique_ptr<Broker>& output) {
    if (output) return snapshot::Status::NotQuiescent;
    return checkpoint::attempt([&] {
        auto s = checkpoint::decode(bytes);
        snapshot::require(fresh > s->incarnation, snapshot::Status::StaleIncarnation);
        snapshot::require(op != 0);
        auto b = std::make_unique<Broker>(s->capacity, CoordinatedSnapshots{}, s->rtt);
        auto& state = *b->impl;
        state.cut = Impl::Cut::Restoring; state.operation = op; state.incarnation = fresh;
        state.nextHandle = s->nextHandle;
        for (unsigned i = 0; i < s->capacity; ++i) {
            auto& c = state.clients[i]; const auto& p = s->peers[i];
            c.token = p.token; c.saved = p.saved; c.lastSend = p.lastSend; c.lastWatch = p.lastWatch;
            c.watch=p.watch;
            c.prepared = true; c.fenced = true;
            // Reserve fresh handles now, without making channels visible.
            snapshot::require(state.nextHandle != 0, snapshot::Status::ResourceLimit);
            const auto reserved = state.nextHandle++;
            state.restoredHandles[i] = reserved;
        }
        for (auto& d : s->pending)
            state.deferred.push_back({state.restoredHandles[d.peer], 0, d.remaining, std::move(d.packet), d.age});
        state.originals.swap(s->originals);
        {
            auto guard = state.registry.quiesceForSnapshot();
            const auto status = state.registry.importSnapshot(guard, s->registry, fresh);
            snapshot::require(status == snapshot::Status::Ok, status);
        }
        output.swap(b);
    });
}
Broker::Handle Broker::attachRestored(std::uint64_t endpoint) {
    if (impl->failed || impl->cut != Impl::Cut::Restoring || !endpoint) return 0;
    for (unsigned i = 0; i < impl->capacity; ++i) {
        auto& c = impl->clients[i];
        if (c.token != endpoint || c.handle) continue;
        const auto handle = impl->restoredHandles[i];
        if (!impl->attempt([&] {
            c.handle = handle;
            if (impl->cohort()) {
                const auto origin = impl->frozenAt;
                impl->freeze(); impl->frozenAt = origin;
            }
        })) return 0;
        return handle;
    }
    return 0;
}
} // namespace melonDS::wm_sdk::browser
