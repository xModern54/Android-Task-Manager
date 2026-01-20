#include <jni.h>
#include <string>
#include <vector>
#include <dirent.h>
#include <fstream>
#include <iostream>
#include <sstream>
#include <android/log.h>
#include <unistd.h>
#include <unordered_map>
#include <unordered_set>
#include <numeric>

#define LOG_TAG "SystemManagerNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Data structure to store previous state for CPU calculation
struct ProcessHistory {
    unsigned long long proc_ticks; // utime + stime
    unsigned long long sys_ticks;  // Total system time at that moment
};

// Static map to persist data between JNI calls
static std::unordered_map<int, ProcessHistory> history_map;

// --- Helper Functions ---

// Get total system ticks from /proc/stat
unsigned long long get_total_system_ticks() {
    std::ifstream statFile("/proc/stat");
    if (!statFile.is_open()) return 0;

    std::string line;
    std::getline(statFile, line); // Read first line: "cpu  ..."
    statFile.close();

    std::stringstream ss(line);
    std::string label;
    ss >> label; // Skip "cpu"

    unsigned long long total = 0;
    unsigned long long val;
    while (ss >> val) {
        total += val;
    }
    return total;
}

// Get process specific ticks (utime + stime) from /proc/[pid]/stat
unsigned long long get_process_ticks(const std::string& pid) {
    std::string statPath = "/proc/" + pid + "/stat";
    std::ifstream statFile(statPath);
    if (!statFile.is_open()) return 0;

    std::string content((std::istreambuf_iterator<char>(statFile)), std::istreambuf_iterator<char>());
    statFile.close();

    // Skip to the part after the filename to avoid parsing errors with spaces
    size_t lastParen = content.rfind(')');
    if (lastParen == std::string::npos) return 0;

    std::string tail = content.substr(lastParen + 1);
    std::stringstream ss(tail);
    std::string token;

    // Tokens after ')' start at index 0 (State).
    // utime is field 14 (index 13 from start), stime is 15 (index 14).
    // In 'tail', they are at index 11 and 12 (because we skipped 2 fields: pid and comm).
    // Wait, let's recount.
    // Original fields:
    // 1: pid, 2: comm, 3: state, 4: ppid ... 14: utime, 15: stime.
    // Tail starts at field 3 (State).
    // So State is index 0.
    // We need 14 (utime) -> index 11.
    // We need 15 (stime) -> index 12.
    
    // Skip 11 fields (0 to 10)
    for (int i = 0; i < 11; ++i) {
        if (!(ss >> token)) return 0;
    }

    unsigned long long utime = 0;
    unsigned long long stime = 0;

    if (ss >> token) utime = std::stoull(token);
    if (ss >> token) stime = std::stoull(token);

    return utime + stime;
}

std::string getProcessName(const std::string& pid) {
    std::string name;
    std::string cmdlinePath = "/proc/" + pid + "/cmdline";
    std::ifstream cmdlineFile(cmdlinePath);

    if (cmdlineFile.is_open()) {
        std::getline(cmdlineFile, name, '\0');
        cmdlineFile.close();
    }

    if (name.empty()) {
        std::string commPath = "/proc/" + pid + "/comm";
        std::ifstream commFile(commPath);
        if (commFile.is_open()) {
            std::getline(commFile, name);
            commFile.close();
            if (!name.empty() && name.back() == '\n') {
                name.pop_back();
            }
        }
    }
    return name.empty() ? "Unknown" : name;
}

long getProcessRamBytes(const std::string& pid, long pageSize) {
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

    // RSS is field 24.
    // Tail starts at field 3.
    // Skip 21 fields (3 to 23).
    for (int i = 0; i < 21; ++i) {
        if (!(ss >> token)) return 0;
    }

    if (ss >> token) {
        try {
            long rssPages = std::stol(token);
            return rssPages * pageSize;
        } catch (...) {
            return 0;
        }
    }
    return 0;
}

// --- JNI Export ---

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getProcessList(
        JNIEnv* env,
        jobject /* this */) {

    std::stringstream ss;
    DIR* procDir = opendir("/proc");
    long pageSize = sysconf(_SC_PAGESIZE);
    unsigned long long current_system_ticks = get_total_system_ticks();
    
    // Set to track alive processes for garbage collection
    std::unordered_set<int> current_scan_pids;

    if (procDir == nullptr) {
        LOGE("Failed to open /proc");
        return env->NewStringUTF("");
    }

    struct dirent* entry;
    while ((entry = readdir(procDir)) != nullptr) {
        if (entry->d_type == DT_DIR) {
            std::string pid_str = entry->d_name;
            if (pid_str.find_first_not_of("0123456789") == std::string::npos) {
                int pid = std::stoi(pid_str);
                
                // 1. Basic Info
                std::string name = getProcessName(pid_str);
                long ramBytes = getProcessRamBytes(pid_str, pageSize);
                
                // Filter out kernel threads/processes with 0 RAM
                if (ramBytes > 0) {
                    // 2. CPU Calculation
                    unsigned long long current_proc_ticks = get_process_ticks(pid_str);
                    double cpu_percent = 0.0;

                    if (history_map.count(pid)) {
                        unsigned long long delta_proc = current_proc_ticks - history_map[pid].proc_ticks;
                        unsigned long long delta_sys = current_system_ticks - history_map[pid].sys_ticks;

                        if (delta_sys > 0) {
                            cpu_percent = (double(delta_proc) / double(delta_sys)) * 100.0;
                        }
                    }

                    // Update history
                    history_map[pid] = {current_proc_ticks, current_system_ticks};
                    current_scan_pids.insert(pid);

                    // 3. Format: PID|Name|Bytes|CPU
                    // Use fixed precision for CPU to save string space
                    ss << pid << "|" << name << "|" << ramBytes << "|" << cpu_percent << "\n";
                }
            }
        }
    }
    closedir(procDir);

    // Garbage Collection: Remove dead processes from history
    for (auto it = history_map.begin(); it != history_map.end(); ) {
        if (current_scan_pids.find(it->first) == current_scan_pids.end()) {
            it = history_map.erase(it);
        } else {
            ++it;
        }
    }

    return env->NewStringUTF(ss.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_hello(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF("Hello from C++ Native Backend!");
}
