// usb_io_usbfs.cpp

// mochy's own way to the dac: usbdevfs ioctls on the file descriptor android hands over, nothing in between

#include "usb_io.h"

#include <linux/usbdevice_fs.h>
#include <poll.h>
#include <sys/ioctl.h>

#include <cerrno>
#include <cstdlib>
#include <cstring>

namespace usbpass {

namespace {

class UsbfsIo : public UsbIo {
public:
    explicit UsbfsIo(int fd) : fd_(fd) {}

    bool highSpeed(int bcdUsb) override {
        int speed = ioctl(fd_, USBDEVFS_GET_SPEED);
        return speed >= 0 ? speed >= 3 : bcdUsb >= 0x0200;
    }

    bool claim(int iface) override {
        // the kernel's usb audio driver lets go, and the interface is ours until we hand it back
        usbdevfs_disconnect_claim take{};
        take.interface = static_cast<unsigned>(iface);
        take.flags = 0;
        if (ioctl(fd_, USBDEVFS_DISCONNECT_CLAIM, &take) == 0) return true;
        unsigned int number = static_cast<unsigned>(iface);
        return ioctl(fd_, USBDEVFS_CLAIMINTERFACE, &number) == 0;
    }

    void release(int iface) override {
        unsigned int number = static_cast<unsigned>(iface);
        ioctl(fd_, USBDEVFS_RELEASEINTERFACE, &number);
        // android's own driver takes it again, so the dac is an ordinary output once more
        usbdevfs_ioctl command{};
        command.ifno = iface;
        command.ioctl_code = USBDEVFS_CONNECT;
        command.data = nullptr;
        ioctl(fd_, USBDEVFS_IOCTL, &command);
    }

    bool setAlt(int iface, int alt) override {
        usbdevfs_setinterface set{};
        set.interface = static_cast<unsigned>(iface);
        set.altsetting = static_cast<unsigned>(alt);
        return ioctl(fd_, USBDEVFS_SETINTERFACE, &set) == 0;
    }

    int control(uint8_t type, uint8_t request, uint16_t value, uint16_t index, void *data, uint16_t length) override {
        usbdevfs_ctrltransfer transfer{};
        transfer.bRequestType = type;
        transfer.bRequest = request;
        transfer.wValue = value;
        transfer.wIndex = index;
        transfer.wLength = length;
        transfer.timeout = 1000;
        transfer.data = data;
        int result = ioctl(fd_, USBDEVFS_CONTROL, &transfer);
        return result < 0 ? -errno : result;
    }

    bool attach(Slot *slot) override {
        slot->native = calloc(1, sizeof(usbdevfs_urb) + slot->packets * sizeof(usbdevfs_iso_packet_desc));
        slot->owner = this;
        return slot->native != nullptr;
    }

    void detach(Slot *slot) override {
        free(slot->native);
        slot->native = nullptr;
    }

    bool submit(Slot *slot) override {
        auto *urb = static_cast<usbdevfs_urb *>(slot->native);
        memset(urb, 0, sizeof(usbdevfs_urb) + slot->packets * sizeof(usbdevfs_iso_packet_desc));
        size_t total = 0;
        for (int i = 0; i < slot->packets; ++i) total += slot->lengths[i];
        urb->type = USBDEVFS_URB_TYPE_ISO;
        urb->endpoint = static_cast<unsigned char>(slot->endpoint);
        urb->flags = USBDEVFS_URB_ISO_ASAP;
        urb->buffer = slot->bytes.data();
        urb->buffer_length = static_cast<int>(total);
        urb->number_of_packets = slot->packets;
        urb->usercontext = slot;
        for (int i = 0; i < slot->packets; ++i) urb->iso_frame_desc[i].length = slot->lengths[i];
        if (ioctl(fd_, USBDEVFS_SUBMITURB, urb) < 0) return false;
        slot->inFlight = true;
        return true;
    }

    Slot *reap(int ms, bool *gone) override {
        if (Slot *slot = reapNow(gone)) return slot;
        pollfd waiter{fd_, POLLOUT, 0};
        int ready = poll(&waiter, 1, ms);
        if ((ready < 0 && errno != EINTR) || (waiter.revents & (POLLHUP | POLLERR))) {
            *gone = true;
            return nullptr;
        }
        return reapNow(gone);
    }

    void cancel(Slot *slot) override { ioctl(fd_, USBDEVFS_DISCARDURB, slot->native); }

private:
    Slot *reapNow(bool *gone) {
        usbdevfs_urb *done = nullptr;
        if (ioctl(fd_, USBDEVFS_REAPURBNDELAY, &done) != 0 || done == nullptr) return nullptr;
        auto *slot = static_cast<Slot *>(done->usercontext);
        slot->inFlight = false;
        slot->gone = done->status == -ENODEV || done->status == -ESHUTDOWN;
        if (slot->gone) *gone = true;
        slot->ok = done->status == 0;
        for (int i = 0; i < slot->packets; ++i) slot->actual[i] = done->iso_frame_desc[i].status == 0 ? done->iso_frame_desc[i].actual_length : 0;
        return slot;
    }

    int fd_;
};

}  // namespace

std::unique_ptr<UsbIo> openUsbfs(int fd) { return std::make_unique<UsbfsIo>(fd); }

}  // namespace usbpass
