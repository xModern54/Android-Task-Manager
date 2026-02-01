#include "disk_stats.h"
#include "native_utils.h"

#include <sys/statvfs.h>
#include <sstream>
#include <string>
#include <vector>
#include <ctime>

namespace {

struct DiskSample {
    long long reads = 0;
    long long writes = 0;
    long long sectorsRead = 0;
    long long sectorsWritten = 0;
    long long readTimeMs = 0;
    long long writeTimeMs = 0;
    long long ioTimeMs = 0;
    long long timestampMs = 0;
    bool valid = false;
};

static DiskSample g_lastSample;

long long now_ms() {
    struct timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000LL + ts.tv_nsec / 1000000LL;
}

std::string basename_path(const std::string& path) {
    size_t pos = path.find_last_of('/');
    if (pos == std::string::npos) return path;
    return path.substr(pos + 1);
}

std::string find_mount_source(const std::string& mountPoint) {
    std::string mounts = read_file_string("/proc/mounts");
    if (mounts.empty()) return "";
    std::stringstream ss(mounts);
    std::string line;
    while (std::getline(ss, line)) {
        std::stringstream ls(line);
        std::string src, mnt;
        if (!(ls >> src >> mnt)) continue;
        if (mnt == mountPoint) return src;
    }
    return "";
}

bool read_block_stat(const std::string& dev, DiskSample& out) {
    std::string statPath = "/sys/block/" + dev + "/stat";
    std::string content = read_file_string(statPath);
    if (content.empty()) return false;
    std::stringstream ss(content);
    long long vals[11];
    for (int i = 0; i < 11; ++i) {
        if (!(ss >> vals[i])) return false;
    }
    out.reads = vals[0];
    out.sectorsRead = vals[2];
    out.readTimeMs = vals[3];
    out.writes = vals[4];
    out.sectorsWritten = vals[6];
    out.writeTimeMs = vals[7];
    out.ioTimeMs = vals[9];
    out.timestampMs = now_ms();
    out.valid = true;
    return true;
}

} // namespace

std::string get_disk_snapshot_json() {
    const std::string mountPoint = "/data";
    std::string error;

    struct statvfs vfs{};
    long long totalBytes = 0;
    long long availableBytes = 0;
    long long usedBytes = 0;
    if (statvfs(mountPoint.c_str(), &vfs) == 0) {
        totalBytes = (long long)vfs.f_blocks * (long long)vfs.f_frsize;
        availableBytes = (long long)vfs.f_bavail * (long long)vfs.f_frsize;
        usedBytes = totalBytes - availableBytes;
    } else {
        error = "statvfs_failed";
    }

    std::string source = find_mount_source(mountPoint);
    std::string blockDevice = "";
    if (!source.empty()) {
        if (source.rfind("/dev/", 0) == 0) {
            blockDevice = basename_path(source);
        } else {
            blockDevice = source;
        }
    }

    long long readBps = 0;
    long long writeBps = 0;
    double activeTimePct = 0.0;
    double avgResponseMs = 0.0;
    long long timestampMs = now_ms();

    if (!blockDevice.empty()) {
        DiskSample current;
        if (read_block_stat(blockDevice, current)) {
            timestampMs = current.timestampMs;
            if (g_lastSample.valid) {
                long long dtMs = current.timestampMs - g_lastSample.timestampMs;
                if (dtMs > 0) {
                    long long dSectorsRead = current.sectorsRead - g_lastSample.sectorsRead;
                    long long dSectorsWritten = current.sectorsWritten - g_lastSample.sectorsWritten;
                    long long dIoTime = current.ioTimeMs - g_lastSample.ioTimeMs;
                    long long dReadTime = current.readTimeMs - g_lastSample.readTimeMs;
                    long long dWriteTime = current.writeTimeMs - g_lastSample.writeTimeMs;
                    long long dReads = current.reads - g_lastSample.reads;
                    long long dWrites = current.writes - g_lastSample.writes;

                    if (dSectorsRead < 0) dSectorsRead = 0;
                    if (dSectorsWritten < 0) dSectorsWritten = 0;
                    if (dIoTime < 0) dIoTime = 0;
                    if (dReadTime < 0) dReadTime = 0;
                    if (dWriteTime < 0) dWriteTime = 0;
                    if (dReads < 0) dReads = 0;
                    if (dWrites < 0) dWrites = 0;

                    long long sectorSize = read_long_from_file("/sys/block/" + blockDevice + "/queue/logical_block_size");
                    if (sectorSize <= 0) sectorSize = 512;

                    readBps = (dSectorsRead * sectorSize * 1000LL) / dtMs;
                    writeBps = (dSectorsWritten * sectorSize * 1000LL) / dtMs;

                    activeTimePct = (double)dIoTime * 100.0 / (double)dtMs;
                    if (activeTimePct < 0.0) activeTimePct = 0.0;
                    if (activeTimePct > 100.0) activeTimePct = 100.0;

                    long long ops = dReads + dWrites;
                    if (ops > 0) {
                        avgResponseMs = (double)(dReadTime + dWriteTime) / (double)ops;
                    }
                }
            }
            g_lastSample = current;
        } else {
            if (error.empty()) error = "stat_read_failed";
        }
    } else {
        if (error.empty()) error = "mount_not_found";
    }

    std::stringstream ss;
    ss << "{";
    ss << "\"totalBytes\":" << totalBytes << ",";
    ss << "\"usedBytes\":" << usedBytes << ",";
    ss << "\"availableBytes\":" << availableBytes << ",";
    ss << "\"readBps\":" << readBps << ",";
    ss << "\"writeBps\":" << writeBps << ",";
    ss << "\"activeTimePct\":" << activeTimePct << ",";
    ss << "\"avgResponseMs\":" << avgResponseMs << ",";
    ss << "\"mountPoint\":\"" << escape_json(mountPoint) << "\",";
    ss << "\"blockDevice\":\"" << escape_json(blockDevice) << "\",";
    ss << "\"timestampMs\":" << timestampMs << ",";
    ss << "\"error\":\"" << escape_json(error) << "\"";
    ss << "}";
    return ss.str();
}
