#pragma once
// Bounded ARM9 metadata reads without CPU bus cycles or MMIO. TCM takes the
// same priority as native ARM9 data accesses. A valid view grants no source
// immutability, writable memory, callback or network authority.
#include <cstdint>
#include <cstring>
namespace melonDS::wm_sdk::sharing {
struct RegistryDataView {
    const std::uint8_t* mainRam;std::uint32_t mainMask;
    const std::uint8_t* itcm;std::uint32_t itcmSize;
    const std::uint8_t* dtcm;std::uint32_t dtcmBase,dtcmMask;
    const std::uint8_t* permissions;
    const std::uint8_t* bytes(std::uint32_t address,unsigned length)const {
        if(!length||length>0x2000||length-1>0xffffffffu-address||!permissions)return nullptr;
        const auto end=address+length-1;const std::uint8_t* result=nullptr;
        if(address<itcmSize){
            const unsigned offset=address&0x7fff;
            if(!itcm||end>=itcmSize||length>0x8000-offset)return nullptr;
            result=itcm+offset;
        }else if((address&dtcmMask)==dtcmBase){
            const unsigned offset=address&0x3fff;
            if(!dtcm||(end&dtcmMask)!=dtcmBase||length>0x4000-offset)return nullptr;
            result=dtcm+offset;
        }else{
            if(address<0x02000000||end>=0x03000000||!mainRam||(mainMask!=0x3fffff&&mainMask!=0xffffff))return nullptr;
            const unsigned offset=address&mainMask;
            if(length>mainMask+1-offset)return nullptr;
            // A range crossing into a higher-priority TCM mapping is not a
            // contiguous RAM view even if its endpoints are readable.
            for(auto at=address;at<=end;++at)if(at<itcmSize||(at&dtcmMask)==dtcmBase)return nullptr;
            result=mainRam+offset;
        }
        for(auto page=address>>12;page<=(end>>12);++page)if(!(permissions[page]&1))return nullptr;
        return result;
    }
    std::uint32_t word(std::uint32_t address)const {
        std::uint32_t value=0;const auto* p=bytes(address,4);if(p)std::memcpy(&value,p,4);return value;
    }
    unsigned half(std::uint32_t address)const {
        std::uint16_t value=0;const auto* p=bytes(address,2);if(p)std::memcpy(&value,p,2);return value;
    }
};
} // namespace melonDS::wm_sdk::sharing
