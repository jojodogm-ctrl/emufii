#pragma once
#include "WmSdkBlockWindowWire.h"
#include <cstring>
#include <limits>
namespace melonDS::wm_sdk::sharing::block_window {
using namespace block_wire;
// Supplied only from the real transport envelope/current MP owner. No field
// here is obtained from a payload-declared address, endpoint or player number.
struct Link {
    std::uint64_t domain=0,localEpoch=0,peerEpoch=0;unsigned localAid=0,peerAid=0;
    bool valid()const{return domain&&localEpoch&&peerEpoch&&localAid<8&&peerAid<8&&localAid!=peerAid&&(!localAid||!peerAid);}
};
inline bool operator==(const Link& a,const Link& b){return a.domain==b.domain&&a.localEpoch==b.localEpoch&&a.peerEpoch==b.peerEpoch&&a.localAid==b.localAid&&a.peerAid==b.peerAid;}
inline bool newer(Nonce a,Nonce b){return a.epoch>b.epoch||(a.epoch==b.epoch&&a.version>b.version);}
struct Out {std::uint64_t ticket=0;Frame frame{};};
enum class Receive {Accepted,Ignored,Duplicate,Closed};
class Receiver {
    struct Slot {bool used=false;std::uint32_t index=0;unsigned parts=0;std::array<std::uint8_t,MaxSegment> body{};};
    Link link_{};Flow flow_{};bool active_=false;std::uint64_t serial_=0;
    std::array<Slot,Depth> slots_{};std::array<Out,Depth+2> controls_{};unsigned count_=0;
    std::optional<Out> cancelled_{};
    std::uint64_t ticket(){return serial_==std::numeric_limits<std::uint64_t>::max()?0:++serial_;}
    bool push(Frame f){if(count_==controls_.size())return false;const auto t=ticket();if(!t)return false;controls_[count_++]={t,f};return true;}
    Frame control(Kind kind,std::uint32_t index=0,Reason reason=Reason::None)const{Frame f;f.kind=kind;f.flow=flow_;f.index=index;f.reason=reason;return f;}
public:
    bool active()const{return active_;}const Flow& flow()const{return flow_;}
    unsigned assembled()const{unsigned n=0;for(const auto& s:slots_)n+=s.used;return n;}
    bool quiet()const{return !count_&&!cancelled_&&!assembled();}
    bool start(const Link& link,const Flow& request){
        Frame f;f.kind=Kind::Open;f.flow=request;
        if(!link.valid()||!block_wire::valid(f))return false;
        link_=link;flow_=request;active_=true;slots_={};controls_={};count_=0;cancelled_.reset();
        if(!push(f)){active_=false;return false;}return true;
    }
    void close(Reason reason){
        if(!active_)return;active_=false;slots_={};controls_={};count_=0;
        const auto t=ticket();if(t)cancelled_=Out{t,control(Kind::Cancel,0,reason)};
    }
    std::optional<Out> nextControl()const{return cancelled_?cancelled_:(count_?std::optional<Out>(controls_[0]):std::nullopt);}
    bool admitted(std::uint64_t t){
        if(cancelled_&&cancelled_->ticket==t){cancelled_.reset();return true;}
        if(!count_||controls_[0].ticket!=t)return false;
        for(unsigned i=1;i<count_;++i)controls_[i-1]=controls_[i];controls_[--count_]={};return true;
    }
    Receive receive(const Link& link,const Frame& f){
        if(!active_||!(link==link_)||!sameRequest(f.flow,flow_))return Receive::Ignored;
        if(!block_wire::valid(f)){close(Reason::InvalidFrame);return Receive::Closed;}
        if(f.kind==Kind::Withdraw||f.kind==Kind::Refuse){
            if(!f.flow.source.empty()&&flow_.source.valid()&&f.flow.source!=flow_.source)return Receive::Ignored;
            active_=false;slots_={};controls_={};count_=0;return Receive::Closed;
        }
        if(f.kind!=Kind::Offer&&f.kind!=Kind::Data)return Receive::Ignored;
        if(flow_.source.empty())flow_.source=f.flow.source;
        if(f.flow.source!=flow_.source){if(f.kind==Kind::Offer){close(Reason::SourceRevoked);return Receive::Closed;}return Receive::Ignored;}
        if(f.kind==Kind::Offer)return Receive::Accepted;
        for(unsigned i=0;i<count_;++i)if(controls_[i].frame.kind==Kind::Ack&&controls_[i].frame.index==f.index)return Receive::Duplicate;
        Slot* slot=nullptr;for(auto& s:slots_)if(s.used&&s.index==f.index){slot=&s;break;}
        if(!slot)for(auto& s:slots_)if(!s.used){s={};s.used=true;s.index=f.index;slot=&s;break;}
        if(!slot){close(Reason::NoCapacity);return Receive::Closed;}
        const unsigned part=f.offset?2u:1u;
        if(slot->parts&part){if(std::memcmp(slot->body.data()+f.offset,f.body.data(),f.length)){close(Reason::InvalidFrame);return Receive::Closed;}return Receive::Duplicate;}
        std::copy_n(f.body.begin(),f.length,slot->body.begin()+f.offset);slot->parts|=part;return Receive::Accepted;
    }
    // Must be called at a real SDK polling boundary with the same live native
    // transfer token. The callback performs only authorized native-equivalent
    // destination/progress writes. It returns false on ownership/bounds loss.
    template<class Commit> unsigned commit(Commit apply,unsigned budget=4){
        unsigned n=0;if(!active_)return n;
        for(auto& s:slots_){
            if(n==budget||count_==controls_.size())break;
            if(!s.used)continue;const unsigned bytes=chunkBytes(flow_,s.index),parts=bytes>PartBytes?3u:1u;
            if(s.parts!=parts)continue;
            if(!apply(s.index,s.body.data(),bytes)){close(Reason::NativeClosed);return n;}
            if(!push(control(Kind::Ack,s.index))){close(Reason::NoCapacity);return n;}
            s={};++n;
        }return n;
    }
};

class Source {
    struct Slot {bool used=false;std::uint32_t index=0;unsigned sent=0;std::array<std::uint8_t,MaxSegment> body{};};
    Link link_{},lastLink_{};Flow flow_{};Nonce lastDestination_{};
    bool active_=false,offer_=false,closing_=false;Reason reason_=Reason::None;
    std::uint32_t cursor_=0;std::uint64_t serial_=0;std::array<Slot,Depth> slots_{};
    std::optional<Out> prepared_{};unsigned preparedSlot_=Depth,preparedPart_=0;
    std::uint64_t ticket(){return serial_==std::numeric_limits<std::uint64_t>::max()?0:++serial_;}
public:
    bool active()const{return active_;}const Flow& flow()const{return flow_;}
    unsigned pendingChunks()const{unsigned n=0;for(const auto& s:slots_)n+=s.used;return n;}
    bool quiet()const{return !prepared_&&!closing_&&!offer_&&!pendingChunks()&&(!active_||cursor_==blocks(flow_));}
    // The runtime must first validate a real native request and a fresh mode0
    // registry token. This scheduler never authorizes guest reads on its own.
    bool start(const Link& link,const Frame& open,Nonce source){
        if(!link.valid()||open.kind!=Kind::Open||!block_wire::valid(open)||!source.valid())return false;
        if(active_&&link==link_&&sameRequest(open.flow,flow_)){
            if(flow_.source!=source){abort(Reason::SourceRevoked);return false;}offer_=true;return true;
        }
        if(link==lastLink_&&!newer(open.flow.destination,lastDestination_))return false;
        link_=link;lastLink_=link;lastDestination_=open.flow.destination;flow_=open.flow;flow_.source=source;
        active_=true;offer_=true;closing_=false;reason_=Reason::None;cursor_=0;slots_={};prepared_.reset();return true;
    }
    void abort(Reason reason){
        if(!active_)return;active_=false;offer_=false;closing_=true;reason_=reason;slots_={};prepared_.reset();
    }
    bool receive(const Link& link,const Frame& f){
        if(!active_||!(link==link_)||!sameRequest(f.flow,flow_)||!block_wire::valid(f))return false;
        if(f.kind==Kind::Cancel&&(f.flow.source.empty()||f.flow.source==flow_.source)){active_=false;slots_={};prepared_.reset();offer_=false;return true;}
        if(f.kind!=Kind::Ack||f.flow.source!=flow_.source)return false;
        for(auto& s:slots_)if(s.used&&s.index==f.index){
            const unsigned parts=chunkBytes(flow_,s.index)>PartBytes?3u:1u;
            if(s.sent!=parts)return false;s={};return true;
        }return false;
    }
    // Read copies precisely one current source chunk into owned storage. A
    // prepared/backpressured fragment never reads the mutable guest again.
    template<class Read> std::optional<Out> next(Read read){
        if(prepared_)return prepared_;Frame f;f.flow=flow_;preparedSlot_=Depth;preparedPart_=0;
        if(closing_){f.kind=Kind::Withdraw;f.reason=reason_;}
        else if(!active_)return std::nullopt;
        else if(offer_)f.kind=Kind::Offer;
        else{
            Slot* slot=nullptr;unsigned index=0;
            for(unsigned i=0;i<slots_.size();++i)if(slots_[i].used){
                const auto parts=chunkBytes(flow_,slots_[i].index)>PartBytes?3u:1u;
                if(slots_[i].sent!=parts){slot=&slots_[i];index=i;break;}
            }
            if(!slot&&cursor_<blocks(flow_))for(unsigned i=0;i<slots_.size();++i)if(!slots_[i].used){
                auto& s=slots_[i];s={};s.used=true;s.index=cursor_++;
                if(!read(s.index,s.body.data(),chunkBytes(flow_,s.index))){abort(Reason::SourceRevoked);return next(read);}
                slot=&s;index=i;break;
            }
            if(!slot)return std::nullopt;
            f.kind=Kind::Data;f.index=slot->index;f.offset=(slot->sent&1)?PartBytes:0;
            f.length=std::min(PartBytes,chunkBytes(flow_,f.index)-f.offset);
            std::copy_n(slot->body.begin()+f.offset,f.length,f.body.begin());preparedSlot_=index;preparedPart_=f.offset?2u:1u;
        }
        const auto t=ticket();if(!t)return std::nullopt;prepared_=Out{t,f};return prepared_;
    }
    bool admitted(std::uint64_t t){
        if(!prepared_||prepared_->ticket!=t)return false;
        const auto kind=prepared_->frame.kind;
        if(kind==Kind::Offer)offer_=false;
        else if(kind==Kind::Withdraw)closing_=false;
        else{if(preparedSlot_>=slots_.size()||!slots_[preparedSlot_].used)return false;slots_[preparedSlot_].sent|=preparedPart_;}
        prepared_.reset();return true;
    }
};
} // namespace melonDS::wm_sdk::sharing::block_window
