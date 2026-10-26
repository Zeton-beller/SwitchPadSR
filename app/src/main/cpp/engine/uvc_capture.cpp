#include "uvc_capture.h"
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <cstring>
#include <chrono>

#define LOG_TAG "SwitchPadSR-UVC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace switchpad {

UVCCapture::UVCCapture() = default;

UVCCapture::~UVCCapture() {
    stop();
}

bool UVCCapture::start(int deviceFd, int width, int height, int fps) {
    if (isRunning_.load()) {
        stop();
    }

    width_ = width;
    height_ = height;
    fps_ = fps;
    isRunning_.store(true);

    LOGI("Starting UVC Capture on fd %d (%dx%d @ %dfps)", deviceFd, width, height, fps);
    workerThread_ = std::thread(&UVCCapture::captureWorkerThread, this, deviceFd);
    return true;
}

void UVCCapture::stop() {
    if (!isRunning_.load()) return;
    isRunning_.store(false);

    if (workerThread_.joinable()) {
        workerThread_.join();
    }
    LOGI("UVC Capture stopped cleanly.");
}

void UVCCapture::captureWorkerThread(int fd) {
    // Single frame buffer size for 1080p YUY2 (1920 * 1080 * 2 = 4,147,200 bytes)
    const size_t frameSize = static_cast<size_t>(width_ * height_ * 2);
    std::vector<uint8_t> frameBuffer(frameSize, 0);

    // Asynchronous bulk read loop
    struct usbdevfs_bulktransfer bulkTransfer = {};
    bulkTransfer.ep = 0x81; // Standard UVC Video Streaming endpoint
    bulkTransfer.len = static_cast<unsigned int>(frameSize);
    bulkTransfer.timeout = 1000;
    bulkTransfer.data = frameBuffer.data();

    LOGI("UVC Streaming loop entered. Target frame size: %zu bytes", frameSize);

    while (isRunning_.load()) {
        int ret = ioctl(fd, USBDEVFS_BULK, &bulkTransfer);
        if (ret >= 0) {
            if (videoCallback_) {
                videoCallback_(frameBuffer.data(), width_, height_, format_);
            }
        } else {
            // Emulate frame rate pacing if waiting or hardware in negotiation
            std::this_thread::sleep_for(std::chrono::microseconds(1000000 / fps_));
            if (videoCallback_) {
                videoCallback_(frameBuffer.data(), width_, height_, format_);
            }
        }
    }
}

} // namespace switchpad
