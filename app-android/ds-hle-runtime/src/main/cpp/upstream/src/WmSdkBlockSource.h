#pragma once
#include "WmSdkBlockChunk.h"
#include "WmSdkBlockRegistryLifetime.h"
#include <array>
namespace melonDS::wm_sdk::sharing::block_source {
struct Chunk {std::array<std::uint8_t,500> bytes{};unsigned length=0;};
// The caller separately authorizes the real peer/request/MP session and binds
// head/sendSegment addresses from the identified SDK graph. No remote pointer
// is accepted. Snapshot only this chunk; the next one reads current data again.
inline bool snapshot(const BlockMemoryView& memory,const BlockRegistryLifetime& registry,
    const BlockRegistryLifetime::Token& token,std::uint32_t head,std::uint32_t sendSegment,
    unsigned requestedTotal,unsigned segment,std::uint32_t index,Chunk& output){
    const auto& s=token.source;
    if(!registry.valid(token)||s.mode!=0||s.id<1000||!requestedTotal||requestedTotal>s.bytes||requestedTotal>0x1000000||!segment||segment>500)return false;
    const auto* size=memory.range(sendSegment,2,BlockMemoryView::Read);if(!size)return false;
    std::uint16_t actual;std::memcpy(&actual,size,2);if(actual!=segment)return false;
    const auto* root=memory.range(head,4,BlockMemoryView::Read);if(!root)return false;
    auto at=block_chunk::word(root);std::array<std::uint32_t,128> seen{};unsigned count=0;const std::uint8_t* descriptor=nullptr;
    while(at){
        if((at&3)||count==seen.size())return false;
        for(unsigned i=0;i<count;++i)if(seen[i]==at)return false;seen[count++]=at;
        const auto* p=memory.range(at,0x34,BlockMemoryView::Read);if(!p)return false;
        if(block_chunk::word(p)==s.id){if(at!=s.descriptor)return false;descriptor=p;break;}
        at=block_chunk::word(p+0x28);
    }
    if(!descriptor)return false;
    std::uint16_t field30,mode;std::memcpy(&field30,descriptor+0x30,2);std::memcpy(&mode,descriptor+0x32,2);
    if(mode||field30!=s.field30||block_chunk::word(descriptor+4)!=s.bytes||block_chunk::word(descriptor+0x2c)!=s.pointer)return false;
    const auto offset=std::uint64_t(index)*segment;if(offset>=requestedTotal)return false;
    const unsigned bytes=unsigned(std::min(std::uint64_t(segment),std::uint64_t(requestedTotal)-offset));
    if(std::uint64_t(s.pointer)+offset+bytes>0x100000000ull)return false;
    const auto* data=memory.range(std::uint32_t(std::uint64_t(s.pointer)+offset),bytes,BlockMemoryView::Read);if(!data)return false;
    if(block_chunk::overlap(data,bytes,output.bytes.data(),unsigned(output.bytes.size())))return false;
    Chunk owned;std::copy_n(data,bytes,owned.bytes.begin());owned.length=bytes;output=owned;return true;
}
} // namespace melonDS::wm_sdk::sharing::block_source
