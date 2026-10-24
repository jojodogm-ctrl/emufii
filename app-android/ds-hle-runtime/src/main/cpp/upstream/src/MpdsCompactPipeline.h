// Opt-in, exact-ROM experiment: eight 256-byte slots in the original 0x820
// allocation; four outstanding SDK bootstrap exchanges. No predicted records,
// generated gameplay ACKs, or writes outside this participant's SDK object.
#pragma once
#include <cstdint>
#include <stdexcept>

namespace melonDS::mpds_room::compact {
inline constexpr char Profile[] = "mpds-us-debugmenu-room-p4-v2"; // command ACK drain included; do not mix v1 peers
inline constexpr std::uint32_t Begin=0x02070bc4, End=0x02071714, CodeCrc=0x4469fb86;
inline constexpr std::uint32_t Info=0x020ca940, Store=0x020714a0, ParentCallback=0x02071280;
inline constexpr std::uint32_t Svc=0xef4d5001;
inline constexpr unsigned Slots=8, Depth=4, Stride=256, DataBytes=156, Sequence=0x7f0;
struct Patch { std::uint32_t address, before, after; };
inline constexpr Patch Patches[] = {
    {0x02070cb0,0xe3500f7f,0xe35000fc}, // maximum body: 252, not 508
    {0x02070cf0,0xe1a00482,0xe1a00402}, // slot stride 512 -> 256
    {0x02070d00,0xe3520004,0xe3520008}, // initialize eight await words
    {0x02070d44,0xe2022003,0xe2022007},
    {0x02070d80,0xe2800b02,0xe2800e7f}, // eight sequence u16s at +0x7f0
    {0x02070d90,0xe2000003,0xe2000007},
    {0x02070dc0,0xe2877c02,0xe2877c01},
    {0x02070dd4,0x03a00002,0x03a00004}, // same SDK empty/recv=0 bootstrap, four slots
    {0x02070de8,0xe3a04003,0xe3a04007}, // child's initial send index: -1 modulo 8
    {0x02070f48,0xe2811003,0xe2811007}, // previous slot modulo 8
    {0x02070f4c,0xe2016003,0xe2016007},
    {0x02070f60,0xe08a2486,0xe08a2406},
    {0x02070f78,0xe2800b02,0xe2800e7f},
    {0x02070f8c,0xe2011003,0xe2011007},
    {0x02070fd0,0xe1a05482,0xe1a05402},
    {0x02070fdc,0xe3a02c02,0xe3a02c01},
    {0x02070fec,0xe08a0480,0xe08a0400},
    {0x02071008,0xe2800b02,0xe2800e7f},
    {0x0207101c,0xe2000003,0xe2000007},
    {0x0207103c,0xe1a00480,0xe1a00400},
    {0x020710c0,0xe1a02482,0xe1a02402},
    {0x020710e0,0xe3a02c02,0xe3a02c01},
    {0x020710fc,0xe2811b02,0xe2811e7f},
    {0x02071110,0xe2011003,0xe2011007},
    {0x02071130,0xe08a6481,0xe08a6401},
    {0x02071180,0xe2022003,0xe2022007},
    {0x02071218,0xe2800b02,0xe2800e7f},
    {0x02071228,0xe2000003,0xe2000007},
    {0x0207124c,0xe2811003,0xe2811007},
    {0x02071250,0xe2011003,0xe2011007},
    {ParentCallback,0xe92d4070,Svc}, // fixed roster: reject removal events, don't erase awaited data
    {0x0207142c,0xe3550c02,0xe3550c01},
    {0x02071430,0x83a05c02,0x83a05c01},
    {0x02071458,0xe0841481,0xe0841401},
    {0x02071474,0xe2800b02,0xe2800e7f},
    {0x02071484,0xe2000003,0xe2000007},
    {Store,0xe92d40f8,Svc}, // original receive/store semantics, search four awaited slots
    {0x0207158c,0xe1a01481,0xe1a01401},
    {0x020715b4,0xe2005003,0xe2005007},
    {0x020715bc,0x02850001,0x02850003}, // recycle current + 4, not current + 2
    {0x020715c0,0x02008003,0x02008007},
    {0x020715c8,0xe08a1488,0xe08a1408},
    {0x020715d0,0xe3a02c02,0xe3a0209c}, // clear real dataset only; sequence area is reserved
    {0x020715e4,0xe1a01488,0xe1a01408},
    {0x020715f8,0xe1a01486,0xe1a01406},
    {0x02071640,0xe08a2486,0xe08a2406},
    {0x02071664,0xe2800b02,0xe2800e7f},
    {0x0207167c,0xe2011003,0xe2011007},
};
static_assert((Slots-1)*Stride+DataBytes < Sequence);
static_assert((Slots-1)*Stride+32+38 < Sequence);
static_assert(Sequence+Slots*2 == 0x800);
static_assert(Slots >= 2*Depth);

// Pure owner-thread memory operation. Caller has checked the exact ROM, info
// pointer and actual payload range. Returns false rather than acknowledging a
// record with no available slot. Original callback's port receipt remains the
// sole transport ACK, after the guest has consumed its mailbox lease.
template<class Read16,class Write16,class Read8,class Write8>
bool store(unsigned aid,std::uint32_t payload,Read16 r16,Write16 w16,Read8 r8,Write8 w8) {
    if(aid>=4 || r16(Info+0x810)!=38 || r16(Info+0x814)!=DataBytes ||
       r16(Info+0x80e)!=15 || r16(Info+0x818)!=1)
        throw std::runtime_error("compact SDK dataset ABI changed");
    const auto cur=r16(Info+0x808);
    if(cur>=Slots)throw std::runtime_error("compact SDK write index");
    const auto bit=std::uint16_t(1u<<aid);
    for(unsigned look=0;look<Depth;++look){
        const auto slot=Info+((cur+look)&(Slots-1))*Stride;
        const auto awaiting=r16(slot);
        if(!(awaiting&bit))continue;
        const auto row=slot+4+aid*38; // exact aidBitmap 15, so rank == aid
        for(unsigned i=0;i<38;++i)w8(row+i,payload?r8(payload+i):0);
        w16(slot,awaiting&~bit);w16(slot+2,r16(slot+2)|bit);
        return true;
    }
    return false;
}
} // namespace melonDS::mpds_room::compact
