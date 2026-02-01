#include "performance_mini.h"
#include "native_utils.h"

#include <dirent.h>
#include <sys/statvfs.h>
#include <sstream>
#include <string>
#include <vector>
#include <ctime>
#include <fstream>

namespace {

long long now_ms() {
    struct timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000LL + ts.tv_nsec / 1000000LL;
}

// CPU mini
static unsigned long long prev_total = 0;
static unsigned long long prev_idle = 0;

double get_cpu_util_percent_mini() {
    std::ifstream statFile("/proc/stat");
    if (!statFile.is_open()) return 0.0;
    std::string line;
    std::getline(statFile, line);
    statFile.close();
    std::stringstream ss(line);
    std::string label;
    ss >> label;
    unsigned long long user, nice, system, idle, iowait, irq, softirq, steal, guest, guest_nice;
    ss >> user >> nice >> system >> idle >> iowait >> irq >> softirq >> steal >> guest >> guest_nice;
    unsigned long long total = user + nice + system + idle + iowait + irq + softirq + steal + guest + guest_nice;
    unsigned long long current_idle = idle + iowait;
    double percent = 0.0;
    if (prev_total > 0) {
        unsigned long long delta_total = total - prev_total;
        unsigned long long delta_idle = current_idle - prev_idle;
        if (delta_total > 0) {
            percent = (double(delta_total - delta_idle) / double(delta_total)) * 100.0;
        }
    }
    prev_total = total;
    prev_idle = current_idle;
    return percent;
}

long get_max_freq_khz_mini() {
    long maxFreq = -1;
    DIR* dir = opendir("/sys/devices/system/cpu/cpufreq");
    if (dir) {
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_type == DT_DIR) {
                std::string name = entry->d_name;
                if (name.rfind("policy", 0) == 0) {
                    std::string base = "/sys/devices/system/cpu/cpufreq/" + name + "/";
                    long cur = read_long_from_file(base + "scaling_cur_freq");
                    if (cur <= 0) cur = read_long_from_file(base + "cpuinfo_cur_freq");
                    if (cur > maxFreq) maxFreq = cur;
                }
            }
        }
        closedir(dir);
    }
    if (maxFreq > 0) return maxFreq;

    DIR* cpuDir = opendir("/sys/devices/system/cpu");
    if (!cpuDir) return 0;
    struct dirent* entry;
    while ((entry = readdir(cpuDir)) != nullptr) {
        if (entry->d_type == DT_DIR) {
            std::string name = entry->d_name;
            if (name.rfind("cpu", 0) == 0) {
                std::string idx = name.substr(3);
                if (idx.empty() || idx.find_first_not_of("0123456789") != std::string::npos) continue;
                std::string base = "/sys/devices/system/cpu/" + name + "/cpufreq/";
                long cur = read_long_from_file(base + "scaling_cur_freq");
                if (cur <= 0) cur = read_long_from_file(base + "cpuinfo_cur_freq");
                if (cur > maxFreq) maxFreq = cur;
            }
        }
    }
    closedir(cpuDir);
    return (maxFreq > 0) ? maxFreq : 0;
}

// Memory mini
void read_meminfo_mini(long& usedBytes, long& totalBytes) {
    std::ifstream memFile("/proc/meminfo");
    long totalKb = 0;
    long availKb = 0;
    if (memFile.is_open()) {
        std::string line;
        while (std::getline(memFile, line)) {
            std::stringstream ss(line);
            std::string key;
            long value = 0;
            std::string unit;
            ss >> key >> value >> unit;
            if (key == "MemTotal:") {
                totalKb = value;
            } else if (key == "MemAvailable:") {
                availKb = value;
            }
            if (totalKb > 0 && availKb > 0) break;
        }
    }
    totalBytes = totalKb * 1024L;
    long availableBytes = availKb * 1024L;
    long used = totalBytes - availableBytes;
    if (used < 0) used = 0;
    usedBytes = used;
}

// Disk mini
struct DiskSampleMini {
    long long sectorsRead = 0;
    long long sectorsWritten = 0;
    long long timestampMs = 0;
    bool valid = false;
};

static DiskSampleMini g_disk_last;
static std::string g_disk_dev;
static long long g_disk_dev_ts = 0;
static long long g_disk_sector_size = 512;

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

std::string basename_path(const std::string& path) {
    size_t pos = path.find_last_of('/');
    if (pos == std::string::npos) return path;
    return path.substr(pos + 1);
}

bool read_block_stat_mini(const std::string& dev, DiskSampleMini& out) {
    std::string statPath = "/sys/block/" + dev + "/stat";
    std::string content = read_file_string(statPath);
    if (content.empty()) return false;
    std::stringstream ss(content);
    long long vals[11];
    for (int i = 0; i < 11; ++i) {
        if (!(ss >> vals[i])) return false;
    }
    out.sectorsRead = vals[2];
    out.sectorsWritten = vals[6];
    out.timestampMs = now_ms();
    out.valid = true;
    return true;
}

void ensure_disk_dev() {
    long long now = now_ms();
    if (!g_disk_dev.empty() && (now - g_disk_dev_ts) < 10000) return;
    std::string source = find_mount_source("/data");
    if (!source.empty()) {
        if (source.rfind("/dev/", 0) == 0) {
            g_disk_dev = basename_path(source);
        } else {
            g_disk_dev = source;
        }
        long sz = read_long_from_file("/sys/block/" + g_disk_dev + "/queue/logical_block_size");
        if (sz > 0) g_disk_sector_size = sz;
    }
    g_disk_dev_ts = now;
}

void get_disk_bps_mini(long long& readBps, long long& writeBps) {
    readBps = 0;
    writeBps = 0;
    ensure_disk_dev();
    if (g_disk_dev.empty()) return;
    DiskSampleMini cur;
    if (!read_block_stat_mini(g_disk_dev, cur)) return;
    if (g_disk_last.valid) {
        long long dtMs = cur.timestampMs - g_disk_last.timestampMs;
        if (dtMs > 0) {
            long long dRead = cur.sectorsRead - g_disk_last.sectorsRead;
            long long dWrite = cur.sectorsWritten - g_disk_last.sectorsWritten;
            if (dRead < 0) dRead = 0;
            if (dWrite < 0) dWrite = 0;
            readBps = (dRead * g_disk_sector_size * 1000LL) / dtMs;
            writeBps = (dWrite * g_disk_sector_size * 1000LL) / dtMs;
        }
    }
    g_disk_last = cur;
}

// Network mini
struct NetCountersMini {
    std::string iface;
    long long rxBytes = 0;
    long long txBytes = 0;
};

bool iface_is_up(const std::string& iface) {
    std::string oper = read_first_line("/sys/class/net/" + iface + "/operstate");
    if (!oper.empty()) {
        std::string lower = to_lower(trim(oper));
        if (lower == "up") return true;
    }
    std::string carrier = read_first_line("/sys/class/net/" + iface + "/carrier");
    if (!carrier.empty()) {
        return trim(carrier) == "1";
    }
    return false;
}

std::vector<std::string> list_ifaces() {
    std::string names = read_file_string("/proc/net/dev");
    std::vector<std::string> out;
    if (names.empty()) return out;
    std::stringstream ss(names);
    std::string line;
    int lineNo = 0;
    while (std::getline(ss, line)) {
        lineNo++;
        if (lineNo <= 2) continue;
        auto colon = line.find(':');
        if (colon == std::string::npos) continue;
        std::string name = trim(line.substr(0, colon));
        if (!name.empty()) out.push_back(name);
    }
    return out;
}

bool read_iface_counters(const std::string& iface, NetCountersMini& out) {
    std::string data = read_file_string("/proc/net/dev");
    if (data.empty()) return false;
    std::stringstream ss(data);
    std::string line;
    while (std::getline(ss, line)) {
        auto colon = line.find(':');
        if (colon == std::string::npos) continue;
        std::string name = trim(line.substr(0, colon));
        if (name != iface) continue;
        std::stringstream ls(line.substr(colon + 1));
        long long rxBytes = 0, rxPackets = 0, rxErr = 0, rxDrop = 0, rxFifo = 0, rxFrame = 0, rxCompressed = 0, rxMulticast = 0;
        long long txBytes = 0, txPackets = 0, txErr = 0, txDrop = 0, txFifo = 0, txColls = 0, txCarrier = 0, txCompressed = 0;
        if (!(ls >> rxBytes >> rxPackets >> rxErr >> rxDrop >> rxFifo >> rxFrame >> rxCompressed >> rxMulticast
                 >> txBytes >> txPackets >> txErr >> txDrop >> txFifo >> txColls >> txCarrier >> txCompressed)) {
            return false;
        }
        out.iface = iface;
        out.rxBytes = rxBytes;
        out.txBytes = txBytes;
        return true;
    }
    return false;
}

std::string pick_active_iface() {
    auto ifaces = list_ifaces();
    std::string firstUp;
    std::string firstActive;
    for (const auto& iface : ifaces) {
        if (iface == "lo") continue;
        if (iface.rfind("wlan", 0) == 0 && iface_is_up(iface)) return iface;
    }
    for (const auto& iface : ifaces) {
        if (iface == "lo") continue;
        if (iface_is_up(iface) && firstUp.empty()) firstUp = iface;
        NetCountersMini c;
        if (read_iface_counters(iface, c)) {
            if ((c.rxBytes + c.txBytes) > 0 && firstActive.empty()) firstActive = iface;
        }
    }
    if (!firstUp.empty()) return firstUp;
    if (!firstActive.empty()) return firstActive;
    for (const auto& iface : ifaces) {
        if (iface != "lo") return iface;
    }
    return "";
}

NetCountersMini get_net_counters_mini() {
    NetCountersMini out;
    std::string iface = pick_active_iface();
    if (!iface.empty()) {
        read_iface_counters(iface, out);
    }
    return out;
}

// GPU mini
int read_gpu_busy_percent() {
    std::string raw = read_file_string("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
    if (raw.empty()) return -1;
    std::stringstream ss(raw);
    int v = -1;
    ss >> v;
    return v;
}

} // namespace

std::string get_performance_mini_snapshot_json() {
    long long timestampMs = now_ms();

    double cpuUtil = get_cpu_util_percent_mini();
    long cpuMaxKHz = get_max_freq_khz_mini();

    long memUsed = 0;
    long memTotal = 0;
    read_meminfo_mini(memUsed, memTotal);

    long long diskReadBps = 0;
    long long diskWriteBps = 0;
    get_disk_bps_mini(diskReadBps, diskWriteBps);

    NetCountersMini net = get_net_counters_mini();

    int gpuUtil = read_gpu_busy_percent();

    std::stringstream ss;
    ss << "{";
    ss << "\"timestampMs\":" << timestampMs << ",";
    ss << "\"cpu\":{\"util\":" << cpuUtil << ",\"maxFreqKHz\":" << cpuMaxKHz << "},";
    ss << "\"mem\":{\"usedBytes\":" << memUsed << ",\"totalBytes\":" << memTotal << "},";
    ss << "\"disk\":{\"readBps\":" << diskReadBps << ",\"writeBps\":" << diskWriteBps << "},";
    ss << "\"net\":{\"iface\":\"" << escape_json(net.iface) << "\",\"rxBytes\":" << net.rxBytes << ",\"txBytes\":" << net.txBytes << "},";
    ss << "\"gpu\":{\"util\":" << gpuUtil << "}";
    ss << "}";
    return ss.str();
}
