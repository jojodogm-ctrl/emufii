// Actual owner-thread service state, shared by the runtime and its field codec.
// This is NOT a wire layout. Never memcpy these objects to a checkpoint.
#pragma once
#include "WmSdkAbi.h"
#include "WmSdkRequestQueue.h"
#include "WmSdkTransport.h"
#include "WmSdkWireWindow.h"
#include "MpdsRoomBootstrap.h"
#include <array>
#include <cstdint>

namespace melonDS::wm_sdk::snapshot {
struct ConsoleSdkState {
    // Private ABI: 0 existing service, 1 NSMB game, 2 firmware, 3 AW,
    // 4 HGSS, 5 Platinum. Shared owner semantics do not imply shared identity.
    // Deliberately not in the public v1 checkpoint wire format. Its validator
    // rejects capture rather than silently restoring a different service.
    std::uint8_t PrivateAbi=0;
    // Private NSMB Reset captures its departure list before clearing guest
    // membership. Kept outside public checkpoints together with PrivateAbi.
    bool PrivateResetActive=false, PrivateResetChild=false;
    std::uint16_t PrivateResetMask=0;
    bool PrivateEndMpActive=false, PrivateEndMpOldMp=false, PrivateDisconnectCleanup=false;
    bool PrivateChildDisconnectActive=false; // final API13 follows departure/ready-send leases
    // Private virtual-radio beacon configuration and next transmission time.
    // Never included in public v1 checkpoints.
    bool PrivateBeaconIndication=false;
    std::uint64_t PrivateBeaconAt=0;
    bool PrivateReceiveRound=false; // NSMB parent raw-port lease has a second consumer
    bool Claimed=false, Pending=false, CallbackInFlight=false, Initialized=false;
    RequestQueue Requests{};
    std::uint32_t RequestHighWater=0, FoundTraceCount=0;
    std::uint32_t Request=0, Callback=0, Work=0, Status=0;
    std::uint32_t TraceCount=0, PostedFrame=0, Released=0;
    std::uint64_t Due=0, Session=0, ScannedParent=0, ClockSession=0;
    std::uint32_t ClockFrame=0;
    std::array<std::uint8_t,192> ScannedBss{};
    std::array<std::uint16_t,3> RadioLifetime{{0xffff,0xffff,0xffff}};
    bool MpStarted=false, ReceiveLease=false, EpochRx=false, EpochTx=false;
    std::uint32_t ReceiveBase=0, ReceiveSize=0, ReceiveBank=0;
    std::uint64_t PollAt=0, MpEpoch=0, ReceiveId=0, ReceiveSourceEpoch=0, ReceiveDomain=0;
    std::uint8_t ReceiveSourceAid=0, MpAid=0;
    std::uint16_t ReceivePort=0, ReceiveWireSequence=0xffff;
    std::uint8_t ReceiveAckState=0;
    std::uint32_t TimingLines=0, MpSent=0, MpReceived=0, MpAcked=0, MpCompleted=0;
    struct SendSlot {
        bool Used=false;
        std::uint64_t Id=0, Order=0;
        wmguest::SendRequest Request{};
        std::uint16_t Effective=0, Sequence=0xffff;
        std::array<std::uint8_t,512> Payload{};
    };
    std::array<SendSlot,wire_window::Wide?64:32> Sends{};
    std::uint64_t NextSendOrder=1;
    std::array<std::uint16_t,256> TraceApiCount{};
    std::array<std::uint32_t,12> Words{};
};

struct ConsoleMpdsState {
    enum class Phase : std::uint8_t { Approach, Drain, Reset, Unload, Admission, Initialize, Running, Failed };
    mpds_room::Plan plan;
    mpds_room::Offer offer;
    Phase phase=Phase::Approach;
    Token session=0;
    Transport::Bus::Session mp{};
    std::uint32_t startFrame=0, resetRequest=0, callbacks=0;
    std::uint16_t nativeDone=0;
    bool offered=false, unloadDone=false, initializingCall=false;
    std::uint64_t admissionPoll=0, lobbyDeadline=0;
    std::uint32_t receiveBase=0, receiveSize=0, receiveBank=0;
    std::uint64_t pollAt=0;
    bool lease=false, receiveLease=false;
    Token receiveDomain=0;
    Transport::Bus::DeliveryToken receiveToken{};
    std::array<std::uint32_t,15> savedRegisters{};
    std::uint32_t savedCpsr=0;
    struct Send {
        bool used=false;
        std::uint64_t id=0, order=0, queuedAt=0, sentAt=0;
        wmguest::SendRequest request{};
        std::uint16_t sequence=0;
        std::array<std::uint8_t,512> bytes{};
    };
    std::array<Send,32> sends{};
    std::array<std::uint16_t,16> sequence{};
    std::uint64_t nextOrder=1, sent=0, received=0, completed=0, steps=0;
    std::uint64_t datasets=0, emptySteps=0, lastDatasetAt=0;
    bool subframePacing=false, compactPipeline=false, stallTrace=false;
    std::uint64_t pumpExitAt=0, pumpExitClock=0;
    std::uint32_t pumpExitFrame=0;
    std::uint64_t serviceAt=0, maxServiceGap=0, paceSleep=0;
    struct Timing {
        std::uint64_t count=0,total=0,maximum=0;
        void add(std::uint64_t us) { ++count;total+=us;if(us>maximum)maximum=us; }
        std::uint64_t average() const { return count?total/count:0; }
    } sendQueueTime,sendCallTime,realAckTime,callbackTime;
    std::uint64_t leaseAt=0;
};
} // namespace melonDS::wm_sdk::snapshot
