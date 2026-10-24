#pragma once
// Emulator extension envelope, never a legacy SDK packet. Transport/session
// provenance comes from the actual delivery token, not a payload-declared AID.
#include <algorithm>
#include <array>
#include <cstdint>
#include <optional>
namespace melonDS::wm_sdk::sharing::block_wire {
inline constexpr unsigned Header=64,FrameBytes=512,PartBytes=FrameBytes-Header,MaxSegment=500,Depth=16;
inline constexpr std::uint32_t Magic=0x31574257; // WBW1
enum class Kind : unsigned {Open=1,Offer=2,Data=3,Ack=4,Cancel=5,Refuse=6,Withdraw=7};
enum class Reason : unsigned {None,NativeClosed,SourceRevoked,Unsupported,InvalidFrame,NoCapacity,TransportClosed};
struct Nonce {std::uint64_t epoch=0,version=0;bool valid()const{return epoch&&version;}bool empty()const{return !epoch&&!version;}};
inline bool operator==(Nonce a,Nonce b){return a.epoch==b.epoch&&a.version==b.version;}
inline bool operator!=(Nonce a,Nonce b){return !(a==b);}
struct Flow {
    Nonce destination{},source{};std::uint32_t id=0,total=0;unsigned segment=0,counter=0;
};
inline bool sameRequest(const Flow& a,const Flow& b){return a.destination==b.destination&&a.id==b.id&&a.total==b.total&&a.segment==b.segment&&a.counter==b.counter;}
inline unsigned blocks(const Flow& f){return f.segment?unsigned((std::uint64_t(f.total)+f.segment-1)/f.segment):0;}
inline unsigned chunkBytes(const Flow& f,std::uint32_t index){return index<blocks(f)?unsigned(std::min(std::uint64_t(f.segment),std::uint64_t(f.total)-std::uint64_t(index)*f.segment)):0;}
struct Frame {
    Kind kind=Kind::Open;Flow flow{};std::uint32_t index=0;unsigned offset=0,length=0;Reason reason=Reason::None;
    std::array<std::uint8_t,PartBytes> body{};
};
struct Encoded {std::array<std::uint8_t,FrameBytes> bytes{};unsigned length=0;};
inline bool valid(const Frame& f){
    const auto& q=f.flow;
    if(!q.destination.valid()||!q.total||q.total>0x1000000||!q.segment||q.segment>MaxSegment||!q.counter||q.counter>255||
       unsigned(f.kind)<1||unsigned(f.kind)>7||unsigned(f.reason)>unsigned(Reason::TransportClosed))return false;
    if(f.kind==Kind::Open)return q.source.empty()&&!f.index&&!f.offset&&!f.length&&f.reason==Reason::None;
    if(f.kind==Kind::Withdraw)return q.source.valid()&&!f.index&&!f.offset&&!f.length&&f.reason!=Reason::None;
    if(f.kind==Kind::Cancel||f.kind==Kind::Refuse)return (q.source.empty()||q.source.valid())&&!f.index&&!f.offset&&!f.length&&f.reason!=Reason::None;
    if(!q.source.valid()||f.reason!=Reason::None)return false;
    if(f.kind==Kind::Offer)return !f.index&&!f.offset&&!f.length;
    if(f.index>=blocks(q))return false;
    if(f.kind==Kind::Ack)return !f.offset&&!f.length;
    const auto n=chunkBytes(q,f.index);
    return (f.offset==0||f.offset==PartBytes)&&f.offset<n&&f.length==std::min(PartBytes,n-f.offset);
}
inline void put(std::uint8_t* p,std::uint64_t n,unsigned bytes){for(unsigned i=0;i<bytes;++i)p[i]=std::uint8_t(n>>(i*8));}
inline std::uint64_t get(const std::uint8_t* p,unsigned bytes){std::uint64_t n=0;for(unsigned i=0;i<bytes;++i)n|=std::uint64_t(p[i])<<(i*8);return n;}
inline std::optional<Encoded> encode(const Frame& f){
    if(!valid(f))return std::nullopt;Encoded out;auto* p=out.bytes.data();
    put(p,Magic,4);p[4]=1;p[5]=std::uint8_t(f.kind);p[6]=std::uint8_t(f.flow.counter);
    put(p+8,f.flow.destination.epoch,8);put(p+16,f.flow.destination.version,8);put(p+24,f.flow.source.epoch,8);put(p+32,f.flow.source.version,8);
    put(p+40,f.flow.id,4);put(p+44,f.flow.total,4);put(p+48,f.flow.segment,2);put(p+50,f.length,2);put(p+52,f.index,4);put(p+56,f.offset,2);put(p+58,unsigned(f.reason),2);
    std::copy_n(f.body.begin(),f.length,p+Header);out.length=Header+f.length;return out;
}
inline std::optional<Frame> decode(const std::uint8_t* p,unsigned length){
    if(!p||length<Header||length>FrameBytes||get(p,4)!=Magic||p[4]!=1||p[7]||get(p+60,4))return std::nullopt;
    Frame f;f.kind=Kind(p[5]);f.flow.counter=p[6];f.flow.destination={get(p+8,8),get(p+16,8)};f.flow.source={get(p+24,8),get(p+32,8)};
    f.flow.id=std::uint32_t(get(p+40,4));f.flow.total=std::uint32_t(get(p+44,4));f.flow.segment=unsigned(get(p+48,2));f.length=unsigned(get(p+50,2));f.index=std::uint32_t(get(p+52,4));f.offset=unsigned(get(p+56,2));f.reason=Reason(get(p+58,2));
    if(f.length>PartBytes||length!=Header+f.length||!valid(f))return std::nullopt;
    std::copy_n(p+Header,f.length,f.body.begin());return f;
}
} // namespace melonDS::wm_sdk::sharing::block_wire
