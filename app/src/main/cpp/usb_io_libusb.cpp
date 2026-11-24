// usb_io_libusb.cpp

// the same jobs through libusb (third_party/libusb, LGPL-2.1), for dacs that behave better with it; it wraps android's file descriptor and never scans for devices itself

#include "usb_io.h"

#include <libusb.h>

#include <deque>

namespace usbpass {

namespace {

class LibusbIo : public UsbIo {
public:
    ~LibusbIo() override {
        if (handle_) libusb_close(handle_);
        if (context_) libusb_exit(context_);
    }

    bool open(int fd, std::string *error) {
        // android apps can't list /dev/bus/usb, so libusb is told not to try and is handed the dac's descriptor instead
        libusb_init_option options[] = {{LIBUSB_OPTION_NO_DEVICE_DISCOVERY, {0}}};
        int rc = libusb_init_context(&context_, options, 1);
        if (rc == 0) rc = libusb_wrap_sys_device(context_, static_cast<intptr_t>(fd), &handle_);
        if (rc != 0 && error) *error = libusb_error_name(rc);
        return rc == 0;
    }

    bool highSpeed(int bcdUsb) override {
        int speed = libusb_get_device_speed(libusb_get_device(handle_));
        return speed == LIBUSB_SPEED_UNKNOWN ? bcdUsb >= 0x0200 : speed >= LIBUSB_SPEED_HIGH;
    }

    bool claim(int iface) override {
        libusb_detach_kernel_driver(handle_, iface);
        return libusb_claim_interface(handle_, iface) == 0;
    }

    void release(int iface) override {
        libusb_release_interface(handle_, iface);
        libusb_attach_kernel_driver(handle_, iface);
    }

    bool setAlt(int iface, int alt) override { return libusb_set_interface_alt_setting(handle_, iface, alt) == 0; }

    int control(uint8_t type, uint8_t request, uint16_t value, uint16_t index, void *data, uint16_t length) override {
        return libusb_control_transfer(handle_, type, request, value, index, static_cast<unsigned char *>(data), length, 1000);
    }

    bool attach(Slot *slot) override {
        slot->native = libusb_alloc_transfer(slot->packets);
        slot->owner = this;
        return slot->native != nullptr;
    }

    void detach(Slot *slot) override {
        libusb_free_transfer(static_cast<libusb_transfer *>(slot->native));
        slot->native = nullptr;
    }

    bool submit(Slot *slot) override {
        auto *transfer = static_cast<libusb_transfer *>(slot->native);
        int total = 0;
        for (int i = 0; i < slot->packets; ++i) total += static_cast<int>(slot->lengths[i]);
        libusb_fill_iso_transfer(transfer, handle_, static_cast<unsigned char>(slot->endpoint), slot->bytes.data(), total, slot->packets, done, slot, 0);
        for (int i = 0; i < slot->packets; ++i) transfer->iso_packet_desc[i].length = slot->lengths[i];
        if (libusb_submit_transfer(transfer) != 0) return false;
        slot->inFlight = true;
        return true;
    }

    Slot *reap(int ms, bool *gone) override {
        if (finished_.empty()) {
            timeval wait{0, ms * 1000};
            if (libusb_handle_events_timeout_completed(context_, &wait, nullptr) == LIBUSB_ERROR_NO_DEVICE) *gone = true;
        }
        if (finished_.empty()) return nullptr;
        Slot *slot = finished_.front();
        finished_.pop_front();
        if (slot->gone) *gone = true;
        return slot;
    }

    void cancel(Slot *slot) override { libusb_cancel_transfer(static_cast<libusb_transfer *>(slot->native)); }

private:
    // runs inside libusb_handle_events on the worker, so the queue needs no lock
    static void LIBUSB_CALL done(libusb_transfer *transfer) {
        auto *slot = static_cast<Slot *>(transfer->user_data);
        auto *self = static_cast<LibusbIo *>(slot->owner);
        slot->inFlight = false;
        slot->ok = transfer->status == LIBUSB_TRANSFER_COMPLETED;
        slot->gone = transfer->status == LIBUSB_TRANSFER_NO_DEVICE;
        for (int i = 0; i < slot->packets; ++i) {
            const libusb_iso_packet_descriptor &p = transfer->iso_packet_desc[i];
            slot->actual[i] = p.status == LIBUSB_TRANSFER_COMPLETED ? p.actual_length : 0;
        }
        self->finished_.push_back(slot);
    }

    libusb_context *context_ = nullptr;
    libusb_device_handle *handle_ = nullptr;
    std::deque<Slot *> finished_;
};

}  // namespace

std::unique_ptr<UsbIo> openLibusb(int fd, std::string *error) {
    auto io = std::make_unique<LibusbIo>();
    if (!io->open(fd, error)) return nullptr;
    return io;
}

}  // namespace usbpass
