#pragma once

#include <aaudio/AAudio.h>
#include <atomic>
#include <vector>
#include <mutex>

namespace switchpad {

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool start(int sampleRate = 48000, int channels = 2);
    void stop();
    void writePcm(const int16_t* data, int numSamples);

    bool isRunning() const { return isRunning_.load(); }

private:
    static aaudio_data_callback_result_t aaudioDataCallback(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames
    );

    AAudioStream* stream_{nullptr};
    std::atomic<bool> isRunning_{false};
    std::vector<int16_t> ringBuffer_;
    size_t writePos_{0};
    size_t readPos_{0};
    std::mutex bufferMutex_;
};

} // namespace switchpad
