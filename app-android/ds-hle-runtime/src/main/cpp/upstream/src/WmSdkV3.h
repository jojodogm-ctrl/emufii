#pragma once
// Independently encoded DKR AWDP SDK 3.2.30001 ABI, not a ROM patch.
// Owner captures r47_other_dkr_abi; loaded ARM7 027E0000..027E88A8.
// NSMB/Pokemon are NOT admitted by this codec's existence.
#include "WmSdkAbi.h"
#include <algorithm>

namespace wmguest::v3 {
// Shared service operations refer to their old MKDS field offsets. Only these
// independently identified fields are translated. Initialization/StartMP use
// this SDK's own implementation, never an offset-shifted MKDS initializer.
inline uint32_t statusOffset(uint32_t old) {
    if (old == 0x44) return 0x70; // receive bank: 027E524C
    if (old == 0x18e) return 0x38; // effective send size: 027E0000/30
    if (old == 0x190) return 0x3a; // effective receive size
    if (old >= 0xb6) return old + 4; // named common fields through lifetime
    return old;
}
using Parameters = std::array<uint8_t,28>;
enum class ParameterFlavor { Dkr320, Nsmb300 };

// 027E8570: individually masked fields; invalid individual values report an
// error without undoing other valid fields. The active-MP forbidden mask is
// 2C00, not all parameters. Reads/writes are supplied by the console owner.
template<class Read16, class Write16, class Write64>
uint16_t parameters(const Parameters& p, Parameters& previous,
                    Read16 r, Write16 w, Write64 w64,
                    ParameterFlavor flavor=ParameterFlavor::Dkr320) {
    previous = {};
    put32(previous.data(),0x3fff);
    // The ROM really reports the current frequency for these three entries.
    for (unsigned o : {4u,6u,8u}) put16(previous.data()+o,r(0x5a));
    constexpr unsigned fields[] = {0x30,0x32,0x44,0x46,0x40,0x42,0x98};
    for (unsigned i=0;i<7;++i) put16(previous.data()+10+2*i,r(fields[i]));
    for (unsigned i=0;i<4;++i) previous[24+i]=uint8_t(r(i<2?0x92+2*i:0x96+2*i));
    uint32_t mask=read32(p.data());
    uint16_t error=0;
    if ((r(0)==9 || r(0)==10) && (mask&0x2c00)) { error=3;mask&=~0x2c00u; }
    for(unsigned i=0;i<3;++i) if(mask&(1u<<i)) {
        const auto n=read16(p.data()+4+2*i);
        const uint16_t value=n?n:16;
        w(0x58+2*i,value);
        if(i && int16_t(r(0x62))>value) w(0x62,value);
    }
    for(unsigned i=0;i<2;++i) if(mask&(8u<<i)) {
        const uint16_t n=uint16_t((uint32_t(read16(p.data()+10+2*i))+1)&0xfffe);
        if(n>r(0x34+2*i)) error=6;
        else {
            // NSMB 027E8594/85C4 compares the rounded halfword in r2, but
            // passes original r0 to its size setter. DKR's existing codec
            // keeps its separately captured rounded-value behavior.
            const auto stored=flavor==ParameterFlavor::Nsmb300?read16(p.data()+10+2*i):n;
            w(0x30+2*i,stored);
            const bool child=r(0x188)!=0;
            w((child==(i==0))?0x3a:0x38,uint16_t(stored+(i?2:4)));
        }
    }
    for(unsigned i=0;i<2;++i) if(mask&(0x20u<<i)) {
        const auto n=read16(p.data()+14+2*i);
        if(n>10000) error=6;
        else { w(0x44+2*i,n); w64(0x48+8*i,uint64_t(n)*33514/65536); }
    }
    for(unsigned i=0;i<2;++i) if(mask&(0x80u<<i)) {
        const auto n=read16(p.data()+18+2*i);
        if(n<191 || (n>219 && n<=262)) w(0x40+2*i,n); else error=6;
    }
    if(mask&0x200)w(0x98,read16(p.data()+22));
    for(unsigned i=0;i<4;++i)if(mask&(0x400u<<i))w(i<2?0x92+2*i:0x96+2*i,p[24+i]);
    return error;
}

// NSMB 027E522C: the child/scanner role must use its allowed channel.
// Only the hardware channel range 1..13 is meaningful to this adapter.
inline bool nsmbMpChannelAllowed(uint16_t mode,uint16_t channel,uint16_t allowed) {
    return mode!=2 || (channel>=1 && channel<=13 && (allowed&(1u<<(channel-1))));
}
// NSMB EndParent calls 027E0100; Reset does not. Other metadata is untouched
// by this helper. This is intentionally separate from DKR clearConnection.
template<class Write16> void nsmbEndParentSizes(Write16 w) {
    for(unsigned o=0x30;o<0x40;o+=2)w(o,0);
}

// 027E5510 and 027E6B6C: role-dependent negotiated payload-size suffixes.
inline void callbackSuffix(CallbackBytes& callback, uint16_t parent, uint16_t child, bool isChild) {
    const unsigned o=read16(callback.data())==PortRecv?0x40:0x24;
    put16(callback.data()+o,isChild?child:parent);
    put16(callback.data()+o+2,isChild?parent:child);
}
// 027E4B28: local teardown notification, before Reset/EndParent completion.
inline CallbackBytes selfDisconnect(bool childRole,uint16_t aid,const std::array<uint8_t,6>& mac) {
    CallbackBytes b{};
    put16(b.data(),childRole?12:8);put16(b.data()+8,0x1a);
    put16(b.data()+(childRole?0xa:0x10),aid);
    put16(b.data()+(childRole?0xc:0x12),0xf001);
    std::copy(mac.begin(),mac.end(),b.begin()+(childRole?0x10:0xa));
    return b;
}
template<class Write8,class Write16,class Write32>
void clearConnection(Write8 w8,Write16 w16,Write32 w32) {
    for(unsigned o=0x30;o<0x40;o+=2)w16(o,0);
    w32(0x198,0);w16(0x196,0);
    for(unsigned o=0x19c;o<0x1ec;++o)w8(o,0);
}
} // namespace wmguest::v3
