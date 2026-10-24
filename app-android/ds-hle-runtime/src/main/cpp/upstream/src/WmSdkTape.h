// Strict semantic transport recording/replay. Independent of NDS and IO.
// Every operation, including negative polls and rejected sends, is an exchange.
// The owner supplies the ACTUAL clock; replay never changes it or constructs peers.
#ifndef MELONDS_WM_SDK_TAPE_H
#define MELONDS_WM_SDK_TAPE_H
#include "WmSdkTransport.h"
#include <functional>
#include <exception>
#include <stdexcept>
#include <string>
#include <vector>
#include <chrono>

namespace melonDS::wm_sdk::tape {
using Bytes = std::vector<std::uint8_t>;
using Bus = Transport::Bus;
struct Fault : std::runtime_error { using std::runtime_error::runtime_error; };
struct Stamp {
    std::uint32_t boot = 0, frame = 0;
    std::uint64_t ticks = 0;
    bool operator==(const Stamp& b) const { return boot==b.boot && frame==b.frame && ticks==b.ticks; }
};
enum class Op : std::uint8_t {
    Open=1, Close, Advertise, Scan, Connect, Disconnect, Pop, StartMp,
    StopMp, Send, Take, Acknowledge, Completion, StopMpWithReceipts, RetainReceipts
};
struct Record {
    std::uint64_t ordinal = 0;
    Stamp stamp{};
    Op op = Op::Open;
    Bytes arguments, result;
};
constexpr std::size_t MaxValueBytes = melonDS::wm_sdk::wire_window::Credits>32?2048:1024;

// Explicit little-endian fields. Never serialize C++ padding, enums, bools,
// pointers, Payload internals, or struct layouts directly.
class Writer {
public:
    Bytes bytes;
    void number(std::uint64_t n, unsigned width) {
        // Grow once for the whole field, not once per byte (u64 used to
        // allocate 1, 2, 4 and 8 bytes on every otherwise empty poll).
        const auto pos=bytes.size();bytes.resize(pos+width);
        for (unsigned i=0;i<width;++i) { bytes[pos+i]=std::uint8_t(n); n>>=8; }
    }
    void u8(std::uint8_t n) { number(n,1); }
    void u16(std::uint16_t n) { number(n,2); }
    void u32(std::uint32_t n) { number(n,4); }
    void u64(std::uint64_t n) { number(n,8); }
    void boolean(bool v) { u8(v ? 1 : 0); }
    template<std::size_t N> void array(const std::array<std::uint8_t,N>& a) {
        bytes.insert(bytes.end(),a.begin(),a.end());
    }
    void status(Bus::Status s) { u8(static_cast<std::uint8_t>(s)); }
    void session(Bus::Session s) { u8(s.aid); u64(s.epoch); }
    void message(const Bus::Message& m) {
        session(m.source); u64(m.send_id); u16(m.sequence); u8(m.port);
        u16(m.targets); u16(m.wire_sequence);
    }
    void token(const Bus::DeliveryToken& t) { session(t.source); session(t.recipient); u64(t.send_id); }
    void event(const Event& e) {
        u8(static_cast<std::uint8_t>(e.kind)); u64(e.peer); boolean(e.toParent);
        u16(e.aid); u16(e.reason); array(e.mac); array(e.data); array(e.bss);
    }
};
class Reader {
    const Bytes& bytes;
    std::size_t pos=0;
    bool* poisoned;
    int exceptions=std::uncaught_exceptions();
public:
    explicit Reader(const Bytes& b, bool* failed=nullptr) : bytes(b),poisoned(failed) {
        if (b.size()>MaxValueBytes) {
            if(poisoned)*poisoned=true;
            throw Fault("oversized semantic value");
        }
    }
    ~Reader() { if(poisoned && std::uncaught_exceptions()>exceptions)*poisoned=true; }
    std::uint64_t number(unsigned width) {
        if (width>8 || width>bytes.size()-pos) throw Fault("truncated semantic value");
        std::uint64_t n=0;
        for (unsigned i=0;i<width;++i) n|=std::uint64_t(bytes[pos++])<<(8*i);
        return n;
    }
    std::uint8_t u8() { return std::uint8_t(number(1)); }
    std::uint16_t u16() { return std::uint16_t(number(2)); }
    std::uint32_t u32() { return std::uint32_t(number(4)); }
    std::uint64_t u64() { return number(8); }
    bool boolean() { const auto n=u8(); if(n>1) throw Fault("invalid boolean"); return n!=0; }
    template<std::size_t N> std::array<std::uint8_t,N> array() {
        if(N>bytes.size()-pos) throw Fault("truncated byte array");
        std::array<std::uint8_t,N> a{};
        std::copy_n(bytes.begin()+static_cast<std::ptrdiff_t>(pos),N,a.begin()); pos+=N; return a;
    }
    Bus::Status status() {
        auto n=u8(); if(n>static_cast<unsigned>(Bus::Status::IdExhausted)) throw Fault("invalid status");
        return static_cast<Bus::Status>(n);
    }
    Bus::Status pollStatus() {
        const auto s=status();
        if(s!=Bus::Status::Ok && s!=Bus::Status::Empty && s!=Bus::Status::SourceNotReady &&
           s!=Bus::Status::InvalidAid && s!=Bus::Status::StaleSession)throw Fault("invalid poll status");
        return s;
    }
    Bus::Session session() {
        Bus::Session s; s.aid=u8(); s.epoch=u64();
        if(s.aid>=8 || !s.epoch) throw Fault("invalid session");
        return s;
    }
    Bus::Message message(bool blockLane=false) {
        Bus::Message m; m.source=session(); m.send_id=u64(); m.sequence=u16();
        m.port=u8(); m.targets=u16(); m.wire_sequence=u16();
        if(!m.send_id || (m.port>=16&&!(blockLane&&m.port==Bus::BlockPort)) || !m.targets || m.targets>255 ||
           (m.source.aid==0 ? (m.targets&1)!=0 : m.targets!=1)) throw Fault("invalid message");
        return m;
    }
    Bus::DeliveryToken token() {
        Bus::DeliveryToken t; t.source=session(); t.recipient=session(); t.send_id=u64();
        if(!t.send_id || (t.source.aid==0 ? t.recipient.aid==0 : t.recipient.aid!=0))
            throw Fault("invalid delivery token");
        return t;
    }
    Event event() {
        Event e; const auto kind=u8();
        if(kind>static_cast<unsigned>(Event::Kind::ConnectFailed)) throw Fault("invalid event kind");
        e.kind=static_cast<Event::Kind>(kind); e.peer=u64(); e.toParent=boolean();
        e.aid=u16(); e.reason=u16(); e.mac=array<6>(); e.data=array<24>(); e.bss=array<192>();
        if(!e.peer || e.aid<1 || e.aid>7) throw Fault("invalid membership event");
        return e;
    }
    void end() const { if(pos!=bytes.size()) throw Fault("trailing semantic bytes"); }
};

class TapeTransport final : public Transport {
public:
    struct Costs { std::uint64_t exchanges=0, exchangeNs=0, mediumNs=0, sinkNs=0; };
    using Clock = std::function<Stamp()>;
    using Sink = std::function<void(const Record&)>;
    // false means feed unavailable/end, NEVER an Empty network result.
    using Source = std::function<bool(Record&)>;
private:
    Transport* live;
    Clock clock;
    Sink sink;
    Source source;
    std::uint64_t next=0;
    bool poisoned=false;
    bool sealed=false;
    bool measureCosts=false;
    Costs costs{};
    struct CostScope {
        using Clock=std::chrono::steady_clock;
        bool enabled; std::uint64_t& total; Clock::time_point start;
        CostScope(bool on, std::uint64_t& n) : enabled(on),total(n),start(on?Clock::now():Clock::time_point{}) {}
        ~CostScope() { if(enabled) total+=std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now()-start).count(); }
    };
    std::array<Token,8> owned{};
    void release(Token t) {
        if(!live || !t)return;
        for(auto& token:owned)if(token==t){live->close(t);token=0;return;}
    }
    template<class Action> Bytes exchange(Op op, Writer& arguments, Action&& action) {
        CostScope measured(measureCosts,costs.exchangeNs);
        if(measureCosts) ++costs.exchanges;
        if(poisoned || sealed) throw Fault("semantic tape failed or sealed");
        try {
            Record record; record.ordinal=next; record.stamp=clock(); record.op=op;
            record.arguments=std::move(arguments.bytes);
            if(record.arguments.size()>MaxValueBytes) throw Fault("oversized arguments");
            if(live) {
                { CostScope measuredMedium(measureCosts,costs.mediumNs);record.result=action(); }
                if(record.result.size()>MaxValueBytes) throw Fault("oversized result");
                { CostScope measuredSink(measureCosts,costs.sinkNs);sink(record); }
            } else {
                Record expected;
                if(!source(expected)) throw Fault("SDK feed exhausted inside operation");
                if(expected.ordinal!=next || !(expected.stamp==record.stamp) ||
                   expected.op!=op || expected.arguments!=record.arguments)
                    throw Fault("SDK call mismatch at ordinal "+std::to_string(next)+
                                " frame "+std::to_string(record.stamp.frame)+
                                " op "+std::to_string(static_cast<unsigned>(op)));
                if(expected.result.size()>MaxValueBytes) throw Fault("oversized result");
                record.result=std::move(expected.result);
            }
            if(next==UINT64_MAX) throw Fault("SDK ordinal exhausted");
            ++next;
            return std::move(record.result);
        } catch(...) { poisoned=true; throw; }
    }
    template<class Function> Bytes call(Op op, Writer& a, Function action) {
        return exchange(op,a,[&]{Writer w; action(w); return std::move(w.bytes);});
    }
public:
    TapeTransport(Transport& medium, Clock c, Sink s, bool measure=false) : live(&medium),clock(std::move(c)),sink(std::move(s)),measureCosts(measure) {
        if(!clock || !sink) throw Fault("missing recorder dependency");
    }
    TapeTransport(Clock c, Source s) : live(nullptr),clock(std::move(c)),source(std::move(s)) {
        if(!clock || !source) throw Fault("missing replay dependency");
    }
    ~TapeTransport() override {
        // Abandoned recording/open failures cannot strand a registry endpoint.
        // This is cleanup outside the captured prefix, never a replayed call.
        for(auto token:owned)try{release(token);}catch(...){}
    }
    // Owner calls only AFTER verifying the final complete frame. Subsequent
    // emulator teardown lies outside the selected prefix; no further SDK call
    // is allowed except resource cleanup. This never turns a fault into PASS.
    void seal() { sealed=true; }
    std::uint64_t calls() const { return next; }
    bool failed() const { return poisoned; }
    Costs getCosts() const { return costs; } // owner-thread diagnostic, no reset or guest effects
    bool advanceFrame(Token token, std::uint64_t frame) override {
        if (poisoned || sealed) throw Fault("frame clock on failed or sealed SDK tape");
        try {
            if (clock().frame != frame) throw Fault("SDK frame metadata disagrees with actual clock");
            return live ? live->advanceFrame(token,frame) : true;
        } catch (...) { poisoned=true; throw; }
    }
    Token open(const Mac& mac) override {
        Token allocated=0;
        try {
            Writer a; a.array(mac); auto b=call(Op::Open,a,[&](Writer& w){allocated=live->open(mac);
                if(allocated){auto slot=std::find(owned.begin(),owned.end(),Token(0));
                    if(slot==owned.end()){live->close(allocated);allocated=0;throw Fault("endpoint ownership capacity");}
                    *slot=allocated;}w.u64(allocated);});
            Reader r(b,&poisoned); auto v=r.u64();r.end();return v;
        }catch(...){release(allocated);throw;}
    }
    void close(Token t) override {
        if(poisoned || sealed){release(t);return;}
        Writer a;a.u64(t); auto b=call(Op::Close,a,[&](Writer&){live->close(t);
            for(auto& token:owned)if(token==t)token=0;}); Reader(b,&poisoned).end();
    }
    bool advertise(Token t,const Bss& bss,std::uint16_t max) override {
        Writer a;a.u64(t);a.array(bss);a.u16(max);
        auto b=call(Op::Advertise,a,[&](Writer& w){w.boolean(live->advertise(t,bss,max));});
        Reader r(b,&poisoned);auto v=r.boolean();r.end();return v;
    }
    Advertisement scan(Token t,std::uint16_t channel,const Mac& filter) override {
        Writer a;a.u64(t);a.u16(channel);a.array(filter);
        auto b=call(Op::Scan,a,[&](Writer& w){auto v=live->scan(t,channel,filter);w.u64(v.parent);w.array(v.bss);});
        Reader r(b,&poisoned);Advertisement v;v.parent=r.u64();v.bss=r.array<192>();r.end();return v;
    }
    bool connect(Token t,Token p,const Bss& bss,const ConnectData& data) override {
        Writer a;a.u64(t);a.u64(p);a.array(bss);a.array(data);
        auto b=call(Op::Connect,a,[&](Writer& w){w.boolean(live->connect(t,p,bss,data));});
        Reader r(b,&poisoned);auto v=r.boolean();r.end();return v;
    }
    std::uint16_t disconnect(Token t,std::uint16_t mask) override {
        Writer a;a.u64(t);a.u16(mask);
        auto b=call(Op::Disconnect,a,[&](Writer& w){w.u16(live->disconnect(t,mask));});
        Reader r(b,&poisoned);auto v=r.u16();
        if(v>255 || (v&~mask)) throw Fault("invalid disconnect result");
        r.end();return v;
    }
    bool pop(Token t,Event& event,bool& fault) override {
        Writer a;a.u64(t);
        auto b=call(Op::Pop,a,[&](Writer& w){Event e;bool f=false;auto ok=live->pop(t,e,f);
            w.boolean(ok);w.boolean(f);if(ok)w.event(e);});
        Reader r(b,&poisoned);const bool ok=r.boolean(), f=r.boolean();Event e;if(ok)e=r.event();r.end();
        fault=f;if(ok)event=e;return ok;
    }
    Bus::SessionResult startMp(Token t) override {
        Writer a;a.u64(t);auto b=call(Op::StartMp,a,[&](Writer& w){auto v=live->startMp(t);
            w.status(v.status);if(v.status==Bus::Status::Ok)w.session(v.session);});
        Reader r(b,&poisoned);Bus::SessionResult v;v.status=r.status();if(v.status==Bus::Status::Ok)v.session=r.session();r.end();return v;
    }
    void stopMp(Token t) override {
        Writer a;a.u64(t);auto b=call(Op::StopMp,a,[&](Writer&){live->stopMp(t);});Reader(b,&poisoned).end();
    }
    bool enableReceiptRetention(Token t) override {
        Writer a;a.u64(t);auto b=call(Op::RetainReceipts,a,[&](Writer& w){w.boolean(live->enableReceiptRetention(t));});
        Reader r(b,&poisoned);const bool enabled=r.boolean();r.end();return enabled;
    }
    std::optional<std::vector<Bus::Completion>> stopMpWithReceipts(Token t) override {
        static_assert(2 + MaxStopMpReceipts * 28 <= MaxValueBytes,
                      "stop receipts exceed semantic record bound");
        Writer a;a.u64(t);
        auto b=call(Op::StopMpWithReceipts,a,[&](Writer& w){
            auto receipts=live->stopMpWithReceipts(t);w.boolean(bool(receipts));
            if(!receipts)return;
            if(receipts->size()>MaxStopMpReceipts)throw Fault("stop receipt count");
            w.u8(static_cast<std::uint8_t>(receipts->size()));
            for(const auto& c:*receipts){w.message(c.message);w.u16(c.acknowledged);
                w.u8(static_cast<std::uint8_t>(c.outcome));w.u8(static_cast<std::uint8_t>(c.reason));}
        });
        Reader r(b,&poisoned);
        if(!r.boolean()){r.end();return std::nullopt;}
        const auto count=r.u8();if(count>MaxStopMpReceipts)throw Fault("stop receipt count");
        std::vector<Bus::Completion> receipts;receipts.reserve(count);
        for(unsigned i=0;i<count;++i){
            Bus::Completion c;c.message=r.message();c.acknowledged=r.u16();
            const auto outcome=r.u8(),reason=r.u8();
            if(outcome>2 || reason>4 || (c.acknowledged&~c.message.targets))throw Fault("invalid stop receipt");
            c.outcome=static_cast<Bus::Outcome>(outcome);c.reason=static_cast<Bus::Reason>(reason);
            if(c.outcome==Bus::Outcome::Delivered && (c.acknowledged!=c.message.targets || c.reason!=Bus::Reason::None))
                throw Fault("incomplete stop delivered result");
            if(c.outcome!=Bus::Outcome::Delivered && c.acknowledged==c.message.targets)throw Fault("stop receipt has no pending targets");
            if(c.outcome==Bus::Outcome::Cancelled && c.reason!=Bus::Reason::SenderLeft && c.reason!=Bus::Reason::SenderReset)
                throw Fault("invalid stop cancellation reason");
            if(c.outcome==Bus::Outcome::Failed && c.reason!=Bus::Reason::RecipientLeft && c.reason!=Bus::Reason::RecipientReset)
                throw Fault("invalid stop failure reason");
            for(const auto& old:receipts)if(old.message.send_id==c.message.send_id && old.message.source==c.message.source)
                throw Fault("duplicate stop receipt");
            receipts.push_back(c);
        }
        r.end();return receipts;
    }
    Bus::SendResult send(Token t,unsigned mask,unsigned port,const std::array<std::uint8_t,512>& data,
                         unsigned length,Bus::Sequence wire=0xffff) override {
        static_assert(sizeof(unsigned)==4,"explicit 32-bit arguments required");
        Writer a;a.u64(t);a.u32(mask);a.u32(port);a.array(data);a.u32(length);a.u16(wire);
        auto b=call(Op::Send,a,[&](Writer& w){auto v=live->send(t,mask,port,data,length,wire);
            w.status(v.status);if(v.status==Bus::Status::Ok)w.message(v.message);});
        Reader r(b,&poisoned);Bus::SendResult v;v.status=r.status();if(v.status==Bus::Status::Ok)v.message=r.message();r.end();return v;
    }
    Bus::DeliveryResult take(Token t,Token& domain) override {
        Writer a;a.u64(t);a.u64(domain);
        auto b=call(Op::Take,a,[&](Writer& w){Token d=domain;auto v=live->take(t,d);w.status(v.status);w.u64(d);
            if(v.status==Bus::Status::Ok){const auto& x=v.delivery;w.token(x.token);w.u16(x.sequence);
                w.u8(x.port);w.u16(x.wire_sequence);w.u16(static_cast<std::uint16_t>(x.payload.size()));
                w.bytes.insert(w.bytes.end(),x.payload.bytes().begin(),x.payload.bytes().begin()+static_cast<std::ptrdiff_t>(x.payload.size()));}});
        Reader r(b,&poisoned);Bus::DeliveryResult v;v.status=r.pollStatus();const auto d=r.u64();
        if(v.status==Bus::Status::Ok){auto& x=v.delivery;x.token=r.token();x.sequence=r.u16();x.port=r.u8();
            x.wire_sequence=r.u16();auto length=r.u16();
            if(!d || x.port>=16 || !length || length>512)throw Fault("invalid delivery");
            std::array<std::uint8_t,512> bytes{};for(unsigned i=0;i<length;++i)bytes[i]=r.u8();
            if(!Bus::Payload::from_owned(bytes,length,x.payload))throw Fault("invalid owned payload");}
        r.end();domain=d;return v;
    }
    Bus::Status acknowledge(Token t,Token d,const Bus::DeliveryToken& token) override {
        Writer a;a.u64(t);a.u64(d);a.token(token);
        auto b=call(Op::Acknowledge,a,[&](Writer& w){w.status(live->acknowledge(t,d,token));});
        Reader r(b,&poisoned);auto v=r.status();r.end();return v;
    }
    Bus::CompletionResult completion(Token t) override {
        Writer a;a.u64(t);auto b=call(Op::Completion,a,[&](Writer& w){auto v=live->completion(t);w.status(v.status);
            if(v.status==Bus::Status::Ok){const auto& c=v.completion;w.message(c.message);w.u16(c.acknowledged);
                w.u8(static_cast<std::uint8_t>(c.outcome));w.u8(static_cast<std::uint8_t>(c.reason));}});
        Reader r(b,&poisoned);Bus::CompletionResult v;v.status=r.pollStatus();if(v.status==Bus::Status::Ok){auto& c=v.completion;
            c.message=r.message();c.acknowledged=r.u16();auto outcome=r.u8(),reason=r.u8();
            if(outcome>2 || reason>4 || (c.acknowledged&~c.message.targets))throw Fault("invalid completion");
            c.outcome=static_cast<Bus::Outcome>(outcome);c.reason=static_cast<Bus::Reason>(reason);
            if(c.outcome==Bus::Outcome::Delivered && (c.acknowledged!=c.message.targets || c.reason!=Bus::Reason::None))
                throw Fault("incomplete delivered result");
            if(c.outcome!=Bus::Outcome::Delivered && c.acknowledged==c.message.targets)throw Fault("terminal send has no pending targets");
            if(c.outcome==Bus::Outcome::Cancelled && c.reason!=Bus::Reason::SenderLeft && c.reason!=Bus::Reason::SenderReset)
                throw Fault("invalid cancellation reason");
            if(c.outcome==Bus::Outcome::Failed && c.reason!=Bus::Reason::RecipientLeft && c.reason!=Bus::Reason::RecipientReset)
                throw Fault("invalid failure reason");}r.end();return v;
    }
};
} // namespace melonDS::wm_sdk::tape
#endif
