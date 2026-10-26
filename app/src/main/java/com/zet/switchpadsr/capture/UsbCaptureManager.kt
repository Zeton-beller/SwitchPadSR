package com.zet.switchpadsr.capture

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.zet.switchpadsr.engine.NativeEngine

class UsbCaptureManager(
    private val context: Context,
    private val onDeviceConnected: (UsbDevice) -> Unit,
    private val onDeviceDisconnected: (UsbDevice) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "SwitchPadSR-UsbManager"
        private const val ACTION_USB_PERMISSION = "com.zet.switchpadsr.USB_PERMISSION"
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var activeConnection: UsbDeviceConnection? = null
    private var activeDevice: UsbDevice? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { connectDevice(it) }
                        } else {
                            onError("用户拒绝了USB采集卡访问权限")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    scanAndConnect()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    disconnect()
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    fun scanAndConnect() {
        val deviceList = usbManager.deviceList
        Log.i(TAG, "Scanning USB devices. Found ${deviceList.size} devices.")

        for ((_, device) in deviceList) {
            if (isUvcDevice(device)) {
                Log.i(TAG, "Detected UVC Capture Card: ${device.deviceName} (Vendor: ${device.vendorId}, Product: ${device.productId})")
                if (usbManager.hasPermission(device)) {
                    connectDevice(device)
                } else {
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val permissionIntent = PendingIntent.getBroadcast(
                        context, 0, Intent(ACTION_USB_PERMISSION), flags
                    )
                    usbManager.requestPermission(device, permissionIntent)
                }
                return
            }
        }
        onError("未检测到兼容的UVC采集卡，请插入Switch采集卡并开启底座电源")
    }

    private fun isUvcDevice(device: UsbDevice): Boolean {
        // Check standard USB Video Class (0x0E)
        if (device.deviceClass == UsbConstants.USB_CLASS_VIDEO) return true

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VIDEO) {
                return true
            }
        }
        // Known MacroSilicon chips (MS2130, MS2109: 0x534d / 0x2109 / 0x2130)
        if (device.vendorId == 0x534d || device.vendorId == 0x1bcf) return true

        return false
    }

    private fun connectDevice(device: UsbDevice) {
        disconnect()
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            onError("无法建立USB连接，请重插采集卡")
            return
        }

        activeDevice = device
        activeConnection = connection
        val fd = connection.fileDescriptor
        Log.i(TAG, "USB Device opened successfully, passing native file descriptor $fd to engine")

        val started = NativeEngine.nativeStartUvcCapture(fd, 1920, 1080, 60)
        if (started) {
            onDeviceConnected(device)
        } else {
            onError("底层驱动初始化失败，请检查采集卡格式支持")
        }
    }

    fun disconnect() {
        NativeEngine.nativeStopUvcCapture()
        activeConnection?.close()
        activeConnection = null
        activeDevice?.let { onDeviceDisconnected(it) }
        activeDevice = null
    }
}
