#include <jni.h>
#include <dlfcn.h>
#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

// This bridge owns no emulator code. These are the verified exported C ABI of
// public NEmul 41f251bffae81e65fcb86a9f2f21d761ea9067b3 libmpdsprobe.so.
struct CoreApi {
    void* handle = nullptr;
    int (*init)(const char*, const char*, int, const char*, int, int) = nullptr;
    void (*input)(uint16_t, uint16_t, uint16_t, uint8_t, uint8_t) = nullptr;
    const uint32_t* (*top)(int) = nullptr;
    const uint32_t* (*bottom)(int) = nullptr;
    int (*running)() = nullptr;
    void (*stop)() = nullptr;
    int (*audio)(int16_t*, int) = nullptr;
    void (*solo)(int) = nullptr;
    void (*name)(const char*) = nullptr;
    void (*room)(const char*) = nullptr;
    long (*frames)(int) = nullptr;
    std::string error;
};
static CoreApi api;
static std::once_flag loaded;
static std::atomic<bool> started{false};
template<typename T> static bool symbol(T& out, const char* name) {
    out = reinterpret_cast<T>(dlsym(api.handle, name));
    if (!out) { api.error = std::string("Missing pinned core symbol: ") + name; return false; }
    return true;
}
static bool loadApi(JNIEnv* env) {
    std::call_once(loaded, [] {
        api.handle = dlopen("libmpdsprobe.so", RTLD_NOW | RTLD_LOCAL);
        if (!api.handle) { const char* e = dlerror(); api.error = e ? e : "Core loading failed"; return; }
        if (!(symbol(api.init, "mpCoreInit") && symbol(api.input, "mpCoreSetLocalInputMic") &&
              symbol(api.top, "mpCoreFbTop") && symbol(api.bottom, "mpCoreFbBot") &&
              symbol(api.running, "mpCoreRunning") && symbol(api.stop, "mpCoreStop") &&
              symbol(api.audio, "mpCoreReadAudio") && symbol(api.solo, "mpCoreSetSolo") &&
              symbol(api.name, "mpCoreSetName") && symbol(api.room, "mpCoreSetRoom") &&
              symbol(api.frames, "mpCoreFrames"))) return;
    });
    if (api.error.empty()) return true;
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), api.error.c_str());
    return false;
}
class Utf final {
    JNIEnv* env;
    jstring value;
public:
    const char* text;
    Utf(JNIEnv* e, jstring s): env(e), value(s), text(s ? e->GetStringUTFChars(s, nullptr) : nullptr) {}
    ~Utf() { if (text) env->ReleaseStringUTFChars(value, text); }
};
extern "C" JNIEXPORT jint JNICALL
Java_eu_emufii_dsnemul_NemulNative_start(JNIEnv* env, jobject, jstring rom, jstring stateDir,
        jint seat, jint players, jint proxyPort, jint delay, jstring room, jstring name) {
    if (seat < 1 || players < 2 || players > 4 || seat > players ||
        proxyPort < 1 || proxyPort > 65535 || delay < 1 || delay > 30) return -100;
    if (!loadApi(env)) return -101;
    bool expected = false;
    if (!started.compare_exchange_strong(expected, true)) return -102;
    Utf r(env, rom), s(env, stateDir), c(env, room), n(env, name);
    if (!r.text || !s.text || !c.text || !n.text) return -103;
    const std::string count = std::to_string(players);
    setenv("MPDS_NCON", count.c_str(), 1);
    setenv("MPDS_NOSTATE", "1", 1);  // N consoles boot the same cart; no per-game offsets/savestates.
    unsetenv("MPDS_NATIVE");          // Emufii admission already chose room + seat.
    unsetenv("MPDS_LIVEBOOT");
    unsetenv("MPDS_AUTOSEAT");
    unsetenv("MPDS_AUTODELAY");        // Every admission carries the same coordinator delay.
    unsetenv("MPDS_ROMCON");
    unsetenv("MPDS_FWBOOT");
    // The pinned core contains a Mario Party name-slot scan over RAM. Stable
    // P1..P4 names make that scan byte-preserving on arbitrary SILVER games and
    // keep each console's generated firmware identical on every device. Real
    // player names remain in Emufii's authenticated roster.
    setenv("MPDS_NAMES", "P1,P2,P3,P4", 1);
    api.solo(0);
    const std::string nativeName = "P" + std::to_string(seat);
    api.name(nativeName.c_str());
    api.room(c.text);
    // The unchanged engine never sees the external relay address or secret.
    return api.init(r.text, s.text, seat, "127.0.0.1", proxyPort, delay);
}
extern "C" JNIEXPORT void JNICALL
Java_eu_emufii_dsnemul_NemulNative_input(JNIEnv* env, jobject, jint keys, jint x, jint y,
        jboolean down, jint mic) {
    if (!loadApi(env)) return;
    api.input(static_cast<uint16_t>(keys & 0xFFF), static_cast<uint16_t>(x),
              static_cast<uint16_t>(y), down ? 1 : 0, static_cast<uint8_t>(mic));
}
extern "C" JNIEXPORT jint JNICALL
Java_eu_emufii_dsnemul_NemulNative_running(JNIEnv* env, jobject) {
    return loadApi(env) ? api.running() : 0;
}
extern "C" JNIEXPORT jlong JNICALL
Java_eu_emufii_dsnemul_NemulNative_frames(JNIEnv* env, jobject, jint seatIndex) {
    return loadApi(env) ? api.frames(seatIndex) : 0;
}
extern "C" JNIEXPORT void JNICALL
Java_eu_emufii_dsnemul_NemulNative_stop(JNIEnv* env, jobject) {
    if (loadApi(env)) api.stop();
}
extern "C" JNIEXPORT jint JNICALL
Java_eu_emufii_dsnemul_NemulNative_audio(JNIEnv* env, jobject, jshortArray samples, jint maxPairs) {
    if (!samples || maxPairs < 0 || maxPairs > 4096 ||
        env->GetArrayLength(samples) < maxPairs * 2 || !loadApi(env)) return 0;
    std::vector<int16_t> output(static_cast<size_t>(maxPairs) * 2);
    const int n = api.audio(output.data(), maxPairs);
    if (n <= 0 || n > maxPairs) return 0;
    env->SetShortArrayRegion(samples, 0, n * 2, reinterpret_cast<const jshort*>(output.data()));
    return n;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_eu_emufii_dsnemul_NemulNative_copyFrame(JNIEnv* env, jobject, jint seatIndex, jobject memory) {
    constexpr int pixels = 256 * 192;
    constexpr jlong bytes = 8 + pixels * 4 * 2;
    if (!memory || env->GetDirectBufferCapacity(memory) < bytes || !loadApi(env)) return JNI_FALSE;
    auto* dst = static_cast<uint32_t*>(env->GetDirectBufferAddress(memory));
    if (!dst) return JNI_FALSE;
    const uint32_t* top = api.top(seatIndex);
    const uint32_t* bottom = api.bottom(seatIndex);
    if (!top || !bottom) return JNI_FALSE;
    auto* sequence = reinterpret_cast<uint64_t*>(dst);
    const uint64_t next = __atomic_load_n(sequence, __ATOMIC_RELAXED) + 1;
    __atomic_store_n(sequence, next, __ATOMIC_RELEASE);  // odd = write in progress
    // GPU2D_Soft already publishes BGRA bytes / 0xAARRGGBB integers. Java
    // Bitmap.setPixels consumes those ARGB integers; swapping R/B is only needed
    // when writing directly into AndroidBitmap's RGBA byte storage.
    std::memcpy(dst + 2, top, pixels * sizeof(uint32_t));
    std::memcpy(dst + 2 + pixels, bottom, pixels * sizeof(uint32_t));
    __atomic_store_n(sequence, next + 1, __ATOMIC_RELEASE);
    return JNI_TRUE;
}
