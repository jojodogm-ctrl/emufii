#pragma once
#include "WmSdkBlockRegistryGraph.h"
namespace melonDS::wm_sdk::sharing::sdk300::response::transfer {
struct Relocation {unsigned offset,target;};
struct Binding {std::uint32_t api=0;};
}
#include "WmSdkBlockTransferPins.h"
namespace melonDS::wm_sdk::sharing::sdk300::response::transfer {
inline bool verifyApi(std::uint32_t at,const CodeView& view,const response::Binding& module){
    const auto* data=view.readableBytes(at,ApiBytes);if(!data)return false;
    auto crc=0xffffffffu;unsigned next=0;
    for(unsigned off=0;off<ApiBytes;off+=4){
        auto word=graph::wordAt(data,off);
        if(next<Relocations.size()&&Relocations[next].offset==off){
            const auto r=Relocations[next++];if((word&0x0f000000)!=0x0b000000||(word>>28)==15)return false;
            std::int64_t delta=word&0xffffff;if(delta&0x800000)delta-=0x1000000;
            const auto target=std::int64_t(at)+off+8+delta*4;
            if(r.target>=response::NodeCount||target!=module.nodes[r.target])return false;
            word=(word&0xff000000)|(r.target+1);
        }
        crc=response::crcWord(crc,word);
    }
    return next==Relocations.size()&&(crc^0xffffffffu)==ApiCrc;
}
inline std::optional<Binding> identify(const sdk300::Binding& library,const graph::Binding& shared,const response::Binding& module,
    const registry::Binding& registry,const CodeView& view,std::uint32_t at){
    const auto parent=registry::identify(library,shared,module,view,registry.nodes);
    if(!parent||parent->nodes!=registry.nodes||!verifyApi(at,view,module))return std::nullopt;
    const auto* bytes=view.readableBytes(at,ApiBytes);
    const auto apart=[&](const std::uint8_t* other,unsigned n){return other&&!graph::overlap(bytes,ApiBytes,other,n);};
    if(!apart(view.readableBytes(library.base,library.variant->bytes),library.variant->bytes))return std::nullopt;
    for(unsigned i=0;i<graph::NodeCount;++i)if(!apart(view.readableBytes(shared.nodes[i],(*shared.family->nodes)[i].bytes),(*shared.family->nodes)[i].bytes))return std::nullopt;
    for(unsigned i=0;i<graph::GlobalCount;++i)if(!apart(graph::dataBytes(view,shared.globals[i],graph::GlobalBytes[i]),graph::GlobalBytes[i]))return std::nullopt;
    for(unsigned i=0;i<response::NodeCount;++i)if(!apart(view.readableBytes(module.nodes[i],response::Nodes[i].bytes),response::Nodes[i].bytes))return std::nullopt;
    for(unsigned i=0;i<response::GlobalCount;++i)if(!apart(graph::dataBytes(view,module.globals[i],response::GlobalBytes[i]),response::GlobalBytes[i]))return std::nullopt;
    for(unsigned i=0;i<registry::Nodes.size();++i)if(!apart(view.readableBytes(registry.nodes[i],registry::Nodes[i].bytes),registry::Nodes[i].bytes))return std::nullopt;
    return Binding{at};
}
inline std::optional<Binding> discover(const sdk300::Binding& library,const graph::Binding& shared,const response::Binding& module,
    const registry::Binding& registry,const CodeView& view,std::uint32_t begin,unsigned length){
    if(!mainCode(begin,length)||length<8)return std::nullopt;
    std::uint32_t found=0;
    for(auto at=begin;at<=begin+length-8;at+=4){
        const auto* data=view.readableBytes(at,8);if(!data)continue;
        const auto crc=response::crcWord(response::crcWord(0xffffffffu,graph::wordAt(data,0)),graph::wordAt(data,4))^0xffffffffu;
        if(crc!=ApiPrefixCrc||!verifyApi(at,view,module))continue;
        if(found)return std::nullopt;found=at;
    }
    return found?identify(library,shared,module,registry,view,found):std::nullopt;
}
} // namespace melonDS::wm_sdk::sharing::sdk300::response::transfer
