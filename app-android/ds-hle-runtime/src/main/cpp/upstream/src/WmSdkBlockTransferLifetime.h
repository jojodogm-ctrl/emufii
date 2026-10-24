#pragma once
// Actual native activation/retirement identities. A token alone grants no
// buffer access, source immutability, packet, callback or delivery authority.
#include <array>
#include <cstdint>
#include <limits>
#include <optional>
namespace melonDS::wm_sdk::sharing {
class BlockTransferLifetime {
public:
    using U32=std::uint32_t;using U64=std::uint64_t;
    enum class State {Cold,Tracking,Closed,Unavailable};
    enum class Reason {None,EpochRegression,MissingInit,UnexpectedActive,InvalidMetadata,VersionOverflow,MaskMismatch,PreexistingQueue,MissingQueuedRequest};
    struct Peer {U32 destination=0,bitmap=0,blocks=0;};
    struct Activation {
        U32 context=0,id=0,bytes=0,segmentBytes=0,callback=0;
        unsigned mask=0,counter=0;
        std::array<Peer,16> peers{};
    };
    struct Token {U64 epoch=0,version=0;Activation activation{};explicit operator bool()const{return epoch&&version;}};
private:
    U64 epoch_=0,serial_=0;State state_=State::Cold;Reason reason_=Reason::None;
    std::optional<Token> current_{};unsigned remaining_=0;
    std::optional<Token> queued_{};
    static bool equal(const Activation& a,const Activation& b){
        if(a.context!=b.context||a.id!=b.id||a.bytes!=b.bytes||a.segmentBytes!=b.segmentBytes||a.callback!=b.callback||a.mask!=b.mask||a.counter!=b.counter)return false;
        for(unsigned i=0;i<16;++i)if(a.peers[i].destination!=b.peers[i].destination||a.peers[i].bitmap!=b.peers[i].bitmap||a.peers[i].blocks!=b.peers[i].blocks)return false;
        return true;
    }
public:
    State state()const{return state_;}Reason reason()const{return reason_;}
    U64 epoch()const{return epoch_;}unsigned remaining()const{return remaining_;}
    std::optional<Token> current()const{return state_==State::Tracking?current_:std::nullopt;}
    bool valid(const Token& token)const{return state_==State::Tracking&&current_&&token.epoch==current_->epoch&&token.version==current_->version&&equal(token.activation,current_->activation);}
    void unavailable(Reason reason){current_.reset();queued_.reset();remaining_=0;state_=State::Unavailable;reason_=reason;}
    void close(){current_.reset();queued_.reset();remaining_=0;state_=State::Closed;reason_=Reason::None;}
    bool begin(U64 epoch,bool nativeQueueEmpty=true){
        if(!epoch||epoch<=epoch_){unavailable(Reason::EpochRegression);return false;}
        epoch_=epoch;current_.reset();queued_.reset();remaining_=0;state_=State::Tracking;reason_=Reason::None;
        if(!nativeQueueEmpty){unavailable(Reason::PreexistingQueue);return false;}return true;
    }
    // Observe the actual queue copy while IRQs remain disabled. Returning from
    // the public API is too late: native activation may run after RestoreIrq.
    bool queued(const Activation& request){
        if(state_!=State::Tracking)return false;
        if(queued_||!request.context||(request.context&3)||!request.mask||request.mask>65535||!request.bytes||request.bytes>0x1000000){
            unavailable(Reason::InvalidMetadata);return false;
        }
        if(serial_==std::numeric_limits<U64>::max()){unavailable(Reason::VersionOverflow);return false;}
        queued_=Token{epoch_,++serial_,request};return true;
    }
    std::optional<Token> activate(const Activation& a){
        if(state_!=State::Tracking){if(state_!=State::Unavailable)unavailable(Reason::MissingInit);return std::nullopt;}
        if(current_){unavailable(Reason::UnexpectedActive);return std::nullopt;}
        if(!a.context||(a.context&3)||!a.mask||a.mask>65535||!a.counter||a.counter>255||
           !a.bytes||a.bytes>0x1000000||!a.segmentBytes||a.segmentBytes>500){
            unavailable(Reason::InvalidMetadata);return std::nullopt;
        }
        const auto blocks=(U64(a.bytes)+a.segmentBytes-1)/a.segmentBytes;
        for(unsigned i=0;i<16;++i)if(a.mask&(1u<<i)){
            const auto& p=a.peers[i];
            if(!p.destination||!p.bitmap||(p.bitmap&3)||p.blocks!=blocks){unavailable(Reason::InvalidMetadata);return std::nullopt;}
        }
        if(!queued_){unavailable(Reason::MissingQueuedRequest);return std::nullopt;}
        auto expected=queued_->activation;expected.segmentBytes=a.segmentBytes;expected.counter=a.counter;
        for(unsigned i=0;i<16;++i)expected.peers[i].blocks=a.peers[i].blocks;
        if(!equal(expected,a)){unavailable(Reason::InvalidMetadata);return std::nullopt;}
        current_=Token{epoch_,queued_->version,a};queued_.reset();remaining_=a.mask;return current_;
    }
    // Call only after the real native mask store and before the app callback.
    // A stale return from before End/Init cannot revoke a newer activation.
    bool retirePeer(const Token& token,unsigned peer,unsigned nativeRemaining){
        if(!valid(token))return false;
        if(peer>=16||!(remaining_&(1u<<peer))||nativeRemaining!=(remaining_&~(1u<<peer))){
            unavailable(Reason::MaskMismatch);return false;
        }
        remaining_=nativeRemaining;if(!remaining_)current_.reset();return true;
    }
};
} // namespace melonDS::wm_sdk::sharing
