#include "process_scan.h"
#include "native_common.h"
#include "system_stats.h"
#include "process_detail.h"
#include "native_utils.h"

#include <dirent.h>
#include <unistd.h>
#include <unordered_map>
#include <unordered_set>
#include <sstream>
#include <fstream>

struct ProcessHistory {
    unsigned long long proc_ticks;
    unsigned long long sys_ticks;
};

static std::unordered_map<int, ProcessHistory> history_map;

static unsigned long long get_process_ticks(const std::string& pid) {
    std::string statPath = "/proc/" + pid + "/stat";
    std::ifstream statFile(statPath);
    if (!statFile.is_open()) return 0;
    std::string content((std::istreambuf_iterator<char>(statFile)), std::istreambuf_iterator<char>());
    statFile.close();
    size_t lastParen = content.rfind(')');
    if (lastParen == std::string::npos) return 0;
    std::string tail = content.substr(lastParen + 1);
    std::stringstream ss(tail);
    std::string token;
    for (int i = 0; i < 11; ++i) {
        if (!(ss >> token)) return 0;
    }
    unsigned long long utime = 0;
    unsigned long long stime = 0;
    if (ss >> token) utime = std::stoull(token);
    if (ss >> token) stime = std::stoull(token);
    return utime + stime;
}

std::string build_process_list() {
    std::stringstream ss;
    double globalCpu = getGlobalCpuUsage();
    RamInfo globalRam = getGlobalRamUsage();
    ss << "HEAD|" << globalCpu << "|" << globalRam.used << "|" << globalRam.total << "\n";

    DIR* procDir = opendir("/proc");
    long pageSize = sysconf(_SC_PAGESIZE);
    unsigned long long current_system_ticks = get_total_system_ticks();
    std::unordered_set<int> current_scan_pids;

    if (procDir == nullptr) {
        LOGE("Failed to open /proc");
        return ss.str();
    }

    struct dirent* entry;
    while ((entry = readdir(procDir)) != nullptr) {
        if (entry->d_type == DT_DIR) {
            std::string pid_str = entry->d_name;
            if (pid_str.find_first_not_of("0123456789") == std::string::npos) {
                int pid = std::stoi(pid_str);
                std::string name = getProcessName(pid_str);
                long ramBytes = getProcessRamBytes(pid_str, pageSize);

                if (ramBytes > 0) {
                    unsigned long long current_proc_ticks = get_process_ticks(pid_str);
                    double cpu_percent = 0.0;

                    if (history_map.count(pid)) {
                        unsigned long long delta_proc = current_proc_ticks - history_map[pid].proc_ticks;
                        unsigned long long delta_sys = current_system_ticks - history_map[pid].sys_ticks;
                        if (delta_sys > 0) {
                            cpu_percent = (double(delta_proc) / double(delta_sys)) * 100.0;
                        }
                    }
                    history_map[pid] = {current_proc_ticks, current_system_ticks};
                    current_scan_pids.insert(pid);

                    std::string prio, nice;
                    get_sched_info(pid_str, prio, nice);

                    ss << pid << "|" << name << "|" << ramBytes << "|" << cpu_percent << "|" << nice << "\n";
                }
            }
        }
    }
    closedir(procDir);

    for (auto it = history_map.begin(); it != history_map.end(); ) {
        if (current_scan_pids.find(it->first) == current_scan_pids.end()) {
            it = history_map.erase(it);
        } else {
            ++it;
        }
    }

    return ss.str();
}
