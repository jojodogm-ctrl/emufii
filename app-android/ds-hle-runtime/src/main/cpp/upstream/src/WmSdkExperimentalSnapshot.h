#pragma once
// CLEANROOM: our ConsoleSdkState, WmSdk owner/lifecycle code, WmFirmwareAbi,
// WmInterfaceRegistry and public semantic snapshot codec. No binary derivation.
// Explicit experimental component only. No core/transport restore, adapter
// discovery, resume permission or collective-cut claim. Public v1 is unchanged.
#include "WmSdkConsoleSnapshot.h"
#include "WmInterfaceRegistry.h"

namespace melonDS::wm_sdk::snapshot {
inline constexpr Kind ExperimentalSdkKind=static_cast<Kind>(0x104);
inline constexpr std::uint32_t ExperimentalSdkVersion=1;

// AW=3 is a persisted adapter contract, not permission to reuse this v1
// service codec. sdkFields still serializes twelve-word requests without a
// per-API byte length. Reject AW until a separately versioned service codec
// accounts for its 16/44-byte guest records and complete runtime state.
constexpr bool supportsExperimentalSdkAbi(std::uint8_t abi) {
    switch(abi) { case 0:case 1:case 2:return true;default:return false; }
}

template<class A> void experimentalPrivateFields(A& a,ConsoleSdkState& s){
    a(s.PrivateAbi);
    a(s.PrivateResetActive);a(s.PrivateResetChild);a(s.PrivateResetMask);
    a(s.PrivateEndMpActive);a(s.PrivateEndMpOldMp);a(s.PrivateDisconnectCleanup);
    a(s.PrivateChildDisconnectActive);a(s.PrivateBeaconIndication);
    a(s.PrivateBeaconAt);a(s.PrivateReceiveRound);
}
inline bool experimentalPrivateEmpty(const ConsoleSdkState& s){
    return !s.PrivateResetActive&&!s.PrivateResetChild&&!s.PrivateResetMask&&
        !s.PrivateEndMpActive&&!s.PrivateEndMpOldMp&&!s.PrivateDisconnectCleanup&&
        !s.PrivateChildDisconnectActive&&!s.PrivateBeaconIndication&&
        !s.PrivateBeaconAt&&!s.PrivateReceiveRound;
}
inline void validateExperimentalSdk(const ConsoleSdkState& s){
    require(supportsExperimentalSdkAbi(s.PrivateAbi),Status::Unsupported);
    if(!s.PrivateAbi){require(experimentalPrivateEmpty(s));validateSdk(s);return;}
    const bool nsmb=s.PrivateAbi==1;
    const auto& d=nsmb?wmguest::interfaces::Sdk300:wmguest::interfaces::Firmware;
    const std::uint32_t workBytes=nsmb?0x300:0x100,statusBytes=nsmb?0x800:0xa00;
    // Keep all common public invariants. Do NOT sanitize private fields or
    // substitute fake allocations and then run the public validator.
    require(s.RequestHighWater<=17&&s.RequestHighWater>=s.Requests.size()+(s.Pending?1u:0u));
    require(s.MpAid<8&&s.ReceiveSourceAid<8&&s.ReceivePort<16&&s.ReceiveAckState<=2);
    require(s.ReceiveBank<=1&&s.ReceiveSize<=0x10000);
    if(s.Pending||s.CallbackInFlight||s.Initialized||s.Requests.size())require(s.Claimed);
    if(s.Pending)require(address(s.Request,48));
    if(s.Initialized||s.CallbackInFlight){
        require(address(s.Callback,256)&&address(s.Work,workBytes)&&address(s.Status,statusBytes));
        require(!((s.Callback|s.Work|s.Status)&31));
        require(apart(s.Work,workBytes,s.Status,statusBytes)&&apart(s.Work,workBytes,s.Callback,256)&&apart(s.Status,statusBytes,s.Callback,256));
        require(s.Work>=d.workOffset);
        const auto owner=s.Work-d.workOffset;
        require(!(owner&31)&&wmguest::mainRange(owner,d.allocationBytes)&&
            s.Status==owner+d.statusOffset&&s.Callback==owner+d.callbackOffset);
        if(s.Pending)require(apart(s.Request,48,s.Work,workBytes)&&apart(s.Request,48,s.Status,statusBytes)&&apart(s.Request,48,s.Callback,256));
    }
    if(s.MpStarted)require(s.Initialized&&s.Session&&s.MpEpoch&&s.ReceiveSize>=32&&address(s.ReceiveBase,2*s.ReceiveSize));
    if(s.ReceiveLease)require(s.MpStarted&&s.CallbackInFlight&&s.ReceiveId&&s.ReceiveSourceEpoch&&s.ReceiveDomain&&s.ReceiveSourceAid!=s.MpAid);
    auto queue=s.Requests;Request r{};
    while(queue.pop(r))if(s.Pending)require(apart(r.address,48,s.Request,48));
    for(std::size_t i=0;i<s.Sends.size();++i){
        const auto& v=s.Sends[i];
        if(!v.Used){require(!v.Id&&!v.Order&&emptySend(v.Request)&&!v.Effective&&v.Sequence==0xffff&&allZero(v.Payload));continue;}
        const auto& req=v.Request;
        // NSMB's raw broadcast request is legitimately FFFF; effective peers
        // remain bounded to the actual eight-seat medium, excluding self.
        const bool validRequest=req.length&&req.length<=512&&wmguest::mainRange(req.payload,(req.length+1)&~1u)&&
            (nsmb||req.destination<=255)&&req.port<16&&req.priority<4;
        require(s.Initialized&&s.MpEpoch&&s.Session&&validRequest&&v.Order&&
            (!s.NextSendOrder||v.Order<s.NextSendOrder)&&v.Effective<=255&&
            !(v.Effective&~req.destination)&&!(v.Effective&(1u<<s.MpAid)));
        for(std::size_t j=0;j<i;++j)if(s.Sends[j].Used)
            require(v.Order!=s.Sends[j].Order&&(!v.Id||v.Id!=s.Sends[j].Id));
    }
    if(!nsmb){require(experimentalPrivateEmpty(s));return;}
    const auto api=s.Words[0]&0xffff;
    if(s.PrivateResetActive)require(s.Initialized&&s.Pending&&api==1);
    else require(!s.PrivateResetChild&&!s.PrivateResetMask);
    require(s.PrivateResetChild?s.PrivateResetMask<=1:!(s.PrivateResetMask&~0xfeu));
    if(s.PrivateEndMpActive)require(s.Initialized&&s.Pending&&api==16);
    else require(!s.PrivateEndMpOldMp);
    if(s.PrivateChildDisconnectActive)require(s.Initialized&&s.Pending&&api==13&&!s.MpStarted&&s.Session);
    if(s.PrivateDisconnectCleanup)require(s.Initialized&&!s.MpStarted);
    if(s.PrivateBeaconAt)require(s.Initialized&&s.Session);
    // BeaconIndication can remain latched across WM End; don't invent a reset.
    if(s.PrivateReceiveRound)require(s.Initialized&&s.ReceiveLease&&s.CallbackInFlight&&s.MpStarted&&s.MpAid==0&&s.ReceivePort==1);
}
inline Status encodeExperimentalSdk(const ConsoleSdkState& state,std::uint64_t incarnation,Bytes& out){
    return attempt([&]{auto copy=state;validateExperimentalSdk(copy);Writer w;
        auto version=ExperimentalSdkVersion;w(version);sdkFields(w,copy);experimentalPrivateFields(w,copy);
        auto encoded=seal(w,ExperimentalSdkKind,incarnation);
        require(encoded.size()<=MaxConsoleStateBytes,Status::ResourceLimit);out.swap(encoded);});
}
// expectedAbi comes from the trusted enclosing checkpoint, NOT image discovery.
// Original guest timestamps and transport identities are preserved verbatim;
// an enclosing core/medium cut must validate/rebase them before any installation.
inline Status decodeExperimentalSdk(const Bytes& bytes,std::uint64_t incarnation,std::uint8_t expectedAbi,ConsoleSdkState& out){
    return attempt([&]{require(supportsExperimentalSdkAbi(expectedAbi),Status::Unsupported);
        require(bytes.size()<=MaxConsoleStateBytes,Status::ResourceLimit);Reader r(bytes);
        require(unseal(r,ExperimentalSdkKind)==incarnation,Status::ModeMismatch);
        std::uint32_t version=0;r(version);require(version==ExperimentalSdkVersion,Status::WrongVersion);
        ConsoleSdkState staged;sdkFields(r,staged);experimentalPrivateFields(r,staged);r.end();
        require(staged.PrivateAbi==expectedAbi,Status::ModeMismatch);validateExperimentalSdk(staged);
        static_assert(std::is_nothrow_move_assignable_v<ConsoleSdkState>);out=std::move(staged);});
}
} // namespace melonDS::wm_sdk::snapshot
