#include "process_detail.h"
#include "native_utils.h"

#include <fstream>
#include <sstream>
#include <unordered_set>
#include <unordered_map>
#include <vector>
#include <algorithm>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>
#include <limits.h>
#include <cstdio>
#include <cstdlib>
#include <cerrno>
#include <cstring>

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

std::string get_status_field(const std::string& pid, const std::string& field) {
    std::ifstream statusFile("/proc/" + pid + "/status");
    std::string line;
    while (std::getline(statusFile, line)) {
        if (line.rfind(field, 0) == 0) {
            size_t colon = line.find(':');
            if (colon != std::string::npos) {
                std::string val = line.substr(colon + 1);
                size_t first = val.find_first_not_of(" \t");
                if (first == std::string::npos) return "";
                size_t last = val.find_last_not_of(" \t");
                return val.substr(first, (last - first + 1));
            }
        }
    }
    return "Unknown";
}

std::string get_exe_path(const std::string& pid) {
    char buf[PATH_MAX];
    std::string linkPath = "/proc/" + pid + "/exe";
    ssize_t len = readlink(linkPath.c_str(), buf, sizeof(buf) - 1);
    if (len != -1) {
        buf[len] = '\0';
        return std::string(buf);
    }
    return "Access Denied / Not Available";
}

std::string get_oom_score(const std::string& pid) {
    std::ifstream oomFile("/proc/" + pid + "/oom_score");
    std::string score;
    if (oomFile >> score) return score;
    return "N/A";
}

long get_system_uptime() {
    std::ifstream uptimeFile("/proc/uptime");
    double uptime = 0;
    if (uptimeFile >> uptime) return (long)uptime;
    return 0;
}

long get_process_elapsed_time(const std::string& pid) {
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
    for (int i = 0; i < 19; ++i) {
        if (!(ss >> token)) return 0;
    }
    if (ss >> token) {
        try {
            unsigned long long start_ticks = std::stoull(token);
            long clk_tck = sysconf(_SC_CLK_TCK);
            long uptime = get_system_uptime();
            long process_seconds = start_ticks / clk_tck;
            return uptime - process_seconds;
        } catch (...) {
            return 0;
        }
    }
    return 0;
}

void get_sched_info(const std::string& pid, std::string& priority, std::string& nice) {
    std::string statPath = "/proc/" + pid + "/stat";
    std::ifstream statFile(statPath);
    if (!statFile.is_open()) return;
    std::string content((std::istreambuf_iterator<char>(statFile)), std::istreambuf_iterator<char>());
    statFile.close();
    size_t lastParen = content.rfind(')');
    if (lastParen == std::string::npos) return;
    std::string tail = content.substr(lastParen + 1);
    std::stringstream ss(tail);
    std::string token;
    for (int i = 0; i < 15; ++i) ss >> token;
    if (ss >> token) priority = token;
    if (ss >> token) nice = token;
}

struct ModuleInfo {
    std::string path;
    long size;
};

std::vector<std::string> get_modules_list(const std::string& pid) {
    std::vector<std::string> modules;
    std::ifstream mapsFile("/proc/" + pid + "/maps");
    if (!mapsFile.is_open()) {
        return modules;
    }

    std::string line;
    std::unordered_set<std::string> uniquePaths;
    std::unordered_map<std::string, long> moduleSizes;

    while (std::getline(mapsFile, line)) {
        try {
            size_t dashPos = line.find('-');
            size_t spacePos = line.find(' ');

            if (dashPos != std::string::npos && spacePos != std::string::npos) {
                std::string startStr = line.substr(0, dashPos);
                std::string endStr = line.substr(dashPos + 1, spacePos - (dashPos + 1));

                unsigned long long start = std::strtoull(startStr.c_str(), nullptr, 16);
                unsigned long long end = std::strtoull(endStr.c_str(), nullptr, 16);
                long mappedSize = (long)(end - start);

                size_t pathPos = line.find('/');
                if (pathPos != std::string::npos) {
                    std::string path = line.substr(pathPos);
                    size_t last = path.find_last_not_of(" \t\n\r");
                    if (last != std::string::npos) path = path.substr(0, last + 1);

                    if (path.find(".so") != std::string::npos ||
                        path.find(".apk") != std::string::npos ||
                        path.find(".dex") != std::string::npos ||
                        path.find(".oat") != std::string::npos ||
                        path.find(".jar") != std::string::npos ||
                        path.find(".ttf") != std::string::npos) {

                        uniquePaths.insert(path);
                        moduleSizes[path] += mappedSize;
                    }
                }
            }
        } catch (...) {
            continue;
        }
    }

    std::vector<ModuleInfo> sortedModules;
    struct stat st;

    for (const auto& path : uniquePaths) {
        long finalSize = 0;

        if (stat(path.c_str(), &st) == 0) {
            finalSize = st.st_size;
        }

        if (finalSize <= 0) {
            finalSize = moduleSizes[path];
        }

        sortedModules.push_back({path, finalSize});
    }

    std::sort(sortedModules.begin(), sortedModules.end(), [](const ModuleInfo& a, const ModuleInfo& b) {
        return a.size > b.size;
    });

    for (const auto& mod : sortedModules) {
        size_t slash = mod.path.rfind('/');
        std::string filename = (slash != std::string::npos) ? mod.path.substr(slash + 1) : mod.path;

        modules.push_back(filename + "|" + mod.path + "|" + std::to_string(mod.size));
    }

    return modules;
}

std::vector<std::string> get_threads_list(const std::string& pid) {
    std::vector<std::string> threads;
    std::string taskPath = "/proc/" + pid + "/task";
    DIR* taskDir = opendir(taskPath.c_str());
    if (taskDir == nullptr) return threads;

    struct dirent* entry;
    while ((entry = readdir(taskDir)) != nullptr) {
        if (entry->d_type == DT_DIR) {
            std::string tid = entry->d_name;
            if (tid.find_first_not_of("0123456789") == std::string::npos) {
                std::string commPath = taskPath + "/" + tid + "/comm";
                std::ifstream commFile(commPath);
                std::string name = "Unknown";
                if (commFile.is_open()) {
                    std::getline(commFile, name);
                    if (!name.empty() && name.back() == '\n') name.pop_back();
                }
                threads.push_back(tid + ":" + name);
            }
        }
    }
    closedir(taskDir);
    return threads;
}

void get_detailed_status(const std::string& pid, std::unordered_map<std::string, std::string>& out) {
    std::ifstream statusFile("/proc/" + pid + "/status");
    std::string line;
    while (std::getline(statusFile, line)) {
        if (line.rfind("voluntary_ctxt_switches:", 0) == 0) {
            out["voluntary_ctxt_switches"] = line.substr(25);
        } else if (line.rfind("nonvoluntary_ctxt_switches:", 0) == 0) {
            out["nonvoluntary_ctxt_switches"] = line.substr(28);
        }
    }
}

void get_page_faults(const std::string& pid, std::unordered_map<std::string, std::string>& out) {
    std::string statPath = "/proc/" + pid + "/stat";
    std::ifstream statFile(statPath);
    if (!statFile.is_open()) return;

    std::string content((std::istreambuf_iterator<char>(statFile)), std::istreambuf_iterator<char>());
    statFile.close();

    size_t lastParen = content.rfind(')');
    if (lastParen == std::string::npos) return;
    std::string tail = content.substr(lastParen + 1);
    std::stringstream ss(tail);
    std::string token;

    for (int i = 3; i < 10; ++i) ss >> token;

    if (ss >> token) out["minflt"] = token;
    ss >> token;
    if (ss >> token) out["majflt"] = token;
}
