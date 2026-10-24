#pragma once
// Code/layout recognition only. Native callbacks, threads and gameplay retain
// their own execution; this contract grants no initialized WM/session authority.
#include "WmSdk300Code.h"

namespace melonDS::wm_sdk::sharing::sdk300::graph {
inline constexpr unsigned NodeCount=37,GlobalCount=2;
enum class RelocationKind {Call,Code,Global};
struct Relocation {unsigned offset;RelocationKind kind;unsigned target,addend;};
struct NodePin {unsigned bytes;std::uint32_t crc;unsigned first,count;};
struct Family {
    const Variant* variant;
    const std::array<NodePin,NodeCount>* nodes;
    const Relocation* relocations;
    std::array<unsigned,unsigned(Boundary::Count)> roots;
};
// WM: initialized flag, system pointer and inline OSMessageQueue (0x20).
// Scheduler: direct global fields only; pointed thread/stack storage is native.
inline constexpr std::array<unsigned,GlobalCount> GlobalBytes{{0x28,0x24}};
struct Binding {
    const Family* family=nullptr;
    std::array<std::uint32_t,NodeCount> nodes{};
    std::array<std::uint32_t,GlobalCount> globals{};
};
}
#include "WmSdk300GraphPins.h"
namespace melonDS::wm_sdk::sharing::sdk300::graph {
inline const Family* familyFor(const Variant* variant) {
    if(variant==&Variants[0])return &ArmReturnFamily;
    if(variant==&Variants[1])return &InterworkReturnFamily;
    return nullptr;
}
inline const std::uint8_t* dataBytes(const CodeView& view,std::uint32_t address,unsigned length) {
    if(!mainCode(address,length)||address<view.itcmSize||!view.mainRam||
       (view.mainMask!=0x3fffff&&view.mainMask!=0xffffff)||!view.permissions)return nullptr;
    for(auto page=address>>12;page<=((address+length-1)>>12);++page)
        if((view.permissions[page]&3)!=3||((page<<12)&view.dtcmMask)==view.dtcmBase)return nullptr;
    return view.mainRam+(address&view.mainMask);
}
inline bool overlap(const std::uint8_t* a,unsigned an,const std::uint8_t* b,unsigned bn) {
    // Compare physical backing too: distinct ITCM mirror addresses can alias.
    const auto x=reinterpret_cast<std::uintptr_t>(a),y=reinterpret_cast<std::uintptr_t>(b);
    return x<=y?y-x<an:x-y<bn;
}
inline std::uint32_t wordAt(const std::uint8_t* bytes,unsigned offset) {
    std::uint32_t word;std::memcpy(&word,bytes+offset,4);return word;
}
inline std::uint32_t crcWord(std::uint32_t crc,std::uint32_t word) {
    for(unsigned byte=0;byte<4;++byte){
        crc^=(word>>(byte*8))&255;
        for(unsigned bit=0;bit<8;++bit)crc=(crc>>1)^(0xedb88320u&(0u-(crc&1)));
    }
    return crc;
}
inline std::optional<Binding> identify(const sdk300::Binding& library,const CodeView& view) {
    const auto* family=familyFor(library.variant);if(!family)return std::nullopt;
    const auto* lib=view.readableBytes(library.base,library.variant->bytes);if(!lib)return std::nullopt;
    Binding result;result.family=family;
    std::array<bool,NodeCount> bound{},checked{};
    std::array<const std::uint8_t*,NodeCount> backing{};
    const auto bind=[&](unsigned node,std::uint32_t address){
        if(node>=NodeCount||!address)return false;
        if(bound[node])return result.nodes[node]==address;
        const auto length=(*family->nodes)[node].bytes;
        const auto* bytes=view.readableBytes(address,length);if(!bytes||overlap(bytes,length,lib,library.variant->bytes))return false;
        for(unsigned i=0;i<NodeCount;++i)if(bound[i]&&
            overlap(bytes,length,backing[i],(*family->nodes)[i].bytes))return false;
        bound[node]=true;result.nodes[node]=address;backing[node]=bytes;return true;
    };
    for(unsigned i=0;i<library.boundaries.size();++i)
        if(!bind(family->roots[i],library.boundaries[i]))return std::nullopt;
    unsigned completed=0;
    for(unsigned pass=0;pass<NodeCount&&completed<NodeCount;++pass){
        bool progress=false;
        for(unsigned node=0;node<NodeCount;++node){
            if(!bound[node]||checked[node])continue;
            const auto& pin=(*family->nodes)[node];const auto* bytes=backing[node];
            auto crc=0xffffffffu;unsigned next=0;
            for(unsigned offset=0;offset<pin.bytes;offset+=4){
                auto word=wordAt(bytes,offset);
                if(next<pin.count&&family->relocations[pin.first+next].offset==offset){
                    const auto& relocation=family->relocations[pin.first+next++];
                    if(relocation.kind==RelocationKind::Call){
                        // Preserve condition/opcode, reject BLX and arithmetic wrap.
                        if((word&0x0f000000)!=0x0b000000||(word>>28)==15)return std::nullopt;
                        std::int64_t delta=word&0xffffff;if(delta&0x800000)delta-=0x1000000;
                        const auto target=std::int64_t(result.nodes[node])+offset+8+delta*4;
                        if(target<=0||target>0xffffffffll||(target&3)||!bind(relocation.target,std::uint32_t(target)))return std::nullopt;
                        word=(word&0xff000000)|(relocation.target+1);
                    }else if(relocation.kind==RelocationKind::Code){
                        if(!bind(relocation.target,word))return std::nullopt;
                        word=0xf1000000|(relocation.target+1);
                    }else{
                        if(relocation.target>=GlobalCount||word<relocation.addend)return std::nullopt;
                        const auto base=word-relocation.addend;
                        if(!dataBytes(view,base,GlobalBytes[relocation.target]))return std::nullopt;
                        auto& observed=result.globals[relocation.target];
                        if(observed&&observed!=base)return std::nullopt;
                        observed=base;word=0xf2000000|(relocation.target<<16)|relocation.addend;
                    }
                }
                crc=crcWord(crc,word);
            }
            if(next!=pin.count||(crc^0xffffffffu)!=pin.crc)return std::nullopt;
            checked[node]=true;++completed;progress=true;
        }
        if(!progress)return std::nullopt;
    }
    if(completed!=NodeCount)return std::nullopt;
    for(unsigned i=0;i<GlobalCount;++i){
        const auto* data=dataBytes(view,result.globals[i],GlobalBytes[i]);if(!data)return std::nullopt;
        if(overlap(data,GlobalBytes[i],lib,library.variant->bytes))return std::nullopt;
        for(unsigned node=0;node<NodeCount;++node)
            if(overlap(data,GlobalBytes[i],backing[node],(*family->nodes)[node].bytes))return std::nullopt;
        for(unsigned j=0;j<i;++j)
            if(overlap(data,GlobalBytes[i],dataBytes(view,result.globals[j],GlobalBytes[j]),GlobalBytes[j]))return std::nullopt;
    }
    return result;
}
inline const std::uint8_t* separateData(const sdk300::Binding& library,const Binding& graph,
    const CodeView& view,std::uint32_t address,unsigned length) {
    const auto* bytes=dataBytes(view,address,length);if(!bytes||!graph.family)return nullptr;
    const auto* lib=view.readableBytes(library.base,library.variant->bytes);
    if(!lib||overlap(bytes,length,lib,library.variant->bytes))return nullptr;
    for(unsigned node=0;node<NodeCount;++node){
        const auto count=(*graph.family->nodes)[node].bytes;
        const auto* code=view.readableBytes(graph.nodes[node],count);
        if(!code||overlap(bytes,length,code,count))return nullptr;
    }
    for(unsigned i=0;i<GlobalCount;++i){
        const auto* data=dataBytes(view,graph.globals[i],GlobalBytes[i]);
        if(!data||overlap(bytes,length,data,GlobalBytes[i]))return nullptr;
    }
    return bytes;
}
struct OwnerLayout {std::uint32_t system,queue;unsigned queueBytes;};
// Additional native storage preflight, not WM ownership: the caller must still
// establish its initialized service/session and exact work/status/callback ABI.
// No callback or TCB target is executed, patched or granted sidecar access here.
inline std::optional<OwnerLayout> nativeLayout(const Binding& graph,const CodeView& view) {
    if(!graph.family)return std::nullopt;
    const auto* wm=dataBytes(view,graph.globals[0],GlobalBytes[0]);
    const auto* scheduler=dataBytes(view,graph.globals[1],GlobalBytes[1]);
    if(!wm||!scheduler||wordAt(scheduler,0xc)!=graph.globals[1]+0x18)return std::nullopt;
    const auto system=wordAt(wm,4),queue=wordAt(wm,0x18);
    const auto capacity=wordAt(wm,0x1c),first=wordAt(wm,0x20),used=wordAt(wm,0x24);
    if((system&31)||!capacity||capacity>64||first>=capacity||used>capacity)return std::nullopt;
    return OwnerLayout{system,queue,capacity*4};
}
inline std::optional<OwnerLayout> ownerLayout(const sdk300::Binding& library,const Binding& graph,const CodeView& view) {
    const auto layout=nativeLayout(graph,view);if(!layout)return std::nullopt;
    const auto system=layout->system,queue=layout->queue,queueBytes=layout->queueBytes;
    const auto* owner=separateData(library,graph,view,system,0xf00);
    const auto* messages=separateData(library,graph,view,queue,queueBytes);
    if(!owner||!messages||overlap(owner,0xf00,messages,queueBytes))return std::nullopt;
    return layout;
}
} // namespace melonDS::wm_sdk::sharing::sdk300::graph
