#ifndef MELONDS_WM_SDK_SNAPSHOT_REMOTE_H
#define MELONDS_WM_SDK_SNAPSHOT_REMOTE_H
#include "WmSdkNet.h"
#include "WmSdkSnapshotMedium.h"

namespace melonDS::wm_sdk::snapshot {
// 512 delivery credits with 512-byte payloads exceed the former 256 KiB WAN cap.
inline constexpr std::size_t MaxRemoteImageBytes = wire_window::Wide ? 512 * 1024 : 64 * 1024;
inline void validateReadyWatch(const net::ReadyWatch& w,Token endpoint,std::uint64_t highWater){
    if(!w.id){require(!w.endpoint&&w.session==Bus::Session{}&&!w.revision&&!w.mask);return;}
    require(w.endpoint==endpoint&&endpoint&&w.id<=highWater&&w.revision&&w.session.epoch&&w.session.aid<8);
    if(w.mask)require(*w.mask<=255&&!(*w.mask&(1u<<w.session.aid))&&
        (!w.session.aid||!(*w.mask&~1u)));
}
template<class A> void readyWatchFields(A& a,net::ReadyWatch& w){
    a(w.endpoint);session(a,w.session);a(w.id);a(w.revision);
    bool mask=bool(w.mask);a(mask);
    if(mask){std::uint16_t value=w.mask.value_or(0);a(value);if constexpr(A::Reading)w.mask=value;}
    else if constexpr(A::Reading)w.mask.reset();
}
class RemoteCodec {
public:
    using Remote = net::Remote;
    struct State {
        Remote::DeliveryTiming timing = Remote::DeliveryTiming::NextFrame;
        std::uint16_t subframePorts = 0;
        Token own = 0;
        Bus::Session session{};
        std::uint64_t frame = 0, nextRequest = 0, replyRequest = 0, nextSend = 0;
        std::uint64_t incarnation = 0, operation = 0;
        std::array<std::uint16_t,16> sequence{};
        Bytes reply;
        std::deque<Event> events;
        std::deque<Remote::Incoming> incoming;
        std::map<std::uint64_t,Remote::Incoming> taken;
        std::map<std::uint64_t,Bus::Message> pending;
        std::deque<Bus::Completion> completed;
        SavedRoomResult saved{};
        bool retainReceipts=false;
        std::uint64_t nextReadyWatch=0;
        net::ReadyWatch readyWatch{};
    };
    static State capture(const Remote& r) {
        require(!r.terminalSend&&!r.sharingWindowClaimed,Status::Unsupported);
        // Failed/closing/active-RPC state cannot establish a checkpoint cut.
        // Sockets, mutexes, CVs, threads and failure diagnostics are not images.
        require(!r.failed && !r.closing && !r.activeRpc && r.snapshotFrozen && r.snapshotPreparing &&
                !r.snapshotReleasing && !r.expectRestoreMarker && r.own && r.snapshotOperation,
                Status::NotQuiescent);
        State s;
        s.timing=r.deliveryTiming;s.own=r.own;s.session=r.session;s.frame=r.frame;
        s.subframePorts=r.subframePorts;
        s.nextRequest=r.nextRequest;s.replyRequest=r.replyRequest;s.nextSend=r.nextSend;
        s.incarnation=r.transportIncarnation;s.operation=r.snapshotOperation;s.sequence=r.sequence;
        s.reply=r.reply;s.events=r.events;s.incoming=r.incoming;s.taken=r.taken;
        s.pending=r.pending;s.completed=r.completed;s.saved=r.saved;
        s.retainReceipts=r.retainReceipts;s.nextReadyWatch=r.nextReadyWatch;s.readyWatch=r.readyWatch;return s;
    }
    template<class A> static void incomingFields(A& a, Remote::Incoming& v) {
        a(v.domain); require(v.domain); delivery(a,v.d); a(v.eligible);
    }
    template<class A, class Q, class F> static void queue(A& a, Q& q, unsigned max, F field) {
        auto n=q.size();count(a,n,max);
        if constexpr (A::Reading) q.resize(n);
        for(auto& v:q) field(a,v);
    }
    template<class A> static void fields(A& a, State& s) {
        if constexpr (!A::Reading) require(static_cast<unsigned>(s.timing)<=2);
        enumeration(a,s.timing,2);
        // Preserve the version-1 NextFrame/Subframe bodies byte for byte.
        if(s.timing==Remote::DeliveryTiming::InitializedPorts)a(s.subframePorts);
        else {
            if constexpr (A::Reading) s.subframePorts=0;
            else require(!s.subframePorts);
        }
        a(s.own);session(a,s.session,true);
        a(s.frame);a(s.nextRequest);a(s.replyRequest);a(s.nextSend);a(s.operation);a(s.sequence);
        queue(a,s.reply,1024,[](auto& ar,auto& v){ar(v);});
        queue(a,s.events,16,[](auto& ar,auto& v){event(ar,v);});
        queue(a,s.incoming,Bus::MaxDeliveriesPerEndpoint,[](auto& ar,auto& v){incomingFields(ar,v);});
        auto n=s.taken.size();count(a,n,Bus::MaxDeliveriesPerEndpoint);
        if constexpr (A::Reading) {
            for(std::size_t i=0;i<n;++i) {
                std::uint64_t key=0;Remote::Incoming v{};a(key);incomingFields(a,v);
                require(key==v.d.token.send_id && s.taken.emplace(key,std::move(v)).second);
            }
        } else for(auto& entry:s.taken) {auto key=entry.first;a(key);incomingFields(a,entry.second);}
        n=s.pending.size();count(a,n,Bus::MaxCompletionsPerEndpoint);
        if constexpr (A::Reading) {
            for(std::size_t i=0;i<n;++i) {
                std::uint64_t key=0;Bus::Message v{};a(key);message(a,v);
                require(key==v.send_id && s.pending.emplace(key,v).second);
            }
        } else for(auto& entry:s.pending) {auto key=entry.first;a(key);message(a,entry.second);}
        queue(a,s.completed,Bus::MaxCompletionsPerEndpoint,[](auto& ar,auto& v){completion(ar,v);});
        saved(a,s.saved);
        // Preserve legacy v1 bodies. An explicit trailer carries receipt
        // policy and the subscription high-water even after a watch ended.
        bool extended=s.retainReceipts||s.nextReadyWatch;
        if constexpr(A::Reading)extended=a.pos<a.bytes.size();
        if(extended){
            std::uint32_t tag=0x31504352;a(tag);require(tag==0x31504352,Status::WrongVersion);
            a(s.retainReceipts);a(s.nextReadyWatch);
            require(s.retainReceipts||s.nextReadyWatch);
        }
        bool watch=bool(s.readyWatch.id);
        if constexpr(A::Reading)watch=a.pos<a.bytes.size();
        if(watch){
            std::uint32_t tag=0x31594452;a(tag);require(tag==0x31594452,Status::WrongVersion);
            readyWatchFields(a,s.readyWatch);require(s.readyWatch.id);
        }
    }
    static void validate(const State& s) {
        require(static_cast<unsigned>(s.timing)<=2);
        require(!s.subframePorts || (s.timing==Remote::DeliveryTiming::InitializedPorts && s.session.epoch));
        require(s.own && s.operation && s.incarnation && s.nextRequest==s.replyRequest);
        validateReadyWatch(s.readyWatch,s.own,s.nextReadyWatch);
        require(s.incoming.size()+s.taken.size()<=Bus::MaxDeliveriesPerEndpoint);
        require(s.session.epoch || (s.incoming.empty()&&s.taken.empty()&&s.pending.empty()&&s.completed.empty()));
        std::array<std::uint64_t,Bus::MaxDeliveriesPerEndpoint> delivered{};unsigned deliveryCount=0;
        const auto deliveryIdentity=[&](const Remote::Incoming& v) {
            require(v.d.token.recipient==s.session);
            for(unsigned i=0;i<deliveryCount;++i) require(delivered[i]!=v.d.token.send_id);
            delivered[deliveryCount++]=v.d.token.send_id;
        };
        for(const auto& v:s.incoming) deliveryIdentity(v);
        for(const auto& kv:s.taken) {require(kv.first==kv.second.d.token.send_id);deliveryIdentity(kv.second);}
        for(const auto& kv:s.pending) require(kv.first==kv.second.send_id && kv.first<=s.nextSend && kv.second.source==s.session);
        std::array<std::uint64_t,Bus::MaxCompletionsPerEndpoint> completions{};unsigned completionCount=0;
        for(const auto& c:s.completed) {
            terminal(c);const auto pending=s.pending.find(c.message.send_id);
            require(pending!=s.pending.end() && sameMessage(pending->second,c.message));
            for(unsigned i=0;i<completionCount;++i) require(completions[i]!=c.message.send_id);
            completions[completionCount++]=c.message.send_id;
        }
        if(s.saved.state==SavedRoomState::Ready) require(s.saved.session==s.session);
    }
    static State decode(const Bytes& bytes) {
        require(bytes.size()<=MaxRemoteImageBytes);
        State s;Reader r(bytes);s.incarnation=unseal(r,Kind::RemoteLocal);fields(r,s);r.end();validate(s);return s;
    }
    static void install(Remote& r, State& s, std::uint64_t fresh,std::uint64_t operation) noexcept {
        static_assert(noexcept(r.reply.swap(s.reply)) && noexcept(r.events.swap(s.events)) &&
            noexcept(r.incoming.swap(s.incoming)) && noexcept(r.taken.swap(s.taken)) &&
            noexcept(r.pending.swap(s.pending)) && noexcept(r.completed.swap(s.completed)));
        r.retainReceipts=s.retainReceipts;r.nextReadyWatch=s.nextReadyWatch;
        r.readyWatch=s.readyWatch;r.own=s.own;r.session=s.session;r.frame=s.frame;r.nextRequest=s.nextRequest;
        r.updateSubframePorts(s.subframePorts);
        r.replyRequest=s.replyRequest;r.nextSend=s.nextSend;r.sequence=s.sequence;
        r.reply.swap(s.reply);r.events.swap(s.events);r.incoming.swap(s.incoming);
        r.taken.swap(s.taken);r.pending.swap(s.pending);r.completed.swap(s.completed);r.saved=s.saved;
        r.transportIncarnation=fresh;r.snapshotOperation=operation;
        r.fencedStream=true;r.stagedImport=true;
    }
};
} // namespace melonDS::wm_sdk::snapshot

namespace melonDS::wm_sdk::net {
inline snapshot::Status Remote::exportSnapshot(const SnapshotQuiescence& guard,snapshot::Bytes& bytes){
    if(guard.owner!=this||!guard.outputLock.owns_lock()||!guard.stateLock.owns_lock())return snapshot::Status::NotQuiescent;
    return snapshot::attempt([&]{
        auto s=snapshot::RemoteCodec::capture(*this);snapshot::Writer w;
        snapshot::RemoteCodec::fields(w,s);snapshot::RemoteCodec::validate(s);
        auto result=snapshot::seal(w,snapshot::Kind::RemoteLocal,s.incarnation);
        snapshot::require(result.size()<=snapshot::MaxRemoteImageBytes);bytes.swap(result);
    });
}
inline snapshot::Status Remote::validateSnapshot(const snapshot::Bytes& bytes){
    return snapshot::attempt([&]{(void)snapshot::RemoteCodec::decode(bytes);});
}
inline snapshot::Status Remote::importSnapshot(const SnapshotQuiescence& guard,const snapshot::Bytes& bytes,
                                              std::uint64_t freshIncarnation,std::uint64_t restoreOperation){
    if(guard.owner!=this||!guard.outputLock.owns_lock()||!guard.stateLock.owns_lock())return snapshot::Status::NotQuiescent;
    if(external||socket!=BadSocket||receiver.joinable())return snapshot::Status::Unsupported;
    return snapshot::attempt([&]{
        snapshot::require(!failed&&!closing&&!activeRpc&&snapshotFrozen&&snapshotPreparing&&!snapshotReleasing,snapshot::Status::NotQuiescent);
        auto s=snapshot::RemoteCodec::decode(bytes);
        snapshot::require(s.timing==deliveryTiming,snapshot::Status::ModeMismatch);
        snapshot::require(restoreOperation!=0);
        snapshot::require(freshIncarnation>s.incarnation&&freshIncarnation>transportIncarnation,snapshot::Status::StaleIncarnation);
        snapshot::RemoteCodec::install(*this,s,freshIncarnation,restoreOperation);
    });
}
} // namespace melonDS::wm_sdk::net
#endif
