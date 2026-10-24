#pragma once
// Optional common SDK registry recognition. No source immutability, registered
// object, callback permission, initialized module or network authority follows
// from a code binding. The original response-module contract remains separate.
#include "WmSdk300ResponseGraph.h"

namespace melonDS::wm_sdk::sharing::sdk300::response::registry {
enum class Role : unsigned {Remove,Register};
enum class Dependency {Response,Shared};
struct Relocation {unsigned offset;Dependency kind;unsigned target;};
struct NodePin {unsigned bytes;std::uint32_t crc;unsigned first,count;std::uint32_t prefixCrc;};
struct Binding {std::array<std::uint32_t,2> nodes{};};
}
#include "WmSdkBlockRegistryPins.h"
namespace melonDS::wm_sdk::sharing::sdk300::response::registry {
template<class Resolve>
inline bool verifyNode(unsigned node,std::uint32_t address,const CodeView& view,Resolve resolve) {
    if(node>=Nodes.size())return false;
    const auto& pin=Nodes[node];const auto* bytes=view.readableBytes(address,pin.bytes);if(!bytes)return false;
    auto crc=0xffffffffu;unsigned next=0;
    for(unsigned off=0;off<pin.bytes;off+=4){
        auto word=graph::wordAt(bytes,off);
        if(next<pin.count&&Relocations[pin.first+next].offset==off){
            const auto& r=Relocations[pin.first+next++];
            if((word&0x0f000000)!=0x0b000000||(word>>28)==15)return false;
            std::int64_t delta=word&0xffffff;if(delta&0x800000)delta-=0x1000000;
            const auto target=std::int64_t(address)+off+8+delta*4;
            if(target<=0||target>0xffffffffll||(target&3)||!resolve(r,std::uint32_t(target)))return false;
            word=(word&0xff000000)|(r.kind==Dependency::Response?0x00400000:0x00800000)|(r.target+1);
        }
        crc=response::crcWord(crc,word);
    }
    return next==pin.count&&(crc^0xffffffffu)==pin.crc;
}
inline std::optional<Binding> identify(const sdk300::Binding& library,const graph::Binding& shared,
    const response::Binding& module,const CodeView& view,const std::array<std::uint32_t,2>& roots) {
    // Never trust a caller-created or stale dependency binding.
    std::array<std::uint32_t,response::RootCount> rr{};
    for(unsigned i=0;i<rr.size();++i)rr[i]=module.nodes[response::Roots[i]];
    const auto checked=response::identify(library,shared,view,rr);
    if(!checked||checked->nodes!=module.nodes||checked->globals!=module.globals)return std::nullopt;
    const auto* lib=view.readableBytes(library.base,library.variant->bytes);if(!lib)return std::nullopt;
    Binding result;std::array<const std::uint8_t*,2> backing{};
    for(unsigned i=0;i<Nodes.size();++i){
        const auto* bytes=view.readableBytes(roots[i],Nodes[i].bytes);if(!bytes)return std::nullopt;
        const auto apart=[&](const std::uint8_t* other,unsigned n){return other&&!graph::overlap(bytes,Nodes[i].bytes,other,n);};
        if(!apart(lib,library.variant->bytes))return std::nullopt;
        for(unsigned j=0;j<graph::NodeCount;++j)
            if(!apart(view.readableBytes(shared.nodes[j],(*shared.family->nodes)[j].bytes),(*shared.family->nodes)[j].bytes))return std::nullopt;
        for(unsigned j=0;j<graph::GlobalCount;++j)
            if(!apart(graph::dataBytes(view,shared.globals[j],graph::GlobalBytes[j]),graph::GlobalBytes[j]))return std::nullopt;
        for(unsigned j=0;j<response::NodeCount;++j)
            if(!apart(view.readableBytes(module.nodes[j],response::Nodes[j].bytes),response::Nodes[j].bytes))return std::nullopt;
        for(unsigned j=0;j<response::GlobalCount;++j)
            if(!apart(graph::dataBytes(view,module.globals[j],response::GlobalBytes[j]),response::GlobalBytes[j]))return std::nullopt;
        for(unsigned j=0;j<i;++j)if(!apart(backing[j],Nodes[j].bytes))return std::nullopt;
        if(!verifyNode(i,roots[i],view,[&](const Relocation& r,std::uint32_t target){
            return r.kind==Dependency::Response?r.target<response::NodeCount&&module.nodes[r.target]==target:
                r.target<graph::NodeCount&&shared.nodes[r.target]==target;
        }))return std::nullopt;
        result.nodes[i]=roots[i];backing[i]=bytes;
    }
    return result;
}
inline std::optional<Binding> discover(const sdk300::Binding& library,const graph::Binding& shared,
    const response::Binding& module,const CodeView& view,std::uint32_t begin,unsigned bytes) {
    if(!mainCode(begin,bytes)||bytes<8)return std::nullopt;
    std::array<std::uint32_t,2> roots{};
    for(std::uint32_t at=begin;at<=begin+bytes-8;at+=4){
        const auto* data=view.readableBytes(at,8);if(!data)continue;
        const auto prefix=response::crcWord(response::crcWord(0xffffffffu,graph::wordAt(data,0)),graph::wordAt(data,4))^0xffffffffu;
        for(unsigned i=0;i<roots.size();++i){
            if(prefix!=Nodes[i].prefixCrc||!verifyNode(i,at,view,[](const Relocation&,std::uint32_t){return true;}))continue;
            if(roots[i])return std::nullopt;
            roots[i]=at;
        }
    }
    for(auto root:roots)if(!root)return std::nullopt;
    return identify(library,shared,module,view,roots);
}
} // namespace melonDS::wm_sdk::sharing::sdk300::response::registry
