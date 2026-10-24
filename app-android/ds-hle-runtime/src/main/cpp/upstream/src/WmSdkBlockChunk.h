#pragma once
// Defined destination/progress effects of native CopyBlock + MarkBlock only.
// This is not the full legacy packet parser, a transfer/MP authorization check,
// a completion callback or a promise that registered source data is immutable.
#include "WmSdkBlockMemoryView.h"
#include <algorithm>
#include <cstdint>
#include <cstring>
namespace melonDS::wm_sdk::sharing::block_chunk {
enum class Result {Copied,AlreadyPresent,InvalidMetadata,InvalidIndex,InvalidLength,Unmapped,Aliased};
inline std::uint32_t word(const std::uint8_t* p){std::uint32_t n;std::memcpy(&n,p,4);return n;}
inline bool overlap(const void* a,unsigned na,const void* b,unsigned nb){
    const auto x=std::uint64_t(reinterpret_cast<std::uintptr_t>(a)),y=std::uint64_t(reinterpret_cast<std::uintptr_t>(b));
    return x<y+nb&&y<x+na;
}
inline Result commit(const BlockMemoryView& memory,std::uint32_t info,unsigned segment,
    std::uint32_t index,const std::uint8_t* body,unsigned length){
    if((info&3)||!segment||segment>500)return Result::InvalidMetadata;
    const auto* meta=memory.range(info,28,BlockMemoryView::Read);if(!meta)return Result::Unmapped;
    const auto total=word(meta),blocks=word(meta+4),count=word(meta+16),bitmap=word(meta+20),destination=word(meta+24);
    if(!total||total>0x1000000||blocks!=(std::uint64_t(total)+segment-1)/segment||count>blocks||!bitmap||(bitmap&3)||!destination)return Result::InvalidMetadata;
    if(index>=blocks)return Result::InvalidIndex;
    const auto offset=std::uint64_t(index)*segment;const unsigned bytes=std::min(std::uint64_t(segment),std::uint64_t(total)-offset);
    if(!body||length!=bytes)return Result::InvalidLength;
    const auto bitmapBytes=unsigned((std::uint64_t(blocks)+31)/32*4);
    if(std::uint64_t(bitmap)+bitmapBytes>0x100000000ull||std::uint64_t(destination)+total>0x100000000ull)return Result::Unmapped;
    const auto* bits=memory.range(bitmap,bitmapBytes,BlockMemoryView::Read);if(!bits)return Result::Unmapped;
    const auto mask=std::uint32_t(1)<<(index&31);const auto prior=word(bits+(index/32)*4);
    if(prior&mask)return Result::AlreadyPresent;
    if(count==blocks)return Result::InvalidMetadata;
    auto* dest=memory.range(destination,total,BlockMemoryView::Write);
    auto* marked=memory.range(bitmap,bitmapBytes,BlockMemoryView::ReadWrite);
    auto* progress=memory.range(info+12,8,BlockMemoryView::Write);
    if(!dest||!marked||!progress)return Result::Unmapped;
    // Fast-path scope excludes buffers aliasing their own control state. The
    // legacy/native path remains responsible for unsupported layouts.
    if(overlap(dest,total,meta,28)||overlap(dest,total,marked,bitmapBytes)||overlap(marked,bitmapBytes,meta,28)||
       overlap(body,length,dest,total)||overlap(body,length,marked,bitmapBytes)||overlap(body,length,meta,28))return Result::Aliased;
    std::memcpy(dest+offset,body,bytes);
    const std::uint32_t next=count+1,combined=prior|mask;
    std::memcpy(progress+4,&next,4);std::memcpy(progress,&index,4);std::memcpy(marked+(index/32)*4,&combined,4);
    return Result::Copied;
}
} // namespace melonDS::wm_sdk::sharing::block_chunk
