package com.zet.switchpadsr.capture

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * UVC / USB Raw Descriptor Parser, adapted from Meta's facebookexperimental/usb-video.
 *
 * Efficiently parses USB Device, Configuration, Interface and Endpoint descriptors directly
 * from raw USB descriptor bytes returned by the Linux/Android USB subsystem.
 */
class UsbDescriptorParser(private val rawDescriptors: ByteArray) {

    companion object {
        const val USB_DT_DEVICE = 0x01
        const val USB_DT_CONFIG = 0x02
        const val USB_DT_STRING = 0x03
        const val USB_DT_INTERFACE = 0x04
        const val USB_DT_ENDPOINT = 0x05
        const val USB_DT_IAD = 0x0B
        const val USB_DT_CS_INTERFACE = 0x24

        // UVC Video Streaming subtypes
        const val UVC_VS_FORMAT_UNCOMPRESSED = 0x04
        const val UVC_VS_FRAME_UNCOMPRESSED = 0x05
        const val UVC_VS_FORMAT_MJPEG = 0x06
        const val UVC_VS_FRAME_MJPEG = 0x07

        // Audio Streaming subclass
        const val USB_CLASS_AUDIO = 0x01
        const val USB_SUBCLASS_AUDIO_STREAMING = 0x02
    }

    data class RawDescriptor(
        val offset: Int,
        val bLength: Int,
        val bDescriptorType: Int,
        val buffer: ByteBuffer
    )

    fun descriptors(): Sequence<RawDescriptor> = sequence {
        val pack = ByteBuffer.wrap(rawDescriptors).order(ByteOrder.LITTLE_ENDIAN)
        while (pack.hasRemaining()) {
            val pos = pack.position()
            if (pack.remaining() < 2) break
            val len = pack.get().toInt() and 0xFF
            if (len == 0 || pos + len > rawDescriptors.size) break
            val type = pack.get().toInt() and 0xFF
            val slice = ByteBuffer.wrap(rawDescriptors, pos, len).order(ByteOrder.LITTLE_ENDIAN)
            yield(RawDescriptor(pos, len, type, slice))
            pack.position(pos + len)
        }
    }

    /**
     * Parse the bcdUSB field from device descriptor (offset 2..3).
     * 0x0300..0x0320: USB 3.0+ SuperSpeed (5Gbps)
     * 0x0200..0x0210: USB 2.0 HighSpeed (480Mbps)
     */
    fun getBcdUsb(): Int {
        val devDesc = descriptors().firstOrNull { it.bDescriptorType == USB_DT_DEVICE && it.bLength >= 18 }
            ?: return 0x0200
        val bcdLow = devDesc.buffer.get(2).toInt() and 0xFF
        val bcdHigh = devDesc.buffer.get(3).toInt() and 0xFF
        return (bcdHigh shl 8) or bcdLow
    }

    fun isSuperSpeed(): Boolean = getBcdUsb() >= 0x0300

    fun getUsbSpeedDescription(): String {
        val bcd = getBcdUsb()
        return when {
            bcd >= 0x0320 -> "USB 3.2 SuperSpeed+ (10Gbps)"
            bcd >= 0x0310 -> "USB 3.1 SuperSpeed (10Gbps)"
            bcd >= 0x0300 -> "USB 3.0 SuperSpeed (5Gbps)"
            bcd >= 0x0210 -> "USB 2.1 HighSpeed (480Mbps)"
            bcd >= 0x0200 -> "USB 2.0 HighSpeed (480Mbps)"
            bcd >= 0x0110 -> "USB 1.1 FullSpeed (12Mbps)"
            else -> "USB (480Mbps)"
        }
    }

    /**
     * Checks if the device has uncompressed format (YUY2/NV12) and MJPEG in video streaming descriptors.
     */
    fun hasUncompressedFormat(): Boolean {
        return descriptors().any {
            it.bDescriptorType == USB_DT_CS_INTERFACE &&
            it.bLength >= 3 &&
            it.buffer.get(2).toInt() and 0xFF == UVC_VS_FORMAT_UNCOMPRESSED
        }
    }

    fun hasMjpegFormat(): Boolean {
        return descriptors().any {
            it.bDescriptorType == USB_DT_CS_INTERFACE &&
            it.bLength >= 3 &&
            it.buffer.get(2).toInt() and 0xFF == UVC_VS_FORMAT_MJPEG
        }
    }

    fun hasAudioStreamingInterface(): Boolean {
        return descriptors().any {
            it.bDescriptorType == USB_DT_INTERFACE &&
            it.bLength >= 9 &&
            (it.buffer.get(5).toInt() and 0xFF) == USB_CLASS_AUDIO &&
            (it.buffer.get(6).toInt() and 0xFF) == USB_SUBCLASS_AUDIO_STREAMING
        }
    }
}
