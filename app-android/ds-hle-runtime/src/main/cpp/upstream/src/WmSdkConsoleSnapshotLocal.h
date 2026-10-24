// Owner-thread parking and LOCAL capture staging. Deliberately no live restore
// commit: the core loader's save-file side effects and network fence are not
// transactional. Do not use these results as room FROZEN/LOADED acknowledgments.
#pragma once
#include "WmSdkConsoleSnapshot.h"
#include "WmPokemonSnapshot.h"
#include <set>
#include <thread>

namespace melonDS::wm_sdk::snapshot {
class OwnerPark {
    std::thread::id owner{};
    bool bound=false,executing=false,parked=false,retired=false;
public:
    // A new disconnected replacement has never executed a frame. Bind it held
    // on its actual constructing owner; this cannot rebind an existing owner.
    Status bindFreshParked() {
        if(bound||executing||parked)return Status::NotQuiescent;
        owner=std::this_thread::get_id();bound=true;parked=true;return Status::Ok;
    }
    bool onOwner() const { return bound&&owner==std::this_thread::get_id(); }
    bool enterFrame() {
        // Existing frontends may hand an unparked console to another thread
        // between frames. Observe the actual executor, do not pin construction
        // or boot threads forever. Concurrent RunFrame was never supported.
        if(executing||parked||retired)return false;
        owner=std::this_thread::get_id();bound=true;
        executing=true;return true;
    }
    void leaveFrame(){if(onOwner())executing=false;}
    Status park() {
        if(!onOwner()||executing)return Status::NotQuiescent;
        parked=true;return Status::Ok;
    }
    bool isParked() const{return onOwner()&&parked&&!executing;}
    bool betweenFrames() const{return onOwner()&&!parked&&!executing&&!retired;}
    bool isRetired() const{return retired;}
    Status retire(){if(!isParked()||retired)return Status::NotQuiescent;retired=true;return Status::Ok;}
    Status release(){if(!isParked()||retired)return Status::NotQuiescent;parked=false;return Status::Ok;}
    struct FrameExit { OwnerPark& gate;~FrameExit(){gate.leaveFrame();} };
};
// Authority is an explicit component, never disguised as SDK or party state.
// Kept under the portable 8 MiB SDK-part budget with its other components.
inline constexpr std::size_t MaxAuthorityLocalBytes=7*1024*1024;
struct LocalImage { Bytes core, sdk, mpds, metadata, authority; };
struct LocalMetadata {
    std::uint32_t console=0,frame=0,keyInput=0,ledger=0,partyNetworkStatus=0;
    std::uint64_t clock=0;
    bool party=false;
    std::uint8_t authorityKind=0; // 0 none, 1 MKDS authority, 2 NSMB rows
    std::uint32_t authorityLength=0,authorityChecksum=0;
    std::array<std::uint32_t,3> lengths{},checksums{};
};
struct LocalStage {
    // Owned decoded service state only. core is retained without executing any
    // loader. Its framing has been checked, NOT every subsystem's semantics.
    ConsoleSdkState sdk;
    std::optional<PokemonOwner> pokemon;
    ConsoleMpdsState mpds;
    LocalMetadata metadata;
    Bytes core;
    // Validated against the source runtime before loading the disposable core.
    // Retained for later binding to fresh routes; never silently discarded.
    Bytes authority;
};
inline constexpr Kind ConsoleLocalKind=static_cast<Kind>(0x102);
inline constexpr Kind ConsoleAuthorityLocalKind=static_cast<Kind>(0x103);
template<class A>void localFields(A& a,LocalMetadata& m) {
    a(m.console);a(m.frame);a(m.keyInput);a(m.ledger);a(m.partyNetworkStatus);a(m.clock);a(m.party);
    a(m.lengths);a(m.checksums);
    require(m.console==0&&m.partyNetworkStatus<=3&&(m.ledger&~0x007fffffu)==0);
}
inline void bindImage(LocalMetadata& m,const LocalImage& i) {
    std::size_t n=0;for(const auto* b:{&i.core,&i.sdk,&i.mpds}){
        require(b->size()<=(n==0?MaxCoreBytes:MaxConsoleStateBytes));
        m.lengths[n]=static_cast<std::uint32_t>(b->size());m.checksums[n]=crc32(b->data(),b->size());++n;
    }
    require(i.authority.size()<=MaxAuthorityLocalBytes,Status::ResourceLimit);
    require(m.authorityKind<=2 && (bool(m.authorityKind)==!i.authority.empty()) &&
        !(m.party&&m.authorityKind),Status::ModeMismatch);
    m.authorityLength=static_cast<std::uint32_t>(i.authority.size());
    m.authorityChecksum=crc32(i.authority.data(),i.authority.size());
}
inline Status encodeLocalMetadata(LocalMetadata metadata,const LocalImage& image,std::uint64_t incarnation,Bytes& out) {
    return attempt([&]{bindImage(metadata,image);
        require(!image.core.empty()&&!image.sdk.empty()&&(metadata.party==!image.mpds.empty()));
        Writer w;localFields(w,metadata);
        if(metadata.authorityKind){w(metadata.authorityKind);w(metadata.authorityLength);w(metadata.authorityChecksum);}
        auto b=seal(w,metadata.authorityKind?ConsoleAuthorityLocalKind:ConsoleLocalKind,incarnation);out.swap(b);});
}
inline Status decodeLocalMetadata(const LocalImage& image,std::uint64_t incarnation,LocalMetadata& out) {
    return attempt([&]{require(image.metadata.size()<=256);Reader r(image.metadata);
        const auto kind=image.authority.empty()?ConsoleLocalKind:ConsoleAuthorityLocalKind;
        require(unseal(r,kind)==incarnation,Status::ModeMismatch);
        LocalMetadata decoded;localFields(r,decoded);
        if(kind==ConsoleAuthorityLocalKind){r(decoded.authorityKind);r(decoded.authorityLength);r(decoded.authorityChecksum);require(decoded.authorityKind);}
        r.end();auto actual=decoded;bindImage(actual,image);
        require(actual.lengths==decoded.lengths&&actual.checksums==decoded.checksums);
        if(kind==ConsoleAuthorityLocalKind)require(actual.authorityLength==decoded.authorityLength&&actual.authorityChecksum==decoded.authorityChecksum);
        require(!image.core.empty()&&!image.sdk.empty()&&(decoded.party==!image.mpds.empty()));
        out=decoded;});
}
// Pure structural validator. Never calls Savestate's mutating loader. A checked
// directory alone cannot establish valid SRAM lengths/scheduler IDs/etc.
inline Status validateCoreFraming(const Bytes& core,std::uint16_t major,std::uint16_t minor) {
    return attempt([&]{require(core.size()>=32&&core.size()<=MaxCoreBytes);
        require(std::equal(core.begin(),core.begin()+4,"MELN"));
        require(wmguest::read16(core.data()+4)==major&&wmguest::read16(core.data()+6)==minor,Status::WrongVersion);
        require(wmguest::read32(core.data()+8)==core.size());
        require(wmguest::read32(core.data()+12)==0);
        std::size_t pos=16;std::set<std::string> names;
        while(pos<core.size()){
            require(core.size()-pos>=16&&names.size()<128);
            const auto* h=core.data()+pos;const auto length=wmguest::read32(h+4);
            require(length>=16&&length<=core.size()-pos);
            require(wmguest::read32(h+8)==0&&wmguest::read32(h+12)==0);
            require(names.emplace(reinterpret_cast<const char*>(h),4).second);
            if(std::equal(h,h+4,"NDSG"))require(length>=20&&wmguest::read32(h+16)==0);
            pos+=length;
        }
        for(const char* name:{"NDSG","ARM9","ARM7","WIFI","GPUG","NDCS"})require(names.count(name)==1);
    });
}
} // namespace melonDS::wm_sdk::snapshot
