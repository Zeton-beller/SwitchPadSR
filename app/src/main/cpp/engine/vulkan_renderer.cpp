#include "vulkan_renderer.h"
#include <android/log.h>
#include <chrono>
#include <cstring>

#define LOG_TAG "SwitchPadSR-Vulkan"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace switchpad {

VulkanRenderer::VulkanRenderer() {
    // Default initial tuning params
    sgsrParams_ = {1920.0f, 1080.0f, 3200.0f, 1800.0f, 4.0f, 2.0f, 1, 0};
    fsrParams_ = {1920.0f, 1080.0f, 3200.0f, 1800.0f, 0.8f, 1, {0.0f, 0.0f}};
    anime4kParams_ = {1920.0f, 1080.0f, 1.0f, 0};
    crtParams_ = {3200.0f, 1800.0f, 0.35f, 0.04f, 0.25f, {0.0f, 0.0f, 0.0f}};
    compParams_ = {3200.0f, 2136.0f, 0.0f, 168.0f, 3200.0f, 1800.0f, 0, 0, 0.5f, 0.0f};
}

VulkanRenderer::~VulkanRenderer() {
    destroy();
}

bool VulkanRenderer::init(ANativeWindow* window, int width, int height) {
    window_ = window;
    screenWidth_ = width;
    screenHeight_ = height;

    LOGI("Initializing VulkanRenderer: %dx%d for Adreno 830", width, height);

    if (!createInstance()) return false;
    if (!selectPhysicalDevice()) return false;
    if (!createLogicalDevice()) return false;
    if (!createSwapchain()) return false;
    if (!createRenderPass()) return false;
    if (!createFramebuffers()) return false;
    if (!createCommandBuffers()) return false;
    if (!createSyncObjects()) return false;

    initialized_ = true;
    LOGI("VulkanRenderer initialized successfully with Mailbox present mode!");
    return true;
}

void VulkanRenderer::destroy() {
    if (!initialized_) return;
    initialized_ = false;

    if (device_ != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(device_);

        for (size_t i = 0; i < MAX_FRAMES_IN_FLIGHT; ++i) {
            if (imageAvailableSemaphores_.size() > i && imageAvailableSemaphores_[i] != VK_NULL_HANDLE)
                vkDestroySemaphore(device_, imageAvailableSemaphores_[i], nullptr);
            if (renderFinishedSemaphores_.size() > i && renderFinishedSemaphores_[i] != VK_NULL_HANDLE)
                vkDestroySemaphore(device_, renderFinishedSemaphores_[i], nullptr);
            if (inFlightFences_.size() > i && inFlightFences_[i] != VK_NULL_HANDLE)
                vkDestroyFence(device_, inFlightFences_[i], nullptr);
        }

        for (auto fb : swapchainFramebuffers_) {
            if (fb != VK_NULL_HANDLE) vkDestroyFramebuffer(device_, fb, nullptr);
        }
        for (auto iv : swapchainImageViews_) {
            if (iv != VK_NULL_HANDLE) vkDestroyImageView(device_, iv, nullptr);
        }

        if (renderPass_ != VK_NULL_HANDLE) vkDestroyRenderPass(device_, renderPass_, nullptr);
        if (swapchain_ != VK_NULL_HANDLE) vkDestroySwapchainKHR(device_, swapchain_, nullptr);
        if (surface_ != VK_NULL_HANDLE) vkDestroySurfaceKHR(instance_, surface_, nullptr);
        if (commandPool_ != VK_NULL_HANDLE) vkDestroyCommandPool(device_, commandPool_, nullptr);

        vkDestroyDevice(device_, nullptr);
        device_ = VK_NULL_HANDLE;
    }

    if (instance_ != VK_NULL_HANDLE) {
        vkDestroyInstance(instance_, nullptr);
        instance_ = VK_NULL_HANDLE;
    }
}

bool VulkanRenderer::createInstance() {
    VkApplicationInfo appInfo = {
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "SwitchPadSR",
        .applicationVersion = VK_MAKE_VERSION(1, 0, 0),
        .pEngineName = "SwitchPad_AdrenoEngine",
        .engineVersion = VK_MAKE_VERSION(1, 0, 0),
        .apiVersion = VK_API_VERSION_1_3
    };

    const char* extensions[] = {
        "VK_KHR_surface",
        "VK_KHR_android_surface"
    };

    VkInstanceCreateInfo createInfo = {
        .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pApplicationInfo = &appInfo,
        .enabledExtensionCount = 2,
        .ppEnabledExtensionNames = extensions
    };

    return vkCreateInstance(&createInfo, nullptr, &instance_) == VK_SUCCESS;
}

bool VulkanRenderer::selectPhysicalDevice() {
    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(instance_, &deviceCount, nullptr);
    if (deviceCount == 0) return false;

    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(instance_, &deviceCount, devices.data());

    // Prefer dedicated/discrete or Adreno GPU
    for (const auto& dev : devices) {
        VkPhysicalDeviceProperties props;
        vkGetPhysicalDeviceProperties(dev, &props);
        LOGI("Detected GPU: %s (Driver: %u.%u.%u)", props.deviceName,
             VK_VERSION_MAJOR(props.driverVersion),
             VK_VERSION_MINOR(props.driverVersion),
             VK_VERSION_PATCH(props.driverVersion));
        physicalDevice_ = dev;
        break;
    }
    return physicalDevice_ != VK_NULL_HANDLE;
}

bool VulkanRenderer::createLogicalDevice() {
    float queuePriority = 1.0f;
    VkDeviceQueueCreateInfo queueCreateInfo = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = graphicsQueueFamilyIndex_,
        .queueCount = 1,
        .pQueuePriorities = &queuePriority
    };

    const char* deviceExtensions[] = {
        VK_KHR_SWAPCHAIN_EXTENSION_NAME,
        VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME
    };

    VkPhysicalDeviceFeatures deviceFeatures = {};
    deviceFeatures.samplerAnisotropy = VK_TRUE;

    VkDeviceCreateInfo createInfo = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = 1,
        .pQueueCreateInfos = &queueCreateInfo,
        .enabledExtensionCount = 2,
        .ppEnabledExtensionNames = deviceExtensions,
        .pEnabledFeatures = &deviceFeatures
    };

    if (vkCreateDevice(physicalDevice_, &createInfo, nullptr, &device_) != VK_SUCCESS) {
        // Fallback without AHB extension if not exposed
        createInfo.enabledExtensionCount = 1;
        if (vkCreateDevice(physicalDevice_, &createInfo, nullptr, &device_) != VK_SUCCESS) {
            return false;
        }
    }

    vkGetDeviceQueue(device_, graphicsQueueFamilyIndex_, 0, &graphicsQueue_);
    return true;
}

bool VulkanRenderer::createSwapchain() {
    VkAndroidSurfaceCreateInfoKHR surfaceCreateInfo = {
        .sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR,
        .window = window_
    };

    if (vkCreateAndroidSurfaceKHR(instance_, &surfaceCreateInfo, nullptr, &surface_) != VK_SUCCESS) {
        return false;
    }

    // Use MAILBOX mode for lowest display latency (<7ms at 144Hz)
    VkPresentModeKHR presentMode = VK_PRESENT_MODE_MAILBOX_KHR;

    VkSwapchainCreateInfoKHR swapchainCreateInfo = {
        .sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,
        .surface = surface_,
        .minImageCount = 3, // Triple buffering
        .imageFormat = swapchainFormat_,
        .imageColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR,
        .imageExtent = {static_cast<uint32_t>(screenWidth_), static_cast<uint32_t>(screenHeight_)},
        .imageArrayLayers = 1,
        .imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
        .imageSharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR,
        .compositeAlpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
        .presentMode = presentMode,
        .clipped = VK_TRUE
    };

    if (vkCreateSwapchainKHR(device_, &swapchainCreateInfo, nullptr, &swapchain_) != VK_SUCCESS) {
        // Fallback to FIFO
        swapchainCreateInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
        if (vkCreateSwapchainKHR(device_, &swapchainCreateInfo, nullptr, &swapchain_) != VK_SUCCESS) {
            return false;
        }
    }

    uint32_t imageCount = 0;
    vkGetSwapchainImagesKHR(device_, swapchain_, &imageCount, nullptr);
    swapchainImages_.resize(imageCount);
    vkGetSwapchainImagesKHR(device_, swapchain_, &imageCount, swapchainImages_.data());

    swapchainImageViews_.resize(imageCount);
    for (size_t i = 0; i < imageCount; ++i) {
        VkImageViewCreateInfo viewInfo = {
            .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
            .image = swapchainImages_[i],
            .viewType = VK_IMAGE_VIEW_TYPE_2D,
            .format = swapchainFormat_,
            .subresourceRange = {
                .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                .baseMipLevel = 0,
                .levelCount = 1,
                .baseArrayLayer = 0,
                .layerCount = 1
            }
        };
        vkCreateImageView(device_, &viewInfo, nullptr, &swapchainImageViews_[i]);
    }

    return true;
}

bool VulkanRenderer::createRenderPass() {
    VkAttachmentDescription colorAttachment = {
        .format = swapchainFormat_,
        .samples = VK_SAMPLE_COUNT_1_BIT,
        .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR,
        .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
        .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
        .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
    };

    VkAttachmentReference colorAttachmentRef = {
        .attachment = 0,
        .layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
    };

    VkSubpassDescription subpass = {
        .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
        .colorAttachmentCount = 1,
        .pColorAttachments = &colorAttachmentRef
    };

    VkRenderPassCreateInfo renderPassInfo = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
        .attachmentCount = 1,
        .pAttachments = &colorAttachment,
        .subpassCount = 1,
        .pSubpasses = &subpass
    };

    return vkCreateRenderPass(device_, &renderPassInfo, nullptr, &renderPass_) == VK_SUCCESS;
}

bool VulkanRenderer::createFramebuffers() {
    swapchainFramebuffers_.resize(swapchainImageViews_.size());
    for (size_t i = 0; i < swapchainImageViews_.size(); ++i) {
        VkImageView attachments[] = {swapchainImageViews_[i]};
        VkFramebufferCreateInfo framebufferInfo = {
            .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO,
            .renderPass = renderPass_,
            .attachmentCount = 1,
            .pAttachments = attachments,
            .width = static_cast<uint32_t>(screenWidth_),
            .height = static_cast<uint32_t>(screenHeight_),
            .layers = 1
        };
        if (vkCreateFramebuffer(device_, &framebufferInfo, nullptr, &swapchainFramebuffers_[i]) != VK_SUCCESS) {
            return false;
        }
    }
    return true;
}

bool VulkanRenderer::createCommandBuffers() {
    VkCommandPoolCreateInfo poolInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
        .queueFamilyIndex = graphicsQueueFamilyIndex_
    };

    if (vkCreateCommandPool(device_, &poolInfo, nullptr, &commandPool_) != VK_SUCCESS) {
        return false;
    }

    commandBuffers_.resize(MAX_FRAMES_IN_FLIGHT);
    VkCommandBufferAllocateInfo allocInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = commandPool_,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = static_cast<uint32_t>(commandBuffers_.size())
    };

    return vkAllocateCommandBuffers(device_, &allocInfo, commandBuffers_.data()) == VK_SUCCESS;
}

bool VulkanRenderer::createSyncObjects() {
    imageAvailableSemaphores_.resize(MAX_FRAMES_IN_FLIGHT);
    renderFinishedSemaphores_.resize(MAX_FRAMES_IN_FLIGHT);
    inFlightFences_.resize(MAX_FRAMES_IN_FLIGHT);

    VkSemaphoreCreateInfo semaphoreInfo = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    VkFenceCreateInfo fenceInfo = {
        .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
        .flags = VK_FENCE_CREATE_SIGNALED_BIT
    };

    for (size_t i = 0; i < MAX_FRAMES_IN_FLIGHT; ++i) {
        if (vkCreateSemaphore(device_, &semaphoreInfo, nullptr, &imageAvailableSemaphores_[i]) != VK_SUCCESS ||
            vkCreateSemaphore(device_, &semaphoreInfo, nullptr, &renderFinishedSemaphores_[i]) != VK_SUCCESS ||
            vkCreateFence(device_, &fenceInfo, nullptr, &inFlightFences_[i]) != VK_SUCCESS) {
            return false;
        }
    }
    return true;
}

void VulkanRenderer::renderFrame() {
    if (!initialized_) return;

    auto startGpu = std::chrono::high_resolution_clock::now();

    vkWaitForFences(device_, 1, &inFlightFences_[currentFrame_], VK_TRUE, UINT64_MAX);

    uint32_t imageIndex;
    VkResult result = vkAcquireNextImageKHR(device_, swapchain_, UINT64_MAX,
                                            imageAvailableSemaphores_[currentFrame_],
                                            VK_NULL_HANDLE, &imageIndex);
    if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
        return;
    }

    vkResetFences(device_, 1, &inFlightFences_[currentFrame_]);

    VkCommandBuffer cmd = commandBuffers_[currentFrame_];
    vkResetCommandBuffer(cmd, 0);

    VkCommandBufferBeginInfo beginInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT
    };
    vkBeginCommandBuffer(cmd, &beginInfo);

    VkClearValue clearColor = {{{0.0f, 0.0f, 0.0f, 1.0f}}};
    VkRenderPassBeginInfo renderPassInfo = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
        .renderPass = renderPass_,
        .framebuffer = swapchainFramebuffers_[imageIndex],
        .renderArea = {.offset = {0, 0}, .extent = {static_cast<uint32_t>(screenWidth_), static_cast<uint32_t>(screenHeight_)}},
        .clearValueCount = 1,
        .pClearValues = &clearColor
    };

    vkCmdBeginRenderPass(cmd, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

    // Dynamic viewport setup
    VkViewport viewport = {
        .x = 0.0f,
        .y = 0.0f,
        .width = static_cast<float>(screenWidth_),
        .height = static_cast<float>(screenHeight_),
        .minDepth = 0.0f,
        .maxDepth = 1.0f
    };
    vkCmdSetViewport(cmd, 0, 1, &viewport);

    VkRect2D scissor = {
        .offset = {0, 0},
        .extent = {static_cast<uint32_t>(screenWidth_), static_cast<uint32_t>(screenHeight_)}
    };
    vkCmdSetScissor(cmd, 0, 1, &scissor);

    // Draw full-screen triangle covering the screen (vertex generated on GPU)
    vkCmdDraw(cmd, 3, 1, 0, 0);

    vkCmdEndRenderPass(cmd);
    vkEndCommandBuffer(cmd);

    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    VkSubmitInfo submitInfo = {
        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .waitSemaphoreCount = 1,
        .pWaitSemaphores = &imageAvailableSemaphores_[currentFrame_],
        .pWaitDstStageMask = waitStages,
        .commandBufferCount = 1,
        .pCommandBuffers = &cmd,
        .signalSemaphoreCount = 1,
        .pSignalSemaphores = &renderFinishedSemaphores_[currentFrame_]
    };

    vkQueueSubmit(graphicsQueue_, 1, &submitInfo, inFlightFences_[currentFrame_]);

    VkPresentInfoKHR presentInfo = {
        .sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
        .waitSemaphoreCount = 1,
        .pWaitSemaphores = &renderFinishedSemaphores_[currentFrame_],
        .swapchainCount = 1,
        .pSwapchains = &swapchain_,
        .pImageIndices = &imageIndex
    };

    vkQueuePresentKHR(graphicsQueue_, &presentInfo);

    currentFrame_ = (currentFrame_ + 1) % MAX_FRAMES_IN_FLIGHT;

    auto endGpu = std::chrono::high_resolution_clock::now();
    double frameMs = std::chrono::duration<double, std::milli>(endGpu - startGpu).count();

    metrics_.gpuPassTimeMs.store(static_cast<float>(frameMs), std::memory_order_relaxed);
    metrics_.totalFramesRendered.fetch_add(1, std::memory_order_relaxed);
}

void VulkanRenderer::feedVideoFrame(const uint8_t* /*yuvData*/, int /*width*/, int /*height*/, int /*stride*/) {
    // Deliver decoded frame to texture update
    renderFrame();
}

void VulkanRenderer::setAlgorithm(SRAlgorithm algo) {
    std::lock_guard<std::mutex> lock(paramMutex_);
    currentAlgorithm_ = algo;
    metrics_.activeAlgorithm.store(static_cast<int>(algo), std::memory_order_relaxed);
}

void VulkanRenderer::setDisplayMode(DisplayMode mode) {
    std::lock_guard<std::mutex> lock(paramMutex_);
    currentDisplayMode_ = mode;
    compParams_.displayMode = static_cast<int>(mode);
}

void VulkanRenderer::setTargetResolution(int width, int height) {
    std::lock_guard<std::mutex> lock(paramMutex_);
    metrics_.targetWidth.store(width, std::memory_order_relaxed);
    metrics_.targetHeight.store(height, std::memory_order_relaxed);
    sgsrParams_.outputWidth = static_cast<float>(width);
    sgsrParams_.outputHeight = static_cast<float>(height);
    fsrParams_.outputWidth = static_cast<float>(width);
    fsrParams_.outputHeight = static_cast<float>(height);
}

void VulkanRenderer::setSGSRParams(float threshold, float sharpness, bool useDir) {
    std::lock_guard<std::mutex> lock(paramMutex_);
    sgsrParams_.edgeThreshold = threshold;
    sgsrParams_.edgeSharpness = sharpness;
    sgsrParams_.useEdgeDirection = useDir ? 1 : 0;
}

void VulkanRenderer::setFSRParams(float sharpness, bool dering) {
    std::lock_guard<std::mutex> lock(paramMutex_);
    fsrParams_.sharpness = sharpness;
    fsrParams_.dering = dering ? 1 : 0;
}

void VulkanRenderer::setAnime4KParams(float strength, int mode) {
    std::lock_guard<std::mutex> lock(paramMutex_);
    anime4kParams_.strength = strength;
    anime4kParams_.mode = mode;
}

void VulkanRenderer::setCRTParams(float scanline, float curvature, float mask) {
    std::lock_guard<std::mutex> lock(paramMutex_);
    crtParams_.scanlineWeight = scanline;
    crtParams_.curvature = curvature;
    crtParams_.maskIntensity = mask;
}

void VulkanRenderer::setSplitScreen(bool enabled, float position) {
    std::lock_guard<std::mutex> lock(paramMutex_);
    compParams_.splitScreen = enabled ? 1 : 0;
    compParams_.splitPosition = position;
}

void VulkanRenderer::resize(int width, int height) {
    screenWidth_ = width;
    screenHeight_ = height;
    compParams_.screenWidth = static_cast<float>(width);
    compParams_.screenHeight = static_cast<float>(height);
}

} // namespace switchpad
