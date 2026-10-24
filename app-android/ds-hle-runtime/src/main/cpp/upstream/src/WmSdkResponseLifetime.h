#pragma once
// Independently written ownership bookkeeping. No packet construction, game
// logic, prediction, peer state writes, timers, or synthesized acknowledgements.
#include <array>
#include <cstdint>
#include <limits>
#include <optional>

namespace melonDS::wm_sdk::sharing {
class ResponseLifetime {
public:
    using U64=std::uint64_t; using U32=std::uint32_t;
    struct Token {
        U64 generation=0,version=0,rx=0,sourceEpoch=0,mpEpoch=0;
        unsigned peer=0,command=0,counter=0;
        explicit operator bool()const{return version!=0;}
    };
    struct Mailbox {bool pending=false;Token token{};};
    struct Build {
        bool used=false,constructed=false;U64 invocation=0,generation=0;
        unsigned path=0,peer=0;U32 stack=0,buffer=0;Token token{};
    };
    struct OutputCall {bool used=false;unsigned path=0;U32 stack=0,buffer=0;U64 invocation=0,generation=0;};
    struct Preview {
        bool used=false;U64 serial=0,generation=0,mpEpoch=0;
        U32 buffer=0;unsigned length=0;bool idle=false;std::array<std::uint8_t,512> bytes{};
    };
    struct Copy {bool used=false;U64 order=0,id=0,mpEpoch=0;unsigned port=0;bool idle=false;};
    struct Counts {
        U64 parsed=0,invalidated=0,consumed=0,noResponse=0,unattributed=0;
        U64 changedDuringBuild=0,previewsDiscarded=0,routesLearned=0;
        U64 admitted=0,submitted=0,delivered=0,cancelled=0,notSubmitted=0,nativeDiscarded=0;
    } counts;
private:
    U64 generation_=0,serial_=0,epoch_=0;
    unsigned routes_=0;
    std::array<Mailbox,16> mailboxes_{};
    std::array<Build,16> builds_{};
    std::array<OutputCall,16> outputs_{};
    // Previews only correlate a native result to its first real immutable WM
    // copy. They own no request and are never an End drain obligation.
    std::array<Preview,64> previews_{};
    // Exactly one metadata slot per actual outstanding WM service descriptor.
    // Unrelated ports remain here too, so learning a route includes copies
    // which were already admitted. Terminal copies are released immediately.
    std::array<Copy,64> copies_{};
    U64 next(){if(serial_==std::numeric_limits<U64>::max())return 0;return ++serial_;}
    Build* findBuild(unsigned path,U32 stack,unsigned peer){
        for(auto& b:builds_)if(b.used&&b.path==path&&b.stack==stack&&b.peer==peer)return &b;
        return nullptr;
    }
public:
    U64 generation()const{return generation_;}
    unsigned routes()const{return routes_;}
    const Mailbox& mailbox(unsigned peer)const{return mailboxes_.at(peer);}
    unsigned pending()const{unsigned n=0;for(const auto& m:mailboxes_)n+=m.pending;return n;}
    std::optional<Build> buildWitness(unsigned path,U32 stack,unsigned peer)const{
        for(const auto& b:builds_)if(b.used&&b.path==path&&b.stack==stack&&b.peer==peer)return b;
        return std::nullopt;
    }
    unsigned activeBuilds()const{unsigned n=0;for(const auto& b:builds_)n+=b.used;return n;}
    unsigned activeOutputs()const{unsigned n=0;for(const auto& o:outputs_)n+=o.used;return n;}
    unsigned copies()const{unsigned n=0;for(const auto& c:copies_)n+=c.used;return n;}
    unsigned moduleCopies()const{
        unsigned n=0;for(const auto& c:copies_)n+=c.used&&!c.idle&&c.mpEpoch==epoch_&&bool(routes_&(1u<<c.port));return n;
    }
    bool quiet()const{return !pending()&&!activeBuilds()&&!activeOutputs()&&!moduleCopies();}
    bool beginLifetime(U64 mpEpoch){
        if(!quiet()||generation_==std::numeric_limits<U64>::max()||!mpEpoch)return false;
        ++generation_;epoch_=mpEpoch;routes_=0;mailboxes_={};previews_={};return true;
    }
    bool invalidate(unsigned peer){
        if(peer>=mailboxes_.size())return false;
        auto& m=mailboxes_[peer];if(m.pending)++counts.invalidated;m={};return true;
    }
    bool parse(unsigned peer,unsigned command,unsigned counter,U64 rx,U64 sourceEpoch,U64 mpEpoch){
        if(peer>=16||!generation_||mpEpoch!=epoch_||!rx||!sourceEpoch||counter>255||
           !(command==2||command==4||command==6||command==8||command==10))return false;
        const auto version=next();if(!version)return false;
        // Versions are observations, never an ordering of the wrapping native
        // byte. Even an identical retransmission is a fresh native mailbox event.
        invalidate(peer);mailboxes_[peer]={true,{generation_,version,rx,sourceEpoch,mpEpoch,peer,command,counter}};
        ++counts.parsed;return true;
    }
    bool attempt(unsigned path,U32 stack,unsigned peer,U32 buffer,unsigned command,unsigned counter){
        if(peer>=16||findBuild(path,stack,peer))return false;
        for(auto& b:builds_)if(!b.used){
            const auto invocation=next();if(!invocation)return false;
            Token token{};const auto& m=mailboxes_[peer];
            if(m.pending&&m.token.generation==generation_&&m.token.command==command&&m.token.counter==counter)token=m.token;
            b={true,false,invocation,generation_,path,peer,stack,buffer,token};return true;
        }return false;
    }
    bool construct(unsigned path,U32 stack,unsigned peer,U32 buffer,unsigned command,unsigned counter){
        auto* b=findBuild(path,stack,peer);
        if(!b){if(!attempt(path,stack,peer,buffer,command,counter))return false;b=findBuild(path,stack,peer);}
        // Build5's earlier attempt survives Notify replacing the native mailbox.
        if(!b||b->constructed||b->buffer!=buffer||b->generation!=generation_)return false;
        b->constructed=true;return true;
    }
    std::optional<Build> consume(unsigned path,U32 stack,unsigned peer,unsigned length){
        auto* b=findBuild(path,stack,peer);
        if(!b||b->generation!=generation_||length>512||(!b->constructed&&length)||
           (b->constructed&&!length))return std::nullopt;
        const auto result=*b;*b={};
        auto& m=mailboxes_[peer];
        if(m.pending){
            if(!result.token||m.token.version!=result.token.version){++counts.changedDuringBuild;++counts.invalidated;}
            m={}; // Actual native state1 store consumes whichever box is present.
        }
        if(!result.token)++counts.unattributed;
        if(length)++counts.consumed;else ++counts.noResponse;
        return result;
    }
    bool outputBegin(unsigned path,U32 stack,U32 buffer){
        for(const auto& o:outputs_)if(o.used&&o.path==path&&o.stack==stack)return false;
        for(auto& o:outputs_)if(!o.used){
            const auto invocation=next();if(!invocation)return false;
            for(auto& p:previews_)if(p.used&&p.buffer==buffer){p.used=false;++counts.previewsDiscarded;}
            o={true,path,stack,buffer,invocation,generation_};return true;
        }return false;
    }
    template<class Read> bool outputEnd(unsigned path,U32 stack,unsigned length,U64 mpEpoch,Read read,bool idle=false){
        OutputCall* found=nullptr;for(auto& o:outputs_)if(o.used&&o.path==path&&o.stack==stack){found=&o;break;}
        if(!found||found->generation!=generation_||length>512)return false;
        const auto call=*found;*found={};if(!length)return true;
        Preview* target=nullptr;
        for(auto& p:previews_)if(!p.used){target=&p;break;}
        if(!target){target=&previews_[0];for(auto& p:previews_)if(p.serial<target->serial)target=&p;++counts.previewsDiscarded;}
        const auto serial=next();if(!serial)return false;
        *target={};target->used=true;target->serial=serial;target->generation=generation_;target->mpEpoch=mpEpoch;
        target->buffer=call.buffer;target->length=length;target->idle=idle;
        for(unsigned i=0;i<length;++i)target->bytes[i]=read(call.buffer+i);
        return true;
    }
    template<class Bytes> bool admit(U64 order,U64 mpEpoch,unsigned port,U32 buffer,unsigned length,const Bytes& bytes){
        if(!order||!mpEpoch||port>15||!length||length>512)return false;
        for(const auto& c:copies_)if(c.used&&c.order==order)return false;
        Copy* target=nullptr;for(auto& c:copies_)if(!c.used){target=&c;break;}if(!target)return false;
        bool idle=false;
        for(auto& p:previews_)if(p.used&&p.generation==generation_&&p.mpEpoch==mpEpoch&&p.buffer==buffer&&p.length==length){
            bool same=true;for(unsigned i=0;i<length;++i)same&=p.bytes[i]==bytes[i];
            // No command/header heuristic grants a port. Correlate actual native
            // result bytes, source buffer and exact length to actual WM copying.
            if(same){if(mpEpoch==epoch_&&!(routes_&(1u<<port))){routes_|=1u<<port;++counts.routesLearned;}idle=p.idle;p.used=false;break;}
        }
        *target={true,order,0,mpEpoch,port,idle};++counts.admitted;return true;
    }
    bool submit(U64 order,U64 id){
        if(!id)return false;
        for(const auto& c:copies_)if(c.used&&c.id==id)return false;
        for(auto& c:copies_)if(c.used&&c.order==order&&!c.id){c.id=id;++counts.submitted;return true;}
        return false;
    }
    bool complete(U64 id,bool delivered){
        if(!id)return false;
        for(auto& c:copies_)if(c.used&&c.id==id){
            c={};if(delivered)++counts.delivered;else ++counts.cancelled;return true;
        }return false;
    }
    bool noRecipients(U64 order){
        for(auto& c:copies_)if(c.used&&c.order==order&&!c.id){c={};++counts.notSubmitted;return true;}
        return false;
    }
    template<class Exists> void reconcile(Exists exists){
        // The native WM lifecycle may discard descriptors during Stop/Finish.
        // Observing their absence releases metadata only; never emit a receipt.
        for(auto& c:copies_)if(c.used&&!exists(c.order,c.id)){c={};++counts.nativeDiscarded;}
    }
};
}
