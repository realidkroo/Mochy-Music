// usb_io.h

// the few things the usb driver asks of the kernel, behind one face: mochy's own usbdevfs calls or libusb do the same jobs
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace usbpass {

constexpr int kMaxPackets = 64;

// one isochronous transfer and its buffer; it stays allocated until it's back from the device
struct Slot {
    enum class Kind { Out, Feedback, Capture };

    Slot(Kind kind, int endpoint, int packets, size_t packetBytes)
        : kind(kind), endpoint(endpoint), packets(packets), bytes(static_cast<size_t>(packets) * packetBytes) {}

    Kind kind;
    int endpoint;
    int packets;
    std::vector<uint8_t> bytes;
    // what each packet carries out, or can take in
    unsigned lengths[kMaxPackets] = {};
    // what each packet carried once it's back
    unsigned actual[kMaxPackets] = {};
    bool ok = false;
    bool gone = false;
    bool inFlight = false;
    uint64_t musicFrames = 0;
    uint64_t generation = 0;
    // the backend's own transfer and itself, for its callback
    void *native = nullptr;
    void *owner = nullptr;
};

class UsbIo {
public:
    virtual ~UsbIo() = default;

    virtual bool highSpeed(int bcdUsb) = 0;
    // takes the interface from the kernel's audio driver; release gives it back so the dac is an ordinary output again
    virtual bool claim(int iface) = 0;
    virtual void release(int iface) = 0;
    virtual bool setAlt(int iface, int alt) = 0;
    virtual int control(uint8_t type, uint8_t request, uint16_t value, uint16_t index, void *data, uint16_t length) = 0;
    // makes and frees the backend's transfer for a slot
    virtual bool attach(Slot *slot) = 0;
    virtual void detach(Slot *slot) = 0;
    virtual bool submit(Slot *slot) = 0;
    // one slot back from the device within [ms], or null; [gone] when the dac was unplugged
    virtual Slot *reap(int ms, bool *gone) = 0;
    virtual void cancel(Slot *slot) = 0;
};

std::unique_ptr<UsbIo> openUsbfs(int fd);

// null with [error] filled when libusb can't take the dac
std::unique_ptr<UsbIo> openLibusb(int fd, std::string *error);

}  // namespace usbpass
