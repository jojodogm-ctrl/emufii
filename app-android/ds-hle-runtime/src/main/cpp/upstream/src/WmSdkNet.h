// nifi2 experiment: one participating NDS per process, asynchronous WM data.
// Loopback-default test broker; explicit trusted-private lab profile optional.
// No replay, guest pointers, authentication or cross-process barrier here.
// Control RPCs are immediate. Broker delays DATA and ACK separately by RTT/2.
// This isolates the data-sharing latency ceiling, not a production WAN protocol.
#ifndef MELONDS_WM_SDK_NET_H
#define MELONDS_WM_SDK_NET_H
#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
#include <mmsystem.h>
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <unistd.h>
#endif
#include "WmSdkTape.h"
#include "WmSdkSharingPolicy.h"
#include "Nifi2NetworkProfile.h"
#include <atomic>
#include <condition_variable>
#include <cstdio>
#include <deque>
#include <map>
#include <memory>
#include <mutex>
#include <thread>

namespace melonDS::wm_sdk::net {
using namespace tape;
#ifdef _WIN32
using Socket=SOCKET;
constexpr Socket BadSocket=INVALID_SOCKET;
inline void closeSocket(Socket s){closesocket(s);}
inline void initSockets(){static const bool ok=[]{WSADATA w{};if(WSAStartup(MAKEWORD(2,2),&w)!=0)return false;timeBeginPeriod(1);return true;}();if(!ok)throw Fault("WSAStartup");}
#else
using Socket=int;
constexpr Socket BadSocket=-1;
inline void closeSocket(Socket s){::close(s);}
inline void initSockets(){}
#endif
inline void stopSocket(Socket s){if(s!=BadSocket)::shutdown(s,2);}
inline void socketOptions(Socket s){
    int one=1;setsockopt(s,IPPROTO_TCP,TCP_NODELAY,reinterpret_cast<const char*>(&one),sizeof(one));
#ifdef SO_NOSIGPIPE
    setsockopt(s,SOL_SOCKET,SO_NOSIGPIPE,reinterpret_cast<const char*>(&one),sizeof(one));
#endif
    // Fail a stuck control writer instead of holding the broker mutex forever.
    // A broken TCP control session still ends the experiment; no reconnect claim.
#ifdef _WIN32
    DWORD timeout=2000;
#else
    timeval timeout{2,0};
#endif
    setsockopt(s,SOL_SOCKET,SO_SNDTIMEO,reinterpret_cast<const char*>(&timeout),sizeof(timeout));
}
inline bool transfer(Socket s,void* ptr,std::size_t n,bool writing){
    char* p=static_cast<char*>(ptr);
    while(n){
#ifdef MSG_NOSIGNAL
        constexpr int sendFlags=MSG_NOSIGNAL;
#else
        constexpr int sendFlags=0;
#endif
        int k=writing ? ::send(s,p,static_cast<int>(n),sendFlags) : ::recv(s,p,static_cast<int>(n),0);
        if(k<=0)return false;p+=k;n-=static_cast<std::size_t>(k);}
    return true;
}
inline bool readPacket(Socket s,Bytes& b){
    std::array<std::uint8_t,4> h{};if(!transfer(s,h.data(),4,false))return false;
    if(h[0]!='N'||h[1]!='2')throw Fault("nifi2 protocol magic");
    auto n=get16(h.data()+2);if(!n || n>1024)throw Fault("nifi2 packet length");
    b.resize(n);return transfer(s,b.data(),n,false);
}
inline void writePacket(Socket s,const Bytes& b){
    if(b.empty()||b.size()>1024)throw Fault("nifi2 output length");
    // Submit one complete record, not a tiny header followed by its body.
    // TCP may still segment it; the reader continues to tolerate any split.
    // The wire bytes and real receive/completion semantics are unchanged.
    std::array<std::uint8_t,1028> packet{{'N','2',0,0}};
    put16(packet.data()+2,static_cast<std::uint16_t>(b.size()));
    std::copy(b.begin(),b.end(),packet.begin()+4);
    if(!transfer(s,packet.data(),b.size()+4,true))throw Fault("nifi2 socket write");
}
enum Code : std::uint8_t {Open=1,Close,Advertise,Scan,Connect,Disconnect,Start,Stop,Send,Ack,AdoptSaved,PollSaved,LookupSaved,
    PrepareSnapshot,ReleaseSnapshot,SendTerminal,MpReady,WatchMpReady,StopReceipts,RetainReceipts,ClaimSharingWindow,SharingDrain,BlockSend,
    Reply=128,Member,Receive,Complete,Fatal,SavedStatus,SnapshotFrozen,SnapshotReleased,MpReadyStatus,
    SharingDrainStatus,ReplyAssociationPending,Incarnated=255};
// Bounded, generation-bound participation status, not delivery/ACK evidence.
// One subscription per endpoint; revision prevents an initial RPC result from
// overwriting a newer notification already consumed by the receive thread.
struct ReadyWatch {
    Token endpoint=0; Bus::Session session{};
    std::uint64_t id=0,revision=0;
    std::optional<std::uint16_t> mask;
};
inline void encodeReadyWatch(Writer& w,const ReadyWatch& s){
    w.u64(s.endpoint);w.session(s.session);w.u64(s.id);w.u64(s.revision);
    w.boolean(bool(s.mask));if(s.mask)w.u16(*s.mask);
}
inline ReadyWatch decodeReadyWatch(Reader& r){
    ReadyWatch s;s.endpoint=r.u64();s.session=r.session();s.id=r.u64();s.revision=r.u64();
    if(r.boolean())s.mask=r.u16();r.end();
    if(!s.endpoint||!s.id||!s.revision||!s.session.epoch||
       (s.mask&&(*s.mask>255||(*s.mask&(1u<<s.session.aid))||(s.session.aid&&(*s.mask&~1u)))))
        throw Fault("MP readiness status identity/mask");
    return s;
}
struct DrainTarget {Token token=0;Bus::Session session{};std::uint64_t id=0;bool ready=false;};
struct DrainNotice {
    Token endpoint=0,domain=0;Bus::Session session{};std::uint64_t revision=0;
    std::array<DrainTarget,8> targets{};std::array<Bus::Epoch,8> live{};
};
inline void encodeDrainNotice(Writer& w,const DrainNotice& n){
    w.u16(2);w.u64(n.endpoint);w.session(n.session);w.u64(n.domain);w.u64(n.revision);
    for(unsigned i=0;i<8;++i){const auto& t=n.targets[i];w.u64(n.live[i]);w.u64(t.token);w.session(t.session);w.u64(t.id);w.boolean(t.ready);}
}
inline DrainNotice decodeDrainNotice(Reader& r){
    if(r.u16()!=2)throw Fault("sharing drain version");DrainNotice n;
    n.endpoint=r.u64();n.session=r.session();n.domain=r.u64();n.revision=r.u64();
    if(!n.endpoint||!n.domain||!n.session.epoch||n.session.aid>=8||!n.revision)throw Fault("sharing drain observer");
    for(unsigned i=0;i<8;++i){auto& t=n.targets[i];n.live[i]=r.u64();t.token=r.u64();t.session.aid=r.u8();t.session.epoch=r.u64();t.id=r.u64();t.ready=r.boolean();
        if(t.token?(!t.id||!t.session.epoch||t.session.aid!=i):(t.id||t.session.epoch||t.session.aid||t.ready))throw Fault("sharing drain target");}
    r.end();return n;
}
inline constexpr std::uint16_t SnapshotProtocolVersion=1;
struct SnapshotControl {std::uint64_t operation=0,incarnation=0;Token endpoint=0;};
inline SnapshotControl decodeSnapshotControl(Reader& r){
    const auto version=r.u16();SnapshotControl c;c.operation=r.u64();c.incarnation=r.u64();c.endpoint=r.u64();r.end();
    if(version!=SnapshotProtocolVersion||!c.operation||!c.incarnation||!c.endpoint)throw Fault("snapshot control identity/version");
    return c;
}
inline Bytes snapshotControl(Code code,std::uint64_t operation,std::uint64_t incarnation,Token own){
    if((code!=PrepareSnapshot&&code!=ReleaseSnapshot&&code!=SnapshotFrozen&&code!=SnapshotReleased)||!operation||!incarnation||!own)
        throw Fault("snapshot control identity");
    Writer w;w.u8(code);w.u16(SnapshotProtocolVersion);w.u64(operation);w.u64(incarnation);w.u64(own);return std::move(w.bytes);
}
// Protocol extension for a coordinated broker adapter. Existing legacy streams
// remain unchanged. Every record on a restored stream MUST use this envelope;
// stale/unframed records fail before any queue mutation, even after release.
inline Bytes incarnatedPacket(const Bytes& payload,std::uint64_t incarnation){
    if(!incarnation||payload.empty()||payload.size()>1015)throw Fault("snapshot envelope bounds");
    Writer w;w.u8(Incarnated);w.u64(incarnation);w.bytes.insert(w.bytes.end(),payload.begin(),payload.end());return std::move(w.bytes);
}
inline void consumeIncarnation(Bytes& packet,std::uint64_t expected){
    Reader r(packet);if(r.u8()!=Incarnated||r.u64()!=expected||!expected||packet.size()<=9)throw Fault("stale snapshot incarnation");
    packet.erase(packet.begin(),packet.begin()+9);
}
inline Bytes snapshotMarker(Code code,std::uint64_t operation,std::uint64_t incarnation,Token own){
    if((code!=SnapshotFrozen&&code!=SnapshotReleased)||!operation||!incarnation||!own)throw Fault("snapshot marker identity");
    return snapshotControl(code,operation,incarnation,own);
}
inline void savedString(Writer& w,const std::string& s,unsigned max){
    if(s.size()>max)throw Fault("saved room string length");
    w.u8(static_cast<std::uint8_t>(s.size()));for(auto c:s)w.u8(static_cast<std::uint8_t>(c));
}
inline std::string savedString(Reader& r,unsigned max){
    const auto n=r.u8();if(n>max)throw Fault("saved room string length");
    std::string s;for(unsigned i=0;i<n;++i)s+=static_cast<char>(r.u8());return s;
}
inline void encodeSavedOffer(Writer& w,const SavedRoomOffer& o){
    savedString(w,o.profile,32);savedString(w,o.romSha,64);savedString(w,o.setSha,64);
    w.u64(o.epoch);w.u32(o.players);w.u32(o.seat);w.u32(o.bufferTicks);w.u32(o.savedAid);
}
inline SavedRoomOffer decodeSavedOffer(Reader& r){
    SavedRoomOffer o;o.profile=savedString(r,32);o.romSha=savedString(r,64);o.setSha=savedString(r,64);
    o.epoch=r.u64();o.players=r.u32();o.seat=r.u32();o.bufferTicks=r.u32();o.savedAid=r.u32();return o;
}
inline void encodeSavedResult(Writer& w,const SavedRoomResult& s){
    w.u8(static_cast<std::uint8_t>(s.state));w.u8(static_cast<std::uint8_t>(s.reason));
    w.u64(s.room);w.u64(s.epoch);w.u16(s.members);
    if(s.state==SavedRoomState::Ready)w.session(s.session);
}
inline SavedRoomResult decodeSavedResult(Reader& r){
    SavedRoomResult s;auto state=r.u8(),reason=r.u8();
    if(state>2||reason>static_cast<unsigned>(SavedRoomReason::Capacity))throw Fault("saved room status");
    s.state=static_cast<SavedRoomState>(state);s.reason=static_cast<SavedRoomReason>(reason);
    s.room=r.u64();s.epoch=r.u64();s.members=r.u16();
    if(s.state==SavedRoomState::Ready){s.session=r.session();
        // Saved cohorts contain 2..8 dense AIDs; admission separately pins the
        // game profile and exact seat mapping. Reject gaps and out-of-range bits.
        if(!s.room||!s.epoch||s.members<3||s.members>255||(s.members&(s.members+1))||
           !(s.members&(1u<<s.session.aid)))throw Fault("saved room membership");
    }else if(s.members)throw Fault("premature saved room membership");
    if((s.state==SavedRoomState::Rejected)==(s.reason==SavedRoomReason::None))throw Fault("saved room reason");
    return s;
}
// The broker and listener-free unit tests share this immediate control handler.
// Validate the entire bounded request before mutating the registry.
inline void savedControl(Transport& transport,Code code,Token t,Reader& r,Writer& w){
    if(code==AdoptSaved){auto room=r.u64();auto offer=decodeSavedOffer(r);r.end();
        encodeSavedResult(w,transport.adoptSavedRoom(t,room,offer));
    }else if(code==PollSaved){r.end();encodeSavedResult(w,transport.pollSavedRoom(t));}
    else if(code==LookupSaved){auto offer=decodeSavedOffer(r);r.end();encodeSavedResult(w,transport.lookupSavedRoom(t,offer));}
    else throw Fault("saved room control code");
}
inline void encodeDelivery(Writer& w,Token domain,const Bus::Delivery& d){
    w.u64(domain);w.token(d.token);w.u16(d.sequence);w.u8(d.port);w.u16(d.wire_sequence);
    w.u16(static_cast<std::uint16_t>(d.payload.size()));w.array(d.payload.bytes());
}
inline Bus::Delivery decodeDelivery(Reader& r,Token& domain,bool blockLane=false){
    domain=r.u64();Bus::Delivery d;d.token=r.token();d.sequence=r.u16();d.port=r.u8();d.wire_sequence=r.u16();
    auto n=r.u16();auto b=r.array<512>();if(!domain||(d.port>15&&!(blockLane&&d.port==Bus::BlockPort))||!Bus::Payload::from_owned(b,n,d.payload))throw Fault("nifi2 delivery");return d;
}
inline void encodeCompletion(Writer& w,const Bus::Completion& c){w.message(c.message);w.u16(c.acknowledged);w.u8(static_cast<std::uint8_t>(c.outcome));w.u8(static_cast<std::uint8_t>(c.reason));}
inline Bus::Completion decodeCompletion(Reader& r,bool blockLane=false){
    Bus::Completion c;c.message=r.message(blockLane);c.acknowledged=r.u16();auto o=r.u8(),e=r.u8();
    if(o>2||e>4||(c.acknowledged&~c.message.targets))throw Fault("nifi2 completion");
    c.outcome=static_cast<Bus::Outcome>(o);c.reason=static_cast<Bus::Reason>(e);return c;
}

class Remote final : public Transport {
public:
    enum class DeliveryTiming { NextFrame=0, Subframe=1, InitializedPorts=2 };
    // Single-owner event-loop adapter (e.g. a browser worker). Carries the SAME
    // bounded broker records as TCP, without a socket or receive thread. send
    // must copy/admit synchronously; receive(wait=true) may suspend the owner,
    // but must finish or fail within five seconds. A non-waiting empty receive
    // is normal. No reentrant Transport calls or manufactured RPC results.
    // Snapshot control uses the native markers/codecs. The parked owner must
    // explicitly pollExternalSnapshot(); no callback runs while a guard lives.
    // Restored callbacks must bind a NEW isolated carrier, never a mutable
    // lookup that could redirect an old Remote onto its replacement's stream.
    struct ExternalChannel {
        std::function<bool(const Bytes&)> send;
        std::function<bool(Bytes&,bool)> receive;
    };
private:
    friend class snapshot::RemoteCodec;
#ifdef WM_SDK_SNAPSHOT_OFFLINE_TEST
    friend struct RemoteSnapshotTestAccess;
    std::vector<Bytes>* offlineOutput=nullptr;
    std::deque<Bytes>* offlineReplies=nullptr;
#endif
    const DeliveryTiming deliveryTiming;
    Socket socket=BadSocket;
    std::thread receiver;
    std::mutex mutex,output;
    std::condition_variable changed;
    bool failed=false,closing=false;
    std::string failure;
    std::unique_ptr<ExternalChannel> external;
    Token own=0;
    Bus::Session session{};
    std::uint16_t subframePorts=0;
    std::uint64_t frame=0,nextRequest=0,replyRequest=0,nextSend=0;
    std::array<std::uint16_t,16> sequence{};
    std::uint16_t blockSequence=0;unsigned acceptedSharingVersion=0;
    Bytes reply;
    struct ScanRequest {
        Token endpoint;std::uint16_t channel;Mac filter;std::uint64_t id,polledFrame;
        std::chrono::steady_clock::time_point deadline;
    };
    std::optional<ScanRequest> scanRequest;
    Code activeRpcCode=Open;
    bool replyAssociationPending=false;
    std::optional<Code> pendingAssociationCode;
    Bytes pendingAssociationArgs;
    static bool associationCode(Code code){return code==Advertise||code==Scan||code==Connect||code==Start;}

    std::deque<Event> events;
    struct Incoming{Token domain;Bus::Delivery d;std::uint64_t eligible;};
    std::deque<Incoming> incoming;
    std::map<std::uint64_t,Incoming> taken;
    std::map<std::uint64_t,Bus::Message> pending;
    std::deque<Bus::Completion> completed;
    // One opt-in reservation through owner polling; never serialized. The
    // broker independently binds its retained result to the real source epoch.
    Bus::SendId terminalSend=0;
    bool retainReceipts=false;
    bool sharingWindowClaimed=false;
    DrainNotice drainNotice{};
    std::array<std::uint64_t,8> drainReported{},blockedEpoch{};
    std::array<Token,8> blockedToken{};
    std::uint16_t drainBlocked=0;
    void consumeDrainNotice(Reader& r){
        const auto n=decodeDrainNotice(r);
        if(!sharingWindowClaimed||n.endpoint!=own)throw Fault("sharing drain foreign endpoint");
        if(session.epoch&&n.session!=session)return;
        if(drainNotice.session==n.session&&n.revision<=drainNotice.revision)return;
        for(unsigned i=0;i<8;++i){const auto& target=n.targets[i];
            if(target.token){drainBlocked|=std::uint16_t(1u<<i);blockedEpoch[i]=target.session.epoch;blockedToken[i]=target.token;}
            else if(n.live[i]&&n.live[i]!=blockedEpoch[i]){drainBlocked&=std::uint16_t(~(1u<<i));blockedEpoch[i]=0;blockedToken[i]=0;}
            if(target.id!=drainNotice.targets[i].id)drainReported[i]=0;
        }
        drainNotice=n;
    }
    void clearDrain(){drainNotice={};drainReported={};blockedEpoch={};blockedToken={};drainBlocked=0;}
    ReadyWatch readyWatch{};
    std::uint64_t nextReadyWatch=0;
    void consumeReadyWatch(Reader& r){
        const auto value=decodeReadyWatch(r);
        // A Stop/Start/Close may have retired an otherwise well-formed in-flight
        // notification. It cannot resurrect that endpoint or generation.
        if(value.endpoint!=readyWatch.endpoint||value.session!=readyWatch.session||value.id!=readyWatch.id)return;
        if(value.revision>readyWatch.revision)readyWatch=value;
    }
    // mutex is held. Only the opt-in send is retired here; ordinary paths keep
    // their existing queue lifecycle and sequence behavior.
    void clearTerminal(){
        if(!terminalSend)return;
        pending.erase(terminalSend);
        completed.erase(std::remove_if(completed.begin(),completed.end(),[&](const auto& c){
            return c.message.send_id==terminalSend;
        }),completed.end());
        terminalSend=0;
    }
    SavedRoomResult saved{};
    std::uint64_t transportIncarnation=1,snapshotOperation=0;
    bool snapshotPreparing=false,snapshotFrozen=false,snapshotReleasing=false;
    bool expectRestoreMarker=false,fencedStream=false,stagedImport=false;
    unsigned activeRpc=0;
    // Caller holds mutex. Readiness never rewrites an existing FIFO entry.
    void updateSubframePorts(std::uint16_t mask){
        if(subframePorts==mask)return;
        subframePorts=mask;
        std::fprintf(stderr,"[wmsdk-delivery] frame=%llu epoch=%llu subframe_ports=0x%04x\n",
            static_cast<unsigned long long>(frame),static_cast<unsigned long long>(session.epoch),
            static_cast<unsigned>(mask));
    }
    void applySaved(const SavedRoomResult& value){
        if(saved.state!=value.state || saved.room!=value.room || saved.epoch!=value.epoch ||
           saved.session!=value.session || saved.members!=value.members)updateSubframePorts(0);
        if(value.state==SavedRoomState::Ready){
            if(saved.state!=SavedRoomState::Ready || saved.room!=value.room || saved.session!=value.session){
                readyWatch={};terminalSend=0;pending.clear();completed.clear();taken.clear();sequence={};blockSequence=0;session=value.session;
            }
        }else if(value.room){
            updateSubframePorts(0);
            readyWatch={};terminalSend=0;clearDrain();session={};pending.clear();completed.clear();incoming.clear();taken.clear();
        }
        saved=value;
    }
    void check(){if(closing)throw Fault("nifi2 channel retired");if(failed)throw Fault("nifi2: "+failure);if(snapshotPreparing)throw Fault("nifi2 snapshot owner is parked");}
    // output is held; fencedStream/incarnation change only while both locks are
    // held and no receiver is running (detached import/attach).
    void writeOutput(const Bytes& b){
        {std::lock_guard<std::mutex> lock(mutex);if(closing)throw Fault("nifi2 channel retired");}
        if(external){
            try{
                const auto packet=fencedStream?incarnatedPacket(b,transportIncarnation):b;
                if(packet.empty()||packet.size()>1024||!external->send(packet))throw Fault("nifi2 external output refused");
            }catch(...){failExternal("external output failure");throw;}
            return;
        }
#ifdef WM_SDK_SNAPSHOT_OFFLINE_TEST
        if(offlineOutput){offlineOutput->push_back(fencedStream?incarnatedPacket(b,transportIncarnation):b);return;}
#endif
        if(socket==BadSocket)throw Fault("nifi2 detached snapshot has no channel");
        writePacket(socket,fencedStream?incarnatedPacket(b,transportIncarnation):b);
    }
    void outputPacket(const Bytes& b){std::lock_guard<std::mutex> lock(output);writeOutput(b);}
    Bytes finishRpcReply(Code code,const Writer& args){
        Bytes value;bool deferred;
        {
            std::lock_guard<std::mutex> lock(mutex);check();
            deferred=replyAssociationPending;value=std::move(reply);
            if(deferred){pendingAssociationCode=code;pendingAssociationArgs=args.bytes;}
            else if(associationCode(code)){pendingAssociationCode.reset();pendingAssociationArgs.clear();}
        }
        // Throw only on the transport owner's stack, after decoding and locks.
        // The receiver's catch-all must never turn a legal Pending into failure.
        if(deferred)throw AssociationPending{};
        return value;
    }
    void consumeAssociationPending(Reader& r){
        const auto id=r.u64();const auto version=r.u16();const auto code=static_cast<Code>(r.u8());const auto endpoint=r.u64();r.end();
        if(!activeRpc||id!=nextRequest||id<=replyRequest||version!=3||!sharingWindowClaimed||
           !endpoint||endpoint!=own||code!=activeRpcCode||!associationCode(code))
            throw Fault("association pending response identity/capability");
        replyRequest=id;reply.clear();replyAssociationPending=true;changed.notify_all();
    }
    Bytes rpc(Code code,const Writer& args){
        Writer w;std::uint64_t id;
        {std::lock_guard<std::mutex> lock(mutex);check();
            if(pendingAssociationCode&&associationCode(code)&&
               (*pendingAssociationCode!=code||pendingAssociationArgs!=args.bytes))
                throw Fault("association retry changed operation/arguments");
            if(scanRequest)throw Fault("control call overlaps pending scan");
            if(sharingWindowClaimed&&activeRpc)throw Fault("overlapping sharing control owner");
            activeRpcCode=code;replyAssociationPending=false;id=++nextRequest;++activeRpc;}
        struct RpcScope{Remote& remote;~RpcScope(){std::lock_guard<std::mutex> lock(remote.mutex);--remote.activeRpc;}} rpcScope{*this};
        w.u8(code);w.u64(id);w.bytes.insert(w.bytes.end(),args.bytes.begin(),args.bytes.end());outputPacket(w.bytes);
        if(external){
            // No mutex survives a browser suspension. Only this owner calls
            // Transport; arrivals remain bytes until this code decodes them.
            const auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(5);
            for(unsigned budget=0;budget<256;++budget){
                if(std::chrono::steady_clock::now()>=deadline){failExternal("external control timeout");throw Fault("nifi2 external control timeout");}
                bool ready;{std::lock_guard<std::mutex> lock(mutex);check();ready=replyRequest==id;}
                if(ready)return finishRpcReply(code,args);
                if(!receiveExternal(true))throw Fault("nifi2 external control timeout");
            }
            failExternal("external control notification budget");
            throw Fault("nifi2 external control notification budget");
        }
        std::unique_lock<std::mutex> lock(mutex);
#ifdef WM_SDK_SNAPSHOT_OFFLINE_TEST
        // Detached tests supply control results without starting a receiver.
        if(offlineReplies){
            if(offlineReplies->empty())throw Fault("missing offline control reply");
            reply=std::move(offlineReplies->front());offlineReplies->pop_front();replyRequest=id;
        }
#endif
        if(!changed.wait_for(lock,std::chrono::seconds(5),[&]{return failed||replyRequest==id;}))throw Fault("nifi2 control timeout");
        check();lock.unlock();return finishRpcReply(code,args);
    }
    // Shared by the receiver and offline access; caller holds mutex. Capture
    // eligibility exactly once, after decoding the actual bounded delivery.
    void consumeArrival(Reader& r){
        Token domain;auto d=decodeDelivery(r,domain,acceptedSharingVersion==sharing_policy::BlockVersion);r.end();
        if(incoming.size()+taken.size()>=Bus::MaxDeliveriesPerEndpoint)throw Fault("nifi2 receive overflow");
        const bool subframe=d.port==Bus::BlockPort || deliveryTiming==DeliveryTiming::Subframe ||
            (deliveryTiming==DeliveryTiming::InitializedPorts && (d.port<Bus::PortCount&&(subframePorts&(1u<<d.port))));
        incoming.push_back({domain,d,frame+(subframe?0:1)});
    }
    void consumeMember(Reader& r){
        auto e=r.event();r.end();
        if(events.size()>=16)throw Fault("nifi2 member overflow");
        events.push_back(e);updateSubframePorts(0);
    }
    void consumeCompletion(Reader& r){
        auto c=decodeCompletion(r,acceptedSharingVersion==sharing_policy::BlockVersion);r.end();
        if(!pending.count(c.message.send_id)||completed.size()>=Bus::MaxCompletionsPerEndpoint)throw Fault("nifi2 unknown completion");
        completed.push_back(c);
    }
    void failExternal(const char* why){
        std::lock_guard<std::mutex> lock(mutex);failed=true;failure=why;
    }
    bool receiveExternal(bool wait,bool snapshotProgress=false){
        if(!external)return false;
        {
            std::lock_guard<std::mutex> lock(mutex);
            if(snapshotProgress){
                if(failed||closing)throw Fault("nifi2 external snapshot channel failed");
                // Do not consume even one byte beyond either FIFO boundary.
                if(!snapshotPreparing||(snapshotFrozen&&!snapshotReleasing))return false;
            }else check();
        }
        try{
            Bytes b;
            if(!external->receive(b,wait)){
                if(wait)failExternal("external control timeout");
                return false;
            }
            if(b.empty()||b.size()>1024)throw Fault("nifi2 external input bounds");
            if(fencedStream)consumeIncarnation(b,transportIncarnation);
            Reader r(b);auto code=r.u8();std::lock_guard<std::mutex> lock(mutex);
            if(failed||closing)throw Fault("nifi2 external channel failed");
            if(expectRestoreMarker&&code!=SnapshotFrozen)throw Fault("snapshot restore marker required");
            if(snapshotReleasing&&code!=SnapshotReleased)throw Fault("snapshot release marker required");
            if(code==SnapshotFrozen||code==SnapshotReleased){
                consumeSnapshotMarker(r,code==SnapshotReleased);
            }else if(code==ReplyAssociationPending){consumeAssociationPending(r);
            }else if(code==Reply){
                const auto id=r.u64();
                if(!activeRpc||id!=nextRequest||id<=replyRequest)throw Fault("nifi2 unexpected external reply");
                Bytes result;while(result.size()+9<b.size())result.push_back(r.u8());r.end();
                replyRequest=id;reply=std::move(result);replyAssociationPending=false;
            }else if(code==Member)consumeMember(r);
            else if(code==Receive)consumeArrival(r);
            else if(code==Complete)consumeCompletion(r);
            else if(code==MpReadyStatus)consumeReadyWatch(r);
            else if(code==SharingDrainStatus)consumeDrainNotice(r);
            else if(code==SavedStatus){auto value=decodeSavedResult(r);r.end();applySaved(value);}
            else throw Fault("nifi2 unsupported external notification");
            r.end();return true;
        }catch(...){failExternal("external channel or protocol failure");throw;}
    }
    void pollExternal(){
        if(external)for(unsigned n=0;n<64 && receiveExternal(false);++n){}
    }
    void receiveLoop(){try{
        Bytes b;while(readPacket(socket,b)){
            if(fencedStream)consumeIncarnation(b,transportIncarnation);
            Reader r(b);auto code=r.u8();std::unique_lock<std::mutex> lock(mutex);
            if(expectRestoreMarker&&code!=SnapshotFrozen)throw Fault("snapshot restore marker required");
            if(snapshotReleasing&&code!=SnapshotReleased)throw Fault("snapshot release marker required");
            if(code==SnapshotFrozen){
                consumeSnapshotMarker(r,false);
                // At this exact FIFO boundary all preceding records have been
                // applied. Leave any later bytes unread until explicit release.
                changed.wait(lock,[&]{return closing||snapshotReleasing;});
                if(closing)return;
                continue;
            }
            if(code==SnapshotReleased){
                consumeSnapshotMarker(r,true);continue;
            }
            if(code==ReplyAssociationPending){consumeAssociationPending(r);}
            else if(code==Reply){const auto id=r.u64();
                if(!activeRpc||id!=nextRequest||id<=replyRequest)throw Fault("nifi2 unexpected control reply");
                replyRequest=id;replyAssociationPending=false;reply.clear();while(reply.size()+9<b.size())reply.push_back(r.u8());changed.notify_all();}
            else if(code==Member){consumeMember(r);}
            else if(code==Receive){consumeArrival(r);}
            else if(code==Complete){consumeCompletion(r);}
            else if(code==MpReadyStatus){consumeReadyWatch(r);}
            else if(code==SharingDrainStatus){consumeDrainNotice(r);}
            else if(code==SavedStatus){auto value=decodeSavedResult(r);r.end();applySaved(value);}
            else if(code==Fatal)throw Fault("broker rejected protocol or data contract");
            else throw Fault("nifi2 unknown notification");r.end();
        }
        std::lock_guard<std::mutex> lock(mutex);if(!closing){failed=true;failure="broker closed";}changed.notify_all();
    }catch(const std::exception& e){std::lock_guard<std::mutex> lock(mutex);failed=true;failure=e.what();changed.notify_all();}}
    void consumeSnapshotMarker(Reader& r,bool release){
        auto c=decodeSnapshotControl(r);acceptSnapshotMarker(c.operation,c.incarnation,c.endpoint,release);
    }
    void acceptSnapshotMarker(std::uint64_t operation,std::uint64_t incarnation,Token endpoint,bool release){
        if(!snapshotPreparing||!operation||operation!=snapshotOperation||incarnation!=transportIncarnation||endpoint!=own||!own||activeRpc||nextRequest!=replyRequest)
            throw Fault("snapshot marker disagrees with prepared cut");
        if(release){
            if(!snapshotReleasing)throw Fault("unrequested snapshot release");
            snapshotPreparing=snapshotFrozen=snapshotReleasing=false;stagedImport=false;
        }else{
            if(snapshotFrozen||snapshotReleasing)throw Fault("duplicate snapshot freeze");
            snapshotFrozen=true;expectRestoreMarker=false;
        }
        changed.notify_all();
    }
public:
    explicit Remote(ExternalChannel channel,DeliveryTiming timing=DeliveryTiming::NextFrame)
        :deliveryTiming(timing),external(std::make_unique<ExternalChannel>(std::move(channel))){
        if(!external->send||!external->receive)throw Fault("nifi2 external channel callbacks");
    }
    // Detached staging object: no socket, receiver, network initialization or
    // fabricated endpoint. Import supplies the actual checkpoint's local state.
    struct DetachedForSnapshot {};
    explicit Remote(DetachedForSnapshot,DeliveryTiming timing=DeliveryTiming::NextFrame):deliveryTiming(timing){
        snapshotPreparing=snapshotFrozen=true;
    }
    class SnapshotQuiescence {
        friend class Remote;
        Remote* owner;
        std::unique_lock<std::mutex> outputLock,stateLock;
        explicit SnapshotQuiescence(Remote& r):owner(&r),outputLock(r.output),stateLock(r.mutex){}
    public:
        SnapshotQuiescence(SnapshotQuiescence&&)=default;
        SnapshotQuiescence& operator=(SnapshotQuiescence&&)=delete;
        SnapshotQuiescence(const SnapshotQuiescence&)=delete;
        // Detached staging permits validation/import, but is NOT a broker ACK.
        bool brokerConfirmed() const {
            return outputLock.owns_lock()&&stateLock.owns_lock()&&
                ((owner->socket!=BadSocket&&owner->receiver.joinable())||owner->external)&&
                !owner->expectRestoreMarker&&owner->snapshotFrozen&&
                !owner->snapshotReleasing&&!owner->failed&&!owner->closing;
        }
        std::uint64_t operation() const {return stateLock.owns_lock()?owner->snapshotOperation:0;}
        std::uint64_t incarnation() const {return stateLock.owns_lock()?owner->transportIncarnation:0;}
    };
    Remote* checkpointRemote() override { return this; }
    // Owner must be parked, with no outstanding calls into this object. This
    // sends a FIFO marker request; it does NOT assert broker quiescence itself.
    // Unsupported legacy brokers fail closed; never probe them during play.
    snapshot::Status prepareSnapshot(std::uint64_t operation,std::uint64_t incarnation){
        std::lock_guard<std::mutex> writer(output);
        {std::lock_guard<std::mutex> lock(mutex);
            if(terminalSend||sharingWindowClaimed)return snapshot::Status::Unsupported;
            if(failed||closing||activeRpc||!own||(!external&&socket==BadSocket)||!operation||incarnation!=transportIncarnation)
                return snapshot::Status::NotQuiescent;
            if(external&&nextRequest!=replyRequest)return snapshot::Status::NotQuiescent;
            if(snapshotPreparing)return operation==snapshotOperation?snapshot::Status::Ok:snapshot::Status::NotQuiescent;
            snapshotOperation=operation;snapshotPreparing=true;
        }
        outputPacketForSnapshot(snapshotControl(PrepareSnapshot,operation,incarnation,own));return snapshot::Status::Ok;
    }
    // Browser owner only, with no SnapshotQuiescence guard held. Consume at
    // most 64 immediately available records, stopping AT freeze/release. Ok
    // means a real marker was observed; an empty carrier is NotQuiescent.
    // Carrier/protocol faults throw and leave the owner failed/parked. The
    // coordinator owns the overall timeout and must abort without resuming.
    snapshot::Status pollExternalSnapshot(){
        if(!external)return snapshot::Status::Unsupported;
        for(unsigned n=0;n<64;++n){
            {
                std::lock_guard<std::mutex> lock(mutex);
                if(failed||closing||activeRpc||!snapshotOperation)return snapshot::Status::NotQuiescent;
                if((snapshotFrozen&&!snapshotReleasing&&!expectRestoreMarker)||!snapshotPreparing)
                    return snapshot::Status::Ok;
            }
            if(!receiveExternal(false,true))break;
        }
        std::lock_guard<std::mutex> lock(mutex);
        return !failed&&!closing&&snapshotOperation&&
            ((snapshotFrozen&&!snapshotReleasing&&!expectRestoreMarker)||!snapshotPreparing)
            ?snapshot::Status::Ok:snapshot::Status::NotQuiescent;
    }
    // Non-waiting acquisition after the receiver observed SnapshotFrozen. The
    // guard is nonforgeable and locks local state/output until destruction.
    // nullptr means not frozen (or failed); caller coordinates timeout/abort.
    std::unique_ptr<SnapshotQuiescence> tryFreezeSnapshot(){
        auto guard=std::unique_ptr<SnapshotQuiescence>(new SnapshotQuiescence(*this));
        if(!snapshotPreparing||!snapshotFrozen||snapshotReleasing||failed||closing||activeRpc||terminalSend||sharingWindowClaimed||pendingAssociationCode)return {};
        return guard;
    }
    snapshot::Status exportSnapshot(const SnapshotQuiescence&,snapshot::Bytes&);
    // Import is deliberately restricted to a DETACHED object. Restoring over a
    // live socket could let pre-restore output escape; create replacement Remote
    // and retire the old instance while every owner remains parked.
    snapshot::Status importSnapshot(const SnapshotQuiescence&,const snapshot::Bytes&,std::uint64_t freshIncarnation,
                                    std::uint64_t restoreOperation);
    static snapshot::Status validateSnapshot(const snapshot::Bytes&);
    // Import into DetachedForSnapshot first, then attach a fresh carrier.
    // Attachment is not confirmation: pollExternalSnapshot must consume the
    // matching incarnated SnapshotFrozen before export or coordinated release.
    snapshot::Status attachRestoredExternalChannel(ExternalChannel channel){
        if(!channel.send||!channel.receive)return snapshot::Status::Unsupported;
        auto attached=std::make_unique<ExternalChannel>(std::move(channel));
        std::lock_guard<std::mutex> writer(output);std::lock_guard<std::mutex> lock(mutex);
        if(external||socket!=BadSocket||receiver.joinable()||failed||closing||activeRpc||
           !stagedImport||!snapshotFrozen||!snapshotPreparing||snapshotReleasing||expectRestoreMarker)
            return snapshot::Status::NotQuiescent;
        external=std::move(attached);expectRestoreMarker=true;snapshotFrozen=false;
        return snapshot::Status::Ok;
    }
    // After a broker has loaded its matching image, attach its NEW isolated
    // channel. The first framed record must be SnapshotFrozen for the imported
    // endpoint, restore operation and fresh incarnation. All later records are
    // fenced as well. No OPEN/START reset is performed.
    snapshot::Status attachRestoredSocket(Socket connected){
        std::lock_guard<std::mutex> writer(output);std::lock_guard<std::mutex> lock(mutex);
        if(external||connected==BadSocket||socket!=BadSocket||receiver.joinable()||!stagedImport||!snapshotFrozen||!snapshotPreparing)
            return snapshot::Status::NotQuiescent;
        socket=connected;expectRestoreMarker=true;snapshotFrozen=false;
        try{receiver=std::thread([this]{receiveLoop();});}
        catch(...){socket=BadSocket;expectRestoreMarker=false;snapshotFrozen=true;throw;}
        return snapshot::Status::Ok;
    }
    // Called after dropping the guard and after the coordinator's release
    // decision. Remains owner-blocked until SnapshotReleased is received.
    snapshot::Status releaseSnapshot(std::uint64_t operation){
        std::lock_guard<std::mutex> writer(output);
        {std::lock_guard<std::mutex> lock(mutex);
            if(failed||closing||!snapshotFrozen||!snapshotPreparing||snapshotReleasing||operation!=snapshotOperation||(!external&&socket==BadSocket))
                return snapshot::Status::NotQuiescent;
            snapshotReleasing=true;
        }
        outputPacketForSnapshot(snapshotControl(ReleaseSnapshot,operation,transportIncarnation,own));changed.notify_all();return snapshot::Status::Ok;
    }
    bool snapshotReleased(std::uint64_t operation){
        std::lock_guard<std::mutex> lock(mutex);
        return operation&&operation==snapshotOperation&&!snapshotPreparing&&!failed&&!closing;
    }
    // Local, terminal fence after the coordinator observed this frozen cut.
    // The old broker may already have closed its stream: failed is allowed,
    // but an unconfirmed/import-only or already-releasing cut is not. Never
    // send CLOSE/STOP: those belong to the obsolete session incarnation.
    snapshot::Status retireSnapshot(std::uint64_t operation,std::uint64_t incarnation){
        std::lock_guard<std::mutex> writer(output);
        std::lock_guard<std::mutex> lock(mutex);
        if(closing||activeRpc||!operation||operation!=snapshotOperation||
           incarnation!=transportIncarnation||!snapshotPreparing||!snapshotFrozen||
           snapshotReleasing||expectRestoreMarker||(!external&&socket==BadSocket))
            return snapshot::Status::NotQuiescent;
        closing=true;changed.notify_all();stopSocket(socket);
        return snapshot::Status::Ok;
    }
    std::uint64_t incarnation(){std::lock_guard<std::mutex> lock(mutex);return transportIncarnation;}
private:
    // Caller holds output. A write failure leaves owners held, never released.
    void outputPacketForSnapshot(const Bytes& bytes){
        try{writeOutput(bytes);}
        catch(const std::exception& e){std::lock_guard<std::mutex> lock(mutex);failed=true;failure=e.what();changed.notify_all();throw;}
    }
public:
    // Own an already-connected stream (also permits listener-free unit tests).
    struct ConnectedSocket { Socket value; };
    explicit Remote(ConnectedSocket connected,DeliveryTiming timing=DeliveryTiming::NextFrame):deliveryTiming(timing),socket(connected.value){
        if(socket==BadSocket)throw Fault("nifi2 connected socket");
        receiver=std::thread([this]{receiveLoop();});
    }
    explicit Remote(unsigned port,DeliveryTiming timing=DeliveryTiming::NextFrame):deliveryTiming(timing){
        if(port<1024||port>65535)throw Fault("nifi2 broker port");initSockets();
        const auto profile=::nifi2::network_profile::environment();
        unsigned who=0;
        if(profile){who=::nifi2::network_profile::local_owner(profile->count);
            if(profile->broker.port!=port)throw Fault("broker port/profile mismatch");}
        socket=::socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);if(socket==BadSocket)throw Fault("nifi2 socket");
        sockaddr_in a{};a.sin_family=AF_INET;a.sin_port=htons(static_cast<std::uint16_t>(port));a.sin_addr.s_addr=htonl(INADDR_LOOPBACK);
        if(profile){
            sockaddr_in local{};local.sin_family=AF_INET;local.sin_addr.s_addr=htonl(profile->peers[who].ip);
            if(::bind(socket,reinterpret_cast<sockaddr*>(&local),sizeof(local))){closeSocket(socket);socket=BadSocket;throw Fault("broker local interface bind");}
            a.sin_addr.s_addr=htonl(profile->broker.ip);
        }
        if(::connect(socket,reinterpret_cast<sockaddr*>(&a),sizeof(a))){closeSocket(socket);socket=BadSocket;throw Fault("nifi2 broker connect");}
        socketOptions(socket);receiver=std::thread([this]{receiveLoop();});
    }
    ~Remote(){ {std::lock_guard<std::mutex> lock(mutex);closing=true;changed.notify_all();}stopSocket(socket);if(receiver.joinable())receiver.join();if(socket!=BadSocket)closeSocket(socket); }
    bool advanceFrame(Token t,std::uint64_t f) override{pollExternal();std::lock_guard<std::mutex> lock(mutex);check();if(t!=own||f<frame)return false;frame=f;return true;}
    bool setSubframePorts(Token t,std::uint16_t mask) override{
        std::lock_guard<std::mutex> lock(mutex);check();
        if(deliveryTiming!=DeliveryTiming::InitializedPorts || !t || t!=own || !session.epoch)return false;
        updateSubframePorts(mask);return true;
    }
    Token open(const Mac& mac) override{Writer w;w.array(mac);auto b=rpc(Open,w);Reader r(b);auto t=r.u64();r.end();std::lock_guard<std::mutex> lock(mutex);updateSubframePorts(0);readyWatch={};terminalSend=0;retainReceipts=false;sharingWindowClaimed=false;acceptedSharingVersion=0;pendingAssociationCode.reset();pendingAssociationArgs.clear();clearDrain();own=t;return t;}
    void close(Token t) override{setSubframePorts(t,0);Writer w;w.u64(t);auto b=rpc(Close,w);Reader r(b);r.end();std::lock_guard<std::mutex> lock(mutex);updateSubframePorts(0);readyWatch={};terminalSend=0;retainReceipts=false;sharingWindowClaimed=false;acceptedSharingVersion=0;pendingAssociationCode.reset();pendingAssociationArgs.clear();clearDrain();own=0;session={};saved={};pending.clear();completed.clear();incoming.clear();taken.clear();events.clear();}
    SharingWindowStatus claimSharingWindow(Token t,unsigned version,unsigned depth) override {
        {std::lock_guard<std::mutex> lock(mutex);check();
            if(!t||t!=own||session.epoch||version>65535||depth>65535)
                return SharingWindowStatus::Rejected;}
        Writer w;w.u64(t);w.u16(static_cast<std::uint16_t>(version));
        w.u16(static_cast<std::uint16_t>(depth));auto bytes=rpc(ClaimSharingWindow,w);Reader r(bytes);
        const auto status=r.u8();const auto acceptedVersion=r.u16(),acceptedDepth=r.u16();r.end();
        if(status>2||!sharing_policy::validClaim(acceptedVersion,acceptedDepth)||
           (status!=2&&(!sharing_policy::validClaim(version,depth)||acceptedVersion!=version||acceptedDepth!=depth)))
            throw Fault("sharing window response");
        if(status!=2){std::lock_guard<std::mutex> lock(mutex);sharingWindowClaimed=true;acceptedSharingVersion=acceptedVersion;}
        return static_cast<SharingWindowStatus>(status);
    }
    void cancelAssociationWait(Token t) override {
        std::lock_guard<std::mutex> lock(mutex);check();
        if(!t||t!=own||activeRpc)throw Fault("association wait cancellation owner");
        pendingAssociationCode.reset();pendingAssociationArgs.clear();
    }
    SharingDrainState sharingDrain(Token t,bool begin,std::uint16_t active,std::uint16_t ready,bool quiet) override {
        pollExternal();Writer args;std::array<std::uint64_t,8> ids{};std::uint16_t offers=0;bool start=false;
        auto state=[&]{const auto& target=drainNotice.targets[session.aid];return target.token==own&&target.session==session?
            (target.ready?SharingDrainState::Ready:SharingDrainState::Pending):SharingDrainState::Idle;};
        {
            std::lock_guard<std::mutex> lock(mutex);check();
            if(!t||t!=own||!session.epoch||!sharingWindowClaimed)return SharingDrainState::Rejected;
            const bool current=drainNotice.session==session;
            start=begin&&(!current||drainNotice.targets[session.aid].token!=own);
            if(current&&quiet&&incoming.empty()&&taken.empty())for(unsigned i=0;i<8;++i){const auto& target=drainNotice.targets[i];
                if(!target.token||drainReported[i]==target.id)continue;
                bool busy=target.token==own?(active||(ready&~drainBlocked)):(active&(1u<<i));
                for(const auto& p:pending)if(target.token==own||(p.second.targets&(1u<<i)))busy=true;
                if(!busy){offers|=std::uint16_t(1u<<i);ids[i]=target.id;}
            }
            if(!start&&!offers)return current?state():SharingDrainState::Idle;
            args.u64(t);args.session(session);args.boolean(start);args.u16(offers);for(auto id:ids)args.u64(id);
        }
        auto bytes=rpc(SharingDrain,args);Reader r(bytes);
        {
            std::lock_guard<std::mutex> lock(mutex);check();consumeDrainNotice(r);
            for(unsigned i=0;i<8;++i)if(ids[i]&&drainNotice.targets[i].id==ids[i])drainReported[i]=ids[i];
            return state();
        }
    }
    SavedRoomResult adoptSavedRoom(Token t,Token room,const SavedRoomOffer& offer) override{
        Writer w;w.u64(t);w.u64(room);encodeSavedOffer(w,offer);auto b=rpc(AdoptSaved,w);Reader r(b);
        auto value=decodeSavedResult(r);r.end();std::lock_guard<std::mutex> lock(mutex);
        // Notifications may already be newer than a pending RPC reply.
        if(saved.room!=value.room || saved.state==SavedRoomState::Pending || !saved.room || value.state==SavedRoomState::Rejected)applySaved(value);
        return saved;
    }
    SavedRoomResult pollSavedRoom(Token t) override{
        Writer w;w.u64(t);auto b=rpc(PollSaved,w);Reader r(b);auto value=decodeSavedResult(r);r.end();
        std::lock_guard<std::mutex> lock(mutex);
        if(saved.room!=value.room || saved.state==SavedRoomState::Pending || !saved.room || value.state==SavedRoomState::Rejected)applySaved(value);
        return saved;
    }
    SavedRoomResult lookupSavedRoom(Token t,const SavedRoomOffer& offer) override{
        Writer w;w.u64(t);encodeSavedOffer(w,offer);auto b=rpc(LookupSaved,w);Reader r(b);
        auto value=decodeSavedResult(r);r.end();return value;
    }
    bool advertise(Token t,const Bss& b,unsigned short n) override{Writer w;w.u64(t);w.array(b);w.u16(n);auto v=rpc(Advertise,w);Reader r(v);auto x=r.boolean();r.end();return x;}
    std::optional<Advertisement> pollScan(Token t,std::uint16_t channel,const Mac& filter,std::uint64_t ownerFrame) override {
        // Sharing-window association deferral has its own negotiated owner.
        // Do not mix it with this ordinary SDK discovery request.
        if(sharingWindowClaimed)return scan(t,channel,filter);
        bool begin=false,poll=false;std::uint64_t id;
        {
            std::lock_guard<std::mutex> lock(mutex);check();
            if(!t||t!=own)throw Fault("scan endpoint identity");
            if(!scanRequest){
                if(activeRpc)throw Fault("scan overlaps control owner");
                id=++nextRequest;++activeRpc;activeRpcCode=Scan;replyAssociationPending=false;
                scanRequest=ScanRequest{t,channel,filter,id,ownerFrame,
                    std::chrono::steady_clock::now()+std::chrono::seconds(5)};
                begin=poll=true;
            }else{
                const auto& scan=*scanRequest;
                if(scan.endpoint!=t||scan.channel!=channel||scan.filter!=filter)
                    throw Fault("pending scan request changed");
                if(ownerFrame<scan.polledFrame)throw Fault("pending scan frame regressed");
                id=scan.id;
                if(std::chrono::steady_clock::now()>=scan.deadline)throw Fault("scan control timeout");
                poll=ownerFrame!=scan.polledFrame;scanRequest->polledFrame=ownerFrame;
            }
        }
        if(begin){Writer w;w.u8(Scan);w.u64(id);w.u64(t);w.u16(channel);w.array(filter);outputPacket(w.bytes);}
        // Never perform a waiting external read on the emulation owner. The
        // socket receiver or bounded nonblocking drain supplies the real reply.
        if(poll)pollExternal();
        Bytes bytes;
        {
            std::lock_guard<std::mutex> lock(mutex);check();
            if(replyRequest!=id)return std::nullopt;
            if(replyAssociationPending)throw Fault("ordinary scan received association deferral");
            bytes=std::move(reply);scanRequest.reset();--activeRpc;
        }
        Reader reader(bytes);Advertisement found;found.parent=reader.u64();found.bss=reader.array<192>();reader.end();
        return found;
    }
    Advertisement scan(Token t,std::uint16_t channel,const Mac& filter) override{Writer w;w.u64(t);w.u16(channel);w.array(filter);auto b=rpc(Scan,w);Reader r(b);Advertisement a;a.parent=r.u64();a.bss=r.array<192>();r.end();return a;}
    bool connect(Token c,Token p,const Bss& b,const ConnectData& d) override{setSubframePorts(c,0);Writer w;w.u64(c);w.u64(p);w.array(b);w.array(d);auto v=rpc(Connect,w);Reader r(v);auto x=r.boolean();r.end();if(x){std::lock_guard<std::mutex> lock(mutex);readyWatch={};clearTerminal();}return x;}
    std::uint16_t disconnect(Token t,std::uint16_t mask) override{setSubframePorts(t,0);Writer w;w.u64(t);w.u16(mask);auto b=rpc(Disconnect,w);Reader r(b);auto n=r.u16();r.end();return n;}
    bool pop(Token t,Event& e,bool& fault) override{pollExternal();std::lock_guard<std::mutex> lock(mutex);check();fault=t!=own;if(fault||events.empty())return false;e=events.front();events.pop_front();
        const auto peerAid=e.toParent?e.aid:0; // child events carry its OWN AID
        if(peerAid<8&&blockedToken[peerAid]==e.peer){drainBlocked&=std::uint16_t(~(1u<<peerAid));blockedEpoch[peerAid]=0;blockedToken[peerAid]=0;}return true;}
    Bus::SessionResult startMp(Token t) override{setSubframePorts(t,0);Writer w;w.u64(t);auto b=rpc(Start,w);Reader r(b);Bus::SessionResult v;v.status=r.status();if(v.status==Bus::Status::Ok)v.session=r.session();r.end();std::lock_guard<std::mutex> lock(mutex);updateSubframePorts(0);readyWatch={};if(v.status==Bus::Status::Ok)clearTerminal();if(drainNotice.session!=v.session)clearDrain();session=v.session;sequence={};blockSequence=0;return v;}
    void stopMp(Token t) override{setSubframePorts(t,0);Writer w;w.u64(t);auto b=rpc(Stop,w);Reader r(b);r.end();std::lock_guard<std::mutex> lock(mutex);updateSubframePorts(0);readyWatch={};terminalSend=0;clearDrain();session={};pending.clear();completed.clear();incoming.clear();taken.clear();}
    bool enableReceiptRetention(Token t) override {
        {std::lock_guard<std::mutex> lock(mutex);check();
            if(t!=own || session.epoch || terminalSend)return false;}
        Writer w;w.u64(t);auto b=rpc(RetainReceipts,w);Reader r(b);
        const bool enabled=r.boolean();r.end();
        if(enabled){std::lock_guard<std::mutex> lock(mutex);retainReceipts=true;}
        return enabled;
    }
    std::optional<std::vector<Bus::Completion>> stopMpWithReceipts(Token t) override {
        // New opcode, never a legacy Stop fallback. Old brokers reject it as
        // unknown (terminal channel fault); they cannot silently discard sends.
        // Single transport-owner call; the receiver may only append inbox data.
        const auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(5);
        try {
            for(;;){
                Writer w;w.u64(t);w.u16(1);auto b=rpc(StopReceipts,w);Reader r(b);
                const auto state=r.u8();
                if(state==0){r.end();return std::nullopt;} // Unsupported, no mutation.
                if(state==2){ // DATA/ACK still in broker delay queue: no stop yet.
                    r.end();if(std::chrono::steady_clock::now()>=deadline)throw Fault("stop receipts admission timeout");
                    if(!external)std::this_thread::sleep_for(std::chrono::milliseconds(1));
                    continue;
                }
                if(state!=1)throw Fault("stop receipts response version/state");
                const auto count=r.u8();if(count>MaxStopMpReceipts)throw Fault("stop receipt count");
                std::vector<Bus::Completion> result;result.reserve(MaxStopMpReceipts);
                for(unsigned i=0;i<count;++i)result.push_back(decodeCompletion(r,acceptedSharingVersion==sharing_policy::BlockVersion));r.end();
                // Broker serializes completion notifications before the reply.
                // The receiver applies them before waking this owner for RPC.
                std::lock_guard<std::mutex> lock(mutex);check();
                if(t!=own||result.size()+completed.size()>MaxStopMpReceipts)throw Fault("stop receipt owner/capacity");
                for(const auto& c:completed)result.push_back(c);
                if(result.size()!=pending.size())throw Fault("stop receipts missing owned sends");
                std::map<std::uint64_t,bool> seen;
                for(const auto& c:result){
                    const auto it=pending.find(c.message.send_id);
                    if(it==pending.end()||!seen.emplace(c.message.send_id,true).second)throw Fault("unknown/duplicate stop receipt");
                    Writer actual,expected;actual.message(c.message);expected.message(it->second);
                    if(actual.bytes!=expected.bytes)throw Fault("stop receipt message identity");
                    if(c.outcome==Bus::Outcome::Delivered && (c.acknowledged!=c.message.targets||c.reason!=Bus::Reason::None))
                        throw Fault("stop receipt false delivery");
                    if(c.outcome!=Bus::Outcome::Delivered && c.acknowledged==c.message.targets)throw Fault("stop receipt no pending targets");
                    if(c.outcome==Bus::Outcome::Cancelled && c.reason!=Bus::Reason::SenderLeft && c.reason!=Bus::Reason::SenderReset)
                        throw Fault("stop receipt cancellation reason");
                    if(c.outcome==Bus::Outcome::Failed && c.reason!=Bus::Reason::RecipientLeft && c.reason!=Bus::Reason::RecipientReset)
                        throw Fault("stop receipt failure reason");
                }
                std::sort(result.begin(),result.end(),[](const auto& a,const auto& b){return a.message.send_id<b.message.send_id;});
                updateSubframePorts(0);readyWatch={};terminalSend=0;clearDrain();session={};
                pending.clear();completed.clear();incoming.clear();taken.clear();return result;
            }
        }catch(...){std::lock_guard<std::mutex> lock(mutex);failed=true;failure="atomic stop receipts failed";changed.notify_all();throw;}
    }
    std::optional<std::uint16_t> mpReadyMask(Token t,Bus::Session expected) override {
        if(!expected.epoch)return std::nullopt;
        Writer w;w.u64(t);w.session(expected);auto b=rpc(MpReady,w);Reader r(b);
        std::optional<std::uint16_t> mask;if(r.boolean())mask=r.u16();r.end();return mask;
    }
    std::optional<std::uint16_t> watchMpReadyMask(Token t,Bus::Session expected) override {
        pollExternal();
        std::uint64_t id;
        {
            std::lock_guard<std::mutex> lock(mutex);check();
            if(!t||t!=own||!expected.epoch||expected!=session)return std::nullopt;
            if(readyWatch.id&&readyWatch.endpoint==t&&readyWatch.session==expected)return readyWatch.mask;
            if(nextReadyWatch==UINT64_MAX)throw Fault("MP readiness subscription exhausted");
            id=++nextReadyWatch;readyWatch={t,expected,id,0,std::nullopt};
        }
        try {
            Writer w;w.u64(t);w.session(expected);w.u64(id);
            auto b=rpc(WatchMpReady,w);Reader r(b);const auto result=decodeReadyWatch(r);
            if(result.endpoint!=t||result.session!=expected||result.id!=id)throw Fault("MP readiness reply binding");
            std::lock_guard<std::mutex> lock(mutex);check();
            if(readyWatch.id!=id||readyWatch.endpoint!=t||readyWatch.session!=expected)return std::nullopt;
            if(result.revision>readyWatch.revision)readyWatch=result;
            return readyWatch.mask;
        } catch(...) {
            // Initial admission is mandatory. Even if the caller catches the
            // error, later notifications must not validate a rejected watch.
            std::lock_guard<std::mutex> lock(mutex);readyWatch={};failed=true;
            failure="MP readiness subscription failed";changed.notify_all();throw;
        }
    }
    Bus::SendResult send(Token t,unsigned mask,unsigned port,const std::array<std::uint8_t,512>& data,unsigned length,Bus::Sequence wire=0xffff) override{
        return sendWithCode(Send,t,mask,port,data,length,wire);
    }
    Bus::SendResult sendBlock(Token t,unsigned mask,const std::array<std::uint8_t,512>& data,unsigned length) override{
        return sendWithCode(BlockSend,t,mask,Bus::BlockPort,data,length,0xffff);
    }
    Bus::SendResult sendTerminal(Token t,unsigned mask,unsigned port,const std::array<std::uint8_t,512>& data,unsigned length,Bus::Sequence wire=0xffff) override{
        return sendWithCode(SendTerminal,t,mask,port,data,length,wire);
    }
private:
    Bus::SendResult sendWithCode(Code code,Token t,unsigned mask,unsigned port,const std::array<std::uint8_t,512>& data,unsigned length,Bus::Sequence wire){
        pollExternal();
        Writer w;Bus::Message m;
        {std::lock_guard<std::mutex> lock(mutex);check();if(t!=own||!session.epoch)return {Bus::Status::SourceNotReady,{}};
            if(code==BlockSend){if(acceptedSharingVersion!=sharing_policy::BlockVersion)return {Bus::Status::SourceNotReady,{}};}
            else if(port>15)return {Bus::Status::InvalidPort,{}};if(!length||length>512)return {Bus::Status::InvalidPayload,{}};
            if(!mask||mask>255||(session.aid==0?(mask&1)!=0:mask!=1))return {Bus::Status::InvalidTargets,{}};
            if(mask&drainBlocked)return {Bus::Status::RecipientNotReady,{}};
            if(drainNotice.session==session&&drainNotice.targets[session.aid].token==own&&
                drainReported[session.aid]==drainNotice.targets[session.aid].id)return {Bus::Status::Backpressure,{}};
            if(pending.size()>=Bus::MaxCompletionsPerEndpoint||(code==SendTerminal&&(terminalSend||retainReceipts)))return {Bus::Status::Backpressure,{}};
            if(nextSend==UINT64_MAX)return {Bus::Status::IdExhausted,{}};
            m={session,++nextSend,(code==BlockSend?blockSequence++:sequence[port]++),static_cast<std::uint8_t>(port),static_cast<std::uint16_t>(mask),wire};pending.emplace(m.send_id,m);
            if(code==SendTerminal)terminalSend=m.send_id;}
        w.u8(code);w.u64(t);w.message(m);w.u16(static_cast<std::uint16_t>(length));w.array(data);outputPacket(w.bytes);return {Bus::Status::Ok,m};
    }
public:
    // An asynchronous SDK initializer may still have the preceding scene's
    // callback installed when its peer's startup dataset arrives. Retain that
    // exact owned delivery in the existing bounded FIFO until the owner proves
    // its new callback is initialized. No receive lease or ACK is issued here.
    // This is a per-call owner policy, with no new transport/checkpoint state.
    Bus::DeliveryResult takeInitialized(Token t,Token& domain,std::uint16_t port,
                                       unsigned bytes,bool initialized){
        pollExternal();
        std::lock_guard<std::mutex> lock(mutex);check();if(t!=own||!session.epoch)return {Bus::Status::SourceNotReady,{}};
        if(incoming.empty()||incoming.front().eligible>frame)return {Bus::Status::Empty,{}};
        const auto& front=incoming.front().d;
        if(!initialized && front.port==port && front.token.source.aid==0 && front.payload.size()==bytes)
            return {Bus::Status::Empty,{}};
        auto v=incoming.front();incoming.pop_front();if(v.d.token.recipient!=session)throw Fault("nifi2 stale receive epoch");
        domain=v.domain;taken.emplace(v.d.token.send_id,v);return {Bus::Status::Ok,v.d};
    }
    Bus::DeliveryResult take(Token t,Token& domain) override{
        return takeInitialized(t,domain,0,0,true);
    }
    Bus::Status acknowledge(Token t,Token domain,const Bus::DeliveryToken& d) override{
        {std::lock_guard<std::mutex> lock(mutex);check();auto i=taken.find(d.send_id);
            if(t!=own||i==taken.end()||i->second.domain!=domain||i->second.d.token.source!=d.source||i->second.d.token.recipient!=d.recipient)return Bus::Status::InvalidToken;taken.erase(i);}
        Writer w;w.u8(Ack);w.u64(t);w.u64(domain);w.token(d);outputPacket(w.bytes);return Bus::Status::Ok;
    }
    Bus::CompletionResult completion(Token t) override{pollExternal();std::lock_guard<std::mutex> lock(mutex);check();if(t!=own||!session.epoch)return {Bus::Status::SourceNotReady,{}};if(completed.empty())return {Bus::Status::Empty,{}};auto c=completed.front();completed.pop_front();pending.erase(c.message.send_id);if(c.message.send_id==terminalSend)terminalSend=0;return {Bus::Status::Ok,c};}
};
} // namespace melonDS::wm_sdk::net
#include "WmSdkSnapshotRemote.h"
#endif
