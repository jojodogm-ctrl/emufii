// Clean-room sources: docs/CLEANROOM.md, our WmSdkTransport/Session/Net and
// docs/prototypes/wm_sdk_broker.cpp. No guest code, assets or emulator instances.
#pragma once
#include <cstdint>
#include <memory>
#include <vector>
#include "../../WmSdkSnapshot.h"

namespace melonDS::wm_sdk::browser {
// Single event-loop owner. Carries native N2 record BODIES (1..1024 bytes).
// Handles identify authenticated carrier attachments, not guest AIDs or tokens.
// The owner provides monotonic wall milliseconds, never an emulated frame clock.
class Broker final {
public:
    using Handle = std::uint32_t;
    using Bytes = std::vector<std::uint8_t>;
    explicit Broker(unsigned players, unsigned rttMs = 0);
    // Private explicit capability cohort. Legacy/default construction does not
    // accept window claims. Snapshots of this new policy are unsupported.
    struct SharingWindow64 {};
    Broker(unsigned players, SharingWindow64, unsigned rttMs = 0);
    // Explicit v4 cohort: capacity64, independently agreed immutable lead.
    struct SharingPipeline { unsigned lead; };
    Broker(unsigned players, SharingPipeline, unsigned rttMs = 0);
    // Explicit v5 cohort, enables the private block lane in addition to v4.
    struct SharingBlocks { unsigned lead; };
    Broker(unsigned players,SharingBlocks,unsigned rttMs=0);
    // Explicit coordinator opt-in; ordinary product construction stays disabled.
    struct CoordinatedSnapshots {};
    Broker(unsigned players, CoordinatedSnapshots, unsigned rttMs = 0);
    ~Broker();
    Broker(const Broker&) = delete;
    Broker& operator=(const Broker&) = delete;
    Handle attach(); // zero on capacity/exhaustion/failure; never replaces a peer
    bool detach(Handle); // EOF closes the real endpoint; stale handle is harmless
    bool receive(Handle, const Bytes&, std::uint64_t nowMs);
    bool tick(std::uint64_t nowMs);
    // Carrier-owned lab timing, shared by TCP and WASM. Delay changes apply to
    // newly admitted DATA/ACK; a hold retains existing records without dropping
    // them. Control remains live. A continuous hold is bounded to 60 seconds.
    bool setDeliveryTiming(unsigned rttMs, bool hold, std::uint64_t nowMs);
    std::size_t pendingRecords() const;
    const Bytes* peek(Handle) const; // stable until next mutation, copy before pop
    bool pop(Handle);
    bool failed() const;
    // WAN64: registry <=1 MiB, <=4096 deferred records of <=1024 bytes,
    // <=512 identity mappings and eight peers fit this finite outer bound.
    static constexpr std::size_t MaxSnapshotBytes = snapshot::Version==1 ? 512 * 1024 : 8 * 1024 * 1024;
    snapshot::Status beginSnapshot(std::uint64_t operation);
    // Requires the full cohort AND every queued freeze marker to be popped.
    // Carrier admission is not an acknowledgement from a Remote/NDS owner.
    bool snapshotFrozen(std::uint64_t operation) const;
    // Matching frozen cut plus actual registry/adapter emptiness, not merely
    // drained output. Does not consume records or release the snapshot.
    bool emptyPreparation(std::uint64_t operation);
    bool snapshotReleased(std::uint64_t operation) const;
    // All queued output (including freeze markers) must have been admitted by
    // the carrier. The coordinator ALSO needs each console/Remote's frozen cut.
    snapshot::Status exportSnapshot(std::uint64_t operation, Bytes& output);
    static snapshot::Status validateSnapshot(const Bytes&);
    // Detached replacement only. Never overwrites a live broker. Fresh must be
    // greater than the image incarnation; the room owner prevents global reuse.
    static snapshot::Status restoreSnapshot(const Bytes&, std::uint64_t freshIncarnation,
        std::uint64_t operation, std::unique_ptr<Broker>& output);
    // Admission authenticates this endpoint against the checkpoint manifest.
    // All restored peers must attach before any freeze marker is emitted.
    Handle attachRestored(std::uint64_t endpoint);
private:
    struct Impl;
    std::unique_ptr<Impl> impl;
};
} // namespace melonDS::wm_sdk::browser
