#pragma once
// Loaded SDK code contracts, not cartridge/gameplay selection. These spans
// include their literal pools; some are aggregates of adjacent SDK helpers.
// Their transitive service calls still require the independently admitted WM
// owner. Observation or a canonical address alone grants no live admission.
#include "WmSdk300SharingAbi.h"
#include <cstdint>
#include <cstring>
#include <stdexcept>

namespace melonDS::wm_sdk::sharing::sdk300 {
struct CodePin { unsigned bytes; std::uint32_t crc; };
inline constexpr std::array<CodePin,12> ArmReturnCode{{
    {0x44,0xccd9c5e2},{0x14,0x6a0e5f7c},{0x80,0x6f50d8ce},
    {0x18,0xbde7bf63},{0x18,0xec9af8c8},{0x484,0xb24106bd},
    {0x1c,0x6cfb6b6e},{0x30,0xb589e210},{0x10,0x3a833e64},
    {0x118,0x9fdc7741},{0xd8,0x8a60c50b},{0x4c,0xed6b1e70}
}};
inline constexpr std::array<CodePin,12> InterworkReturnCode{{
    {0x44,0xccd9c5e2},{0x14,0x6a0e5f7c},{0x88,0x4a173970},
    {0x18,0xbde7bf63},{0x18,0xec9af8c8},{0x4d4,0x11d3ce6a},
    {0x1c,0x6cfb6b6e},{0x34,0x96ef4c47},{0x10,0xe6372197},
    {0x120,0xb3d3cc7f},{0xe0,0x1f13b3b3},{0x4c,0xed6b1e70}
}};
inline const std::array<CodePin,12>& codePins(const Variant& variant) {
    if(&variant==&Variants[0])return ArmReturnCode;
    if(&variant==&Variants[1])return InterworkReturnCode;
    throw std::invalid_argument("unsupported SDK callee contract");
}

// Read the actual instruction backing, with no bus/MMIO calls, CodeCycles
// writes, guest execution or permission changes. Main-RAM mirrors and backing
// wraparound are deliberately unsupported. ITCM mirrors are read-only here.
struct CodeView {
    const std::uint8_t* mainRam;
    std::uint32_t mainMask;
    const std::uint8_t* itcm;
    std::uint32_t itcmSize;
    const std::uint8_t* permissions;
    std::uint32_t dtcmBase=0xffffffffu,dtcmMask=0;
    const std::uint8_t* bytes(std::uint32_t address,unsigned length)const {
        if((address&3)||!length||length>0x2000||length-1>0xffffffffu-address||!permissions)return nullptr;
        for(auto page=address>>12;page<=((address+length-1)>>12);++page)
            if(!(permissions[page]&4))return nullptr;
        if(address<itcmSize){
            const auto offset=address&0x7fff;
            if(!itcm||length>itcmSize-address||length>0x8000-offset)return nullptr;
            return itcm+offset;
        }
        if(!mainRam||(mainMask!=0x3fffff&&mainMask!=0xffffff)||!mainCode(address,length))return nullptr;
        return mainRam+(address&mainMask);
    }
    // These SDK spans also contain literal pools read by ARM DataRead32.
    // Conservatively require the whole span to be readable and to share its
    // instruction backing. ITCM takes priority over DTCM for native data reads.
    const std::uint8_t* readableBytes(std::uint32_t address,unsigned length)const {
        const auto* code=bytes(address,length);if(!code)return nullptr;
        for(auto page=address>>12;page<=((address+length-1)>>12);++page)
            if(!(permissions[page]&1)||(address>=itcmSize&&((page<<12)&dtcmMask)==dtcmBase))return nullptr;
        return code;
    }
};

// A finite list of owner-cell literals observed in the retained loaded code.
// No branch, instruction immediate, other pointer or neighboring word is
// normalized. The -4 cell is the adjacent SDK data cell used by CheckState.
struct OwnerLiteral { Boundary kind; unsigned offset; int displacement; };
inline constexpr OwnerLiteral ArmReturnOwnerLiterals[]{
    {Boundary::ConnectedMask,0x4c,0},{Boundary::ConnectedMask,0x7c,0},
    {Boundary::Aid,0x2c,0},{Boundary::SystemWork,0xc,0},
    {Boundary::CheckState,0xa4,0},{Boundary::CheckState,0xf8,0},
    {Boundary::CheckState,0x114,-4}
};
inline constexpr OwnerLiteral InterworkReturnOwnerLiterals[]{
    {Boundary::ConnectedMask,0x50,0},{Boundary::ConnectedMask,0x84,0},
    {Boundary::Aid,0x30,0},{Boundary::SystemWork,0xc,0},
    {Boundary::CheckState,0xa4,0},{Boundary::CheckState,0x100,0},
    {Boundary::CheckState,0x11c,-4}
};
// expectedOwnerCell=0 is only a candidate check. Live admission must repeat
// every callee with the one cell resolved from Binding::SystemWork+0xc, then
// independently establish the initialized service owner/session. Runtime code
// snapshots must retain actual raw bytes, not this normalized fingerprint.
inline bool matchesCode(const Variant& variant,Boundary kind,std::uint32_t address,
                        const CodeView& view,std::uint32_t expectedOwnerCell=0) {
    if(unsigned(kind)>=unsigned(Boundary::Count))return false;
    const auto pin=codePins(variant)[unsigned(kind)];
    const auto* bytes=view.bytes(address,pin.bytes);if(!bytes)return false;
    const bool arm=&variant==&Variants[0];
    const auto* literals=arm?ArmReturnOwnerLiterals:InterworkReturnOwnerLiterals;
    const auto count=arm?std::size(ArmReturnOwnerLiterals):std::size(InterworkReturnOwnerLiterals);
    const std::uint32_t canonicalCell=arm?0x020d152c:0x020a93ec;
    std::uint32_t ownerCell=expectedOwnerCell,crc=0xffffffffu;
    if(ownerCell&&!mainCode(ownerCell-4,8))return false;
    for(unsigned offset=0;offset<pin.bytes;offset+=4){
        std::uint32_t word;std::memcpy(&word,bytes+offset,4);
        for(unsigned i=0;i<count;++i)if(literals[i].kind==kind&&literals[i].offset==offset){
            const auto displacement=literals[i].displacement;
            const auto candidate=std::int64_t(word)-displacement;
            if(candidate<4||candidate>0xffffffffll||!mainCode(std::uint32_t(candidate)-4,8))return false;
            if(!ownerCell)ownerCell=std::uint32_t(candidate);
            if(candidate!=ownerCell)return false;
            word=std::uint32_t(std::int64_t(canonicalCell)+displacement);
        }
        for(unsigned byte=0;byte<4;++byte){
            crc^=(word>>(byte*8))&255;
            for(unsigned bit=0;bit<8;++bit)crc=(crc>>1)^(0xedb88320u&(0u-(crc&1)));
        }
    }
    return (crc^0xffffffffu)==pin.crc;
}
} // namespace melonDS::wm_sdk::sharing::sdk300
