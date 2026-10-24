#pragma once
// Shared DataSharing storage/row bookkeeping, independent of title and gameplay.
// A family adapter must separately prove the loaded ABI, own the memory/callback
// leases and admit this geometry before use. Geometry alone grants no SDK ABI.
#include <array>
#include <cstddef>
#include <cstdint>
#include <stdexcept>

namespace melonDS::wm_sdk::sharing {
inline constexpr unsigned MaxAids=16,MaxSlots=256,MaxDatasetBytes=512;
inline constexpr std::size_t MaxObjectBytes=1024*1024;
struct MetadataFields {
    unsigned writeIndex,rowBytes,datasetBytes,aidMask,mode,expectedMode;
};
struct Layout {
    std::uint32_t base,objectBytes,metadata,sequence;
    unsigned slots,depth,stride,datasetBytes,rowBytes;
    std::uint16_t aidMask;
    unsigned childScratch;
    std::array<unsigned,MaxAids> rowOffsets;
    MetadataFields fields;
    constexpr bool valid() const {
        if(!base||!objectBytes||objectBytes>MaxObjectBytes||
           std::uint64_t(base)+objectBytes>0x100000000ull||
           slots<2||slots>MaxSlots||(slots&(slots-1))||!depth||depth>slots/2||
           stride<4||(stride&(stride-1))||datasetBytes<4||datasetBytes>MaxDatasetBytes||
           datasetBytes>stride||!rowBytes||rowBytes>datasetBytes-4||!aidMask||
           childScratch>stride||rowBytes>stride-childScratch||
           std::uint64_t(slots-1)*stride+datasetBytes>sequence||
           std::uint64_t(slots-1)*stride+childScratch+rowBytes>sequence||
           std::uint64_t(sequence)+slots*2>metadata||
           std::uint64_t(metadata)+32>objectBytes||fields.expectedMode>1)return false;
        const unsigned offsets[]={fields.writeIndex,fields.rowBytes,fields.datasetBytes,fields.aidMask,fields.mode};
        for(unsigned i=0;i<5;++i){
            if((offsets[i]&1)||offsets[i]>30)return false;
            for(unsigned j=0;j<i;++j)if(offsets[i]==offsets[j])return false;
        }
        for(unsigned aid=0;aid<MaxAids;++aid){
            if(!(aidMask&(1u<<aid))){if(rowOffsets[aid])return false;continue;}
            const auto offset=rowOffsets[aid];
            if(offset<4||offset>datasetBytes||rowBytes>datasetBytes-offset)return false;
            for(unsigned other=0;other<aid;++other)if(aidMask&(1u<<other)){
                const auto previous=rowOffsets[other];
                if(offset<previous+rowBytes&&previous<offset+rowBytes)return false;
            }
        }
        return true;
    }
    constexpr bool contains(std::uint32_t address,unsigned length) const {
        return length&&address>=base&&address-base<objectBytes&&length<=objectBytes-(address-base);
    }
};
// Checked refers only to memory geometry, never code identity or gameplay.
// Supports both constexpr descriptors and actual runtime SDK Start arguments.
class CheckedLayout {
    Layout layout;
public:
    constexpr explicit CheckedLayout(Layout value):layout(value) {
        if(!layout.valid())throw std::invalid_argument("invalid SDK DataSharing window geometry");
    }
    constexpr const Layout& get() const{return layout;}
};

template<std::uint32_t Base,std::size_t Capacity> class Storage {
    static_assert(Capacity&&Capacity<=MaxObjectBytes&&std::uint64_t(Base)+Capacity<=0x100000000ull);
    std::array<std::uint8_t,Capacity> bytes{};
    static std::size_t offset(std::uint32_t address,unsigned length) {
        if(!contains(address,length))throw std::out_of_range("SDK DataSharing allocation");
        return address-Base;
    }
public:
    static constexpr bool contains(std::uint32_t address,unsigned length=1) {
        return length&&address>=Base&&address-Base<Capacity&&length<=Capacity-(address-Base);
    }
    std::uint8_t read8(std::uint32_t address)const{return bytes[offset(address,1)];}
    std::uint16_t read16(std::uint32_t address)const {
        const auto i=offset(address,2);return std::uint16_t(bytes[i])|(std::uint16_t(bytes[i+1])<<8);
    }
    std::uint32_t read32(std::uint32_t address)const {
        const auto i=offset(address,4);return std::uint32_t(bytes[i])|(std::uint32_t(bytes[i+1])<<8)|
            (std::uint32_t(bytes[i+2])<<16)|(std::uint32_t(bytes[i+3])<<24);
    }
    void write8(std::uint32_t address,std::uint8_t value){bytes[offset(address,1)]=value;}
    void write16(std::uint32_t address,std::uint16_t value) {
        const auto i=offset(address,2);bytes[i]=std::uint8_t(value);bytes[i+1]=std::uint8_t(value>>8);
    }
    void write32(std::uint32_t address,std::uint32_t value) {
        const auto i=offset(address,4);for(unsigned b=0;b<4;++b)bytes[i+b]=std::uint8_t(value>>(8*b));
    }
    const auto& raw()const{return bytes;}
};

inline bool ownsSendPayload(const CheckedLayout& checked,unsigned aid,std::uint32_t address,unsigned length) {
    const auto& layout=checked.get();
    if(aid>=MaxAids||!(layout.aidMask&(1u<<aid))||!layout.contains(address,length))return false;
    const auto offset=address-layout.base;
    if(offset/layout.stride>=layout.slots)return false;
    return aid==0 ? length==layout.datasetBytes&&offset%layout.stride==0
                  : length==layout.rowBytes&&offset%layout.stride==layout.childScratch;
}

template<class Read16,class Write16,class Read8,class Write8>
bool store(const CheckedLayout& checked,unsigned aid,std::uint32_t payload,
           Read16&& r16,Write16&& w16,Read8&& r8,Write8&& w8) {
    const auto& layout=checked.get();const auto meta=layout.base+layout.metadata;const auto& fields=layout.fields;
    if(aid>=MaxAids||!(layout.aidMask&(1u<<aid))||r16(meta+fields.rowBytes)!=layout.rowBytes||
       r16(meta+fields.datasetBytes)!=layout.datasetBytes||r16(meta+fields.aidMask)!=layout.aidMask||
       r16(meta+fields.mode)!=fields.expectedMode)throw std::runtime_error("SDK DataSharing ABI changed");
    const auto current=r16(meta+fields.writeIndex);
    if(current>=layout.slots)throw std::runtime_error("SDK DataSharing write index");
    const auto bit=std::uint16_t(1u<<aid);
    for(unsigned look=0;look<layout.depth;++look){
        const auto slot=layout.base+((current+look)&(layout.slots-1))*layout.stride;
        const auto awaiting=r16(slot);if(!(awaiting&bit))continue;
        const auto row=slot+layout.rowOffsets[aid];
        // A null record is only the caller's explicit native SDK initialization
        // operation. This function neither authors nor predicts future records.
        for(unsigned i=0;i<layout.rowBytes;++i)w8(row+i,payload?r8(payload+i):0);
        w16(slot,awaiting&~bit);w16(slot+2,r16(slot+2)|bit);return true;
    }
    return false; // No free slot: no mutation and no successful receipt.
}
}
