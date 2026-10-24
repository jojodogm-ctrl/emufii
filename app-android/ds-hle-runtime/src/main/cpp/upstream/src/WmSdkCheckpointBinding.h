// Clean-room: our service state, Remote protocol and explicit snapshot codecs.
// Cross-component validation before importing a disconnected replacement.
#pragma once
#include "WmSdkConsoleSnapshotLocal.h"
#include "WmSdkSnapshotRemote.h"

namespace melonDS::wm_sdk::snapshot {
inline Status validateServiceRemote(const LocalStage& local,const RemoteCodec::State& remote,
                                    std::uint64_t capturedIncarnation) {
    return attempt([&]{
        RemoteCodec::validate(remote);
        if(local.pokemon){
            validatePokemonSdk(local.sdk,*local.pokemon);
            require(remote.retainReceipts==local.pokemon->receiptRetention,Status::ModeMismatch);
        }else if(local.metadata.authorityKind==2){
            require(local.sdk.PrivateAbi==1,Status::ModeMismatch);
            validateExperimentalSdk(local.sdk);require(!remote.retainReceipts,Status::ModeMismatch);
        }else{validateSdk(local.sdk);require(!remote.retainReceipts,Status::ModeMismatch);}
        require(remote.incarnation==capturedIncarnation,Status::ModeMismatch);
        std::size_t sends=0;
        const auto sent=[&](std::uint64_t id,const wmguest::SendRequest& request,
                            std::uint16_t targets,std::uint16_t sdkSequence) {
            if(!id)return;
            ++sends;const auto found=remote.pending.find(id);
            require(found!=remote.pending.end(),Status::ModeMismatch);
            const auto& m=found->second;
            require(m.source==remote.session && m.port==request.port && m.targets==targets &&
                sdkSequence==(m.port>=8?std::uint16_t(m.wire_sequence<<1):0xffff),Status::ModeMismatch);
        };
        const auto received=[&](Token domain,const Bus::DeliveryToken& token) -> const Bus::Delivery& {
            require(remote.taken.size()==1,Status::ModeMismatch);
            const auto& taken=remote.taken.begin()->second;
            require(taken.domain==domain && taken.d.token.send_id==token.send_id &&
                taken.d.token.source==token.source && taken.d.token.recipient==token.recipient,
                Status::ModeMismatch);
            return taken.d;
        };
        if(local.metadata.party){
            const auto& s=local.mpds;validateMpds(s);
            require(!local.sdk.Session&&!local.sdk.Claimed,Status::ModeMismatch);
            require(s.phase==ConsoleMpdsState::Phase::Running && s.session==remote.own &&
                s.mp==remote.session,Status::ModeMismatch);
            for(const auto& v:s.sends)if(v.used)
                sent(v.id,v.request,v.request.destination&((1u<<s.plan.players)-1)&~(1u<<s.plan.savedAid),v.sequence);
            if(s.receiveLease)received(s.receiveDomain,s.receiveToken);
            else require(remote.taken.empty(),Status::ModeMismatch);
        }else{
            const auto& s=local.sdk;
            require(s.Session==remote.own,Status::ModeMismatch);
            // StopReceipts has consumed Remote's pending sends and cleared its
            // session. The owner still needs its original epoch/descriptors to
            // publish exactly one guest callback for each transferred receipt.
            const bool stopped=local.pokemon&&!s.MpStarted&&remote.session==Bus::Session{};
            if(!stopped)require(Bus::Session{s.MpAid,s.MpEpoch}==remote.session,Status::ModeMismatch);
            else require(!s.ReceiveLease&&remote.pending.empty()&&remote.taken.empty(),Status::ModeMismatch);
            for(const auto& v:s.Sends)if(v.Used){
                bool retired=false;
                if(local.pokemon&&v.Id){const auto& p=*local.pokemon;
                    for(unsigned i=p.retiredIndex;i<p.retiredCount;++i)retired|=p.retiredSends[i].id==v.Id;}
                if(retired)require(stopped&&!remote.pending.count(v.Id),Status::ModeMismatch);
                else sent(v.Id,v.Request,v.Effective,v.Sequence);
            }
            // A callback can retain its guest payload after acknowledge() has
            // consumed the transport lease. Do not manufacture a second ACK.
            if(s.ReceiveLease&&!s.ReceiveAckState){
                const auto& d=received(s.ReceiveDomain,{{s.ReceiveSourceAid,s.ReceiveSourceEpoch},
                    {s.MpAid,s.MpEpoch},s.ReceiveId});
                require(d.port==s.ReceivePort && d.wire_sequence==s.ReceiveWireSequence,Status::ModeMismatch);
            }else require(remote.taken.empty(),Status::ModeMismatch);
        }
        require(sends==remote.pending.size(),Status::ModeMismatch);
    });
}
}
