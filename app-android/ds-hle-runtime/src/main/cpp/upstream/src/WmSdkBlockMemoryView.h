#pragma once
// ARM9 data backing only. No bus access, MMIO or guest-pointer deserialization.
// A caller must separately bind the actual SDK/MP owner and protected regions.
#include <cstdint>
namespace melonDS::wm_sdk::sharing {
struct BlockMemoryView {
    std::uint8_t* mainRam=nullptr;std::uint32_t mainMask=0;
    std::uint8_t* itcm=nullptr;std::uint32_t itcmSize=0;
    std::uint8_t* dtcm=nullptr;std::uint32_t dtcmBase=0xffffffffu,dtcmMask=0xffffffffu;
    const std::uint8_t* permissions=nullptr;
    enum Access : unsigned {Read=1,Write=2,ReadWrite=3};
    std::uint8_t* range(std::uint32_t address,unsigned length,Access access)const {
        if(!length||length>0x1000000||std::uint64_t(address)+length>0x100000000ull||!permissions)return nullptr;
        const auto end=std::uint64_t(address)+length-1;std::uint8_t* result=nullptr;
        if(address<itcmSize){
            const auto offset=address&0x7fff;
            if(!itcm||end>=itcmSize||length>0x8000-offset)return nullptr;
            result=itcm+offset;
        }else if((address&dtcmMask)==dtcmBase){
            const auto offset=address&0x3fff;
            if(!dtcm||(std::uint32_t(end)&dtcmMask)!=dtcmBase||length>0x4000-offset)return nullptr;
            result=dtcm+offset;
        }else{
            if(address<0x02000000||end>=0x03000000||!mainRam||(mainMask!=0x3fffff&&mainMask!=0xffffff))return nullptr;
            const auto offset=address&mainMask;
            if(length>mainMask+1-offset||address<itcmSize)return nullptr;
            const auto tcmBytes=std::uint64_t(~dtcmMask)+1;
            if(dtcmBase!=0xffffffffu&&!(tcmBytes&(tcmBytes-1))&&
               std::uint64_t(address)<std::uint64_t(dtcmBase)+tcmBytes&&std::uint64_t(dtcmBase)<=end)return nullptr;
            result=mainRam+offset;
        }
        for(auto page=address>>12;page<=std::uint32_t(end)>>12;++page)if((permissions[page]&unsigned(access))!=unsigned(access))return nullptr;
        return result;
    }
};
} // namespace melonDS::wm_sdk::sharing
