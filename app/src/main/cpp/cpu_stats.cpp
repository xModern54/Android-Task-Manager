#include "cpu_stats.h"
#include "native_utils.h"
#include "system_stats.h"

#include <dirent.h>
#include <fstream>
#include <sstream>
#include <set>
#include <unordered_set>
#include <vector>
#include <algorithm>
#include <unistd.h>
#include <limits>

int count_logical_cores_from_online() {
    std::string online = trim(read_first_line("/sys/devices/system/cpu/online"));
    if (online.empty()) return -1;
    int count = 0;
    std::stringstream ss(online);
    std::string token;
    while (std::getline(ss, token, ',')) {
        size_t dash = token.find('-');
        if (dash == std::string::npos) {
            count += 1;
        } else {
            int start = std::stoi(token.substr(0, dash));
            int end = std::stoi(token.substr(dash + 1));
            if (end >= start) count += (end - start + 1);
        }
    }
    return count;
}

int count_logical_cores_by_scan() {
    DIR* dir = opendir("/sys/devices/system/cpu");
    if (!dir) return -1;
    int count = 0;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type == DT_DIR) {
            std::string name = entry->d_name;
            if (name.rfind("cpu", 0) == 0) {
                std::string idx = name.substr(3);
                if (!idx.empty() && idx.find_first_not_of("0123456789") == std::string::npos) {
                    count++;
                }
            }
        }
    }
    closedir(dir);
    return count;
}

int count_physical_cores() {
    DIR* dir = opendir("/sys/devices/system/cpu");
    if (!dir) return -1;
    std::set<std::string> cores;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type == DT_DIR) {
            std::string name = entry->d_name;
            if (name.rfind("cpu", 0) == 0) {
                std::string idx = name.substr(3);
                if (idx.empty() || idx.find_first_not_of("0123456789") != std::string::npos) continue;
                std::string cpuPath = "/sys/devices/system/cpu/" + name + "/topology/";
                std::string packageId = trim(read_first_line(cpuPath + "physical_package_id"));
                std::string coreId = trim(read_first_line(cpuPath + "core_id"));
                if (packageId.empty()) packageId = "0";
                if (coreId.empty()) coreId = idx;
                cores.insert(packageId + ":" + coreId);
            }
        }
    }
    closedir(dir);
    return (int)cores.size();
}

long get_max_freq_khz() {
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

int count_processes() {
    DIR* procDir = opendir("/proc");
    if (!procDir) return 0;
    int count = 0;
    struct dirent* entry;
    while ((entry = readdir(procDir)) != nullptr) {
        if (entry->d_type == DT_DIR) {
            std::string name = entry->d_name;
            if (!name.empty() && name.find_first_not_of("0123456789") == std::string::npos) {
                count++;
            }
        }
    }
    closedir(procDir);
    return count;
}

long count_threads() {
    DIR* procDir = opendir("/proc");
    if (!procDir) return 0;
    long total = 0;
    struct dirent* entry;
    while ((entry = readdir(procDir)) != nullptr) {
        if (entry->d_type == DT_DIR) {
            std::string name = entry->d_name;
            if (name.empty() || name.find_first_not_of("0123456789") != std::string::npos) continue;
            std::string taskPath = "/proc/" + name + "/task";
            DIR* taskDir = opendir(taskPath.c_str());
            if (!taskDir) continue;
            struct dirent* t;
            while ((t = readdir(taskDir)) != nullptr) {
                if (t->d_type == DT_DIR) {
                    std::string tid = t->d_name;
                    if (!tid.empty() && tid.find_first_not_of("0123456789") == std::string::npos) {
                        total++;
                    }
                }
            }
            closedir(taskDir);
        }
    }
    closedir(procDir);
    return total;
}

long get_handles_count() {
    std::ifstream file("/proc/sys/fs/file-nr");
    if (!file.is_open()) return 0;
    long allocated = 0;
    file >> allocated;
    return allocated;
}

long get_uptime_seconds() {
    std::ifstream file("/proc/uptime");
    if (!file.is_open()) return 0;
    double uptime = 0;
    file >> uptime;
    return (long)uptime;
}

int count_cpus_from_list(const std::string& list) {
    int count = 0;
    std::stringstream ss(list);
    std::string token;
    while (ss >> token) {
        size_t dash = token.find('-');
        if (dash == std::string::npos) {
            count += 1;
        } else {
            int start = std::stoi(token.substr(0, dash));
            int end = std::stoi(token.substr(dash + 1));
            if (end >= start) count += (end - start + 1);
        }
    }
    return count;
}

int read_smt_active() {
    long v = read_long_from_file("/sys/devices/system/cpu/smt/active");
    if (v < 0) return 0;
    return (int)v;
}

struct CpuCluster {
    int count;
    long maxFreq;
    std::string key;
};

std::vector<CpuCluster> get_clusters_from_policies() {
    std::vector<CpuCluster> clusters;
    DIR* dir = opendir("/sys/devices/system/cpu/cpufreq");
    if (!dir) return clusters;

    std::set<std::string> seen;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type == DT_DIR) {
            std::string name = entry->d_name;
            if (name.rfind("policy", 0) == 0) {
                std::string base = "/sys/devices/system/cpu/cpufreq/" + name + "/";
                std::string related = trim(read_first_line(base + "related_cpus"));
                if (related.empty()) continue;
                if (seen.count(related)) continue;
                seen.insert(related);

                int count = count_cpus_from_list(related);
                long maxFreq = read_long_from_file(base + "cpuinfo_max_freq");
                if (maxFreq <= 0) maxFreq = read_long_from_file(base + "scaling_max_freq");
                clusters.push_back({count, maxFreq, related});
            }
        }
    }
    closedir(dir);
    return clusters;
}

std::string build_core_layout(const std::vector<CpuCluster>& clusters) {
    if (clusters.empty()) return "";
    std::stringstream ss;
    for (size_t i = 0; i < clusters.size(); ++i) {
        ss << clusters[i].count;
        if (i + 1 < clusters.size()) ss << "+";
    }
    return ss.str();
}

std::string build_core_layout_labeled(const std::vector<CpuCluster>& clusters) {
    if (clusters.empty()) return "";
    std::stringstream ss;
    for (size_t i = 0; i < clusters.size(); ++i) {
        std::string label = "E";
        if (clusters.size() == 1) {
            label = "P";
        } else if (clusters.size() == 2) {
            label = (i == 0) ? "P" : "E";
        } else if (clusters.size() == 3) {
            label = (i < 2) ? "P" : "E";
        } else {
            label = (i < 2) ? "P" : "E";
        }
        ss << clusters[i].count << label;
        if (i + 1 < clusters.size()) ss << " + ";
    }
    return ss.str();
}

std::string get_cpu_name_best_effort() {
    std::string soc = trim(get_system_property("ro.soc.model"));
    if (!soc.empty()) return soc;
    std::string machine = trim(read_first_line("/sys/devices/soc0/machine"));
    if (!machine.empty()) return machine;

    std::ifstream cpuinfo("/proc/cpuinfo");
    if (cpuinfo.is_open()) {
        std::string line;
        while (std::getline(cpuinfo, line)) {
            if (line.find("Hardware") != std::string::npos ||
                line.find("model name") != std::string::npos ||
                line.find("Processor") != std::string::npos) {
                size_t colon = line.find(':');
                if (colon != std::string::npos) {
                    std::string val = trim(line.substr(colon + 1));
                    if (!val.empty()) return val;
                }
            }
        }
    }
    return "Unknown";
}

struct TempCandidate {
    std::string type;
    long raw;
    double c;
};

double get_cpu_temp_c(std::string& sourceOut, long& rawOut, std::string& candidatesOut, std::string& unitOut) {
    DIR* dir = opendir("/sys/class/thermal");
    if (!dir) return -1.0;
    struct dirent* entry;
    std::vector<std::string> include = {"cpu", "cpullc", "qmx", "apss", "tsens", "soc", "cluster", "big", "little", "gold", "prime", "qcom", "msm", "sdm"};
    std::vector<std::string> exclude = {"battery", "skin", "usb", "charger", "pmic"};
    std::vector<TempCandidate> bucketCpu;
    std::vector<TempCandidate> bucketApss;
    std::vector<TempCandidate> bucketSoc;
    std::vector<TempCandidate> bucketOther;

    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        if (name.rfind("thermal_zone", 0) != 0) continue;
        std::string base = "/sys/class/thermal/" + name + "/";
        std::string type = trim(read_first_line(base + "type"));
        if (type.empty()) continue;
        std::string typeLower = to_lower(type);
        if (contains_any(typeLower, exclude)) continue;
        if (!contains_any(typeLower, include)) continue;
        if (typeLower.find("hw-trip") != std::string::npos) continue;
        long raw = read_long_from_file(base + "temp");
        double c = parse_temp_c(raw);
        if (c < 0) continue;
        TempCandidate cand{type, raw, c};
        if (typeLower.rfind("cpu", 0) == 0 || typeLower.find("cpu") != std::string::npos ||
            typeLower.find("cpullc") != std::string::npos || typeLower.find("qmx") != std::string::npos) {
            bucketCpu.push_back(cand);
        } else if (typeLower.find("apss") != std::string::npos || typeLower.find("tsens") != std::string::npos) {
            bucketApss.push_back(cand);
        } else if (typeLower.find("soc") != std::string::npos) {
            bucketSoc.push_back(cand);
        } else {
            bucketOther.push_back(cand);
        }
    }
    closedir(dir);

    auto pick_from_bucket = [&](const std::vector<TempCandidate>& bucket, const std::string& label) -> double {
        if (bucket.empty()) return -1.0;
        std::vector<TempCandidate> sorted = bucket;
        std::sort(sorted.begin(), sorted.end(), [](const TempCandidate& a, const TempCandidate& b) {
            return a.c < b.c;
        });
        const TempCandidate& chosen = sorted[sorted.size() / 2];
        rawOut = chosen.raw;
        sourceOut = "thermal_zone:" + label;
        unitOut = (rawOut >= 1000) ? "mC" : "C";

        std::stringstream ss;
        for (size_t i = 0; i < sorted.size() && i < 3; ++i) {
            ss << sorted[i].type << "=" << sorted[i].raw;
            if (i + 1 < sorted.size() && i < 2) ss << ",";
        }
        candidatesOut = ss.str();
        return chosen.c;
    };

    double chosen = -1.0;
    if (bucketCpu.size() >= 2) {
        chosen = pick_from_bucket(bucketCpu, "cpu(median)");
    } else if (bucketCpu.size() == 1) {
        chosen = pick_from_bucket(bucketCpu, "cpu(single)");
    } else if (!bucketApss.empty()) {
        chosen = pick_from_bucket(bucketApss, "apss(median)");
    } else if (!bucketSoc.empty()) {
        chosen = pick_from_bucket(bucketSoc, "soc(median)");
    } else if (!bucketOther.empty()) {
        chosen = pick_from_bucket(bucketOther, "other(median)");
    }

    if (chosen >= 0) return chosen;

    double best = -1.0;
    long bestRaw = 0;
    std::string bestSource;
    std::string bestUnit;
    std::string bestCandidates;
    DIR* hdir = opendir("/sys/class/hwmon");
    if (!hdir) return -1.0;
    struct dirent* hentry;
    while ((hentry = readdir(hdir)) != nullptr) {
        std::string hname = hentry->d_name;
        if (hname.rfind("hwmon", 0) != 0) continue;
        std::string base = "/sys/class/hwmon/" + hname + "/";
        std::string name = trim(read_first_line(base + "name"));
        std::string nameLower = to_lower(name);
        bool excluded = contains_any(nameLower, exclude);

        DIR* fdir = opendir(base.c_str());
        if (!fdir) continue;
        struct dirent* fentry;
        while ((fentry = readdir(fdir)) != nullptr) {
            std::string fname = fentry->d_name;
            if (fname.rfind("temp", 0) != 0) continue;
            if (fname.find("_input") == std::string::npos) continue;
            std::string path = base + fname;
            long raw = read_long_from_file(path);
            double c = parse_temp_c(raw);
            if (c < 0) continue;
            bool preferred = !excluded && contains_any(nameLower, {"tsens", "cpu", "soc", "apss", "qcom", "msm", "sdm"});
            if (preferred || best < 0 || (c > best)) {
                best = c;
                bestRaw = raw;
                bestSource = "hwmon:" + (name.empty() ? (hname + "/" + fname) : (name + "/" + fname));
                bestUnit = (raw >= 1000) ? "mC" : "C";
                bestCandidates = fname + "=" + std::to_string(raw);
            }
        }
        closedir(fdir);
    }
    closedir(hdir);
    if (best >= 0) {
        rawOut = bestRaw;
        sourceOut = bestSource;
        unitOut = bestUnit;
        candidatesOut = bestCandidates;
        return best;
    }
    return -1.0;
}

std::string get_cpu_snapshot_json() {
    std::string cpuName = get_cpu_name_best_effort();

    int logical = count_logical_cores_from_online();
    if (logical <= 0) {
        logical = count_logical_cores_by_scan();
    }
    if (logical <= 0) {
        logical = (int)sysconf(_SC_NPROCESSORS_CONF);
    }

    int physical = logical;
    if (read_smt_active() == 1) {
        int topo = count_physical_cores();
        if (topo > 0) physical = topo;
    }

    double usage = getGlobalCpuUsage();
    long maxFreq = get_max_freq_khz();
    int processes = count_processes();
    long threads = count_threads();
    long handles = get_handles_count();
    long uptime = get_uptime_seconds();
    std::string tempSource;
    std::string tempCandidates;
    std::string tempUnit;
    long tempRaw = 0;
    double tempC = get_cpu_temp_c(tempSource, tempRaw, tempCandidates, tempUnit);

    std::vector<CpuCluster> clusters = get_clusters_from_policies();
    std::sort(clusters.begin(), clusters.end(), [](const CpuCluster& a, const CpuCluster& b) {
        return a.maxFreq > b.maxFreq;
    });
    std::string coreLayout = build_core_layout(clusters);
    std::string coreLayoutLabeled = build_core_layout_labeled(clusters);

    std::stringstream ss;
    ss << "{";
    ss << "\"cpuName\":\"" << escape_json(cpuName) << "\",";
    ss << "\"coresPhysical\":" << physical << ",";
    ss << "\"coresLogical\":" << logical << ",";
    ss << "\"usagePercent\":" << usage << ",";
    ss << "\"maxFreqKHz\":" << maxFreq << ",";
    ss << "\"processes\":" << processes << ",";
    ss << "\"threads\":" << threads << ",";
    ss << "\"handles\":" << handles << ",";
    ss << "\"uptimeSeconds\":" << uptime << ",";
    ss << "\"coreLayout\":\"" << escape_json(coreLayout) << "\",";
    ss << "\"coreLayoutLabeled\":\"" << escape_json(coreLayoutLabeled) << "\",";
    ss << "\"cpuTempC\":" << tempC << ",";
    ss << "\"cpuTempSource\":\"" << escape_json(tempSource) << "\",";
    ss << "\"cpuTempRaw\":" << tempRaw << ",";
    ss << "\"cpuTempCandidates\":\"" << escape_json(tempCandidates) << "\",";
    ss << "\"cpuTempUnitAssumption\":\"" << escape_json(tempUnit) << "\"";
    ss << "}";
    return ss.str();
}
