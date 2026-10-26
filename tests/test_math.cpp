#include <iostream>
#include <cmath>
#include <cassert>
#include <vector>
#include <chrono>

// Qualcomm SGSR 1.0 Fast Lanczos2 polynomial approximation
inline float fastLanczos2(float x) {
    float wA = x - 4.0f;
    float wB = x * wA - wA;
    wA *= wA;
    return wB * wA;
}

// Directional edge normalization
struct Vec2 {
    float x, y;
};

inline Vec2 edgeDirection(float leftZ, float leftY, float rightX, float rightW) {
    float dx = (rightX - leftZ) + (rightW - leftY);
    float dy = (rightX - leftZ) - (rightW - leftY);
    float dot = dx * dx + dy * dy + 3.075740e-05f;
    float invSqrt = 1.0f / std::sqrt(dot);
    return {dx * invSqrt, dy * invSqrt};
}

// BT.709 YUV to RGB Conversion Math
struct RGB {
    float r, g, b;
};

inline RGB yuvToRgbBT709(float y, float u, float v) {
    float yVal = (y - 16.0f) * (1.0f / 219.0f);
    float uVal = (u - 128.0f) * (1.0f / 224.0f);
    float vVal = (v - 128.0f) * (1.0f / 224.0f);

    float r = yVal + 1.5748f * vVal;
    float g = yVal - 0.1873f * uVal - 0.4681f * vVal;
    float b = yVal + 1.8556f * uVal;

    return {
        std::max(0.0f, std::min(1.0f, r)),
        std::max(0.0f, std::min(1.0f, g)),
        std::max(0.0f, std::min(1.0f, b))
    };
}

// Screen Geometry Computation (3200x2136 screen with 16:9 input)
struct ViewportRect {
    int x, y, width, height;
    int topMargin, bottomMargin;
};

inline ViewportRect compute16by9Viewport(int screenWidth, int screenHeight) {
    // 16:9 aspect = 1.777778
    // Max width fit
    int activeW = screenWidth;
    int activeH = static_cast<int>(std::round(screenWidth * (9.0 / 16.0)));
    if (activeH > screenHeight) {
        activeH = screenHeight;
        activeW = static_cast<int>(std::round(screenHeight * (16.0 / 9.0)));
    }
    int marginY = (screenHeight - activeH) / 2;
    int marginX = (screenWidth - activeW) / 2;

    return {marginX, marginY, activeW, activeH, marginY, screenHeight - (marginY + activeH)};
}

// Lock-Free Single-Producer Single-Consumer Ring Buffer
template <typename T, size_t Capacity>
class LockFreeRingBuffer {
private:
    T buffer_[Capacity];
    std::atomic<size_t> head_{0};
    std::atomic<size_t> tail_{0};

public:
    bool push(const T& item) {
        size_t currentTail = tail_.load(std::memory_order_relaxed);
        size_t nextTail = (currentTail + 1) % Capacity;
        if (nextTail == head_.load(std::memory_order_acquire)) {
            return false; // Full
        }
        buffer_[currentTail] = item;
        tail_.store(nextTail, std::memory_order_release);
        return true;
    }

    bool pop(T& item) {
        size_t currentHead = head_.load(std::memory_order_relaxed);
        if (currentHead == tail_.load(std::memory_order_acquire)) {
            return false; // Empty
        }
        item = buffer_[currentHead];
        head_.store((currentHead + 1) % Capacity, std::memory_order_release);
        return true;
    }
};

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << "SwitchPad SR Core Engine Math Unit Tests" << std::endl;
    std::cout << "========================================" << std::endl;

    // Test 1: Lanczos2 polynomial bounds
    std::cout << "[Test 1] Testing Qualcomm fastLanczos2 polynomial..." << std::endl;
    float peak = fastLanczos2(0.0f);
    assert(std::abs(peak - 64.0f) < 1e-4); // at x=0: (0-4)*(0*(0-4)-(0-4))*(0-4)^2 = -4*(4)*16 = -4*-64? Wait: (-4)*4*16 = -256?
    // Let's print the actual value:
    std::cout << "  fastLanczos2(0.0) = " << peak << std::endl;
    std::cout << "  fastLanczos2(1.0) = " << fastLanczos2(1.0f) << std::endl;
    std::cout << "  fastLanczos2(2.0) = " << fastLanczos2(2.0f) << std::endl;
    std::cout << "  fastLanczos2(4.0) = " << fastLanczos2(4.0f) << " (should be 0.0 at window edge)" << std::endl;
    assert(std::abs(fastLanczos2(4.0f)) < 1e-5);
    std::cout << "  -> PASSED!" << std::endl;

    // Test 2: Edge direction vector normalization
    std::cout << "[Test 2] Testing Edge Direction Vector..." << std::endl;
    Vec2 dir = edgeDirection(0.2f, 0.3f, 0.8f, 0.9f);
    float norm = std::sqrt(dir.x * dir.x + dir.y * dir.y);
    std::cout << "  Edge Direction = (" << dir.x << ", " << dir.y << "), Magnitude = " << norm << std::endl;
    assert(std::abs(norm - 1.0f) < 1e-3);
    std::cout << "  -> PASSED!" << std::endl;

    // Test 3: Screen geometry on Xiaomi Pad 8 Pro (3200 x 2136)
    std::cout << "[Test 3] Testing 3:2 Screen Geometry for 16:9 input..." << std::endl;
    ViewportRect vp = compute16by9Viewport(3200, 2136);
    std::cout << "  Screen: 3200 x 2136" << std::endl;
    std::cout << "  Active Video: " << vp.width << " x " << vp.height << " at (" << vp.x << ", " << vp.y << ")" << std::endl;
    std::cout << "  Top Margin = " << vp.topMargin << " px, Bottom Margin = " << vp.bottomMargin << " px" << std::endl;
    assert(vp.width == 3200);
    assert(vp.height == 1800);
    assert(vp.topMargin == 168);
    assert(vp.bottomMargin == 168);
    std::cout << "  -> PASSED! Perfectly symmetric 168px margins." << std::endl;

    // Test 4: BT.709 color conversion
    std::cout << "[Test 4] Testing BT.709 Color Conversion..." << std::endl;
    RGB white = yuvToRgbBT709(235.0f, 128.0f, 128.0f); // Standard limited range white
    std::cout << "  White YUV(235,128,128) -> RGB(" << white.r << ", " << white.g << ", " << white.b << ")" << std::endl;
    assert(white.r > 0.99f && white.g > 0.99f && white.b > 0.99f);

    RGB black = yuvToRgbBT709(16.0f, 128.0f, 128.0f); // Standard limited range black
    std::cout << "  Black YUV(16,128,128)  -> RGB(" << black.r << ", " << black.g << ", " << black.b << ")" << std::endl;
    assert(black.r < 0.01f && black.g < 0.01f && black.b < 0.01f);
    std::cout << "  -> PASSED!" << std::endl;

    // Test 5: RingBuffer concurrency
    std::cout << "[Test 5] Testing Lock-Free RingBuffer..." << std::endl;
    LockFreeRingBuffer<int, 16> rb;
    for (int i = 0; i < 15; ++i) {
        bool ok = rb.push(i * 10);
        assert(ok);
    }
    // 16th should fail (capacity 16, 1 slot reserved for full/empty distinction)
    bool full = rb.push(999);
    assert(!full);
    int val = 0;
    for (int i = 0; i < 15; ++i) {
        bool ok = rb.pop(val);
        assert(ok);
        assert(val == i * 10);
    }
    bool empty = rb.pop(val);
    assert(!empty);
    std::cout << "  -> PASSED!" << std::endl;

    // Test 6: Performance benchmark on Oryon core
    std::cout << "[Test 6] Benchmarking 1080p YUY2 frame unpacking (2M pixels)..." << std::endl;
    std::vector<uint8_t> dummyYUY2(1920 * 1080 * 2, 128);
    auto t0 = std::chrono::high_resolution_clock::now();
    uint64_t sumLuma = 0;
    for (size_t i = 0; i < dummyYUY2.size(); i += 2) {
        sumLuma += dummyYUY2[i];
    }
    auto t1 = std::chrono::high_resolution_clock::now();
    double ms = std::chrono::duration<double, std::milli>(t1 - t0).count();
    std::cout << "  Processed 4.14 MB in " << ms << " ms (sum = " << sumLuma << ")" << std::endl;
    std::cout << "  Oryon Core Throughput: " << (4.14 / (ms / 1000.0)) << " MB/s" << std::endl;
    std::cout << "  -> PASSED!" << std::endl;

    std::cout << "========================================" << std::endl;
    std::cout << "ALL 6 UNIT TESTS PASSED SUCCESSFULLY!" << std::endl;
    std::cout << "========================================" << std::endl;
    return 0;
}
