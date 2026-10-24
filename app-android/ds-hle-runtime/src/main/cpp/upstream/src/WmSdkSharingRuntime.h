#pragma once
// Private SDK-family window. Each NDS owns its storage and callback generations;
// no game command, application input or other console RAM is represented here.
#include "WmSdk300ResponseGraph.h"
#include "WmSdkResponseLifetime.h"
#include "WmSdkResponseHooks.h"
#include "WmSdkBlockRegistryGraph.h"
#include "WmSdkBlockRegistryHooks.h"
#include "WmSdkBlockRegistryLifetime.h"
#include "WmSdkRegistryDataView.h"
#include "WmSdkBlockTransferGraph.h"
#include "WmSdkBlockTransferHooks.h"
#include "WmSdkBlockTransferLifetime.h"
#include "WmSdkBlockSource.h"
#include "WmSdkBlockWindow.h"
#include "WmSdkAbi.h"
#include "WmSdkTransport.h"
#include "types.h"
#include <chrono>
#include <memory>
#include <vector>
namespace melonDS {
class NDS;class ARM;
class WmSdkSharingRuntime {
    NDS& n;
    unsigned lead=64; // Cold, versioned whole-cohort policy; storage remains128.
    // Optional graph-admitted SDK lifetimes; native packets/callbacks remain owned.
    bool endProbe=false,endProbeWaiting=false,stepProbeEnabled=false;
    u32 stepReturnOffset=0; // family-relative, covered by admitted library snapshot
    u32 endProbeSp=0,endProbeLr=0;
    unsigned endProbeGraceMs=0; // private policy0 observe,1 drain; no grace timer
    wm_sdk::sharing::ResponseLifetime responseLifetime;
    void responseAdmit(const wmguest::SendRequest&,u64,const std::array<u8,512>&);
    void responseSubmit(u64,u64);
    void responseComplete(u64,bool);
    void responseReconcile();
    void responseStateLog(const char*);
    unsigned responsePeer(u32)const;
    friend class NDS;
    std::chrono::steady_clock::time_point endProbeSince{};
    wm_sdk::sharing::sdk300::Binding binding;
    wm_sdk::sharing::sdk300::graph::Binding sdkGraph;
    wm_sdk::sharing::sdk300::response::Binding responseGraph;
    std::array<u32,42> responseHooks{},responseOriginals{},responseReplacements{};
    bool registryProbe=false;
    wm_sdk::sharing::sdk300::response::registry::Binding registryGraph;
    wm_sdk::sharing::BlockRegistryLifetime blockRegistry;
    std::array<u32,10> registrySites{},registryOriginals{};
    struct RegistryInvocation {
        bool used=false,committed=false;unsigned node=0;
        u32 stack=0,returnPc=0,descriptor=0,id=0,source=0,bytes=0,field30=0;
        void start(unsigned role,u32 sp,u32 lr){*this={};used=true;node=role;stack=sp;returnPc=lr;}
        void finish(){*this={};}
    };
    std::array<RegistryInvocation,16> registryInvocations{};
    unsigned registryLines=0;
    bool registryService(ARM&);
    void registryUnavailable(wm_sdk::sharing::BlockRegistryLifetime::Reason);
    void registryLog(const char*,const wm_sdk::sharing::BlockRegistryLifetime::Token* =nullptr);
    bool transferProbe=false;
    wm_sdk::sharing::sdk300::response::transfer::Binding transferGraph;
    wm_sdk::sharing::BlockTransferLifetime blockTransfer;
    std::array<u32,6> transferSites{},transferOriginals{},transferReplacements{};
    struct TransferInvocation {
        bool used=false;u32 stack=0,returnPc=0;unsigned peer=0;
        wm_sdk::sharing::BlockTransferLifetime::Token token{};
    };
    std::array<TransferInvocation,16> transferInvocations{};
    bool blocksConfigured=false,blocksEnabled=false,blocksClosed=true;
    u32 blockDriveSite=0,blockDriveOriginal=0;u64 blockActivationEndpoint=0,blockActivationMpEpoch=0;
    using BlockLink=wm_sdk::sharing::block_window::Link;
    using BlockFrame=wm_sdk::sharing::block_wire::Frame;
    using BlockSourceToken=wm_sdk::sharing::BlockRegistryLifetime::Token;
    using BlockDestToken=wm_sdk::sharing::BlockTransferLifetime::Token;
    struct BlockGrant {
        BlockSourceToken source{};wm_sdk::sharing::ResponseLifetime::Token request{};
        BlockLink link{};unsigned segment=0;bool used=false;
    };
    struct BlockPreparedGrant {bool used=false;u32 stack=0;u64 invocation=0;BlockGrant grant{};};
    struct BlockReceipt {
        bool used=false;wm_sdk::Transport::Bus::Message message{};BlockLink link{};BlockFrame frame{};
    };
    std::array<BlockLink,8> blockLinks{};
    std::array<BlockGrant,8> blockGrants{};
    std::array<BlockPreparedGrant,8> blockPreparedGrants{};
    std::array<std::optional<BlockFrame>,8> blockOpens{};
    std::array<wm_sdk::sharing::block_wire::Nonce,8> blockCancelled{};
    std::array<wm_sdk::sharing::block_window::Source,8> blockSources{};
    std::array<wm_sdk::sharing::block_window::Receiver,8> blockReceivers{};
    std::array<std::optional<BlockDestToken>,8> blockDestinations{};
    std::array<BlockReceipt,8> blockReceipts{};
    unsigned blockRound=0,blockLines=0;u64 blockSent=0,blockCompleted=0,blockCopied=0;
    wm_sdk::sharing::BlockMemoryView blockMemory()const;
    bool blockBuffers(const BlockDestToken&)const;
    bool blockQuiet()const;
    void blockLog(const char*,unsigned=8);
    void blockClose();
    void blockForget(unsigned);
    void blockEnvelope(wm_sdk::Token,const wm_sdk::Transport::Bus::Delivery&);
    void blockReceive(wm_sdk::Token,const wm_sdk::Transport::Bus::Delivery&);
    bool blockComplete(const wm_sdk::Transport::Bus::Completion&);
    bool blockRetireTransport();
    void blockPrepareGrant(ARM&,unsigned);
    void blockAuthorize(const wm_sdk::sharing::ResponseLifetime::Build&);
    void blockOffer(unsigned);
    void blockPump();
    void blockDrive();
    unsigned transferLines=0;
    bool transferService(ARM&);
    void transferUnavailable(wm_sdk::sharing::BlockTransferLifetime::Reason);
    void transferLog(const char*,const wm_sdk::sharing::BlockTransferLifetime::Token* =nullptr);
    std::optional<wm_sdk::sharing::BlockTransferLifetime::Activation> transferMetadata(const wm_sdk::sharing::RegistryDataView&,u32,bool)const;
    bool transferMatches(const wm_sdk::sharing::RegistryDataView&,const wm_sdk::sharing::BlockTransferLifetime::Token&,unsigned)const;
    unsigned responseMailboxSnapshots=0,responseBuildLines=0;
    unsigned responseWaitLogs=0;u32 responseWaitFrame=0;
    void responseMailboxLog(const char*);
    u32 sdkQueue=0;unsigned sdkQueueBytes=0;
    u32 system=0;
    u64 ownerSession=0;
    u32 replacementRequest=0,replacementApi=0;
    u32 associationRequest=0,associationApi=0,associationFrame=0;
    std::array<u32,12> associationWords{};
    std::chrono::steady_clock::time_point associationSince{};
    u32 startRequest=0;u16 startState=0,startPreparedState=0,startAid=0;
    u32 departureRequest=0;u16 departureApi=0,departureBit=0;
    bool departureChild=false,departureObserved=false;
    std::chrono::steady_clock::time_point departureSince{};
    std::array<u32,17> codeMappings{};
    struct CodeRange {
        u32 address;
        const u8* backing;
        std::vector<u8> expected,permissions;
        bool mutableData=false;
    };
    std::vector<CodeRange> codeRanges;
    u32 checkedFrame=0xffffffff;
    u64 nextGeneration=1;
    u32 drainingRequest=0;
    std::chrono::steady_clock::time_point drainSince{};
    struct StepProbeSample {
        u32 frame=0;unsigned w=0,s=0,r=0,mode=0,seq=0,state=0,aw=0,ar=0,pw=0,pr=0;
    };
    struct StepProbe {
        u64 entries=0,exits=0,unobserved=0;std::array<u64,8> results{};
        std::array<u64,128> gap5{};
        StepProbeSample before{},zeroBefore{},zeroAfter{},fiveBefore{},fiveAfter{};
        u32 lastLogFrame=0;unsigned lines=0;bool active=false;
    };
    struct Lease {
        u32 original=0,base=0;unsigned port=0,aid=0;u16 peers=0;u64 generation=0;
        u64 session=0;
        bool transportRetired=false;
        wm_sdk::sharing::Layout layout;StepProbe stepProbe{};
        std::array<u8,wm_sdk::sharing::sdk300::ObjectBytes> bytes{};
    };
    // Separate addresses while old requests/callbacks still own a generation.
    // Reuse requires both native End and a genuinely quiescent WM owner.
    std::array<std::unique_ptr<Lease>,32> leases{};
    StepProbeSample stepSnapshot(const Lease&)const;
    void stepLog(Lease&);
    Lease* find(u32 address,unsigned bytes)const;
    Lease* info(u32 address)const;
    bool quiet()const;
    void owner()const;
    std::array<u32,17> mappings()const;
    void snapshotCode();
    void checkCode()const;
    wm_sdk::SharingDrainState drainControl(bool begin);
    void push(ARM&,u32 instruction,u32 next);
public:
    explicit WmSdkSharingRuntime(NDS&);
    bool contains(u32 address,unsigned bytes=1)const{return find(address,bytes)!=nullptr;}
    u32 read(u32 address,unsigned bytes)const;
    void write(u32 address,u32 value,unsigned bytes);
    u32 timing(u32 address)const;
    bool payload(const wmguest::SendRequest&,unsigned rounded)const;
    void poll();
    bool drainEndMp();
    bool waitForDeparture(u16 bit=0);
    void pendingAssociation();
    bool pauseAssociation() const;
    void completeAssociation();
    unsigned resumeStartMp(u16& state) const;
    void retainStartMp(u16 state);
    void noteDeparture(bool toParent,unsigned aid) noexcept;
    bool service(ARM&);
    void beginTransportReplacement();
    void finishTransportReplacement();
};
}
