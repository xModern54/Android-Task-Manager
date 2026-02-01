#include "gpu_stats.h"
#include "native_utils.h"
#include "native_common.h"

#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <future>
#include <chrono>
#include <mutex>
#include <vector>
#include <sstream>
#include <iomanip>
#include <algorithm>
#include <dirent.h>
#include <cmath>
#include <limits>

struct VulkanContext {
    bool initialized = false;
    bool supported = false;
    bool hasMemoryBudget = false;
    std::string error;

    void* handle = nullptr;
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;

    PFN_vkGetInstanceProcAddr getInstanceProcAddr = nullptr;
    PFN_vkDestroyInstance destroyInstance = nullptr;
    PFN_vkEnumeratePhysicalDevices enumeratePhysicalDevices = nullptr;
    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = nullptr;
    PFN_vkEnumerateDeviceExtensionProperties enumerateDeviceExtensionProperties = nullptr;
    PFN_vkGetPhysicalDeviceMemoryProperties getMemoryProperties = nullptr;
    PFN_vkGetPhysicalDeviceMemoryProperties2 getMemoryProperties2 = nullptr;

    uint32_t instanceVersion = 0;
    VkPhysicalDeviceProperties properties{};
};

static std::mutex g_vulkan_mutex;
static VulkanContext g_vulkan_ctx;
static bool g_vulkan_init_attempted = false;

static std::string format_vk_version(uint32_t version) {
    if (version == 0) return "";
    std::stringstream ss;
    ss << VK_VERSION_MAJOR(version) << "."
       << VK_VERSION_MINOR(version) << "."
       << VK_VERSION_PATCH(version);
    return ss.str();
}

static std::string make_vulkan_json(
        bool supported,
        const std::string& instanceVersion,
        const std::string& deviceApiVersion,
        const std::string& driverVersion,
        uint32_t vendorId,
        uint32_t deviceId,
        const std::string& deviceName,
        const std::string& error) {
    std::stringstream ss;
    ss << "{";
    ss << "\"vulkanSupported\":" << (supported ? "true" : "false") << ",";
    ss << "\"instanceVersion\":\"" << escape_json(instanceVersion) << "\",";
    ss << "\"deviceApiVersion\":\"" << escape_json(deviceApiVersion) << "\",";
    ss << "\"driverVersion\":\"" << escape_json(driverVersion) << "\",";
    ss << "\"vendorId\":" << vendorId << ",";
    ss << "\"deviceId\":" << deviceId << ",";
    ss << "\"deviceName\":\"" << escape_json(deviceName) << "\",";
    ss << "\"error\":\"" << escape_json(error) << "\"";
    ss << "}";
    return ss.str();
}

static bool init_vulkan_context_internal(VulkanContext& ctx, std::string& errOut) {
    const char* candidates[] = {
            "libvulkan.so",
            "/system/lib64/libvulkan.so",
            "/vendor/lib64/libvulkan.so",
            "/system/lib/libvulkan.so",
            "/vendor/lib/libvulkan.so"
    };

    void* handle = nullptr;
    std::string lastError;
    for (const char* path : candidates) {
        handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
        if (handle) break;
        const char* err = dlerror();
        if (err) lastError = err;
    }

    if (!handle) {
        errOut = "dlopen(libvulkan.so) failed: " + lastError;
        return false;
    }

    auto gpa = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
            dlsym(handle, "vkGetInstanceProcAddr"));
    if (!gpa) {
        const char* errPtr = dlerror();
        errOut = std::string("dlsym(vkGetInstanceProcAddr) failed: ") + (errPtr ? errPtr : "unknown");
        dlclose(handle);
        return false;
    }

    auto enumerateInstanceVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
            gpa(nullptr, "vkEnumerateInstanceVersion"));
    uint32_t instanceVersionNum = VK_API_VERSION_1_0;
    if (enumerateInstanceVersion) {
        if (enumerateInstanceVersion(&instanceVersionNum) != VK_SUCCESS) {
            instanceVersionNum = VK_API_VERSION_1_0;
        }
    }

    auto createInstance = reinterpret_cast<PFN_vkCreateInstance>(
            gpa(nullptr, "vkCreateInstance"));
    if (!createInstance) {
        errOut = "vkCreateInstance not available";
        dlclose(handle);
        return false;
    }

    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "TaskManager";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "TaskManager";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VkInstance instance = VK_NULL_HANDLE;
    if (createInstance(&createInfo, nullptr, &instance) != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        errOut = "vkCreateInstance failed";
        dlclose(handle);
        return false;
    }

    auto enumeratePhysicalDevices = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(
            gpa(instance, "vkEnumeratePhysicalDevices"));
    auto getPhysicalDeviceProperties = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(
            gpa(instance, "vkGetPhysicalDeviceProperties"));
    auto destroyInstance = reinterpret_cast<PFN_vkDestroyInstance>(
            gpa(instance, "vkDestroyInstance"));
    auto enumerateDeviceExtensionProperties = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
            gpa(instance, "vkEnumerateDeviceExtensionProperties"));
    auto getMemoryProperties = reinterpret_cast<PFN_vkGetPhysicalDeviceMemoryProperties>(
            gpa(instance, "vkGetPhysicalDeviceMemoryProperties"));
    auto getMemoryProperties2 = reinterpret_cast<PFN_vkGetPhysicalDeviceMemoryProperties2>(
            gpa(instance, "vkGetPhysicalDeviceMemoryProperties2"));
    if (!getMemoryProperties2) {
        getMemoryProperties2 = reinterpret_cast<PFN_vkGetPhysicalDeviceMemoryProperties2>(
                gpa(instance, "vkGetPhysicalDeviceMemoryProperties2KHR"));
    }

    if (!enumeratePhysicalDevices || !getPhysicalDeviceProperties || !destroyInstance || !getMemoryProperties) {
        destroyInstance(instance, nullptr);
        dlclose(handle);
        errOut = "vk functions missing";
        return false;
    }

    uint32_t deviceCount = 0;
    if (enumeratePhysicalDevices(instance, &deviceCount, nullptr) != VK_SUCCESS || deviceCount == 0) {
        destroyInstance(instance, nullptr);
        dlclose(handle);
        errOut = "no Vulkan devices";
        return false;
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    if (enumeratePhysicalDevices(instance, &deviceCount, devices.data()) != VK_SUCCESS) {
        destroyInstance(instance, nullptr);
        dlclose(handle);
        errOut = "vkEnumeratePhysicalDevices failed";
        return false;
    }

    VkPhysicalDeviceProperties props{};
    getPhysicalDeviceProperties(devices[0], &props);

    bool hasBudget = false;
    if (enumerateDeviceExtensionProperties) {
        uint32_t extCount = 0;
        if (enumerateDeviceExtensionProperties(devices[0], nullptr, &extCount, nullptr) == VK_SUCCESS && extCount > 0) {
            std::vector<VkExtensionProperties> exts(extCount);
            if (enumerateDeviceExtensionProperties(devices[0], nullptr, &extCount, exts.data()) == VK_SUCCESS) {
                for (const auto& ext : exts) {
                    if (std::string(ext.extensionName) == VK_EXT_MEMORY_BUDGET_EXTENSION_NAME) {
                        hasBudget = true;
                        break;
                    }
                }
            }
        }
    }

    ctx.handle = handle;
    ctx.getInstanceProcAddr = gpa;
    ctx.instance = instance;
    ctx.physicalDevice = devices[0];
    ctx.destroyInstance = destroyInstance;
    ctx.enumeratePhysicalDevices = enumeratePhysicalDevices;
    ctx.getPhysicalDeviceProperties = getPhysicalDeviceProperties;
    ctx.enumerateDeviceExtensionProperties = enumerateDeviceExtensionProperties;
    ctx.getMemoryProperties = getMemoryProperties;
    ctx.getMemoryProperties2 = getMemoryProperties2;
    ctx.instanceVersion = instanceVersionNum;
    ctx.properties = props;
    ctx.hasMemoryBudget = hasBudget && (getMemoryProperties2 != nullptr);
    ctx.supported = true;
    return true;
}

static const VulkanContext& get_vulkan_context() {
    {
        std::lock_guard<std::mutex> lock(g_vulkan_mutex);
        if (g_vulkan_init_attempted) {
            return g_vulkan_ctx;
        }
        g_vulkan_init_attempted = true;
    }

    auto ctx = std::make_shared<VulkanContext>();
    auto err = std::make_shared<std::string>();
    auto future = std::async(std::launch::async, [ctx, err]() {
        return init_vulkan_context_internal(*ctx, *err);
    });
    if (future.wait_for(std::chrono::seconds(2)) != std::future_status::ready) {
        ctx->supported = false;
        ctx->error = "timeout during Vulkan probe";
    } else {
        bool ok = future.get();
        if (!ok) {
            ctx->supported = false;
            ctx->error = *err;
        }
    }
    ctx->initialized = true;

    {
        std::lock_guard<std::mutex> lock(g_vulkan_mutex);
        g_vulkan_ctx = *ctx;
    }
    return g_vulkan_ctx;
}

std::string get_vulkan_info_json() {
    const VulkanContext& ctx = get_vulkan_context();
    if (!ctx.supported) {
        return make_vulkan_json(false, "", "", "", 0, 0, "", ctx.error);
    }

    std::stringstream dv;
    dv << "0x" << std::uppercase << std::hex << std::setw(8) << std::setfill('0')
       << ctx.properties.driverVersion;

    return make_vulkan_json(
            true,
            format_vk_version(ctx.instanceVersion),
            format_vk_version(ctx.properties.apiVersion),
            dv.str(),
            ctx.properties.vendorID,
            ctx.properties.deviceID,
            ctx.properties.deviceName,
            "");
}

struct VulkanMemorySnapshot {
    bool hasBudget = false;
    uint64_t dedicatedBudget = 0;
    uint64_t dedicatedUsed = 0;
    uint64_t sharedBudget = 0;
    uint64_t sharedUsed = 0;
    uint64_t dedicatedTotal = 0;
    uint64_t sharedTotal = 0;
};

static VulkanMemorySnapshot get_vulkan_memory_snapshot(std::string& errorOut) {
    VulkanMemorySnapshot out{};
    const VulkanContext& ctx = get_vulkan_context();
    if (!ctx.supported) {
        errorOut = ctx.error;
        return out;
    }

    VkPhysicalDeviceMemoryProperties memProps{};
    ctx.getMemoryProperties(ctx.physicalDevice, &memProps);
    std::vector<uint64_t> heapUsage(memProps.memoryHeapCount, 0);
    std::vector<uint64_t> heapBudget(memProps.memoryHeapCount, 0);

    for (uint32_t i = 0; i < memProps.memoryHeapCount; ++i) {
        bool deviceLocal = (memProps.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0;
        if (deviceLocal) {
            out.dedicatedTotal += memProps.memoryHeaps[i].size;
        } else {
            out.sharedTotal += memProps.memoryHeaps[i].size;
        }
    }

    if (ctx.hasMemoryBudget && ctx.getMemoryProperties2) {
        VkPhysicalDeviceMemoryBudgetPropertiesEXT budget{};
        budget.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MEMORY_BUDGET_PROPERTIES_EXT;
        VkPhysicalDeviceMemoryProperties2 props2{};
        props2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MEMORY_PROPERTIES_2;
        props2.pNext = &budget;
        ctx.getMemoryProperties2(ctx.physicalDevice, &props2);

        for (uint32_t i = 0; i < props2.memoryProperties.memoryHeapCount; ++i) {
            bool deviceLocal = (props2.memoryProperties.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0;
            heapUsage[i] = budget.heapUsage[i];
            heapBudget[i] = budget.heapBudget[i];
            if (deviceLocal) {
                out.dedicatedBudget += budget.heapBudget[i];
                out.dedicatedUsed += budget.heapUsage[i];
            } else {
                out.sharedBudget += budget.heapBudget[i];
                out.sharedUsed += budget.heapUsage[i];
            }
        }
        if (out.dedicatedBudget > 0 || out.sharedBudget > 0) {
            out.hasBudget = true;
        }
    }

    return out;
}

static double read_gpu_busy_percent() {
    std::string raw = read_file_string("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
    int v = parse_first_int(raw);
    if (v < 0 || v > 100) return -1.0;
    return static_cast<double>(v);
}

static double read_gpu_busy_from_gpubusy() {
    std::string raw = read_file_string("/sys/class/kgsl/kgsl-3d0/gpubusy");
    if (raw.empty()) return -1.0;
    std::stringstream ss(raw);
    long long busy = 0;
    long long total = 0;
    if (!(ss >> busy >> total)) return -1.0;
    static long long prevBusy = -1;
    static long long prevTotal = -1;
    if (prevBusy < 0 || prevTotal < 0) {
        prevBusy = busy;
        prevTotal = total;
        return -1.0;
    }
    long long deltaBusy = busy - prevBusy;
    long long deltaTotal = total - prevTotal;
    prevBusy = busy;
    prevTotal = total;
    if (deltaTotal <= 0 || deltaBusy < 0) return -1.0;
    double pct = (double)deltaBusy / (double)deltaTotal * 100.0;
    if (pct < 0.0 || pct > 100.0) return -1.0;
    return pct;
}

struct GpuThermalZones {
    bool initialized = false;
    std::vector<std::string> gpuss;
    std::vector<std::string> qmx;
    std::string socd;
};

static std::mutex g_gpu_thermal_mutex;
static GpuThermalZones g_gpu_thermal_zones;

static void init_gpu_thermal_zones() {
    std::lock_guard<std::mutex> lock(g_gpu_thermal_mutex);
    if (g_gpu_thermal_zones.initialized) return;

    DIR* dir = opendir("/sys/class/thermal");
    if (!dir) {
        g_gpu_thermal_zones.initialized = true;
        return;
    }

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        if (name.rfind("thermal_zone", 0) != 0) continue;
        std::string base = "/sys/class/thermal/" + name + "/";
        std::string type = trim(read_first_line(base + "type"));
        if (type.empty()) continue;
        std::string typeLower = lower_str(type);
        std::string tempPath = base + "temp";
        if (typeLower.rfind("gpuss", 0) == 0) {
            g_gpu_thermal_zones.gpuss.push_back(tempPath);
        } else if (typeLower.rfind("qmx", 0) == 0) {
            g_gpu_thermal_zones.qmx.push_back(tempPath);
        } else if (typeLower.find("soc") != std::string::npos) {
            if (g_gpu_thermal_zones.socd.empty()) {
                g_gpu_thermal_zones.socd = tempPath;
            }
        } else if (typeLower == "socd") {
            g_gpu_thermal_zones.socd = tempPath;
        }
    }
    closedir(dir);
    g_gpu_thermal_zones.initialized = true;
}

static double read_temp_c_from_path(const std::string& path) {
    long raw = read_long_from_file(path);
    if (raw == -1) return std::numeric_limits<double>::quiet_NaN();
    double c = (raw >= 1000) ? (raw / 1000.0) : (double)raw;
    if (c < -50 || c > 200) return std::numeric_limits<double>::quiet_NaN();
    return c;
}

static double median_of_values(std::vector<double>& values) {
    if (values.empty()) return std::numeric_limits<double>::quiet_NaN();
    std::sort(values.begin(), values.end());
    size_t mid = values.size() / 2;
    if (values.size() % 2 == 0) {
        return (values[mid - 1] + values[mid]) / 2.0;
    }
    return values[mid];
}

static double get_gpu_temp_c(std::string& sourceOut, std::string& samplesOut) {
    init_gpu_thermal_zones();

    std::vector<double> vals;
    {
        std::lock_guard<std::mutex> lock(g_gpu_thermal_mutex);
        for (const auto& path : g_gpu_thermal_zones.gpuss) {
            double v = read_temp_c_from_path(path);
            if (!std::isnan(v)) vals.push_back(v);
        }
        if (!vals.empty()) {
            sourceOut = "gpuss";
            std::stringstream ss;
            for (size_t i = 0; i < vals.size() && i < 6; ++i) {
                if (i > 0) ss << ",";
                ss << std::fixed << std::setprecision(1) << vals[i];
            }
            samplesOut = ss.str();
            return median_of_values(vals);
        }

        vals.clear();
        for (const auto& path : g_gpu_thermal_zones.qmx) {
            double v = read_temp_c_from_path(path);
            if (!std::isnan(v)) vals.push_back(v);
        }
        if (!vals.empty()) {
            sourceOut = "qmx";
            std::stringstream ss;
            for (size_t i = 0; i < vals.size() && i < 6; ++i) {
                if (i > 0) ss << ",";
                ss << std::fixed << std::setprecision(1) << vals[i];
            }
            samplesOut = ss.str();
            return median_of_values(vals);
        }

        if (!g_gpu_thermal_zones.socd.empty()) {
            double v = read_temp_c_from_path(g_gpu_thermal_zones.socd);
            if (!std::isnan(v)) {
                sourceOut = "socd";
                std::stringstream ss;
                ss << std::fixed << std::setprecision(1) << v;
                samplesOut = ss.str();
                return v;
            }
        }
    }
    sourceOut = "";
    samplesOut = "";
    return -1.0;
}

static std::string get_gpu_name_kgsl() {
    std::string name = trim(read_first_line("/sys/class/kgsl/kgsl-3d0/gpu_model"));
    return name;
}

std::string get_gpu_snapshot_json() {
    std::string error;
    double util = read_gpu_busy_percent();
    if (util < 0.0) {
        util = read_gpu_busy_from_gpubusy();
    }

    std::string tempSource;
    std::string tempSamples;
    double tempC = get_gpu_temp_c(tempSource, tempSamples);

    VulkanMemorySnapshot mem = get_vulkan_memory_snapshot(error);
    const VulkanContext& ctx = get_vulkan_context();
    std::string gpuName = get_gpu_name_kgsl();
    if (gpuName.empty() && ctx.supported) {
        gpuName = ctx.properties.deviceName;
    }

    std::stringstream driverHex;
    if (ctx.supported) {
        driverHex << "0x" << std::uppercase << std::hex << std::setw(8) << std::setfill('0')
                  << ctx.properties.driverVersion;
    }

    std::stringstream ss;
    ss << "{";
    ss << "\"gpuName\":\"" << escape_json(gpuName) << "\",";
    ss << "\"utilPercent\":" << util << ",";
    ss << "\"tempC\":" << tempC << ",";
    ss << "\"vulkanApiVersion\":\"" << escape_json(ctx.supported ? format_vk_version(ctx.properties.apiVersion) : "") << "\",";
    ss << "\"vulkanDriverVersion\":\"" << escape_json(ctx.supported ? driverHex.str() : "") << "\",";
    ss << "\"dedicatedBudgetBytes\":" << mem.dedicatedBudget << ",";
    ss << "\"dedicatedUsedBytes\":" << mem.dedicatedUsed << ",";
    ss << "\"sharedBudgetBytes\":" << mem.sharedBudget << ",";
    ss << "\"sharedUsedBytes\":" << mem.sharedUsed << ",";
    ss << "\"dedicatedTotalBytes\":" << mem.dedicatedTotal << ",";
    ss << "\"sharedTotalBytes\":" << mem.sharedTotal << ",";
    ss << "\"hasMemoryBudget\":" << (mem.hasBudget ? "true" : "false") << ",";
    ss << "\"error\":\"" << escape_json(error) << "\"";
    ss << "}";
    return ss.str();
}
