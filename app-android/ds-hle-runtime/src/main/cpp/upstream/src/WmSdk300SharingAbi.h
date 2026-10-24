#pragma once
// SDK3 DataSharing library variants, independent of cartridge/gameplay IDs.
// These code/geometry contracts do not activate a runtime or grant callback,
// source-memory, transport, lifecycle or checkpoint ownership. A live adapter
// must establish those contracts AND unanimous window agreement before patching.
#include "WmSdkDataSharingWindow.h"
#include "WmSdkSharingPolicy.h"
#include <array>
#include <optional>
#include <type_traits>

namespace melonDS::wm_sdk::sharing::sdk300 {
inline constexpr std::uint32_t DefaultBase=0x0f100000,ObjectBytes=0x20800,
    Metadata=0x20400,Sequence=0x20000,Svc=0xef574d01;
inline constexpr unsigned Slots=128,Depth=64,Stride=1024;
enum class Boundary:unsigned {Popcount,DisableIrq,ConnectedMask,Clear16,RestoreIrq,SetMpData,Copy16,Aid,SystemWork,CheckState,SetPortCallback,Clear32,Count};
enum class Function:unsigned {RowAddress,SharedAddress,SendDataset,Store,ChildCallback,
    ParentCallback,SendCallback,Step,End,Start,Count};
struct Call {unsigned offset;Boundary kind;};
struct Literal {unsigned offset,targetOffset;};
struct Patch {unsigned offset;std::uint32_t before,after;};
struct Variant {
    const char* name;unsigned bytes;std::uint32_t signature;
    std::array<unsigned,10> entries;
    const Call* calls;unsigned callCount;
    const Literal* literals;unsigned literalCount;
    const Patch* patches;unsigned patchCount;
};
inline constexpr Call ArmReturnCalls[]={
    {0x20,Boundary::Popcount},
    {0xa0,Boundary::DisableIrq},
    {0xc0,Boundary::ConnectedMask},
    {0x108,Boundary::Clear16},
    {0x148,Boundary::RestoreIrq},
    {0x184,Boundary::SetMpData},
    {0x1e0,Boundary::RestoreIrq},
    {0x29c,Boundary::Copy16},
    {0x2b0,Boundary::Clear16},
    {0x2b4,Boundary::DisableIrq},
    {0x2dc,Boundary::RestoreIrq},
    {0x368,Boundary::Aid},
    {0x3b0,Boundary::Copy16},
    {0x4a4,Boundary::DisableIrq},
    {0x4ec,Boundary::RestoreIrq},
    {0x538,Boundary::SystemWork},
    {0x584,Boundary::Aid},
    {0x644,Boundary::CheckState},
    {0x684,Boundary::Aid},
    {0x690,Boundary::ConnectedMask},
    {0x734,Boundary::SetMpData},
    {0x7c0,Boundary::Copy16},
    {0x8b8,Boundary::Copy16},
    {0x910,Boundary::Copy16},
    {0x940,Boundary::SetMpData},
    {0x998,Boundary::CheckState},
    {0x9d0,Boundary::SetPortCallback},
    {0xa10,Boundary::CheckState},
    {0xa50,Boundary::Aid},
    {0xa5c,Boundary::ConnectedMask},
    {0xa70,Boundary::Clear32},
    {0xabc,Boundary::Popcount},
    {0xb34,Boundary::SetPortCallback},
    {0xb98,Boundary::SetMpData},
    {0xc28,Boundary::SetPortCallback},
};
inline constexpr Literal ArmReturnLiterals[]={
    {0x1ec,0x52c},
    {0x61c,0x3f4},
    {0x620,0x2e8},
    {0x97c,0x52c},
    {0xc38,0x3f4},
    {0xc3c,0x52c},
    {0xc44,0x2e8},
};
inline constexpr Patch ArmReturnPatches[]={
    {0x0,0xe92d4030,0xef574d01}, // entry-RowAddress
    {0x24,0xe2851b02,0xe2851b81}, // metadata
    {0xa4,0xe28a1b02,0xe28a1b81}, // metadata
    {0xb0,0xe1a01481,0xe1a01501}, // slot-stride
    {0xc4,0xe28a1b02,0xe28a1b81}, // metadata
    {0xd8,0xe2000003,0xe200007f}, // slot-mask
    {0xe4,0xe3510001,0xe1510001}, // future-clear-both-modes
    {0xe8,0x02840001,0x0284003f}, // future-clear-double
    {0xec,0x02000003,0x0200007f}, // slot-mask
    {0xfc,0xe08a1488,0xe08a1508}, // slot-stride
    {0x10c,0xe28a0b02,0xe28a0b81}, // metadata
    {0x118,0xe1a01488,0xe1a01508}, // slot-stride
    {0x12c,0xe1a01486,0xe1a01506}, // slot-stride
    {0x14c,0xe28a3b02,0xe28a3b81}, // metadata
    {0x174,0xe08a2486,0xe08a2506}, // slot-stride
    {0x198,0xe2800b02,0xe2800802}, // sequence
    {0x1a0,0xe28a0b02,0xe28a0b81}, // metadata
    {0x1b0,0xe2011003,0xe201107f}, // slot-mask
    {0x1cc,0x128a0b02,0x128a0b81}, // metadata
    {0x1f4,0xe92d40f0,0xef574d01}, // entry-Store
    {0x20c,0xe287cb02,0xe287cb81}, // metadata
    {0x230,0xe1a00484,0xe1a00504}, // slot-stride
    {0x254,0xe2000003,0xe200007f}, // slot-mask
    {0x260,0xe1a00484,0xe1a00504}, // slot-stride
    {0x274,0xe0872484,0xe0872504}, // slot-stride
    {0x290,0xe2870b02,0xe2870b81}, // metadata
    {0x2a4,0xe2870b02,0xe2870b81}, // metadata
    {0x2b8,0xe1a04484,0xe1a04504}, // slot-stride
    {0x36c,0xe2871b02,0xe2871b81}, // metadata
    {0x39c,0xe2870b02,0xe2870b81}, // metadata
    {0x3ac,0xe0871481,0xe0871501}, // slot-stride
    {0x3b4,0xe2871b02,0xe2871b81}, // metadata
    {0x3c8,0xe2800b02,0xe2800802}, // sequence
    {0x3d8,0xe2000003,0xe200007f}, // slot-mask
    {0x3e4,0xe2870b02,0xe2870b81}, // metadata
    {0x4a8,0xe2841b02,0xe2841b81}, // metadata
    {0x4b4,0xe1a0348e,0xe1a0350e}, // slot-stride
    {0x4d4,0xe2011003,0xe201107f}, // slot-mask
    {0x4dc,0xe1a023a1,0xe1a02321}, // slot-stride-disconnect
    {0x4fc,0xe2840b02,0xe2840b81}, // metadata
    {0x51c,0xe2840b02,0xe2840b81}, // metadata
    {0x5a0,0xe2841b02,0xe2841b81}, // metadata
    {0x5b8,0xe2800b02,0xe2800802}, // sequence
    {0x5c8,0xe2000003,0xe200007f}, // slot-mask
    {0x5e0,0x12840b02,0x12840b81}, // metadata
    {0x5ec,0x12811003,0x1281107f}, // previous-slot
    {0x5f0,0x12011003,0x1201107f}, // slot-mask
    {0x5f8,0xe2840b02,0xe2840b81}, // metadata
    {0x608,0xe2840b02,0xe2840b81}, // metadata
    {0x624,0xe92d4ff0,0xef574d01}, // entry-Step
    {0x698,0xe28a0b02,0xe28a0b81}, // metadata
    {0x6e4,0xe28a1b02,0xe28a1b81}, // metadata
    {0x710,0xe2822003,0xe282207f}, // previous-slot
    {0x714,0xe2022003,0xe202207f}, // slot-mask
    {0x730,0xe08a2485,0xe08a2505}, // slot-stride
    {0x748,0xe2800b02,0xe2800802}, // sequence
    {0x750,0xe28a0b02,0xe28a0b81}, // metadata
    {0x75c,0xe2011003,0xe201107f}, // slot-mask
    {0x774,0x128a0b02,0x128a0b81}, // metadata
    {0x78c,0xe28a0b02,0xe28a0b81}, // metadata
    {0x7a0,0xe1a04482,0xe1a04502}, // slot-stride
    {0x7bc,0xe08a0480,0xe08a0500}, // slot-stride
    {0x7c4,0xe28a1b02,0xe28a1b81}, // metadata
    {0x7d8,0xe2800b02,0xe2800802}, // sequence
    {0x7ec,0xe2000003,0xe200007f}, // slot-mask
    {0x80c,0xe1a00480,0xe1a00500}, // slot-stride
    {0x848,0xe28a0b02,0xe28a0b81}, // metadata
    {0x870,0x028a1b02,0x028a1b81}, // metadata
    {0x880,0xe28a1b02,0xe28a1b81}, // metadata
    {0x894,0xe1a02482,0xe1a02502}, // slot-stride
    {0x8bc,0xe28a2b02,0xe28a2b81}, // metadata
    {0x8d0,0xe2811b02,0xe2811802}, // sequence
    {0x8e4,0xe2011003,0xe201107f}, // slot-mask
    {0x8f4,0xe28a0b02,0xe28a0b81}, // metadata
    {0x904,0xe08a1481,0xe08a1501}, // slot-stride
    {0x914,0xe28a3b02,0xe28a3b81}, // metadata
    {0x944,0xe28a1b02,0xe28a1b81}, // metadata
    {0x954,0xe2022003,0xe202207f}, // slot-mask
    {0x984,0xe92d4010,0xef574d01}, // entry-End
    {0x9b0,0xe2840b02,0xe2840b81}, // metadata
    {0x9d4,0xe2841b02,0xe2841b81}, // metadata
    {0x9e8,0xe92d4ff0,0xef574d01}, // entry-Start
    {0xa6c,0xe3a02e82,0xe3a02b82}, // allocation-size
    {0xa74,0xe28a0b02,0xe28a0b81}, // metadata
    {0xaac,0xe28a1b02,0xe28a1b81}, // metadata
    {0xac0,0xe28a3b02,0xe28a3b81}, // metadata
    {0xb10,0xe1a00484,0xe1a00504}, // slot-stride
    {0xb20,0xe3540004,0xe3540080}, // slot-count
    {0xb40,0xe3a04002,0xe3a04040}, // bootstrap-double
    {0xb54,0xe28acb02,0xe28acb81}, // metadata
    {0xb68,0xe2022003,0xe202207f}, // slot-mask
    {0xba8,0xe2800b02,0xe2800802}, // sequence
    {0xbb0,0xe28a0b02,0xe28a0b81}, // metadata
    {0xbbc,0xe2011003,0xe201107f}, // slot-mask
    {0xbd4,0x128a0b02,0x128a0b81}, // metadata
    {0xbec,0xe2888c02,0xe2888b01}, // bootstrap-payload-stride
    {0xbf4,0xe28a0b02,0xe28a0b81}, // metadata
    {0xc00,0x11a00006,0x11a00004}, // bootstrap-single
    {0xc18,0xe3a04003,0xe3a0407f}, // child-send-index
};
inline constexpr Call InterworkReturnCalls[]={
    {0x20,Boundary::Popcount},
    {0xb8,Boundary::DisableIrq},
    {0xd8,Boundary::ConnectedMask},
    {0x120,Boundary::Clear16},
    {0x160,Boundary::RestoreIrq},
    {0x19c,Boundary::SetMpData},
    {0x204,Boundary::RestoreIrq},
    {0x2d0,Boundary::Copy16},
    {0x2e4,Boundary::Clear16},
    {0x2e8,Boundary::DisableIrq},
    {0x310,Boundary::RestoreIrq},
    {0x3c4,Boundary::Aid},
    {0x414,Boundary::Copy16},
    {0x538,Boundary::DisableIrq},
    {0x580,Boundary::RestoreIrq},
    {0x5d8,Boundary::SystemWork},
    {0x630,Boundary::Aid},
    {0x700,Boundary::CheckState},
    {0x750,Boundary::Aid},
    {0x75c,Boundary::ConnectedMask},
    {0x808,Boundary::SetMpData},
    {0x898,Boundary::Copy16},
    {0x990,Boundary::Copy16},
    {0x9e8,Boundary::Copy16},
    {0xa18,Boundary::SetMpData},
    {0xa74,Boundary::CheckState},
    {0xab8,Boundary::SetPortCallback},
    {0xafc,Boundary::CheckState},
    {0xb4c,Boundary::Aid},
    {0xb58,Boundary::ConnectedMask},
    {0xb6c,Boundary::Clear32},
    {0xbb8,Boundary::Popcount},
    {0xc34,Boundary::SetPortCallback},
    {0xc98,Boundary::SetMpData},
    {0xd2c,Boundary::SetPortCallback},
};
inline constexpr Literal InterworkReturnLiterals[]={
    {0x214,0x5cc},
    {0x6d8,0x460},
    {0x6dc,0x320},
    {0xa58,0x5cc},
    {0xd40,0x460},
    {0xd44,0x5cc},
    {0xd4c,0x320},
    {0xd58,0xa60}, // LDR ip,[pc]; BX ip: internal End tail veneer
};
inline constexpr Patch InterworkReturnPatches[]={
    {0x0,0xe92d4030,0xef574d01}, // entry-RowAddress
    {0x24,0xe2851b02,0xe2851b81}, // metadata
    {0xbc,0xe28a1b02,0xe28a1b81}, // metadata
    {0xc8,0xe1a01481,0xe1a01501}, // slot-stride
    {0xdc,0xe28a1b02,0xe28a1b81}, // metadata
    {0xf0,0xe2000003,0xe200007f}, // slot-mask
    {0xfc,0xe3510001,0xe1510001}, // future-clear-both-modes
    {0x100,0x02840001,0x0284003f}, // future-clear-double
    {0x104,0x02000003,0x0200007f}, // slot-mask
    {0x114,0xe08a1488,0xe08a1508}, // slot-stride
    {0x124,0xe28a0b02,0xe28a0b81}, // metadata
    {0x130,0xe1a01488,0xe1a01508}, // slot-stride
    {0x144,0xe1a01486,0xe1a01506}, // slot-stride
    {0x164,0xe28a3b02,0xe28a3b81}, // metadata
    {0x18c,0xe08a2486,0xe08a2506}, // slot-stride
    {0x1b0,0xe2800b02,0xe2800802}, // sequence
    {0x1b8,0xe28a0b02,0xe28a0b81}, // metadata
    {0x1c8,0xe2011003,0xe201107f}, // slot-mask
    {0x1ec,0x128a0b02,0x128a0b81}, // metadata
    {0x21c,0xe92d40f0,0xef574d01}, // entry-Store
    {0x234,0xe287cb02,0xe287cb81}, // metadata
    {0x25c,0xe1a00484,0xe1a00504}, // slot-stride
    {0x284,0xe2000003,0xe200007f}, // slot-mask
    {0x290,0xe1a00484,0xe1a00504}, // slot-stride
    {0x2a8,0xe0872484,0xe0872504}, // slot-stride
    {0x2c4,0xe2870b02,0xe2870b81}, // metadata
    {0x2d8,0xe2870b02,0xe2870b81}, // metadata
    {0x2ec,0xe1a04484,0xe1a04504}, // slot-stride
    {0x3c8,0xe2871b02,0xe2871b81}, // metadata
    {0x400,0xe2870b02,0xe2870b81}, // metadata
    {0x410,0xe0871481,0xe0871501}, // slot-stride
    {0x418,0xe2871b02,0xe2871b81}, // metadata
    {0x42c,0xe2800b02,0xe2800802}, // sequence
    {0x43c,0xe2000003,0xe200007f}, // slot-mask
    {0x44c,0xe2870b02,0xe2870b81}, // metadata
    {0x53c,0xe2841b02,0xe2841b81}, // metadata
    {0x548,0xe1a0348e,0xe1a0350e}, // slot-stride
    {0x568,0xe2011003,0xe201107f}, // slot-mask
    {0x570,0xe1a023a1,0xe1a02321}, // slot-stride-disconnect
    {0x590,0xe2840b02,0xe2840b81}, // metadata
    {0x5b8,0xe2840b02,0xe2840b81}, // metadata
    {0x650,0xe2841b02,0xe2841b81}, // metadata
    {0x668,0xe2800b02,0xe2800802}, // sequence
    {0x678,0xe2000003,0xe200007f}, // slot-mask
    {0x694,0x12840b02,0x12840b81}, // metadata
    {0x6a0,0x12811003,0x1281107f}, // previous-slot
    {0x6a4,0x12011003,0x1201107f}, // slot-mask
    {0x6ac,0xe2840b02,0xe2840b81}, // metadata
    {0x6c0,0xe2840b02,0xe2840b81}, // metadata
    {0x6e0,0xe92d4ff0,0xef574d01}, // entry-Step
    {0x764,0xe28a0b02,0xe28a0b81}, // metadata
    {0x7b8,0xe28a1b02,0xe28a1b81}, // metadata
    {0x7e4,0xe2822003,0xe282207f}, // previous-slot
    {0x7e8,0xe2022003,0xe202207f}, // slot-mask
    {0x804,0xe08a2485,0xe08a2505}, // slot-stride
    {0x81c,0xe2800b02,0xe2800802}, // sequence
    {0x824,0xe28a0b02,0xe28a0b81}, // metadata
    {0x830,0xe2011003,0xe201107f}, // slot-mask
    {0x848,0x128a0b02,0x128a0b81}, // metadata
    {0x864,0xe28a0b02,0xe28a0b81}, // metadata
    {0x878,0xe1a04482,0xe1a04502}, // slot-stride
    {0x894,0xe08a0480,0xe08a0500}, // slot-stride
    {0x89c,0xe28a1b02,0xe28a1b81}, // metadata
    {0x8b0,0xe2800b02,0xe2800802}, // sequence
    {0x8c4,0xe2000003,0xe200007f}, // slot-mask
    {0x8e4,0xe1a00480,0xe1a00500}, // slot-stride
    {0x920,0xe28a0b02,0xe28a0b81}, // metadata
    {0x948,0x028a1b02,0x028a1b81}, // metadata
    {0x958,0xe28a1b02,0xe28a1b81}, // metadata
    {0x96c,0xe1a02482,0xe1a02502}, // slot-stride
    {0x994,0xe28a2b02,0xe28a2b81}, // metadata
    {0x9a8,0xe2811b02,0xe2811802}, // sequence
    {0x9bc,0xe2011003,0xe201107f}, // slot-mask
    {0x9cc,0xe28a0b02,0xe28a0b81}, // metadata
    {0x9dc,0xe08a1481,0xe08a1501}, // slot-stride
    {0x9ec,0xe28a3b02,0xe28a3b81}, // metadata
    {0xa1c,0xe28a1b02,0xe28a1b81}, // metadata
    {0xa2c,0xe2022003,0xe202207f}, // slot-mask
    {0xa60,0xe92d4010,0xef574d01}, // entry-End
    {0xa94,0xe2840b02,0xe2840b81}, // metadata
    {0xabc,0xe2841b02,0xe2841b81}, // metadata
    {0xad4,0xe92d4ff0,0xef574d01}, // entry-Start
    {0xb68,0xe3a02e82,0xe3a02b82}, // allocation-size
    {0xb70,0xe28a0b02,0xe28a0b81}, // metadata
    {0xba8,0xe28a1b02,0xe28a1b81}, // metadata
    {0xbbc,0xe28a3b02,0xe28a3b81}, // metadata
    {0xc10,0xe1a00484,0xe1a00504}, // slot-stride
    {0xc20,0xe3540004,0xe3540080}, // slot-count
    {0xc40,0xe3a04002,0xe3a04040}, // bootstrap-double
    {0xc54,0xe28acb02,0xe28acb81}, // metadata
    {0xc68,0xe2022003,0xe202207f}, // slot-mask
    {0xca8,0xe2800b02,0xe2800802}, // sequence
    {0xcb0,0xe28a0b02,0xe28a0b81}, // metadata
    {0xcbc,0xe2011003,0xe201107f}, // slot-mask
    {0xcd4,0x128a0b02,0x128a0b81}, // metadata
    {0xcf0,0xe2888c02,0xe2888b01}, // bootstrap-payload-stride
    {0xcf8,0xe28a0b02,0xe28a0b81}, // metadata
    {0xd04,0x11a00006,0x11a00004}, // bootstrap-single
    {0xd1c,0xe3a04003,0xe3a0407f}, // child-send-index
};
inline constexpr Variant Variants[]={
    {"ArmReturn",0xc48,0x7a93690c,{0x0,0x38,0x90,0x1f4,0x2e8,0x3f4,0x52c,0x624,0x984,0x9e8},ArmReturnCalls,35,ArmReturnLiterals,7,ArmReturnPatches,99},
    {"InterworkReturn",0xd5c,0xa1941153,{0x0,0x3c,0xa8,0x21c,0x320,0x460,0x5cc,0x6e0,0xa60,0xad4},InterworkReturnCalls,35,InterworkReturnLiterals,8,InterworkReturnPatches,99},
};
struct Binding {
    const Variant* variant=nullptr;std::uint32_t base=0;
    std::array<std::uint32_t,static_cast<unsigned>(Boundary::Count)> boundaries{};
    std::uint32_t entry(Function function)const{return base+variant->entries[static_cast<unsigned>(function)];}
};
inline constexpr bool mainCode(std::uint32_t base,unsigned size) {
    return !(base&3)&&size&&base>=0x02000000&&base<0x02400000&&size<=0x02400000-base;
}
// All bytes are fingerprinted. Only enumerated external BL relocations and
// exact internal callback-pointer literals normalize. Repeated call sites of
// one boundary must resolve to one actual target, including ITCM code targets.
// verify MUST independently establish the loaded callee ABI and owner;
// returning true based only on a canonical address is insufficient admission.
// A whole-Binding verifier can establish transitive roles consistently. The
// older per-boundary verifier remains available to existing research callers.
template<class Read32,class VerifyBoundary>
std::optional<Binding> identifyAt(std::uint32_t base,Read32 read,VerifyBoundary verify) {
    for(const auto& variant:Variants){
        if(!mainCode(base,variant.bytes))continue;
        Binding binding{&variant,base,{}};std::uint32_t crc=0xffffffffu;bool valid=true;
        for(unsigned offset=0;offset<variant.bytes&&valid;offset+=4){
            auto word=read(base+offset);
            for(unsigned i=0;i<variant.callCount;++i)if(variant.calls[i].offset==offset){
                if((word&0x0f000000)!=0x0b000000){valid=false;break;}
                std::int64_t displacement=word&0xffffff;
                if(displacement&0x800000)displacement-=0x1000000;
                const auto target=std::int64_t(base)+offset+8+4*displacement;
                if(target<=0||target>0xffffffffll||(target&3)||
                    (target>=base&&target<std::int64_t(base)+variant.bytes)){valid=false;break;}
                auto& observed=binding.boundaries[static_cast<unsigned>(variant.calls[i].kind)];
                if(observed&&observed!=target){valid=false;break;}
                observed=static_cast<std::uint32_t>(target);word&=0xff000000;
            }
            for(unsigned i=0;i<variant.literalCount;++i)if(variant.literals[i].offset==offset){
                if(word!=base+variant.literals[i].targetOffset){valid=false;break;}
                word=variant.literals[i].targetOffset;
            }
            for(unsigned byte=0;byte<4;++byte){
                crc^=(word>>(byte*8))&255;
                for(unsigned bit=0;bit<8;++bit)crc=(crc>>1)^(0xedb88320u&(0u-(crc&1)));
            }
        }
        if(!valid||(crc^0xffffffffu)!=variant.signature)continue;
        for(auto target:binding.boundaries)if(!target){valid=false;break;}
        if constexpr(std::is_invocable_r_v<bool,VerifyBoundary,const Binding&>){
            if(valid&&!verify(binding))valid=false;
        }else{
            for(unsigned i=0;i<binding.boundaries.size()&&valid;++i)
                if(!verify(static_cast<Boundary>(i),binding.boundaries[i]))valid=false;
        }
        if(valid)return binding;
    }
    return std::nullopt;
}
// Caller owns the stopped native/JIT code generation. Preflight every changed
// word before writing any: a stale or partial plan cannot silently patch code.
inline std::uint32_t replacement(const Variant& variant,const Patch& patch,unsigned lead=Depth){
    // Storage/strides/masks stay128/1024. Only the native future initialization
    // distance and the number of native bootstrap records select the lead.
    const bool arm=&variant==&Variants[0];
    if(patch.offset==(arm?0xe8u:0x100u))return 0x02840000u|(lead-1);
    if(patch.offset==(arm?0xb40u:0xc40u))return 0xe3a04000u|lead;
    return patch.after;
}
template<class Read32,class Write32>
bool apply(const Binding& binding,Read32 read,Write32 write,unsigned lead=Depth) {
    if(!binding.variant||!mainCode(binding.base,binding.variant->bytes)||!sharing_policy::validLead(lead)||
       (binding.variant!=&Variants[0]&&binding.variant!=&Variants[1]))return false;
    for(unsigned i=0;i<binding.variant->patchCount;++i){const auto& p=binding.variant->patches[i];
        if(read(binding.base+p.offset)!=p.before)return false;}
    for(unsigned i=0;i<binding.variant->patchCount;++i){const auto& p=binding.variant->patches[i];
        write(binding.base+p.offset,replacement(*binding.variant,p,lead));}
    return true;
}
// Native DataSharing uses halfword copy helpers: only even rows are admitted.
// Configured row bitmap and actual connected participants are separate. All
// configured rows occupy their SDK-defined position, including inactive AIDs.
inline std::optional<Layout> geometry(std::uint32_t base,std::uint16_t bitmap,
                                      unsigned rowBytes,unsigned mode,unsigned lead=Depth) {
    if(!bitmap||!rowBytes||(rowBytes&1)||rowBytes>508||mode>1||!sharing_policy::validLead(lead))return std::nullopt;
    Layout layout{base,ObjectBytes,Metadata,Sequence,Slots,lead,Stride,4,rowBytes,
        bitmap,0x20,{}, {8,0x10,0x14,0x0e,0x18,mode}};
    for(unsigned aid=0;aid<MaxAids;++aid)if(bitmap&(1u<<aid)){
        layout.rowOffsets[aid]=layout.datasetBytes;layout.datasetBytes+=rowBytes;
    }
    if(!layout.valid())return std::nullopt;
    return layout;
}
} // namespace melonDS::wm_sdk::sharing::sdk300
