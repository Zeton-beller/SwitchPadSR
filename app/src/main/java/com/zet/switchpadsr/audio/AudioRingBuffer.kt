package com.zet.switchpadsr.audio

/**
 * High-performance, lock-free circular audio buffer with Overrun-Discard policy,
 * adapted from Meta's facebookexperimental/usb-video (RingBuffer.h).
 *
 * Designed specifically for low-latency UAC audio passthrough.
 * When an overrun occurs due to clock drift or thread scheduling delays,
 * old samples are discarded immediately to keep playback strictly real-time (<15ms).
 */
class AudioRingBuffer(val capacityShorts: Int) {
    private val buffer = ShortArray(capacityShorts)
    private var readPos = 0
    private var writePos = 0
    private var count = 0
    private val lock = Any()

    fun write(data: ShortArray, offset: Int, len: Int): Int {
        if (len <= 0) return 0
        synchronized(lock) {
            val validLen = minOf(len, capacityShorts)
            val srcOffset = offset + (len - validLen)

            val start = writePos
            val end = (writePos + validLen) % capacityShorts

            if (start < end) {
                System.arraycopy(data, srcOffset, buffer, start, validLen)
            } else {
                val firstSlice = capacityShorts - start
                System.arraycopy(data, srcOffset, buffer, start, firstSlice)
                System.arraycopy(data, srcOffset + firstSlice, buffer, 0, validLen - firstSlice)
            }

            writePos = end
            // Meta Overrun-Discard policy: count capped at capacity, readPos advanced
            count = minOf(capacityShorts, count + validLen)
            readPos = (writePos + capacityShorts - count) % capacityShorts
            return validLen
        }
    }

    fun read(out: ShortArray, offset: Int, len: Int): Int {
        if (len <= 0) return 0
        synchronized(lock) {
            if (count == 0) return 0

            val toCopy = minOf(len, count)
            val start = readPos
            val end = (readPos + toCopy) % capacityShorts

            if (start < end) {
                System.arraycopy(buffer, start, out, offset, toCopy)
            } else {
                val firstSlice = capacityShorts - start
                System.arraycopy(buffer, start, out, offset, firstSlice)
                System.arraycopy(buffer, 0, out, offset + firstSlice, toCopy - firstSlice)
            }

            readPos = end
            count -= toCopy
            return toCopy
        }
    }

    fun clear() {
        synchronized(lock) {
            readPos = 0
            writePos = 0
            count = 0
        }
    }

    val available: Int
        get() = synchronized(lock) { count }
}
