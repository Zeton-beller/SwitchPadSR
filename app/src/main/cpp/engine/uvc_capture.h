#pragma once

#include <atomic>
#include <thread>
#include <functional>
#include <vector>
#include <cstdint>

namespace switchpad {

enum class UVCFormat : int {
    YUY2 = 0, // Uncompressed 4:2:2 (Default for USB 3.0 MS2130)
    NV12 = 1,
    MJPEG = 2
};

using VideoFrameCallback = std::function<void(const uint8_t* data, int width, int height, UVCFormat format)>;
using AudioFrameCallback = std::function<void(const int16_t* pcmData, int samples, int channels)>;

class UVCCapture {
public:
    UVCCapture();
    ~UVCCapture();

    bool start(int deviceFd, int width, int height, int fps);
    void stop();

    void setVideoCallback(VideoFrameCallback cb) { videoCallback_ = cb; }
    void setAudioCallback(AudioFrameCallback cb) { audioCallback_ = cb; }

    bool isRunning() const { return isRunning_.load(); }

private:
    void captureWorkerThread(int fd);

    std::atomic<bool> isRunning_{false};
    std::thread workerThread_;

    int width_{1920};
    int height_{1080};
    int fps_{60};
    UVCFormat format_{UVCFormat::YUY2};

    VideoFrameCallback videoCallback_;
    AudioFrameCallback audioCallback_;
};

} // namespace switchpad
