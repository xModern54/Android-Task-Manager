#include "system_stats.h"
#include "native_utils.h"

#include <fstream>
#include <sstream>

static unsigned long long prev_global_total = 0;
static unsigned long long prev_global_idle = 0;

unsigned long long get_total_system_ticks() {
    std::ifstream statFile("/proc/stat");
    if (!statFile.is_open()) return 0;
    std::string line;
    std::getline(statFile, line);
    statFile.close();
    std::stringstream ss(line);
    std::string label;
    ss >> label;
    unsigned long long total = 0;
    unsigned long long val;
    while (ss >> val) {
        total += val;
    }
    return total;
}

double getGlobalCpuUsage() {
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
    if (prev_global_total > 0) {
        unsigned long long delta_total = total - prev_global_total;
        unsigned long long delta_idle = current_idle - prev_global_idle;
        if (delta_total > 0) {
            percent = (double(delta_total - delta_idle) / double(delta_total)) * 100.0;
        }
    }
    prev_global_total = total;
    prev_global_idle = current_idle;
    return percent;
}

RamInfo getGlobalRamUsage() {
    std::ifstream memFile("/proc/meminfo");
    long total = 0;
    long available = 0;
    if (memFile.is_open()) {
        std::string token;
        long value;
        std::string unit;
        std::string line;
        while (std::getline(memFile, line)) {
            std::stringstream ss(line);
            ss >> token >> value >> unit;
            if (token == "MemTotal:") {
                total = value * 1024;
            } else if (token == "MemAvailable:") {
                available = value * 1024;
            }
            if (total > 0 && available > 0) break;
        }
        memFile.close();
    }
    return {total, total - available};
}

long get_mem_available() {
    RamInfo ram = getGlobalRamUsage();
    return ram.total - ram.used;
}
