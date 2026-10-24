// Frame-local dictionary coding, preserving EVERY call and timestamp exactly.
// Compression changes storage only: no merged calls, skipped polls or clock writes.
#ifndef MELONDS_WM_SDK_TAPE_FRAMES_H
#define MELONDS_WM_SDK_TAPE_FRAMES_H
#include "WmSdkTape.h"
#include <map>
#include <limits>

namespace melonDS::wm_sdk::tape {
constexpr std::size_t MaxFrameBytes=4*1024*1024, MaxFrameCalls=16384, MaxDictionary=4096;
constexpr std::uint32_t FrameMagic=0x31465357; // WSF1; not the old radio NTP1 format
struct Frame {
    std::uint32_t boot=0, frame=0;
    std::uint64_t firstOrdinal=0, startTicks=0, endTicks=0;
    std::vector<Record> records;
};
inline std::uint64_t checksum(const std::uint8_t* p,std::size_t n) {
    std::uint64_t h=14695981039346656037ULL;
    for(std::size_t i=0;i<n;++i){h^=p[i];h*=1099511628211ULL;}return h;
}
inline void variable(Writer& w,std::uint64_t v) {
    do {auto b=std::uint8_t(v&127);v>>=7;w.u8(std::uint8_t(b|(v?128:0)));}while(v);
}
inline Bytes encodeFrame(const Frame& frame) {
    if(frame.records.size()>MaxFrameCalls || frame.endTicks<frame.startTicks ||
       frame.firstOrdinal>UINT64_MAX-frame.records.size())throw Fault("invalid frame bounds");
    std::map<Bytes,std::uint16_t> lookup;
    std::vector<Bytes> dictionary;
    Writer schedule;
    std::uint64_t ticks=frame.startTicks,ordinal=frame.firstOrdinal;
    for(const auto& record:frame.records){
        const auto op=static_cast<std::uint8_t>(record.op);
        if(!op || op>static_cast<unsigned>(Op::RetainReceipts) || record.stamp.boot!=frame.boot || record.stamp.frame!=frame.frame ||
           record.ordinal!=ordinal++ || record.stamp.ticks<ticks || record.stamp.ticks>frame.endTicks ||
           record.arguments.size()>MaxValueBytes || record.result.size()>MaxValueBytes)
            throw Fault("invalid record in frame");
        Writer key;key.u8(op);key.u16(static_cast<std::uint16_t>(record.arguments.size()));
        key.u16(static_cast<std::uint16_t>(record.result.size()));
        key.bytes.insert(key.bytes.end(),record.arguments.begin(),record.arguments.end());
        key.bytes.insert(key.bytes.end(),record.result.begin(),record.result.end());
        auto found=lookup.find(key.bytes);std::uint16_t index;
        if(found==lookup.end()){
            if(dictionary.size()==MaxDictionary)throw Fault("frame dictionary capacity exceeded");
            index=static_cast<std::uint16_t>(dictionary.size());
            lookup.emplace(key.bytes,index);dictionary.push_back(std::move(key.bytes));
        }else index=found->second;
        variable(schedule,index);variable(schedule,record.stamp.ticks-ticks);ticks=record.stamp.ticks;
    }
    Writer w;w.u32(FrameMagic);w.u32(0);w.u32(frame.boot);w.u32(frame.frame);
    w.u64(frame.firstOrdinal);w.u64(frame.startTicks);w.u64(frame.endTicks);
    w.u32(static_cast<std::uint32_t>(frame.records.size()));w.u16(static_cast<std::uint16_t>(dictionary.size()));
    for(const auto& d:dictionary)w.bytes.insert(w.bytes.end(),d.begin(),d.end());
    w.bytes.insert(w.bytes.end(),schedule.bytes.begin(),schedule.bytes.end());
    const std::size_t length=w.bytes.size()+8;
    if(length>MaxFrameBytes)throw Fault("frame byte capacity exceeded");
    for(unsigned i=0;i<4;++i)w.bytes[4+i]=std::uint8_t(length>>(8*i));
    w.u64(checksum(w.bytes.data(),w.bytes.size()));return w.bytes;
}

// A frame is decoded and bounds/checksum-validated BEFORE guest execution.
// Missing/truncated frames are errors, not a successful shortened replay.
inline Frame decodeFrame(const Bytes& bytes) {
    if(bytes.size()<54 || bytes.size()>MaxFrameBytes)throw Fault("invalid frame size");
    std::size_t pos=0;
    auto number=[&](unsigned width){
        if(width>8 || width>bytes.size()-pos)throw Fault("truncated frame");
        std::uint64_t n=0;for(unsigned i=0;i<width;++i)n|=std::uint64_t(bytes[pos++])<<(8*i);return n;
    };
    auto var=[&](){
        std::uint64_t n=0;
        for(unsigned i=0;i<10;++i){auto b=number(1);
            if(i==9 && b>1)throw Fault("varint overflow");
            n|=(b&127)<<(i*7);
            if(!(b&128)){if(i && !b)throw Fault("noncanonical varint");return n;}}
        throw Fault("unterminated varint");
    };
    if(number(4)!=FrameMagic || number(4)!=bytes.size())throw Fault("unsupported frame header");
    const auto end=bytes.size()-8;
    std::uint64_t expected=0;for(unsigned i=0;i<8;++i)expected|=std::uint64_t(bytes[end+i])<<(8*i);
    if(checksum(bytes.data(),end)!=expected)throw Fault("frame checksum mismatch");
    Frame frame;frame.boot=static_cast<std::uint32_t>(number(4));frame.frame=static_cast<std::uint32_t>(number(4));
    frame.firstOrdinal=number(8);frame.startTicks=number(8);frame.endTicks=number(8);
    const auto calls=number(4),entries=number(2);
    if(calls>MaxFrameCalls || entries>MaxDictionary || entries>calls || frame.endTicks<frame.startTicks ||
       frame.firstOrdinal>UINT64_MAX-calls)throw Fault("invalid decoded frame bounds");
    std::vector<Record> dictionary;dictionary.reserve(static_cast<std::size_t>(entries));
    for(std::uint64_t i=0;i<entries;++i){
        Record r;const auto op=number(1),a=number(2),b=number(2);
        if(!op || op>static_cast<unsigned>(Op::RetainReceipts) || a>MaxValueBytes || b>MaxValueBytes || a+b>bytes.size()-pos)
            throw Fault("invalid dictionary entry");
        r.op=static_cast<Op>(op);
        r.arguments.assign(bytes.begin()+static_cast<std::ptrdiff_t>(pos),bytes.begin()+static_cast<std::ptrdiff_t>(pos+a));pos+=static_cast<std::size_t>(a);
        r.result.assign(bytes.begin()+static_cast<std::ptrdiff_t>(pos),bytes.begin()+static_cast<std::ptrdiff_t>(pos+b));pos+=static_cast<std::size_t>(b);
        dictionary.push_back(std::move(r));
    }
    frame.records.reserve(static_cast<std::size_t>(calls));std::uint64_t ticks=frame.startTicks;
    for(std::uint64_t i=0;i<calls;++i){
        const auto index=var(),delta=var();
        if(index>=entries || delta>frame.endTicks-ticks)throw Fault("invalid call schedule");
        ticks+=delta;Record r=dictionary[static_cast<std::size_t>(index)];
        r.ordinal=frame.firstOrdinal+i;r.stamp={frame.boot,frame.frame,ticks};frame.records.push_back(std::move(r));
    }
    if(pos!=end)throw Fault("trailing frame bytes");
    return frame;
}
} // namespace melonDS::wm_sdk::tape
#endif
