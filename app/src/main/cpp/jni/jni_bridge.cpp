#include <jni.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <memory>
#include "vulkan_renderer.h"
#include "uvc_capture.h"
#include "audio_engine.h"

#define LOG_TAG "SwitchPadSR-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<switchpad::VulkanRenderer> g_renderer;
static std::unique_ptr<switchpad::UVCCapture> g_uvcCapture;
static std::unique_ptr<switchpad::AudioEngine> g_audioEngine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeInit(
    JNIEnv* env, jobject /*thiz*/, jobject surface, jint width, jint height) {
    LOGI("NativeEngine::nativeInit called (%dx%d)", width, height);

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to get ANativeWindow from surface");
        return JNI_FALSE;
    }

    g_renderer = std::make_unique<switchpad::VulkanRenderer>();
    g_uvcCapture = std::make_unique<switchpad::UVCCapture>();
    g_audioEngine = std::make_unique<switchpad::AudioEngine>();

    if (!g_renderer->init(window, width, height)) {
        LOGE("Failed to initialize Vulkan renderer");
        return JNI_FALSE;
    }

    // Connect UVC capture callbacks to renderer and audio
    g_uvcCapture->setVideoCallback([](const uint8_t* data, int w, int h, switchpad::UVCFormat /*fmt*/) {
        if (g_renderer) {
            g_renderer->feedVideoFrame(data, w, h, w * 2);
        }
    });

    g_uvcCapture->setAudioCallback([](const int16_t* pcm, int samples, int /*channels*/) {
        if (g_audioEngine) {
            g_audioEngine->writePcm(pcm, samples);
        }
    });

    g_audioEngine->start(48000, 2);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeDestroy(JNIEnv* /*env*/, jobject /*thiz*/) {
    LOGI("NativeEngine::nativeDestroy called");
    if (g_uvcCapture) g_uvcCapture->stop();
    if (g_audioEngine) g_audioEngine->stop();
    if (g_renderer) g_renderer->destroy();

    g_uvcCapture.reset();
    g_audioEngine.reset();
    g_renderer.reset();
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeSetAlgorithm(
    JNIEnv* /*env*/, jobject /*thiz*/, jint algo) {
    if (g_renderer) {
        g_renderer->setAlgorithm(static_cast<switchpad::SRAlgorithm>(algo));
    }
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeSetDisplayMode(
    JNIEnv* /*env*/, jobject /*thiz*/, jint mode) {
    if (g_renderer) {
        g_renderer->setDisplayMode(static_cast<switchpad::DisplayMode>(mode));
    }
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeSetTargetResolution(
    JNIEnv* /*env*/, jobject /*thiz*/, jint width, jint height) {
    if (g_renderer) {
        g_renderer->setTargetResolution(width, height);
    }
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeSetSGSRParams(
    JNIEnv* /*env*/, jobject /*thiz*/, jfloat threshold, jfloat sharpness, jboolean useDir) {
    if (g_renderer) {
        g_renderer->setSGSRParams(threshold, sharpness, useDir == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeSetFSRParams(
    JNIEnv* /*env*/, jobject /*thiz*/, jfloat sharpness, jboolean dering) {
    if (g_renderer) {
        g_renderer->setFSRParams(sharpness, dering == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeSetAnime4KParams(
    JNIEnv* /*env*/, jobject /*thiz*/, jfloat strength, jint mode) {
    if (g_renderer) {
        g_renderer->setAnime4KParams(strength, mode);
    }
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeSetCRTParams(
    JNIEnv* /*env*/, jobject /*thiz*/, jfloat scanline, jfloat curvature, jfloat mask) {
    if (g_renderer) {
        g_renderer->setCRTParams(scanline, curvature, mask);
    }
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeSetSplitScreen(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled, jfloat position) {
    if (g_renderer) {
        g_renderer->setSplitScreen(enabled == JNI_TRUE, position);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeStartUvcCapture(
    JNIEnv* /*env*/, jobject /*thiz*/, jint fd, jint width, jint height, jint fps) {
    if (g_uvcCapture) {
        return g_uvcCapture->start(fd, width, height, fps) ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeStopUvcCapture(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    if (g_uvcCapture) {
        g_uvcCapture->stop();
    }
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeRenderFrame(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    if (g_renderer) {
        g_renderer->renderFrame();
    }
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeFeedVideoFrame(
    JNIEnv* env, jobject /*thiz*/, jobject directBuffer, jint width, jint height, jint stride) {
    if (!g_renderer || !directBuffer) return;
    auto* ptr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(directBuffer));
    if (ptr) {
        g_renderer->feedVideoFrame(ptr, width, height, stride);
    }
}

JNIEXPORT void JNICALL
Java_com_zet_switchpadsr_engine_NativeEngine_nativeGetMetrics(
    JNIEnv* env, jobject /*thiz*/, jfloatArray outArray) {
    if (!g_renderer || !outArray) return;
    jsize len = env->GetArrayLength(outArray);
    if (len < 5) return;

    auto& m = g_renderer->getMetrics();
    jfloat elements[5] = {
        m.currentFps.load(std::memory_order_relaxed),
        m.gpuPassTimeMs.load(std::memory_order_relaxed),
        m.glassToGlassLatencyMs.load(std::memory_order_relaxed),
        static_cast<jfloat>(m.totalFramesRendered.load(std::memory_order_relaxed)),
        static_cast<jfloat>(m.activeAlgorithm.load(std::memory_order_relaxed))
    };
    env->SetFloatArrayRegion(outArray, 0, 5, elements);
}

} // extern "C"
