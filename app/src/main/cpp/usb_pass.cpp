// usb_pass.cpp

// mochy's own usb audio driver: android hands over the dac's file descriptor, this takes the dac from the kernel's driver
// and streams isochronous packets at the song's own rate, nothing mixed or scaled on the way; the kernel calls go through
// usb_io (mochy's own usbdevfs, or libusb when the person picks it)

#include <jni.h>
#include <sys/resource.h>
#include <unistd.h>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include <android/log.h>

#include "usb_io.h"

#define TAG "UsbPass"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

using usbpass::Slot;
using usbpass::UsbIo;
using usbpass::kMaxPackets;

namespace {

// the ints kotlin hands over per plan, in UsbPass.kt's order
constexpr int kPlanInts = 26;

// urbs in flight and how long each one plays: about 64 ms queued at the dac, so a worker woken late in the background or with the screen off doesn't run it dry (issue 234)
constexpr int kSlots = 8;
constexpr int kCaptureSlots = 4;
constexpr uint32_t kUrbUs = 8000;

struct Plan {
    int control, streaming, alt, uac2, clock, selector, selectorPin;
    int endpoint, attributes, maxPacket, interval, rateControl;
    int feedback, feedbackMaxPacket;
    int rate, channels, subslot, bits;
    // the song's channels, and the capture stream an implicit-feedback dac paces itself by (interface -1 when there's none)
    int sourceChannels;
    int implicitInterface, implicitAlt, implicitEndpoint, implicitMaxPacket, implicitInterval, implicitFrameBytes, implicitRateControl;
};

Plan planAt(const jint *v) {
    return Plan{v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17],
                v[18], v[19], v[20], v[21], v[22], v[23], v[24], v[25]};
}

class Stream {
public:
    std::unique_ptr<UsbIo> io;
    Plan plan{};
    int chosen = -1;
    bool highSpeed = true;
    // sends the song's rate even to a clock that doesn't advertise it (audio pass's "every rate" switch)
    std::atomic<bool> force{false};
    uint32_t periodUs = 1000;
    uint32_t capturePeriodUs = 1000;
    int packetsPerUrb = 1;
    // a frame as the dac takes it, and as the song comes in; a dac with more channels gets the rest as silence
    size_t frameBytes = 4;
    size_t sourceBytes = 4;
    std::vector<int> claimed;

    // what kotlin wrote and the worker hasn't sent yet, guarded by lock
    std::mutex lock;
    std::condition_variable changed;
    std::vector<uint8_t> ring;
    size_t ringStart = 0, ringSize = 0;
    uint64_t generation = 0;
    uint64_t played = 0;
    bool playing = false;
    bool closing = false;
    std::string error;

    // what a crackle actually is, counted so a logcat says which one: a packet the dac didn't take whole,
    // and a packet that went out as silence because the queue ran dry at the wrong moment
    uint64_t shortPackets = 0, starvedPackets = 0, failedPackets = 0, clampedPackets = 0;
    uint64_t nextReport = 0, nextLengthReport = 0;
    bool loggedLengths = false, loggedFeedback = false;

    std::vector<std::unique_ptr<Slot>> slots;
    double nominal = 0, pace = 0, remainder = 0;
    std::thread worker;

    explicit Stream(std::unique_ptr<UsbIo> io) : io(std::move(io)) {}

    bool claim(int iface) {
        for (int c : claimed) if (c == iface) return true;
        if (!io->claim(iface)) return false;
        claimed.push_back(iface);
        return true;
    }

    void giveBack() {
        for (auto it = claimed.rbegin(); it != claimed.rend(); ++it) io->release(*it);
        claimed.clear();
    }

    int control(uint8_t type, uint8_t request, uint16_t value, uint16_t index, void *data, uint16_t length) {
        return io->control(type, request, value, index, data, length);
    }

    // sets the dac's clock to the song's rate and reads it back: a dac that quietly stays at another rate would play the song too fast or slow
    bool setRate(const Plan &p) {
        if (p.uac2) {
            if (p.selector > 0 && p.selectorPin > 0) {
                uint8_t pin = static_cast<uint8_t>(p.selectorPin);
                control(0x21, 0x01, 0x0100, static_cast<uint16_t>((p.selector << 8) | p.control), &pin, 1);
            }
            uint16_t index = static_cast<uint16_t>((p.clock << 8) | p.control);
            uint32_t wanted = static_cast<uint32_t>(p.rate), now = 0;
            // a dac's readback echoes whatever was written even on clocks that can't produce that rate. the advertised
            // ranges are the honest answer, so a clock that doesn't list the rate is skipped and the next one tried. a dac
            // that wants the song's rate sent anyway (some bridges do, and then play it) opts in with [force]
            int supports = rangeSupports(index, p.clock, wanted);
            if (supports == 0 && !force.load()) {
                LOGI("clock %d doesn't list %u Hz, trying the next clock", p.clock, wanted);
                return false;
            }
            if (control(0xa1, 0x01, 0x0100, index, &now, 4) != 4 || now != wanted) {
                control(0x21, 0x01, 0x0100, index, &wanted, 4);
                now = 0;
                if (control(0xa1, 0x01, 0x0100, index, &now, 4) != 4 || now != wanted) {
                    LOGI("clock %d: asked %u Hz, dac answers %u", p.clock, wanted, now);
                    return false;
                }
            }
            LOGI("clock %d set to %u Hz, readback %u, range %s", p.clock, wanted, now,
                 supports > 0 ? "lists it" : supports == 0 ? "doesn't list it, sent anyway" : "unreadable");
            settle(index, p.clock);
            // a dac whose clock was already right is very unlikely to be re-locking; no reason to wait on it every song
            auto t = std::chrono::steady_clock::now().time_since_epoch();
            nextLengthReport = static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::seconds>(t).count());
            return true;
        }
        if (!p.rateControl) return true;
        return setEndpointRate(p.endpoint, p.rate);
    }

    // the clock's sampling-frequency ranges: 1 when rate is listed, 0 when it isn't, -1 when the dac won't say
    int rangeSupports(uint16_t index, int clock, uint32_t rate) {
        uint8_t bytes[512] = {};
        int read = control(0xa1, 0x02, 0x0100, index, bytes, sizeof(bytes));
        if (read < 2) return -1;
        int count = bytes[0] | (bytes[1] << 8);
        if (count < 1 || read < 2 + count * 12) return -1;
        std::string listed;
        for (int i = 0; i < count; ++i) {
            const uint8_t *r = bytes + 2 + i * 12;
            uint32_t low = r[0] | (r[1] << 8) | (r[2] << 16) | ((uint32_t)r[3] << 24);
            uint32_t high = r[4] | (r[5] << 8) | (r[6] << 16) | ((uint32_t)r[7] << 24);
            if (!listed.empty()) listed += ",";
            listed += std::to_string(low);
            if (high != low) listed += "-" + std::to_string(high);
            if (rate >= low && rate <= high) {
                LOGI("clock %d GET_RANGE lists %s", clock, listed.c_str());
                return 1;
            }
        }
        LOGI("clock %d GET_RANGE lists %s", clock, listed.c_str());
        return 0;
    }

    // waits for the clock to report itself locked: a dac can answer the rate readback while its pll is still locking, and audio
    // sent then crackles until it does. 15 ms when the dac has no valid control, up to 300 ms when it does
    void settle(uint16_t index, int clock) {
        usleep(15000);
        int lock = lockState(index);
        for (int waited = 15; lock == 0 && waited < 300; waited += 10) {
            usleep(10000);
            lock = lockState(index);
        }
        if (lock == 0) LOGI("clock %d hasn't locked after 300 ms, playing anyway", clock);
    }

    // the clock's own valid bit: 1 locked, 0 not yet, -1 the dac has no such control and settles on its own
    int lockState(uint16_t index) {
        uint8_t valid = 0;
        if (control(0xa1, 0x01, 0x0200, index, &valid, 1) != 1) return -1;
        return valid ? 1 : 0;
    }

    // uac1 sets the rate on the endpoint itself
    bool setEndpointRate(int endpoint, int rate) {
        uint8_t wanted[3] = {static_cast<uint8_t>(rate), static_cast<uint8_t>(rate >> 8), static_cast<uint8_t>(rate >> 16)};
        uint8_t now[3] = {};
        control(0x22, 0x01, 0x0100, static_cast<uint16_t>(endpoint), wanted, 3);
        // some uac1 dacs take the rate but can't say it back; only a different answer counts as refused
        int read = control(0xa2, 0x81, 0x0100, static_cast<uint16_t>(endpoint), now, 3);
        bool ok = read != 3 || memcmp(now, wanted, 3) == 0;
        if (ok) usleep(15000);
        return ok;
    }

    bool implicit() const { return plan.implicitInterface >= 0 && plan.implicitEndpoint >= 0 && plan.implicitFrameBytes > 0; }

    bool configure(const Plan &p) {
        uint32_t period = (highSpeed ? 125u : 1000u) << (p.interval - 1);
        double perPacket = static_cast<double>(p.rate) * period / 1e6;
        size_t bytes = static_cast<size_t>(p.channels) * p.subslot;
        // room for one frame more than nominal, which is as far as a dac's feedback ever asks
        if ((static_cast<size_t>(std::ceil(perPacket)) + 1) * bytes > static_cast<size_t>(p.maxPacket)) return false;
        if (p.sourceChannels < 1 || p.sourceChannels > p.channels) return false;
        bool follows = p.implicitInterface >= 0 && p.implicitEndpoint >= 0;
        if (!claim(p.control) || !claim(p.streaming)) return false;
        if (follows && !claim(p.implicitInterface)) return false;
        if (!io->setAlt(p.streaming, 0)) return false;
        if (follows) io->setAlt(p.implicitInterface, 0);
        if (p.uac2 && !setRate(p)) return false;
        if (!io->setAlt(p.streaming, p.alt)) return false;
        if (!p.uac2 && !setRate(p)) return false;
        // the capture an implicit-feedback dac paces by runs at the same rate, its endpoint told too on uac1
        if (follows) {
            if (!io->setAlt(p.implicitInterface, p.implicitAlt)) return false;
            if (!p.uac2 && p.implicitRateControl) setEndpointRate(p.implicitEndpoint, p.rate);
        }
        plan = p;
        periodUs = period;
        capturePeriodUs = (highSpeed ? 125u : 1000u) << (std::max(1, p.implicitInterval) - 1);
        frameBytes = bytes;
        sourceBytes = static_cast<size_t>(p.sourceChannels) * p.subslot;
        nominal = perPacket;
        pace = perPacket;
        packetsPerUrb = static_cast<int>(std::min<uint32_t>(std::max<uint32_t>(kUrbUs / period, 1), kMaxPackets));
        return true;
    }

    // frames for the next packet, carrying the fraction over so 44.1 khz in 1 ms packets comes out exact
    size_t nextPacketFrames() {
        remainder += pace;
        double whole = std::floor(remainder);
        remainder -= whole;
        return static_cast<size_t>(whole);
    }

    // the dac says how fast it really plays; both usb speeds' number formats are tried, and anything far from the nominal rate is ignored
    void readFeedback(const uint8_t *data, int length) {
        double perFrame;
        if (length >= 4) {
            uint32_t v = data[0] | (data[1] << 8) | (data[2] << 16) | (static_cast<uint32_t>(data[3]) << 24);
            perFrame = v / 65536.0;
        } else if (length == 3) {
            uint32_t v = data[0] | (data[1] << 8) | (data[2] << 16);
            perFrame = v / 16384.0;
        } else {
            return;
        }
        double framesPerUnit = highSpeed ? perFrame * (periodUs / 125.0) : perFrame * (periodUs / 1000.0);
        for (double candidate : {framesPerUnit, framesPerUnit * 4.0, framesPerUnit / 4.0}) {
            if (std::fabs(candidate - nominal) <= nominal * 0.02) {
                pace = candidate;
                if (!loggedFeedback) {
                    loggedFeedback = true;
                    LOGI("feedback: %zu bytes at %u Hz, %.4f raw, asking %.3f frames a packet (nominal %.3f)",
                         (size_t)length, plan.rate, perFrame, candidate, nominal);
                }
                return;
            }
        }
    }

    // an implicit-feedback dac paces by what it captures: the frames that arrived, scaled to an output packet, steer the pace a little each time
    void readCapture(const Slot &slot) {
        if (!slot.ok) return;
        size_t bytes = 0;
        for (int i = 0; i < slot.packets; ++i) bytes += slot.actual[i];
        if (bytes == 0) return;
        double perCapture = static_cast<double>(bytes) / plan.implicitFrameBytes / slot.packets;
        double perOut = perCapture * periodUs / capturePeriodUs;
        if (std::fabs(perOut - nominal) > nominal * 0.02) return;
        pace += (perOut - pace) * 0.1;
    }

    void fill(Slot *slot) {
        size_t total = 0;
        for (int i = 0; i < packetsPerUrb; ++i) {
            size_t frames = nextPacketFrames();
            size_t wanted = frames * frameBytes, room = static_cast<size_t>(plan.maxPacket) / frameBytes * frameBytes;
            // the queue never lets this clamp bite (see fill's ring check), so a clamp here means the dac's own
            // packet size is smaller than its rate needs, which is heard as crackle
            if (wanted > room) ++clampedPackets;
            slot->lengths[i] = static_cast<unsigned>(std::min(wanted, room));
            total += slot->lengths[i];
        }
        // once per stream: what the dac is actually being sent, packet by packet
        if (!loggedLengths) {
            loggedLengths = true;
            size_t minFrames = slot->lengths[0], maxFrames = slot->lengths[0];
            for (int i = 1; i < packetsPerUrb; ++i) {
                minFrames = std::min(minFrames, static_cast<size_t>(slot->lengths[i]));
                maxFrames = std::max(maxFrames, static_cast<size_t>(slot->lengths[i]));
            }
            LOGI("first urb: %d packets, %zu..%zu frames each, %zu bytes a frame", packetsPerUrb,
                 minFrames / frameBytes, maxFrames / frameBytes, frameBytes);
        }
        // "once" would hide a dac whose feedback keeps changing the packet size, so this one comes every 5 s
        auto now = std::chrono::steady_clock::now().time_since_epoch();
        auto secs = static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::seconds>(now).count());
        if (secs >= nextLengthReport) {
            nextLengthReport = secs + 5;
            size_t music = 0;
            for (int i = 0; i < packetsPerUrb; ++i) music += slot->lengths[i];
            LOGI("sending %zu frames in %d packets this urb (pace %.3f, nominal %.3f)", music / frameBytes, packetsPerUrb, pace, nominal);
        }
        size_t music = 0;
        {
            std::lock_guard<std::mutex> guard(lock);
            if (playing) {
                music = std::min(total, ringSize) / frameBytes * frameBytes;
                size_t first = std::min(music, ring.size() - ringStart);
                memcpy(slot->bytes.data(), ring.data() + ringStart, first);
                memcpy(slot->bytes.data() + first, ring.data(), music - first);
                ringStart = (ringStart + music) % ring.size();
                ringSize -= music;
                // silence in the middle of a song is heard as a crackle; count it so the report can name the cause
                if (music < total) starvedPackets += (total - music + frameBytes - 1) / frameBytes;
            }
            slot->generation = generation;
            changed.notify_all();
        }
        // a short queue or a pause goes out as silence, so the dac's clock never loses lock
        memset(slot->bytes.data() + music, 0, total - music);
        slot->musicFrames = music / frameBytes;
    }

    // packets that came back shorter than asked or with an error are what a crackle sounds like
    void countDone(const Slot &slot) {
        for (int i = 0; i < slot.packets; ++i) {
            if (slot.actual[i] < slot.lengths[i]) ++shortPackets;
        }
        if (!slot.ok) ++failedPackets;
    }

    // one line every few seconds while something is going wrong, and one closing summary, so a logcat
    // taken during the crackle says whether it was the host starving the dac or the dac refusing packets
    void report(bool force) {
        auto now = std::chrono::steady_clock::now().time_since_epoch();
        auto secs = static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::seconds>(now).count());
        if (!force && secs < nextReport) return;
        nextReport = secs + 5;
        if (force || starvedPackets || shortPackets || failedPackets || clampedPackets) {
            LOGI("usb pass %d Hz: starved %llu, short %llu, failed %llu, clamped %llu", plan.rate,
                 (unsigned long long)starvedPackets, (unsigned long long)shortPackets,
                 (unsigned long long)failedPackets, (unsigned long long)clampedPackets);
        }
    }

    void prime(Slot *slot) {
        if (slot->kind == Slot::Kind::Out) {
            fill(slot);
            return;
        }
        // feedback and capture packets take whatever fits, the device says how much it sent
        for (int i = 0; i < slot->packets; ++i) slot->lengths[i] = static_cast<unsigned>(slot->bytes.size() / slot->packets);
    }

    void fail(const std::string &why) {
        std::lock_guard<std::mutex> guard(lock);
        if (error.empty()) error = why;
        playing = false;
        changed.notify_all();
    }

    Slot *add(Slot::Kind kind, int endpoint, int packets, size_t packetBytes) {
        auto slot = std::make_unique<Slot>(kind, endpoint, packets, packetBytes);
        if (!io->attach(slot.get())) return nullptr;
        slots.push_back(std::move(slot));
        return slots.back().get();
    }

    void run() {
        // best effort: a late packet is a click, so the worker asks to be scheduled first, as urgently as android lets an app
        if (setpriority(PRIO_PROCESS, 0, -19) != 0) setpriority(PRIO_PROCESS, 0, -16);
        // the capture an implicit-feedback dac paces by starts first, so its pace is known before much music goes out
        if (implicit()) {
            for (int i = 0; i < kCaptureSlots; ++i) add(Slot::Kind::Capture, plan.implicitEndpoint, packetsPerUrb, static_cast<size_t>(plan.implicitMaxPacket));
        }
        if (plan.feedback >= 0 && plan.feedbackMaxPacket > 0) add(Slot::Kind::Feedback, plan.feedback, 1, static_cast<size_t>(plan.feedbackMaxPacket));
        for (int i = 0; i < kSlots; ++i) add(Slot::Kind::Out, plan.endpoint, packetsPerUrb, static_cast<size_t>(plan.maxPacket));
        for (auto &s : slots) {
            prime(s.get());
            if (!io->submit(s.get()) && s->kind == Slot::Kind::Out) {
                fail("the dac refused the stream");
                break;
            }
        }
        while (true) {
            {
                std::lock_guard<std::mutex> guard(lock);
                if (closing || !error.empty()) break;
            }
            bool gone = false;
            Slot *done = io->reap(50, &gone);
            if (gone) {
                fail("the dac was unplugged");
                break;
            }
            if (done == nullptr) continue;
            if (done->kind == Slot::Kind::Feedback) {
                if (done->ok) readFeedback(done->bytes.data(), static_cast<int>(done->actual[0]));
                prime(done);
                io->submit(done);
                continue;
            }
            if (done->kind == Slot::Kind::Capture) {
                readCapture(*done);
                prime(done);
                io->submit(done);
                continue;
            }
            {
                std::lock_guard<std::mutex> guard(lock);
                if (done->generation == generation) played += done->musicFrames;
                countDone(*done);
                report(false);
                changed.notify_all();
                if (closing) continue;
            }
            fill(done);
            if (!io->submit(done)) {
                fail("the dac stopped taking packets");
                break;
            }
        }
        {
            std::lock_guard<std::mutex> guard(lock);
            report(true);
        }
        // the device still owns every transfer in flight; each is cancelled and collected before its memory goes
        for (auto &s : slots) if (s->inFlight) io->cancel(s.get());
        for (int tries = 0; tries < 200; ++tries) {
            bool any = false;
            for (auto &s : slots) any |= s->inFlight;
            if (!any) break;
            bool gone = false;
            io->reap(5, &gone);
        }
        for (auto &s : slots) {
            if (!s->inFlight) io->detach(s.get());
        }
    }

    void close() {
        {
            std::lock_guard<std::mutex> guard(lock);
            closing = true;
            changed.notify_all();
        }
        if (worker.joinable()) worker.join();
        if (plan.alt > 0) io->setAlt(plan.streaming, 0);
        if (implicit()) io->setAlt(plan.implicitInterface, 0);
        giveBack();
    }
};

void throwState(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

Stream *streamOf(jlong handle) { return reinterpret_cast<Stream *>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_mochyy_mochymusic_playback_audio_pass_PassNative_usbOpen(JNIEnv *env, jobject, jint fd, jintArray plans, jint bcdUsb, jint driver, jboolean force) {
    jsize count = env->GetArrayLength(plans);
    if (count <= 0 || count % kPlanInts != 0) {
        throwState(env, "no way to stream to this dac");
        return 0;
    }
    std::vector<jint> values(static_cast<size_t>(count));
    env->GetIntArrayRegion(plans, 0, count, values.data());
    std::string why;
    std::unique_ptr<UsbIo> io = driver == 1 ? usbpass::openLibusb(fd, &why) : usbpass::openUsbfs(fd);
    if (!io) {
        throwState(env, ("libusb couldn't open the dac: " + why).c_str());
        return 0;
    }
    auto *stream = new Stream(std::move(io));
    stream->highSpeed = stream->io->highSpeed(bcdUsb);
    stream->force.store(force);
    for (int i = 0; i < count / kPlanInts; ++i) {
        Plan p = planAt(values.data() + i * kPlanInts);
        if (stream->configure(p)) {
            stream->chosen = i;
            break;
        }
        LOGI("plan %d (%d Hz, %d-bit in %d bytes, %d of %d channels, clock %d) refused", i, p.rate, p.bits, p.subslot, p.sourceChannels, p.channels, p.clock);
    }
    if (stream->chosen < 0) {
        stream->giveBack();
        delete stream;
        throwState(env, "the dac wouldn't run at this song's rate");
        return 0;
    }
    {
        const Plan &p = stream->plan;
        LOGI("plan %d of %d: %s %s iface %d alt %d ep 0x%02x interval %d maxPacket %d, %d ch x %d bytes, %d Hz, feedback %s, implicit %s, source %d ch",
             stream->chosen, count / kPlanInts, stream->highSpeed ? "high" : "full", p.uac2 ? "uac2" : "uac1",
             p.streaming, p.alt, p.endpoint, p.interval, p.maxPacket, p.channels, p.subslot, p.rate,
             p.feedback >= 0 ? "yes" : "no", p.implicitInterface >= 0 ? "yes" : "no", p.sourceChannels);
    }
    // a second of audio waits here, so the player can sleep through a slow background wake-up; pause and seek don't wait on it
    size_t ringBytes = std::max<size_t>(static_cast<size_t>(stream->plan.rate), 4096) * stream->frameBytes;
    stream->ring.resize(ringBytes);
    stream->worker = std::thread([stream] { stream->run(); });
    return reinterpret_cast<jlong>(stream);
}

JNIEXPORT jint JNICALL
Java_com_mochyy_mochymusic_playback_audio_pass_PassNative_usbChosen(JNIEnv *, jobject, jlong handle) {
    return streamOf(handle)->chosen;
}

JNIEXPORT jint JNICALL
Java_com_mochyy_mochymusic_playback_audio_pass_PassNative_usbCapacity(JNIEnv *, jobject, jlong handle) {
    Stream *s = streamOf(handle);
    return static_cast<jint>(s->ring.size() / s->frameBytes);
}

JNIEXPORT jint JNICALL
Java_com_mochyy_mochymusic_playback_audio_pass_PassNative_usbWrite(JNIEnv *env, jobject, jlong handle, jobject buffer, jint offset, jint length) {
    Stream *s = streamOf(handle);
    auto *data = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));
    if (!data) {
        throwState(env, "not a direct buffer");
        return 0;
    }
    std::lock_guard<std::mutex> guard(s->lock);
    if (!s->error.empty()) {
        throwState(env, s->error.c_str());
        return 0;
    }
    const uint8_t *in = data + offset;
    size_t roomFrames = (s->ring.size() - s->ringSize) / s->frameBytes;
    size_t frames = std::min(static_cast<size_t>(length) / s->sourceBytes, roomFrames);
    size_t end = (s->ringStart + s->ringSize) % s->ring.size();
    if (s->sourceBytes == s->frameBytes) {
        size_t take = frames * s->frameBytes;
        size_t first = std::min(take, s->ring.size() - end);
        memcpy(s->ring.data() + end, in, first);
        memcpy(s->ring.data(), in + first, take - first);
    } else {
        // the song's channels go first in each of the dac's frames, the rest are silent; frames never straddle the ring's end
        for (size_t f = 0; f < frames; ++f) {
            memcpy(s->ring.data() + end, in + f * s->sourceBytes, s->sourceBytes);
            memset(s->ring.data() + end + s->sourceBytes, 0, s->frameBytes - s->sourceBytes);
            end = (end + s->frameBytes) % s->ring.size();
        }
    }
    s->ringSize += frames * s->frameBytes;
    return static_cast<jint>(frames * s->sourceBytes);
}

JNIEXPORT void JNICALL
Java_com_mochyy_mochymusic_playback_audio_pass_PassNative_usbPlay(JNIEnv *env, jobject, jlong handle, jboolean playing) {
    Stream *s = streamOf(handle);
    std::lock_guard<std::mutex> guard(s->lock);
    if (!s->error.empty()) {
        throwState(env, s->error.c_str());
        return;
    }
    s->playing = playing;
}

JNIEXPORT void JNICALL
Java_com_mochyy_mochymusic_playback_audio_pass_PassNative_usbFlush(JNIEnv *, jobject, jlong handle) {
    Stream *s = streamOf(handle);
    std::lock_guard<std::mutex> guard(s->lock);
    s->playing = false;
    s->ringStart = 0;
    s->ringSize = 0;
    s->played = 0;
    s->pace = s->nominal;
    s->remainder = 0;
    // packets already with the dac finish, but no longer count towards the new start
    s->generation++;
}

JNIEXPORT jlong JNICALL
Java_com_mochyy_mochymusic_playback_audio_pass_PassNative_usbPlayed(JNIEnv *env, jobject, jlong handle) {
    Stream *s = streamOf(handle);
    std::lock_guard<std::mutex> guard(s->lock);
    if (!s->error.empty()) {
        throwState(env, s->error.c_str());
        return 0;
    }
    return static_cast<jlong>(s->played);
}

JNIEXPORT jint JNICALL
Java_com_mochyy_mochymusic_playback_audio_pass_PassNative_usbControl(JNIEnv *env, jobject, jlong handle, jint type, jint request, jint value, jint index, jbyteArray data) {
    Stream *s = streamOf(handle);
    jsize length = env->GetArrayLength(data);
    std::vector<uint8_t> bytes(static_cast<size_t>(length));
    env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(bytes.data()));
    int result = s->control(static_cast<uint8_t>(type), static_cast<uint8_t>(request), static_cast<uint16_t>(value),
                            static_cast<uint16_t>(index), bytes.data(), static_cast<uint16_t>(length));
    if (result > 0 && (type & 0x80)) env->SetByteArrayRegion(data, 0, result, reinterpret_cast<const jbyte *>(bytes.data()));
    return result;
}

JNIEXPORT void JNICALL
Java_com_mochyy_mochymusic_playback_audio_pass_PassNative_usbClose(JNIEnv *, jobject, jlong handle) {
    Stream *s = streamOf(handle);
    s->close();
    delete s;
}

}  // extern "C"
