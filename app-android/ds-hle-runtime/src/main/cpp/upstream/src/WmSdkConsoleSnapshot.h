// Local component codecs only. Successful decoding is NOT a distributed cut,
// core restore, transport restore, or permission to resume a room.
#pragma once
#include "WmSdkConsoleSnapshotState.h"
#include "WmSdkSnapshot.h"
#include <limits>
#include <utility>

namespace melonDS::wm_sdk::snapshot {
// Disjoint from the medium's kinds 1/2. The shared envelope version is 1.
inline constexpr Kind ConsoleSdkKind=static_cast<Kind>(0x100);
inline constexpr Kind ConsoleMpdsKind=static_cast<Kind>(0x101);
inline constexpr std::size_t MaxConsoleStateBytes=64*1024;
inline constexpr std::size_t MaxCoreBytes=32*1024*1024;

template<class A> void sendRequest(A& a,wmguest::SendRequest& r) {
    a(r.payload);a(r.length);a(r.destination);a(r.port);a(r.priority);a(r.callback);a(r.argument);
}
inline bool validSend(const wmguest::SendRequest& r) {
    return r.length && r.length<=512 && wmguest::mainRange(r.payload,(r.length+1)&~1u) &&
        r.destination<=255 && r.port<16 && r.priority<4;
}
inline bool emptySend(const wmguest::SendRequest& r) {
    return !r.payload&&!r.length&&!r.destination&&!r.port&&!r.priority&&!r.callback&&!r.argument;
}
template<class T,std::size_t N>bool allZero(const std::array<T,N>& a) {
    return std::all_of(a.begin(),a.end(),[](T v){return v==0;});
}
inline bool address(std::uint32_t p,std::uint32_t length) {
    return !(p&3) && wmguest::mainRange(p,length);
}
inline bool apart(std::uint32_t a,std::uint32_t an,std::uint32_t b,std::uint32_t bn) {
    return std::uint64_t(a)+an<=b || std::uint64_t(b)+bn<=a;
}
template<class A> void requestQueue(A& a,RequestQueue& q) {
    auto source=q; // Never pop the actual live queue while encoding.
    auto size=static_cast<std::uint8_t>(q.size());a(size);require(size<=16);
    RequestQueue next;
    for(unsigned i=0;i<size;++i) {
        Request r{};
        if constexpr(!A::Reading) require(source.pop(r));
        a(r.address);a(r.words);a(r.dwell);
        require(address(r.address,48));
        // Dwell is selected from this immutable request, not a wall deadline.
        const auto api=r.words[0]&0xffffu;
        require(r.dwell==((api==10||api==0x1e)?std::uint64_t(r.words[2]&0xffffu)*33514u:0));
        require(next.push(r)==RequestQueue::Result::Ok);
    }
    if constexpr(A::Reading) q=next;
}
template<class A> void sdkFields(A& a,ConsoleSdkState& s) {
    a(s.Claimed);a(s.Pending);a(s.CallbackInFlight);a(s.Initialized);
    requestQueue(a,s.Requests);
    a(s.RequestHighWater);a(s.FoundTraceCount);a(s.Request);a(s.Callback);a(s.Work);a(s.Status);
    a(s.TraceCount);a(s.PostedFrame);a(s.Released);a(s.Due);a(s.Session);a(s.ScannedParent);
    a(s.ClockSession);a(s.ClockFrame);a(s.ScannedBss);a(s.RadioLifetime);
    a(s.MpStarted);a(s.ReceiveLease);a(s.EpochRx);a(s.EpochTx);
    a(s.ReceiveBase);a(s.ReceiveSize);a(s.ReceiveBank);a(s.PollAt);a(s.MpEpoch);
    a(s.ReceiveId);a(s.ReceiveSourceEpoch);a(s.ReceiveDomain);a(s.ReceiveSourceAid);a(s.MpAid);
    a(s.ReceivePort);a(s.ReceiveWireSequence);a(s.ReceiveAckState);
    a(s.TimingLines);a(s.MpSent);a(s.MpReceived);a(s.MpAcked);a(s.MpCompleted);
    // Slot positions and all owned payload bytes are preserved, including the
    // odd-length SDK padding byte and inactive slot values (never host padding).
    for(auto& slot:s.Sends) {
        a(slot.Used);a(slot.Id);a(slot.Order);sendRequest(a,slot.Request);
        a(slot.Effective);a(slot.Sequence);a(slot.Payload);
    }
    a(s.NextSendOrder);a(s.TraceApiCount);a(s.Words);
}
inline void validateSdk(const ConsoleSdkState& s) {
    require(s.PrivateAbi==0 && !s.PrivateResetActive && !s.PrivateResetChild
            && !s.PrivateResetMask && !s.PrivateEndMpActive && !s.PrivateEndMpOldMp
            && !s.PrivateDisconnectCleanup && !s.PrivateBeaconIndication
            && !s.PrivateBeaconAt && !s.PrivateReceiveRound
            && !s.PrivateChildDisconnectActive,Status::Unsupported);
    require(s.RequestHighWater<=17 && s.RequestHighWater>=s.Requests.size()+(s.Pending?1u:0u));
    require(s.MpAid<8 && s.ReceiveSourceAid<8 && s.ReceivePort<16 && s.ReceiveAckState<=2);
    require(s.ReceiveBank<=1 && s.ReceiveSize<=0x10000);
    if(s.Pending || s.CallbackInFlight || s.Initialized || s.Requests.size()) require(s.Claimed);
    if(s.Pending) require(address(s.Request,48));
    if(s.Initialized || s.CallbackInFlight) {
        require(address(s.Callback,256)&&address(s.Work,0x300)&&address(s.Status,0x800));
        require(!((s.Callback|s.Work|s.Status)&31));
        require(apart(s.Work,0x300,s.Status,0x800)&&apart(s.Work,0x300,s.Callback,256)&&
                apart(s.Status,0x800,s.Callback,256));
    }
    if(s.MpStarted) require(s.Initialized&&s.Session&&s.MpEpoch&&s.ReceiveSize>=32&&
        address(s.ReceiveBase,2*s.ReceiveSize));
    if(s.ReceiveLease) require(s.MpStarted&&s.CallbackInFlight&&s.ReceiveId&&s.ReceiveSourceEpoch&&
        s.ReceiveDomain&&s.ReceiveSourceAid!=s.MpAid);
    auto q=s.Requests;Request r{};
    while(q.pop(r)) if(s.Pending) require(apart(r.address,48,s.Request,48));
    for(std::size_t i=0;i<s.Sends.size();++i) {
        const auto& v=s.Sends[i];if(!v.Used){
            require(!v.Id&&!v.Order&&emptySend(v.Request)&&!v.Effective&&v.Sequence==0xffff&&allZero(v.Payload));
            continue;
        }
        require(s.Initialized&&s.MpEpoch&&s.Session&&validSend(v.Request)&&v.Order&&
            (!s.NextSendOrder||v.Order<s.NextSendOrder)&&v.Effective<=255&&
            !(v.Effective&~v.Request.destination)&&!(v.Effective&(1u<<s.MpAid)));
        for(std::size_t j=0;j<i;++j) if(s.Sends[j].Used)
            require(v.Order!=s.Sends[j].Order&&(!v.Id||v.Id!=s.Sends[j].Id));
    }
}
inline Status encodeSdk(const ConsoleSdkState& state,std::uint64_t incarnation,Bytes& out) {
    return attempt([&]{auto copy=state;validateSdk(copy);Writer w;sdkFields(w,copy);
        auto encoded=seal(w,ConsoleSdkKind,incarnation);require(encoded.size()<=MaxConsoleStateBytes);
        out.swap(encoded);});
}
inline Status decodeSdk(const Bytes& bytes,std::uint64_t incarnation,ConsoleSdkState& out) {
    return attempt([&]{require(bytes.size()<=MaxConsoleStateBytes);Reader r(bytes);
        require(unseal(r,ConsoleSdkKind)==incarnation,Status::ModeMismatch);
        ConsoleSdkState staged;sdkFields(r,staged);r.end();validateSdk(staged);
        out=std::move(staged);});
}

template<class A> void unsigned32(A& a,unsigned& value) {
    static_assert(sizeof(unsigned)==4);std::uint32_t n=value;a(n);value=n;
}
template<class A> void planFields(A& a,mpds_room::Plan& p) {
    unsigned32(a,p.players);unsigned32(a,p.seat);unsigned32(a,p.localConsoles);unsigned32(a,p.savedAid);
    a.string(p.snapshotName,96);a.string(p.snapshotSha,64);a.string(p.setSha,64);
    std::uint64_t size=p.snapshotSize;a(size);require(size<=MaxCoreBytes);p.snapshotSize=static_cast<std::size_t>(size);
}
template<class A> void offerFields(A& a,mpds_room::Offer& o) {
    a.string(o.profile,96);a.string(o.romSha,64);a.string(o.setSha,64);a(o.epoch);
    unsigned32(a,o.players);unsigned32(a,o.seat);unsigned32(a,o.bufferTicks);unsigned32(a,o.savedAid);
}
inline bool samePlan(const mpds_room::Plan& a,const mpds_room::Plan& b) {
    return a.players==b.players&&a.seat==b.seat&&a.localConsoles==b.localConsoles&&a.savedAid==b.savedAid&&
        a.snapshotName==b.snapshotName&&a.snapshotSha==b.snapshotSha&&a.setSha==b.setSha&&a.snapshotSize==b.snapshotSize;
}
inline bool sameOffer(const mpds_room::Offer& a,const mpds_room::Offer& b) {
    return a.profile==b.profile&&a.romSha==b.romSha&&a.setSha==b.setSha&&a.epoch==b.epoch&&
        a.players==b.players&&a.seat==b.seat&&a.bufferTicks==b.bufferTicks&&a.savedAid==b.savedAid;
}
inline bool hashText(const std::string& s) {
    return s.size()==64&&std::all_of(s.begin(),s.end(),[](char c){return (c>='0'&&c<='9')||(c>='a'&&c<='f');});
}
// Wall times use explicit presence + direction + magnitude relative to a cut.
// Thus overdue deadlines stay overdue, zero sentinels stay absent, and no old
// machine's monotonic clock is imported. Reject impossible under/overflow.
template<class A> void wallField(A& a,std::uint64_t& value,std::uint64_t origin) {
    bool present=value!=0, future=value>origin;
    std::uint64_t distance=value>origin?value-origin:origin-value;
    if(!present){future=false;distance=0;}
    a(present);a(future);a(distance);
    if(!present){require(!future&&!distance);value=0;return;}
    if(future){require(distance<=UINT64_MAX-origin);value=origin+distance;}
    else {require(distance<origin);value=origin-distance;}
}
template<class A> void busSession(A& a,Transport::Bus::Session& s){a(s.aid);a(s.epoch);require(s.aid<8);}
template<class A> void mpdsFields(A& a,ConsoleMpdsState& s,std::uint64_t now) {
    planFields(a,s.plan);offerFields(a,s.offer);enumeration(a,s.phase,7);a(s.session);busSession(a,s.mp);
    a(s.startFrame);a(s.resetRequest);a(s.callbacks);a(s.nativeDone);
    a(s.offered);a(s.unloadDone);a(s.initializingCall);
    wallField(a,s.admissionPoll,now);wallField(a,s.lobbyDeadline,now);
    a(s.receiveBase);a(s.receiveSize);a(s.receiveBank);a(s.pollAt);
    a(s.lease);a(s.receiveLease);a(s.receiveDomain);
    busSession(a,s.receiveToken.source);busSession(a,s.receiveToken.recipient);a(s.receiveToken.send_id);
    a(s.savedRegisters);a(s.savedCpsr);
    for(auto& slot:s.sends){a(slot.used);a(slot.id);a(slot.order);
        wallField(a,slot.queuedAt,now);wallField(a,slot.sentAt,now);
        sendRequest(a,slot.request);a(slot.sequence);a(slot.bytes);}
    a(s.sequence);a(s.nextOrder);a(s.sent);a(s.received);a(s.completed);a(s.steps);
    a(s.datasets);a(s.emptySteps);wallField(a,s.lastDatasetAt,now);
    a(s.subframePacing);a(s.compactPipeline);a(s.stallTrace);
    wallField(a,s.pumpExitAt,now);a(s.pumpExitClock);a(s.pumpExitFrame);
    wallField(a,s.serviceAt,now);a(s.maxServiceGap);a(s.paceSleep);
    for(auto* t:{&s.sendQueueTime,&s.sendCallTime,&s.realAckTime,&s.callbackTime}) {
        a(t->count);a(t->total);a(t->maximum);
        require(t->maximum<=t->total && (t->count||!t->total));
    }
    wallField(a,s.leaseAt,now);
}
inline void validateMpds(const ConsoleMpdsState& s) {
    const auto& p=s.plan;const auto& o=s.offer;
    require(p.players>=2&&p.players<=4&&p.seat>=1&&p.seat<=p.players&&p.localConsoles==1&&p.savedAid<p.players);
    require((p.seat==1)==(p.savedAid==0));
    require(hashText(p.snapshotSha)&&hashText(p.setSha)&&p.snapshotSize&&p.snapshotSize<=MaxCoreBytes);
    require(!p.snapshotName.empty()&&p.snapshotName.size()<=96&&
        p.snapshotName.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.-")==std::string::npos);
    require(o.players==p.players&&o.seat==p.seat&&o.savedAid==p.savedAid&&o.setSha==p.setSha&&o.epoch&&
        o.bufferTicks>=2&&o.bufferTicks<=12&&o.romSha==mpds_room::PatchedSha);
    require(o.profile==(s.compactPipeline?"mpds-us-debugmenu-room-p4-v2":mpds_room::Profile));
    require(s.receiveSize>=188&&s.receiveSize<=1024&&address(s.receiveBase,2*s.receiveSize)&&s.receiveBank<=1);
    // The codec represents every phase, but only the fully adopted runtime is
    // eligible for owner-thread capture. This validator still catches garbage.
    if(s.phase==ConsoleMpdsState::Phase::Running) require(s.session&&s.mp.epoch&&
        s.mp.aid==p.savedAid&&s.offered&&s.initializingCall);
    if(s.receiveLease) require(s.lease&&s.receiveDomain&&s.receiveToken.send_id&&
        s.receiveToken.source.epoch&&s.receiveToken.source.aid!=s.mp.aid&&s.receiveToken.recipient==s.mp);
    if(s.lease) require(s.leaseAt);
    for(std::size_t i=0;i<s.sends.size();++i){const auto& v=s.sends[i];if(!v.used){
        require(!v.id&&!v.order&&!v.queuedAt&&!v.sentAt&&emptySend(v.request)&&!v.sequence&&allZero(v.bytes));continue;}
        require(validSend(v.request)&&v.order&&v.queuedAt&&(!s.nextOrder||v.order<s.nextOrder)&&
            (!v.id||v.sentAt)&&((v.request.destination&((1u<<p.players)-1)&~(1u<<p.savedAid))!=0));
        if(p.savedAid)require(v.request.destination==1);
        for(std::size_t j=0;j<i;++j)if(s.sends[j].used)
            require(v.order!=s.sends[j].order&&(!v.id||v.id!=s.sends[j].id));
    }
}
inline Status encodeMpds(const ConsoleMpdsState& state,std::uint64_t incarnation,std::uint64_t now,Bytes& out) {
    return attempt([&]{require(now);auto copy=state;validateMpds(copy);Writer w;mpdsFields(w,copy,now);
        auto encoded=seal(w,ConsoleMpdsKind,incarnation);require(encoded.size()<=MaxConsoleStateBytes);
        out.swap(encoded);});
}
inline Status decodeMpds(const Bytes& bytes,std::uint64_t incarnation,std::uint64_t now,ConsoleMpdsState& out) {
    return attempt([&]{require(now&&bytes.size()<=MaxConsoleStateBytes);Reader r(bytes);
        require(unseal(r,ConsoleMpdsKind)==incarnation,Status::ModeMismatch);
        ConsoleMpdsState staged;mpdsFields(r,staged,now);r.end();validateMpds(staged);
        static_assert(std::is_nothrow_move_assignable_v<ConsoleMpdsState>);
        out=std::move(staged);});
}
} // namespace melonDS::wm_sdk::snapshot
