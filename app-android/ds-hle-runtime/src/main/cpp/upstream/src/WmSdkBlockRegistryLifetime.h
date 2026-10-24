#pragma once
// Identity/lifetime bookkeeping only. A token does not grant immutable contents,
// source reads, packet construction, a callback or delivery acknowledgement.
#include <array>
#include <cstdint>
#include <limits>
#include <optional>
namespace melonDS::wm_sdk::sharing {
class BlockRegistryLifetime {
public:
    using U64=std::uint64_t;using U32=std::uint32_t;
    enum class State {Cold,Tracking,Closed,Unavailable};
    enum class Reason {None,MissingInit,EpochRegression,DuplicateDescriptor,Capacity,VersionOverflow,UnknownDescriptor,InvalidMetadata};
    struct Source {
        U32 descriptor=0,id=0,bytes=0,pointer=0;
        unsigned field30=0,mode=0;
    };
    struct Token {U64 epoch=0,version=0;Source source{};explicit operator bool()const{return epoch&&version;}};
private:
    struct Entry {bool used=false;Token token{};};
    std::array<Entry,128> entries_{};
    U64 epoch_=0,serial_=0;
    State state_=State::Cold;Reason reason_=Reason::None;
    static bool equal(const Source& a,const Source& z){return a.descriptor==z.descriptor&&a.id==z.id&&a.bytes==z.bytes&&a.pointer==z.pointer&&a.field30==z.field30&&a.mode==z.mode;}
public:
    State state()const{return state_;}Reason reason()const{return reason_;}
    U64 epoch()const{return epoch_;}
    unsigned active()const{unsigned n=0;for(const auto& x:entries_)n+=x.used;return n;}
    void unavailable(Reason why){entries_={};state_=State::Unavailable;reason_=why;}
    bool begin(U64 observedEpoch){
        if(!observedEpoch||observedEpoch<=epoch_){unavailable(Reason::EpochRegression);return false;}
        epoch_=observedEpoch;entries_={};state_=State::Tracking;reason_=Reason::None;return true;
    }
    void close(){entries_={};state_=State::Closed;reason_=Reason::None;}
    std::optional<Token> current(U32 descriptor)const{
        if(state_!=State::Tracking)return std::nullopt;
        for(const auto& x:entries_)if(x.used&&x.token.source.descriptor==descriptor)return x.token;
        return std::nullopt;
    }
    bool valid(const Token& token)const{
        const auto found=current(token.source.descriptor);
        return found&&token.epoch==found->epoch&&token.version==found->version&&equal(token.source,found->source);
    }
    std::optional<Token> registered(Source source){
        if(state_!=State::Tracking){if(state_!=State::Unavailable)unavailable(Reason::MissingInit);return std::nullopt;}
        if(!source.descriptor||source.mode>1||source.field30>65535||(!source.mode&&!source.pointer)){
            unavailable(Reason::InvalidMetadata);return std::nullopt;
        }
        // Mode1's unused pointer cell may contain old bytes. It never becomes a
        // readable static source by being observed here.
        if(source.mode==1)source.pointer=0;
        if(current(source.descriptor)){unavailable(Reason::DuplicateDescriptor);return std::nullopt;}
        if(serial_==std::numeric_limits<U64>::max()){unavailable(Reason::VersionOverflow);return std::nullopt;}
        for(auto& x:entries_)if(!x.used){x={true,{epoch_,++serial_,source}};return x.token;}
        unavailable(Reason::Capacity);return std::nullopt;
    }
    std::optional<Token> removed(U32 actualDescriptor){
        if(state_!=State::Tracking)return std::nullopt;
        for(auto& x:entries_)if(x.used&&x.token.source.descriptor==actualDescriptor){
            const auto token=x.token;x={};return token;
        }
        unavailable(Reason::UnknownDescriptor);return std::nullopt;
    }
};
} // namespace melonDS::wm_sdk::sharing
