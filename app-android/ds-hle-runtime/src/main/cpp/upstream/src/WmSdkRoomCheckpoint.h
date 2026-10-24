// Clean-room: our owner parking and Remote snapshot protocol.
// Capture and held-import barrier. This does not enable live restoration.
#pragma once
#include "WmSdkConsoleSnapshotLocal.h"
#include "WmSdkSnapshotRemote.h"

namespace melonDS::wm_sdk::snapshot {
class RoomCheckpoint {
    std::uint64_t operation=0,incarnation=0;
    Transport* bound=nullptr;
    bool confirmed=false;
    template<class F> static Status guarded(F&& f) {
        try { return attempt(std::forward<F>(f)); }
        catch(const tape::Fault&) { return Status::NotQuiescent; }
    }
    net::Remote* remote(OwnerPark& owner,Transport* current) const {
        require(owner.isParked()&&!owner.isRetired()&&operation&&bound&&bound==current,Status::NotQuiescent);
        auto* r=current->checkpointRemote();require(r,Status::Unsupported);return r;
    }
public:
    bool active() const { return operation!=0; }
    bool matches(std::uint64_t op,std::uint64_t epoch) const {
        return op&&op==operation&&epoch==incarnation;
    }
    bool retiredFor(const OwnerPark& owner,std::uint64_t op,std::uint64_t fresh) const {
        return owner.isParked()&&owner.isRetired()&&confirmed&&!bound&&
            op&&op==operation&&incarnation&&fresh>incarnation;
    }
    // Only an imported, still-disconnected Remote may be adopted here. This
    // latches its cut but is NOT a broker-confirmed frozen acknowledgment.
    Status adoptImported(OwnerPark& owner,Transport* current,std::uint64_t op,std::uint64_t epoch) {
        if(!owner.isParked()||owner.isRetired()||active()||!op||!epoch)return Status::NotQuiescent;
        auto* r=current?current->checkpointRemote():nullptr;
        if(!r)return Status::Unsupported;
        return guarded([&]{auto guard=r->tryFreezeSnapshot();
            require(guard&&!guard->brokerConfirmed()&&guard->operation()==op&&guard->incarnation()==epoch,
                Status::NotQuiescent);
            operation=op;incarnation=epoch;bound=current;
        });
    }
    // Transport teardown is terminal for this cut; never dereference the old
    // address, even if a replacement allocator happens to reuse it.
    void invalidate() { bound=nullptr; }
    Status prepare(OwnerPark& owner,Transport* current,std::uint64_t op,std::uint64_t epoch) {
        if(!owner.isParked()||owner.isRetired()||!op||!epoch)return Status::NotQuiescent;
        if(active()&&(operation!=op||incarnation!=epoch||bound!=current||!bound))return Status::NotQuiescent;
        auto* r=current?current->checkpointRemote():nullptr;
        if(!r)return Status::Unsupported;
        // Latch before the first send; failures cannot unpark this owner.
        operation=op;incarnation=epoch;bound=current;
        return guarded([&]{auto result=r->prepareSnapshot(op,epoch);require(result==Status::Ok,result);});
    }
    Status frozen(OwnerPark& owner,Transport* current) {
        return guarded([&]{auto* r=remote(owner,current);r->pollExternalSnapshot();
            auto guard=r->tryFreezeSnapshot();
            require(guard&&guard->brokerConfirmed()&&guard->operation()==operation&&
                guard->incarnation()==incarnation,Status::NotQuiescent);confirmed=true;});
    }
    Status retire(OwnerPark& owner,Transport* current,std::uint64_t op,std::uint64_t epoch) {
        // Do not poll here: coordinator retirement has already closed the old
        // broker. Use the previously observed boundary and exact cut identity.
        if(!confirmed||op!=operation||epoch!=incarnation)return Status::NotQuiescent;
        return guarded([&]{auto* r=remote(owner,current);
            const auto result=r->retireSnapshot(op,epoch);require(result==Status::Ok,result);
            require(owner.retire()==Status::Ok,Status::NotQuiescent);
            bound=nullptr; // keep active latched so destructors send no CLOSE
        });
    }
    // CaptureLocal may serialize owned core/service state only: the Remote's
    // output/state locks are held, so it must never call transport operations.
    template<class Capture> Status capture(OwnerPark& owner,Transport* current,
            LocalImage& local,Bytes& transport,Capture&& captureLocal) {
        return guarded([&]{auto* r=remote(owner,current);auto guard=r->tryFreezeSnapshot();
            require(guard&&guard->brokerConfirmed()&&guard->operation()==operation&&
                guard->incarnation()==incarnation,Status::NotQuiescent);
            LocalImage staged;Bytes wire;
            auto result=captureLocal(incarnation,staged);require(result==Status::Ok,result);
            result=r->exportSnapshot(*guard,wire);require(result==Status::Ok,result);
            static_assert(std::is_nothrow_move_assignable_v<LocalImage>);
            local=std::move(staged);transport.swap(wire);
        });
    }
    Status release(OwnerPark& owner,Transport* current) {
        const auto status=frozen(owner,current);if(status!=Status::Ok)return status;
        return guarded([&]{auto result=remote(owner,current)->releaseSnapshot(operation);require(result==Status::Ok,result);});
    }
    Status released(OwnerPark& owner,Transport* current) {
        return guarded([&]{auto* r=remote(owner,current);r->pollExternalSnapshot();
            require(r->incarnation()==incarnation&&r->snapshotReleased(operation),Status::NotQuiescent);});
    }
    Status resume(OwnerPark& owner,Transport* current) {
        const auto status=released(owner,current);if(status!=Status::Ok)return status;
        const auto result=owner.release();if(result!=Status::Ok)return result;
        operation=incarnation=0;bound=nullptr;confirmed=false;return Status::Ok;
    }
};
}
