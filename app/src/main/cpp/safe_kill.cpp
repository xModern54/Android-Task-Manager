#include "safe_kill.h"
#include "native_utils.h"
#include "process_detail.h"
#include "system_stats.h"

#include <fstream>
#include <dirent.h>
#include <set>
#include <unordered_set>
#include <sstream>
#include <unistd.h>
#include <cstdlib>

int get_oom_score_adj(const std::string& pid) {
    std::ifstream oomFile("/proc/" + pid + "/oom_score_adj");
    int score;
    if (oomFile >> score) return score;
    return -1000;
}

int get_uid_int(const std::string& pid) {
    std::string uid_full = get_status_field(pid, "Uid:");
    if (uid_full.empty()) return -1;
    try {
        size_t end = uid_full.find_first_of(" \t");
        return std::stoi(uid_full.substr(0, end));
    } catch (...) {
        return -1;
    }
}

std::string get_kill_candidates() {
    DIR* procDir = opendir("/proc");
    if (procDir == nullptr) return "";

    std::set<std::string> candidates;

    struct dirent* entry;
    while ((entry = readdir(procDir)) != nullptr) {
        if (entry->d_type == DT_DIR) {
            std::string pid_str = entry->d_name;
            if (pid_str.find_first_not_of("0123456789") == std::string::npos) {
                int uid = get_uid_int(pid_str);
                if (uid >= 10000) {
                    int oom_adj = get_oom_score_adj(pid_str);
                    if (oom_adj >= 100) {
                        std::string name = getProcessName(pid_str);
                        if (!name.empty() && name != "Unknown" && name != "sh" && name != "su") {
                            candidates.insert(name);
                        }
                    }
                }
            }
        }
    }
    closedir(procDir);

    std::string recentsOut = execute_shell_command("su -c \"dumpsys activity recents\"");
    std::stringstream rss(recentsOut);
    std::string line;
    while (std::getline(rss, line)) {
        size_t aPos = line.find("A=");
        if (aPos == std::string::npos) aPos = line.find("affinity=");

        if (aPos != std::string::npos) {
            size_t valStart = line.find('=', aPos) + 1;
            std::string after = line.substr(valStart);
            size_t end = after.find_first_of(" }\n");
            std::string rawPkg = after.substr(0, end);
            size_t colon = rawPkg.find(':');
            if (colon != std::string::npos) rawPkg = rawPkg.substr(colon + 1);

            if (!rawPkg.empty() &&
                rawPkg != "android" &&
                rawPkg != "com.android.systemui" &&
                rawPkg != "com.android.launcher" &&
                rawPkg != "com.google.android.inputmethod.latin") {

                candidates.insert(rawPkg);
            }
        }
    }

    std::stringstream ss;
    for (const auto& pkg : candidates) {
        ss << pkg << "|";
    }
    std::string res = ss.str();
    if (!res.empty()) res.pop_back();

    return res;
}

long execute_kill_transaction(const std::string& packages) {
    if (packages.empty()) return 0;

    RamInfo startRam = getGlobalRamUsage();
    long startAvailable = startRam.total - startRam.used;

    std::unordered_set<std::string> targets;
    std::stringstream ps(packages);
    std::string pkgToken;
    while (std::getline(ps, pkgToken, '|')) {
        if (!pkgToken.empty()) targets.insert(pkgToken);
    }

    std::string dumpsys_out = execute_shell_command("su -c \"dumpsys activity recents\"");
    std::stringstream dss(dumpsys_out);
    std::string line;

    while (std::getline(dss, line)) {
        size_t taskStart = line.find("Task{");
        if (taskStart != std::string::npos) {
            size_t hashPos = line.find('#', taskStart);
            if (hashPos == std::string::npos) continue;
            size_t idEnd = line.find(' ', hashPos);
            if (idEnd == std::string::npos) continue;
            std::string tid = line.substr(hashPos + 1, idEnd - hashPos - 1);

            size_t aPos = line.find("A=");
            if (aPos == std::string::npos) aPos = line.find("affinity=");
            if (aPos == std::string::npos) continue;

            size_t valStart = line.find('=', aPos) + 1;
            std::string after = line.substr(valStart);
            size_t pkgEnd = after.find_first_of(" }\n");
            std::string rawPkg = after.substr(0, pkgEnd);
            size_t colon = rawPkg.find(':');
            if (colon != std::string::npos) rawPkg = rawPkg.substr(colon + 1);

            if (targets.count(rawPkg)) {
                if (rawPkg == "com.example.taskmanager") continue;
                std::string cmd = "su -c \"am stack remove " + tid + "\"";
                system(cmd.c_str());
                std::string killCmd = "su -c \"am force-stop " + rawPkg + "\"";
                system(killCmd.c_str());
            }
        }
    }

    for (const auto& t : targets) {
        if (t == "com.example.taskmanager") continue;
        std::string killCmd = "su -c \"am force-stop " + t + "\"";
        system(killCmd.c_str());
    }

    usleep(200000);
    RamInfo endRam = getGlobalRamUsage();
    long endAvailable = endRam.total - endRam.used;
    long freed = endAvailable - startAvailable;
    return (freed > 0) ? freed : 0;
}
