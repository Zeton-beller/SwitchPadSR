package com.zet.switchpadsr.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam

enum class CaptureStatus { IDLE, NO_DEVICE, REQUESTING_PERMISSION, OPENING, STREAMING, ERROR }

data class CaptureInfo(
    val status: CaptureStatus = CaptureStatus.IDLE,
    val deviceName: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val message: String = "",
    val usbSpeed: String = "",
    val formatName: String = ""
)

/**
 * UVC capture card input, built on the libuvc based UVCAndroid library
 * (com.herohan:UVCAndroid, the maintained fork of saki4510t/UVCCamera).
 * Frames are decoded natively (MJPEG/YUYV) straight into the SurfaceTexture surface of the renderer.
 */
class UvcCapture(
    context: Context,
    private val onSize: (Int, Int) -> Unit,
    private val onInfo: (CaptureInfo) -> Unit
) {
    companion object { private const val TAG = "SwitchPadSR-UVC" }

    private val appContext = context.applicationContext
    private val lock = Any()
    private var monitor: USBMonitor? = null
    private var camera: UVCCamera? = null
    private var surface: Surface? = null
    private var previewing = false
    private var opening = false
    private var chosen: Size? = null
    private var deviceName = ""
    private var started = false
    private var info = CaptureInfo()

    var formatPreference: String = "AUTO" // "AUTO", "YUY2", "MJPEG"
    private var currentUsbSpeed: String = ""
    private var currentFormatName: String = ""

    private fun push(f: (CaptureInfo) -> CaptureInfo) {
        info = f(info)
        onInfo(info)
    }

    private val listener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice) {
            Log.i(TAG, "onAttach ${device.deviceName}")
            if (isVideoDevice(device)) requestDevice(device)
        }

        override fun onDetach(device: UsbDevice) {
            Log.i(TAG, "onDetach ${device.deviceName}")
            if (device.deviceName == deviceName || camera == null) {
                closeCamera()
                push { CaptureInfo(CaptureStatus.NO_DEVICE, message = "采集卡已拔出") }
            }
        }

        override fun onDeviceOpen(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock, createNew: Boolean) {
            Log.i(TAG, "onDeviceOpen ${device.deviceName} new=$createNew")
            openCamera(device, ctrlBlock)
        }

        override fun onDeviceClose(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
            Log.i(TAG, "onDeviceClose")
            closeCamera()
        }

        override fun onCancel(device: UsbDevice) {
            synchronized(lock) { opening = false }
            val hasCamera = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val msg = if (!hasCamera) {
                "缺少「相机」权限，Android 系统拦截了 USB 采集卡授权！请在下方点击授权或前往系统设置授予相机权限。"
            } else {
                "USB 权限已取消。若未见系统弹窗，请检查 HyperOS 的「后台弹出界面」权限或重新拔插采集卡。"
            }
            push { CaptureInfo(CaptureStatus.ERROR, device.productNameSafe(), message = msg) }
        }

        override fun onError(device: UsbDevice?, e: USBMonitor.USBException) {
            Log.e(TAG, "USB error", e)
            synchronized(lock) { opening = false }
            push { CaptureInfo(CaptureStatus.ERROR, message = "USB 错误: ${e.message}") }
        }
    }

    fun isVideoDevice(d: UsbDevice): Boolean {
        if (d.deviceClass == UsbConstants.USB_CLASS_VIDEO) return true
        if (d.deviceClass == 239 /* misc / IAD */) return true
        for (i in 0 until d.interfaceCount) {
            val cls = d.getInterface(i).interfaceClass
            if (cls == UsbConstants.USB_CLASS_VIDEO || cls == 14) return true
        }
        val name = (d.productName ?: d.deviceName).lowercase()
        return name.contains("capture") || name.contains("cam") || name.contains("hdmi") ||
               name.contains("ms2109") || name.contains("ms2130") || name.contains("shadowcast")
    }

    /** Start monitoring; connects to the first UVC device found. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        push { CaptureInfo(CaptureStatus.NO_DEVICE, message = "正在查找采集卡…") }
        try {
            val m = USBMonitor(appContext, listener)
            monitor = m
            m.register()
        } catch (e: Throwable) {
            Log.e(TAG, "USBMonitor register failed", e)
            synchronized(lock) { started = false }
            push { CaptureInfo(CaptureStatus.ERROR, message = "USB 监听启动失败: ${e.message}") }
            return
        }
        scanExisting()
    }

    private fun scanExisting() {
        try {
            val um = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
            val dev = um.deviceList.values.firstOrNull { isVideoDevice(it) }
            if (dev != null) requestDevice(dev)
            else push { CaptureInfo(CaptureStatus.NO_DEVICE, message = "未检测到采集卡，请通过 USB 连接并开启底座") }
        } catch (e: Throwable) {
            Log.e(TAG, "scan failed", e)
        }
    }

    private fun requestDevice(device: UsbDevice) {
        synchronized(lock) {
            if (opening || camera != null) return
            opening = true
        }
        push { CaptureInfo(CaptureStatus.REQUESTING_PERMISSION, device.productNameSafe(), message = "请求 USB 权限…") }
        try {
            monitor?.requestPermission(device)
        } catch (e: Throwable) {
            Log.e(TAG, "requestPermission failed", e)
            synchronized(lock) { opening = false }
            push { CaptureInfo(CaptureStatus.ERROR, message = "请求权限失败: ${e.message}") }
        }
    }

    private fun UsbDevice.productNameSafe(): String =
        try { productName ?: deviceName } catch (_: Throwable) { deviceName }

    private fun pickSize(cam: UVCCamera, isSuperSpeed: Boolean): Size? {
        val list: List<Size> = try { cam.supportedSizeList ?: emptyList() } catch (_: Throwable) { emptyList() }
        Log.i(TAG, "supported sizes: " + list.joinToString { "${it.width}x${it.height}@${it.fps}(fmt=${it.type})" })
        fun maxFps(s: Size): Int = (s.fpsList?.maxOrNull() ?: s.fps).coerceAtLeast(30)

        // Zero-compression latency optimization inspired by Meta's facebookexperimental/usb-video:
        // On USB 3.0 / SuperSpeed (5Gbps), uncompressed YUY2 @ 60fps eliminates all compression and decompression delays (~15ms saved).
        // On USB 2.0 / HighSpeed (480Mbps), bandwidth constraint requires MJPEG for 60fps.
        val preferYuy2 = when (formatPreference) {
            "YUY2" -> true
            "MJPEG" -> false
            else -> isSuperSpeed // AUTO
        }

        val best = list.maxByOrNull { s ->
            val fps = maxFps(s)
            val resScore = when {
                s.width == 1920 && s.height == 1080 -> 250_000
                s.width == 1280 && s.height == 720 -> 120_000
                else -> (s.width * s.height) / 10
            }
            val formatBonus = if (preferYuy2) {
                if (s.type == UVCCamera.FRAME_FORMAT_YUYV) 75_000 else 0
            } else {
                if (s.type == UVCCamera.FRAME_FORMAT_MJPEG) 75_000 else 0
            }
            resScore + fps * 1_000 + formatBonus
        } ?: return null
        return best.clone().also { it.fps = maxFps(best) }
    }

    private fun openCamera(device: UsbDevice, ctrl: USBMonitor.UsbControlBlock) {
        try {
            val rawDesc = try { ctrl.rawDescriptors } catch (_: Throwable) { null }
            val parser = if (rawDesc != null) UsbDescriptorParser(rawDesc) else null
            val isSuperSpeed = parser?.isSuperSpeed() == true || ctrl.usbVersion?.startsWith("3") == true
            currentUsbSpeed = parser?.getUsbSpeedDescription() ?: if (isSuperSpeed) "USB 3.0 (5Gbps)" else "USB 2.0 (480Mbps)"

            val cam = UVCCamera(UVCParam())
            cam.open(ctrl)
            var size = pickSize(cam, isSuperSpeed)
            var applied = false
            if (size != null) {
                try { cam.setPreviewSize(size); applied = true } catch (e: Throwable) { Log.w(TAG, "setPreviewSize(Size) failed", e) }
            }
            if (!applied) {
                val fallbackFormats = if (isSuperSpeed) intArrayOf(UVCCamera.FRAME_FORMAT_YUYV, UVCCamera.FRAME_FORMAT_MJPEG)
                                       else intArrayOf(UVCCamera.FRAME_FORMAT_MJPEG, UVCCamera.FRAME_FORMAT_YUYV)
                for (fmt in fallbackFormats) {
                    try { cam.setPreviewSize(1920, 1080, fmt); size = Size(fmt, 1920, 1080, 30, null); applied = true; break }
                    catch (e: Throwable) { Log.w(TAG, "setPreviewSize fmt=$fmt failed", e) }
                }
            }
            if (!applied || size == null) throw IllegalStateException("采集卡不支持可用的预览分辨率")

            currentFormatName = when (size.type) {
                UVCCamera.FRAME_FORMAT_YUYV -> "YUY2 (0编解码延迟)"
                UVCCamera.FRAME_FORMAT_MJPEG -> "MJPEG"
                else -> "RAW"
            }

            synchronized(lock) {
                camera = cam
                chosen = size
                deviceName = device.deviceName
                opening = false
                previewing = false
            }
            push {
                CaptureInfo(
                    status = CaptureStatus.OPENING,
                    deviceName = device.productNameSafe(),
                    width = size.width,
                    height = size.height,
                    fps = size.fps,
                    message = "已连接，等待渲染表面…",
                    usbSpeed = currentUsbSpeed,
                    formatName = currentFormatName
                )
            }
            onSize(size.width, size.height)
            startPreviewIfReady()
        } catch (e: Throwable) {
            Log.e(TAG, "openCamera failed", e)
            synchronized(lock) { opening = false }
            closeCamera()
            push { CaptureInfo(CaptureStatus.ERROR, message = "打开采集卡失败: ${e.message}") }
        }
    }

    private fun startPreviewIfReady() {
        val (cam, s, size) = synchronized(lock) { Triple(camera, surface, chosen) }
        if (cam == null || s == null || size == null) return
        synchronized(lock) { if (previewing) return }
        try {
            cam.setPreviewDisplay(s)
            cam.startPreview()
            synchronized(lock) { previewing = true }
            push {
                CaptureInfo(
                    status = CaptureStatus.STREAMING,
                    deviceName = deviceName,
                    width = size.width,
                    height = size.height,
                    fps = size.fps,
                    message = "",
                    usbSpeed = currentUsbSpeed,
                    formatName = currentFormatName
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "startPreview failed", e)
            push { CaptureInfo(CaptureStatus.ERROR, deviceName, message = "启动预览失败: ${e.message}") }
        }
    }

    /** Called with the renderer's input surface (or null when it is destroyed). */
    fun attachSurface(s: Surface?) {
        val cam: UVCCamera?
        synchronized(lock) {
            surface = s
            cam = camera
        }
        if (s == null) {
            if (cam != null) {
                try { cam.stopPreview() } catch (_: Throwable) {}
                synchronized(lock) { previewing = false }
            }
        } else {
            startPreviewIfReady()
        }
    }

    private fun closeCamera() {
        val cam: UVCCamera?
        synchronized(lock) {
            cam = camera
            camera = null
            previewing = false
            chosen = null
        }
        if (cam != null) {
            try { cam.stopPreview() } catch (_: Throwable) {}
            try { cam.destroy() } catch (_: Throwable) {}
        }
    }

    fun onDeviceAttached(device: UsbDevice) {
        if (isVideoDevice(device)) {
            Log.i(TAG, "onDeviceAttached intent: ${device.deviceName}")
            requestDevice(device)
        }
    }

    /** Re-run discovery (the "重新连接" button). */
    fun reconnect() {
        synchronized(lock) { opening = false }
        closeCamera()
        if (!started || monitor == null) {
            start()
        } else {
            scanExisting()
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!started) return
            started = false
            opening = false
        }
        closeCamera()
        try { monitor?.unregister() } catch (_: Throwable) {}
        try { monitor?.destroy() } catch (_: Throwable) {}
        monitor = null
        push { CaptureInfo() }
    }
}
