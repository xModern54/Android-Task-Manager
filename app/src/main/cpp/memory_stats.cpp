#include "memory_stats.h"
#include "native_utils.h"

#include <fstream>
#include <sstream>
#include <chrono>

struct MemInfoValues {
    long memTotalKb = 0;
    long memAvailableKb = 0;
    long cachedKb = 0;
    long compressedKb = 0;
    long committedKb = 0;
    long commitLimitKb = 0;
};

static MemInfoValues read_meminfo() {
    MemInfoValues out{};
    std::ifstream memFile("/proc/meminfo");
    if (!memFile.is_open()) return out;
    std::string line;
    while (std::getline(memFile, line)) {
        std::stringstream ss(line);
        std::string key;
        long value = 0;
        std::string unit;
        ss >> key >> value >> unit;
        if (key == "MemTotal:") {
            out.memTotalKb = value;
        } else if (key == "MemAvailable:") {
            out.memAvailableKb = value;
        } else if (key == "Cached:") {
            out.cachedKb = value;
        } else if (key == "Compressed:") {
            out.compressedKb = value;
        } else if (key == "Committed_AS:") {
            out.committedKb = value;
        } else if (key == "CommitLimit:") {
            out.commitLimitKb = value;
        }
    }
    return out;
}

std::string get_memory_snapshot_json() {
    MemInfoValues info = read_meminfo();
    long totalBytes = info.memTotalKb * 1024L;
    long availableBytes = info.memAvailableKb * 1024L;
    long cachedBytes = info.cachedKb * 1024L;
    long usedBytes = totalBytes - availableBytes;
    if (usedBytes < 0) usedBytes = 0;

    long compressedBytes = info.compressedKb * 1024L;
    long committedUsedBytes = info.committedKb * 1024L;
    long committedLimitBytes = info.commitLimitKb * 1024L;

    long long timestampMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();

    std::stringstream ss;
    ss << "{";
    ss << "\"totalBytes\":" << totalBytes << ",";
    ss << "\"usedBytes\":" << usedBytes << ",";
    ss << "\"availableBytes\":" << availableBytes << ",";
    ss << "\"cachedBytes\":" << cachedBytes << ",";
    ss << "\"compressedBytes\":" << compressedBytes << ",";
    ss << "\"committedUsedBytes\":" << committedUsedBytes << ",";
    ss << "\"committedLimitBytes\":" << committedLimitBytes << ",";
    ss << "\"timestampMs\":" << timestampMs << ",";
    ss << "\"error\":\"\"";
    ss << "}";
    return ss.str();
}
