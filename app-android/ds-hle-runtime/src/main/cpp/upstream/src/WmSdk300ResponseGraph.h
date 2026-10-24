#pragma once
// Read-only loaded-code recognition. This grants no initialized module, WM
// session, callback execution, packet authority, or gameplay compatibility.
#include "WmSdk300Graph.h"

namespace melonDS::wm_sdk::sharing::sdk300::response {
inline constexpr unsigned NodeCount=54,RootCount=7,GlobalCount=2;
enum class Kind {Call,SharedCall,Global};
struct Relocation {unsigned offset;Kind kind;unsigned target,addend;};
struct NodePin {unsigned bytes;std::uint32_t crc;unsigned first,count;std::uint32_t prefixCrc;};
// Finite ArmReturn ABI groups: role/traversal cache; initialized flag, counter,
// sizes, queue and context through the end of its sixteen native peer slots.
inline constexpr std::array<unsigned,GlobalCount> GlobalBytes{{0x14,0x868}};
struct Binding {
    std::array<std::uint32_t,NodeCount> nodes{};
    std::array<std::uint32_t,GlobalCount> globals{};
};
}
#include "WmSdk300ResponseGraphPins.h"
namespace melonDS::wm_sdk::sharing::sdk300::response {
inline constexpr auto CrcTable=[] {
    std::array<std::uint32_t,256> t{};
    for(unsigned i=0;i<t.size();++i){auto x=i;for(unsigned b=0;b<8;++b)x=(x>>1)^(0xedb88320u&(0u-(x&1)));t[i]=x;}
    return t;
}();
inline std::uint32_t crcWord(std::uint32_t crc,std::uint32_t word) {
    for(unsigned b=0;b<4;++b)crc=(crc>>8)^CrcTable[(crc^(word>>(b*8)))&255];
    return crc;
}
template<class Resolve>
inline bool verifyNode(unsigned node,std::uint32_t address,const CodeView& view,Resolve resolve) {
    if(node>=NodeCount)return false;
    const auto& pin=Nodes[node];const auto* bytes=view.readableBytes(address,pin.bytes);if(!bytes)return false;
    auto crc=0xffffffffu;unsigned next=0;
    for(unsigned off=0;off<pin.bytes;off+=4){
        auto word=graph::wordAt(bytes,off);
        if(next<pin.count&&Relocations[pin.first+next].offset==off){
            const auto& r=Relocations[pin.first+next++];std::uint32_t target=0;
            if(r.kind==Kind::Call||r.kind==Kind::SharedCall){
                if((word&0x0f000000)!=0x0b000000||(word>>28)==15)return false;
                std::int64_t delta=word&0xffffff;if(delta&0x800000)delta-=0x1000000;
                const auto absolute=std::int64_t(address)+off+8+delta*4;
                if(absolute<=0||absolute>0xffffffffll||(absolute&3))return false;
                target=std::uint32_t(absolute);
                word=(word&0xff000000)|(r.kind==Kind::SharedCall?0x00800000:0)|(r.target+1);
            }else{
                if(r.target>=GlobalCount||word<r.addend)return false;
                target=word-r.addend;
                if(!graph::dataBytes(view,target,GlobalBytes[r.target]))return false;
                word=0xf2000000|(r.target<<16)|r.addend;
            }
            if(!resolve(r,target))return false;
        }
        crc=crcWord(crc,word);
    }
    return next==pin.count&&(crc^0xffffffffu)==pin.crc;
}
inline std::optional<Binding> identify(const sdk300::Binding& library,const graph::Binding& shared,
    const CodeView& view,const std::array<std::uint32_t,RootCount>& roots) {
    // This response family has no qualified Interwork variant. Recheck the
    // shared graph: stale or caller-invented dependency bindings are rejected.
    if(library.variant!=&Variants[0]||shared.family!=&graph::ArmReturnFamily)return std::nullopt;
    std::optional<graph::Binding> checked;
    const auto qualified=identifyAt(library.base,[&](std::uint32_t at){
        const auto* bytes=view.readableBytes(at,4);return bytes?graph::wordAt(bytes,0):0u;
    },[&](const sdk300::Binding& candidate){
        if(candidate.variant!=library.variant||candidate.boundaries!=library.boundaries)return false;
        checked=graph::identify(candidate,view);return bool(checked);
    });
    if(!qualified||!checked||checked->nodes!=shared.nodes||checked->globals!=shared.globals)return std::nullopt;
    const auto* lib=view.readableBytes(library.base,library.variant->bytes);if(!lib)return std::nullopt;
    Binding result;std::array<bool,NodeCount> bound{},done{};
    std::array<const std::uint8_t*,NodeCount> backing{};
    const auto separate=[&](const std::uint8_t* bytes,unsigned length){
        if(graph::overlap(bytes,length,lib,library.variant->bytes))return false;
        for(unsigned i=0;i<graph::NodeCount;++i){
            const auto n=(*shared.family->nodes)[i].bytes;
            const auto* other=view.readableBytes(shared.nodes[i],n);
            if(!other||graph::overlap(bytes,length,other,n))return false;
        }
        for(unsigned i=0;i<graph::GlobalCount;++i){
            const auto* other=graph::dataBytes(view,shared.globals[i],graph::GlobalBytes[i]);
            if(!other||graph::overlap(bytes,length,other,graph::GlobalBytes[i]))return false;
        }
        return true;
    };
    const auto bind=[&](unsigned node,std::uint32_t at){
        if(node>=NodeCount||!at)return false;
        if(bound[node])return result.nodes[node]==at;
        const auto count=Nodes[node].bytes;const auto* bytes=view.readableBytes(at,count);
        if(!bytes||!separate(bytes,count))return false;
        for(unsigned j=0;j<NodeCount;++j)
            if(bound[j]&&graph::overlap(bytes,count,backing[j],Nodes[j].bytes))return false;
        bound[node]=true;backing[node]=bytes;result.nodes[node]=at;return true;
    };
    for(unsigned i=0;i<RootCount;++i)if(!bind(Roots[i],roots[i]))return std::nullopt;
    unsigned completed=0;
    for(unsigned pass=0;pass<NodeCount&&completed<NodeCount;++pass){
        bool progress=false;
        for(unsigned i=0;i<NodeCount;++i){
            if(!bound[i]||done[i])continue;
            const bool valid=verifyNode(i,result.nodes[i],view,[&](const Relocation& r,std::uint32_t target){
                if(r.kind==Kind::Call)return bind(r.target,target);
                if(r.kind==Kind::SharedCall)return r.target<graph::NodeCount&&shared.nodes[r.target]==target;
                auto& value=result.globals[r.target];if(value&&value!=target)return false;value=target;return true;
            });
            if(!valid)return std::nullopt;
            done[i]=true;++completed;progress=true;
        }
        if(!progress)return std::nullopt;
    }
    if(completed!=NodeCount)return std::nullopt;
    for(unsigned i=0;i<GlobalCount;++i){
        const auto* bytes=graph::dataBytes(view,result.globals[i],GlobalBytes[i]);
        if(!bytes||!separate(bytes,GlobalBytes[i]))return std::nullopt;
        for(unsigned j=0;j<NodeCount;++j)if(graph::overlap(bytes,GlobalBytes[i],backing[j],Nodes[j].bytes))return std::nullopt;
        for(unsigned j=0;j<i;++j)if(graph::overlap(bytes,GlobalBytes[i],graph::dataBytes(view,result.globals[j],GlobalBytes[j]),GlobalBytes[j]))return std::nullopt;
    }
    return result;
}
// Bounded main-RAM discovery. Candidate CRCs never grant authority: the full
// graph and all shared targets/globals are checked afterwards. Duplicate root
// candidates are deliberately unsupported; relative layout alone is unused.
inline std::optional<Binding> discover(const sdk300::Binding& library,const graph::Binding& shared,
    const CodeView& view,std::uint32_t begin,unsigned bytes) {
    if(!mainCode(begin,bytes)||bytes<8)return std::nullopt;
    std::array<std::uint32_t,RootCount> roots{};
    for(std::uint32_t at=begin;at<=begin+bytes-8;at+=4){
        const auto* data=view.readableBytes(at,8);if(!data)continue;
        const auto prefix=crcWord(crcWord(0xffffffffu,graph::wordAt(data,0)),graph::wordAt(data,4))^0xffffffffu;
        for(unsigned r=0;r<RootCount;++r){
            if(prefix!=Nodes[Roots[r]].prefixCrc)continue;
            if(!verifyNode(Roots[r],at,view,[](const Relocation&,std::uint32_t){return true;}))continue;
            if(roots[r])return std::nullopt;
            roots[r]=at;
        }
    }
    for(auto at:roots)if(!at)return std::nullopt;
    return identify(library,shared,view,roots);
}
} // namespace melonDS::wm_sdk::sharing::sdk300::response
