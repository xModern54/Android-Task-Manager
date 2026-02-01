#include "net_stats.h"
#include "native_utils.h"

#include <sstream>
#include <string>
#include <vector>
#include <ctime>

namespace {

long long now_ms() {
    struct timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000LL + ts.tv_nsec / 1000000LL;
}

struct NetCounters {
    std::string iface;
    long long rxBytes = 0;
    long long txBytes = 0;
    long long rxPackets = 0;
    long long txPackets = 0;
};

bool read_iface_counters(const std::string& iface, NetCounters& out) {
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
        out.rxPackets = rxPackets;
        out.txPackets = txPackets;
        return true;
    }
    return false;
}

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
        NetCounters c;
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

} // namespace

std::string get_net_snapshot_json() {
    std::string error;
    std::string iface = pick_active_iface();
    NetCounters counters;
    if (!iface.empty()) {
        if (!read_iface_counters(iface, counters)) {
            error = "read_failed";
        }
    } else {
        error = "no_iface";
    }

    long long timestampMs = now_ms();

    std::stringstream ss;
    ss << "{";
    ss << "\"iface\":\"" << escape_json(counters.iface) << "\",";
    ss << "\"rxBytes\":" << counters.rxBytes << ",";
    ss << "\"txBytes\":" << counters.txBytes << ",";
    ss << "\"rxPackets\":" << counters.rxPackets << ",";
    ss << "\"txPackets\":" << counters.txPackets << ",";
    ss << "\"timestampMs\":" << timestampMs << ",";
    ss << "\"error\":\"" << escape_json(error) << "\"";
    ss << "}";
    return ss.str();
}
