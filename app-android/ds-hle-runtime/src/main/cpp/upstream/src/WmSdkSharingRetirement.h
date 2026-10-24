#pragma once
#include "WmSdkDataSharingWindow.h"
namespace melonDS::wm_sdk::sharing {
// A real SDK member-departure callback removes an obligation, never authors a
// row or marks it received. Native send/callback retirement remains separate.
template<class Read16,class Write16>
unsigned retireAwaitedAid(const CheckedLayout& checked,unsigned aid,Read16 read,Write16 write){
    const auto& l=checked.get();const auto meta=l.base+l.metadata;const auto& f=l.fields;
    if(aid>=MaxAids||read(meta+f.rowBytes)!=l.rowBytes||read(meta+f.datasetBytes)!=l.datasetBytes||
       read(meta+f.aidMask)!=l.aidMask||read(meta+f.mode)!=f.expectedMode)
        throw std::runtime_error("SDK departure geometry changed");
    const auto current=read(meta+f.writeIndex);if(current>=l.slots)throw std::runtime_error("SDK departure write index");
    const auto bit=std::uint16_t(1u<<aid);unsigned changed=0;
    for(unsigned i=0;i<l.depth;++i){const auto slot=l.base+((current+i)&(l.slots-1))*l.stride;
        const auto awaited=read(slot);if(awaited&bit){write(slot,std::uint16_t(awaited&~bit));++changed;}
    }
    return changed;
}
}
