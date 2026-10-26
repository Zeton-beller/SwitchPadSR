#include "audio_engine.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "SwitchPadSR-Audio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace switchpad {

AudioEngine::AudioEngine() {
    ringBuffer_.resize(48000 * 2); // 1 second buffer
}

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start(int sampleRate, int channels) {
    if (isRunning_.load()) return true;

    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
        LOGE("Failed to create AAudioStreamBuilder");
        return false;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    // Try exclusive low latency first; fallback to shared if device rejects exclusive
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, channels);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setDataCallback(builder, aaudioDataCallback, this);

    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream_);
    if (result != AAUDIO_OK) {
        LOGI("Exclusive AAudio mode unavailable (%s), trying SHARED mode fallback", AAudio_convertResultToText(result));
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        result = AAudioStreamBuilder_openStream(builder, &stream_);
    }
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK) {
        LOGE("Failed to open AAudio stream: %s", AAudio_convertResultToText(result));
        return false;
    }

    result = AAudioStream_requestStart(stream_);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start AAudio stream: %s", AAudio_convertResultToText(result));
        AAudioStream_close(stream_);
        stream_ = nullptr;
        return false;
    }

    isRunning_.store(true);
    LOGI("Exclusive low-latency AAudio engine started successfully (Buffer burst: %d frames)",
         AAudioStream_getFramesPerBurst(stream_));
    return true;
}

void AudioEngine::stop() {
    if (!isRunning_.load()) return;
    isRunning_.store(false);

    if (stream_) {
        AAudioStream_requestStop(stream_);
        AAudioStream_close(stream_);
        stream_ = nullptr;
    }
    LOGI("AAudio engine stopped.");
}

void AudioEngine::writePcm(const int16_t* data, int numSamples) {
    std::lock_guard<std::mutex> lock(bufferMutex_);
    for (int i = 0; i < numSamples; ++i) {
        ringBuffer_[writePos_] = data[i];
        writePos_ = (writePos_ + 1) % ringBuffer_.size();
    }
}

aaudio_data_callback_result_t AudioEngine::aaudioDataCallback(
    AAudioStream* /*stream*/,
    void* userData,
    void* audioData,
    int32_t numFrames
) {
    auto* self = static_cast<AudioEngine*>(userData);
    auto* output = static_cast<int16_t*>(audioData);
    size_t samplesNeeded = static_cast<size_t>(numFrames * 2);

    std::lock_guard<std::mutex> lock(self->bufferMutex_);
    for (size_t i = 0; i < samplesNeeded; ++i) {
        if (self->readPos_ != self->writePos_) {
            output[i] = self->ringBuffer_[self->readPos_];
            self->readPos_ = (self->readPos_ + 1) % self->ringBuffer_.size();
        } else {
            output[i] = 0; // Underrun silence
        }
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

} // namespace switchpad
