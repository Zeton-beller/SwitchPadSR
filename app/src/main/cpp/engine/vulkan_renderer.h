#pragma once

#include <android/native_window.h>
#include <android/hardware_buffer.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#include <vector>
#include <memory>
#include <mutex>
#include "sr_pipeline.h"
#include "metrics.h"

namespace switchpad {

class VulkanRenderer {
public:
    VulkanRenderer();
    ~VulkanRenderer();

    bool init(ANativeWindow* window, int width, int height);
    void destroy();
    void resize(int width, int height);

    // Frame rendering
    void renderFrame();
    void feedVideoFrame(const uint8_t* yuvData, int width, int height, int stride);

    // Dynamic Parameter & Mode Controls
    void setAlgorithm(SRAlgorithm algo);
    void setDisplayMode(DisplayMode mode);
    void setTargetResolution(int width, int height);
    void setSGSRParams(float threshold, float sharpness, bool useDir);
    void setFSRParams(float sharpness, bool dering);
    void setAnime4KParams(float strength, int mode);
    void setCRTParams(float scanline, float curvature, float mask);
    void setSplitScreen(bool enabled, float position);

    EngineMetrics& getMetrics() { return metrics_; }

private:
    bool createInstance();
    bool selectPhysicalDevice();
    bool createLogicalDevice();
    bool createSwapchain();
    bool createRenderPass();
    bool createPipelines();
    bool createFramebuffers();
    bool createCommandBuffers();
    bool createSyncObjects();

    VkShaderModule createShaderModule(const uint32_t* code, size_t size);

    ANativeWindow* window_{nullptr};
    int screenWidth_{3200};
    int screenHeight_{2136};
    bool initialized_{false};

    VkInstance instance_{VK_NULL_HANDLE};
    VkPhysicalDevice physicalDevice_{VK_NULL_HANDLE};
    VkDevice device_{VK_NULL_HANDLE};
    VkQueue graphicsQueue_{VK_NULL_HANDLE};
    uint32_t graphicsQueueFamilyIndex_{0};

    VkSurfaceKHR surface_{VK_NULL_HANDLE};
    VkSwapchainKHR swapchain_{VK_NULL_HANDLE};
    std::vector<VkImage> swapchainImages_;
    std::vector<VkImageView> swapchainImageViews_;
    std::vector<VkFramebuffer> swapchainFramebuffers_;
    VkFormat swapchainFormat_{VK_FORMAT_R8G8B8A8_UNORM};
    VkExtent2D swapchainExtent_{3200, 2136};

    VkRenderPass renderPass_{VK_NULL_HANDLE};
    VkCommandPool commandPool_{VK_NULL_HANDLE};
    std::vector<VkCommandBuffer> commandBuffers_;

    // Semaphores & Fences for Mailbox presentation
    static constexpr int MAX_FRAMES_IN_FLIGHT = 2;
    std::vector<VkSemaphore> imageAvailableSemaphores_;
    std::vector<VkSemaphore> renderFinishedSemaphores_;
    std::vector<VkFence> inFlightFences_;
    size_t currentFrame_{0};

    // Pipelines
    VkPipelineLayout pipelineLayout_{VK_NULL_HANDLE};
    VkPipeline pipelineSGSR_{VK_NULL_HANDLE};
    VkPipeline pipelineFSR_{VK_NULL_HANDLE};
    VkPipeline pipelineAnime4K_{VK_NULL_HANDLE};
    VkPipeline pipelineCRT_{VK_NULL_HANDLE};
    VkPipeline pipelineBicubic_{VK_NULL_HANDLE};
    VkPipeline pipelineComposition_{VK_NULL_HANDLE};

    // Active Parameters
    std::mutex paramMutex_;
    SRAlgorithm currentAlgorithm_{SRAlgorithm::SGSR_1_0};
    DisplayMode currentDisplayMode_{DisplayMode::LETTERBOX};
    SGSRParams sgsrParams_{};
    FSRParams fsrParams_{};
    Anime4KParams anime4kParams_{};
    CRTParams crtParams_{};
    CompositionParams compParams_{};

    EngineMetrics metrics_;
};

} // namespace switchpad
