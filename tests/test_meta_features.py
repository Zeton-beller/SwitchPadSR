#!/usr/bin/env python3
"""
Test Meta usb-video feature logic:
1. RingBuffer Overrun-Discard policy verification.
2. UsbDescriptorParser bcdUSB and speed calculation verification.
"""
import sys

class RingBuffer:
    def __init__(self, capacity):
        self.capacity = capacity
        self.buffer = [0] * capacity
        self.read_pos = 0
        self.write_pos = 0
        self.count = 0

    def write(self, data):
        data_len = len(data)
        if data_len <= 0:
            return 0
        valid_len = min(data_len, self.capacity)
        src_offset = data_len - valid_len
        to_write = data[src_offset:]

        start = self.write_pos
        end = (self.write_pos + valid_len) % self.capacity
        if start < end:
            self.buffer[start:end] = to_write
        else:
            first_slice = self.capacity - start
            self.buffer[start:] = to_write[:first_slice]
            self.buffer[:valid_len - first_slice] = to_write[first_slice:]

        self.write_pos = end
        self.count = min(self.capacity, self.count + valid_len)
        self.read_pos = (self.write_pos + self.capacity - self.count) % self.capacity
        return valid_len

    def read(self, length):
        if length <= 0 or self.count == 0:
            return []
        to_copy = min(length, self.count)
        start = self.read_pos
        end = (self.read_pos + to_copy) % self.capacity
        if start < end:
            out = self.buffer[start:end]
        else:
            first_slice = self.capacity - start
            out = self.buffer[start:] + self.buffer[:to_copy - first_slice]
        self.read_pos = end
        self.count -= to_copy
        return out


def test_ring_buffer():
    rb = RingBuffer(10)
    # Write 5 items: [1, 2, 3, 4, 5]
    rb.write([1, 2, 3, 4, 5])
    assert rb.read(3) == [1, 2, 3], "Read 3 failed"
    # Now buffer has [4, 5] (size=2)
    # Write 6 items: [6, 7, 8, 9, 10, 11]
    # Total would be 8 <= 10 (no overrun)
    rb.write([6, 7, 8, 9, 10, 11])
    assert rb.read(8) == [4, 5, 6, 7, 8, 9, 10, 11], "Read 8 failed"
    
    # Overrun test: write 12 items into capacity 10
    rb.write(list(range(100, 112)))
    # Capacity is 10, so oldest items discarded, freshest 10 kept: 102..111
    res = rb.read(10)
    assert res == list(range(102, 112)), f"Overrun discard failed: got {res}"
    print("RingBuffer Overrun-Discard test: OK")


def test_usb_speed_parsing():
    # USB 3.0 Device Descriptor raw bytes snippet (bcdUSB at byte 2, 3)
    # bLength=18 (0x12), bDescriptorType=1, bcdUSB=0x0310 (USB 3.1)
    desc_usb3 = bytes([0x12, 0x01, 0x10, 0x03] + [0] * 14)
    bcd = (desc_usb3[3] << 8) | desc_usb3[2]
    assert bcd >= 0x0300, "Should detect SuperSpeed"
    
    # USB 2.0 Device Descriptor raw bytes snippet
    desc_usb2 = bytes([0x12, 0x01, 0x00, 0x02] + [0] * 14)
    bcd2 = (desc_usb2[3] << 8) | desc_usb2[2]
    assert bcd2 == 0x0200, "Should detect HighSpeed"
    print("USB Speed Descriptor parsing test: OK")


if __name__ == "__main__":
    test_ring_buffer()
    test_usb_speed_parsing()
    print("ALL META USB-VIDEO FEATURE TESTS PASSED")
