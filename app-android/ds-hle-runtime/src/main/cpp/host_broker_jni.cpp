#include <jni.h>
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include "upstream/src/frontend/wasm/BrowserBroker.h"

namespace {
using Broker = melonDS::wm_sdk::browser::Broker;
using Status = melonDS::wm_sdk::snapshot::Status;
std::mutex ownership;
std::unique_ptr<Broker> broker;
std::uint64_t ticket = 0, sequence = 1;
bool valid(jlong id) { return id > 0 && static_cast<std::uint64_t>(id) == ticket && broker; }
bool peer(jlong id) { return id > 0 && static_cast<std::uint64_t>(id) <= UINT32_MAX; }
Broker::Bytes bytes(JNIEnv* env, jbyteArray input, std::size_t limit) {
    if (!input) throw 0;
    const auto size = env->GetArrayLength(input);
    if (size < 1 || static_cast<std::size_t>(size) > limit) throw 0;
    Broker::Bytes value(size);
    env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte*>(value.data()));
    if (env->ExceptionCheck()) throw 0;
    return value;
}
jbyteArray output(JNIEnv* env, const Broker::Bytes& value) {
    auto result = env->NewByteArray(static_cast<jsize>(value.size()));
    if (result && !value.empty()) env->SetByteArrayRegion(result, 0,
        static_cast<jsize>(value.size()), reinterpret_cast<const jbyte*>(value.data()));
    return result;
}
}

extern "C" {
JNIEXPORT jlong JNICALL Java_com_mpds_online_HostBrokerBridge_create(JNIEnv*, jobject,
        jint players, jint rtt, jboolean snapshots) {
    std::lock_guard<std::mutex> lock(ownership);
    if (broker || players < 2 || players > 8 || rtt < 0 || rtt > 1000 || sequence > INT64_MAX) return 0;
    try {
        broker = snapshots ? std::make_unique<Broker>(players, Broker::CoordinatedSnapshots{}, rtt)
                           : std::make_unique<Broker>(players, rtt);
        ticket = sequence++; return static_cast<jlong>(ticket);
    } catch (...) { broker.reset(); ticket = 0; return 0; }
}
JNIEXPORT jlong JNICALL Java_com_mpds_online_HostBrokerBridge_createSdkBlocks(JNIEnv*, jobject,
        jint players, jint rtt, jint lead) {
    std::lock_guard<std::mutex> lock(ownership);
    if (broker || players < 2 || players > 8 || rtt < 0 || rtt > 500 ||
        lead < 2 || lead > 64 || sequence > INT64_MAX) return 0;
    try {
        broker = std::make_unique<Broker>(players, Broker::SharingBlocks{static_cast<unsigned>(lead)}, rtt);
        ticket = sequence++; return static_cast<jlong>(ticket);
    } catch (...) { broker.reset(); ticket = 0; return 0; }
}
JNIEXPORT jlong JNICALL Java_com_mpds_online_HostBrokerBridge_attach(JNIEnv*, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(ownership);
    if (!valid(id)) return 0;
    try { return broker->attach(); } catch (...) { return 0; }
}
JNIEXPORT jboolean JNICALL Java_com_mpds_online_HostBrokerBridge_detach(JNIEnv*, jobject, jlong id, jlong h) {
    std::lock_guard<std::mutex> lock(ownership);
    if (!valid(id) || !peer(h)) return false;
    try { return broker->detach(static_cast<Broker::Handle>(h)); } catch (...) { return false; }
}
JNIEXPORT jboolean JNICALL Java_com_mpds_online_HostBrokerBridge_receive(JNIEnv* env, jobject,
        jlong id, jlong h, jbyteArray input, jlong now) {
    std::lock_guard<std::mutex> lock(ownership);
    if (!valid(id) || !peer(h) || now < 0) return false;
    try { return broker->receive(static_cast<Broker::Handle>(h), bytes(env,input,1024), now); }
    catch (...) { return false; }
}
JNIEXPORT jboolean JNICALL Java_com_mpds_online_HostBrokerBridge_tick(JNIEnv*, jobject, jlong id, jlong now) {
    std::lock_guard<std::mutex> lock(ownership);
    if (!valid(id) || now < 0) return false;
    try { return broker->tick(now); } catch (...) { return false; }
}
JNIEXPORT jbyteArray JNICALL Java_com_mpds_online_HostBrokerBridge_peek(JNIEnv* env, jobject, jlong id, jlong h) {
    std::lock_guard<std::mutex> lock(ownership);
    if (!valid(id) || !peer(h)) return nullptr;
    try {
        const auto* value = broker->peek(static_cast<Broker::Handle>(h));
        return value && !value->empty() && value->size() <= 1024 ? output(env,*value) : nullptr;
    } catch (...) { return nullptr; }
}
JNIEXPORT jboolean JNICALL Java_com_mpds_online_HostBrokerBridge_pop(JNIEnv*, jobject, jlong id, jlong h) {
    std::lock_guard<std::mutex> lock(ownership);
    if (!valid(id) || !peer(h)) return false;
    try { return broker->pop(static_cast<Broker::Handle>(h)); } catch (...) { return false; }
}
JNIEXPORT jboolean JNICALL Java_com_mpds_online_HostBrokerBridge_failed(JNIEnv*, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(ownership);
    return !valid(id) || broker->failed();
}
JNIEXPORT jint JNICALL Java_com_mpds_online_HostBrokerBridge_snapshotStatus(JNIEnv*, jobject,
        jlong id, jlong operation, jint action) {
    std::lock_guard<std::mutex> lock(ownership);
    if (!valid(id) || operation == 0) return static_cast<int>(Status::NotQuiescent);
    try {
        switch (action) {
            case 0: return static_cast<int>(broker->beginSnapshot(operation));
            case 1: return broker->snapshotFrozen(operation) ? 0 : 1;
            case 2: return broker->snapshotReleased(operation) ? 0 : 1;
            case 3: return broker->emptyPreparation(operation) ? 0 : 1;
            default: return static_cast<int>(Status::Unsupported);
        }
    } catch (...) { return static_cast<int>(Status::InvalidImage); }
}
JNIEXPORT jbyteArray JNICALL Java_com_mpds_online_HostBrokerBridge_exportSnapshot(JNIEnv* env, jobject,
        jlong id, jlong operation) {
    std::lock_guard<std::mutex> lock(ownership);
    if (!valid(id) || operation == 0) return nullptr;
    try {
        Broker::Bytes image;
        const auto status = broker->exportSnapshot(operation,image);
        Broker::Bytes envelope{static_cast<std::uint8_t>(status),0,0,0};
        if (status == Status::Ok) {
            if (image.empty() || image.size() > Broker::MaxSnapshotBytes) return nullptr;
            envelope.insert(envelope.end(),image.begin(),image.end());
        }
        return output(env,envelope);
    } catch (...) { return nullptr; }
}
JNIEXPORT jint JNICALL Java_com_mpds_online_HostBrokerBridge_validateSnapshot(JNIEnv* env, jobject, jbyteArray input) {
    try { return static_cast<int>(Broker::validateSnapshot(bytes(env,input,Broker::MaxSnapshotBytes))); }
    catch (...) { return static_cast<int>(Status::InvalidImage); }
}
JNIEXPORT jlong JNICALL Java_com_mpds_online_HostBrokerBridge_restore(JNIEnv* env, jobject,
        jbyteArray input, jlong fresh, jlong operation) {
    std::lock_guard<std::mutex> lock(ownership);
    if (broker || fresh == 0 || operation == 0 || sequence > INT64_MAX) return 0;
    try {
        const auto status = Broker::restoreSnapshot(bytes(env,input,Broker::MaxSnapshotBytes),fresh,operation,broker);
        if (status != Status::Ok) { broker.reset(); return 0; }
        ticket = sequence++; return ticket;
    } catch (...) { broker.reset(); ticket = 0; return 0; }
}
JNIEXPORT jlong JNICALL Java_com_mpds_online_HostBrokerBridge_attachRestored(JNIEnv*, jobject, jlong id, jlong endpoint) {
    std::lock_guard<std::mutex> lock(ownership);
    if (!valid(id) || endpoint == 0) return 0;
    try { return broker->attachRestored(endpoint); } catch (...) { return 0; }
}
JNIEXPORT void JNICALL Java_com_mpds_online_HostBrokerBridge_close(JNIEnv*, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(ownership);
    if (valid(id)) { broker.reset(); ticket = 0; }
}
}
