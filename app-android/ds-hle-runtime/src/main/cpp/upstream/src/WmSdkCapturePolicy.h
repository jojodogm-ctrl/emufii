#pragma once
// Read-only capture selection, never runtime ABI/title admission. The optional
// HGSS profile reserves a separate StartMP budget for the observed child AID;
// Union Room's earlier parent cycles must not consume all four child samples.
#include <cstdint>
namespace melonDS::wm_sdk {
inline int captureBucket(uint16_t api,uint16_t port,bool hgss,bool child,bool familyNeutral=false) {
    // Observation only: capture the bounded WM API range and port producers,
    // including 0F/23 and every receive port. No SDK status layout/AID reads.
    // Existing per-direction/per-generation sample limits still apply.
    if(familyNeutral)return api<=0x2d || api==0x81 || api==0x82 ? int(api) : -1;
    if(hgss) {
        if(api!=0 && api!=1 && api!=2 && api!=7 && api!=8 && api!=9
           && api!=12 && api!=13 && api!=14 && api!=15 && api!=16
           && api!=0x18 && api!=0x1e && api!=0x81 && api!=0x82)return -1;
        if(api==0x82 && port!=14)return -1;
        return api==14 && child ? 256 : api;
    }
    if(api!=0 && api!=3 && api!=5 && api!=7 && api!=8 && api!=9 && api!=12
       && api!=13 && api!=14 && api!=16 && api!=0x23 && api!=0x81 && api!=0x82)return -1;
    return api==0x82 && port!=1 ? -1 : api;
}
}
