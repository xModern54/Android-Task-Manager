#include "battery_stats.h"
#include "native_utils.h"

#include <algorithm>
#include <sstream>

namespace {

long long read_ll(const std::string& path) {
    std::string text = trim(read_first_line(path));
    if (text.empty()) return -1;
    try {
        return std::stoll(text);
    } catch (...) {
        return -1;
    }
}

int read_int(const std::string& path) {
    std::string text = trim(read_first_line(path));
    if (text.empty()) return -1;
    try {
        return std::stoi(text);
    } catch (...) {
        return -1;
    }
}

std::string detect_power_source() {
    auto is_online = [](const std::string& path) -> bool {
        return read_int(path) == 1;
    };

    if (is_online("/sys/class/power_supply/wireless/online")) return "Wireless";
    if (is_online("/sys/class/power_supply/ac/online")) return "AC";
    if (is_online("/sys/class/power_supply/usb/online")) return "USB";
    return "Battery";
}

} // namespace

BatteryInfo read_battery_info() {
    const std::string base = "/sys/class/power_supply/battery/";
    BatteryInfo info;

    info.type = trim(read_first_line(base + "type"));
    info.status = trim(read_first_line(base + "status"));
    info.powerSource = detect_power_source();
    info.tempDeciC = read_int(base + "temp");
    info.voltageNowUv = std::max(0LL, read_ll(base + "voltage_now"));
    info.currentNowUa = read_ll(base + "current_now");
    info.powerNowUw = std::max(0LL, read_ll(base + "power_now"));
    info.chargeType = trim(read_first_line(base + "charge_type"));
    info.cycleCount = read_int(base + "cycle_count");
    info.technology = trim(read_first_line(base + "technology"));

    long long ttfNow = read_ll(base + "time_to_full_now");
    long long ttfAvg = read_ll(base + "time_to_full_avg");
    long long tteAvg = read_ll(base + "time_to_empty_avg");
    info.timeToFullSec = (ttfNow >= 0) ? ttfNow : ttfAvg;
    info.timeToEmptySec = tteAvg;

    long long now = read_ll(base + "charge_now");
    long long full = read_ll(base + "charge_full");
    if (full <= 0) full = read_ll(base + "charge_full_design");
    if (now > 0 || full > 0) {
        info.chargeNow = std::max(0LL, now);
        info.chargeFull = std::max(0LL, full);
        info.chargeUnit = "uAh";
    } else {
        now = read_ll(base + "energy_now");
        full = read_ll(base + "energy_full");
        if (full <= 0) full = read_ll(base + "energy_full_design");
        if (now > 0 || full > 0) {
            info.chargeNow = std::max(0LL, now);
            info.chargeFull = std::max(0LL, full);
            info.chargeUnit = "uWh";
        }
    }

    info.levelPercent = read_int(base + "capacity");
    if (info.levelPercent < 0 && info.chargeNow > 0 && info.chargeFull > 0) {
        double pct = (double)info.chargeNow * 100.0 / (double)info.chargeFull;
        info.levelPercent = (int)(pct + 0.5);
    }
    if (info.levelPercent >= 0) {
        info.levelPercent = std::clamp(info.levelPercent, 0, 100);
    }

    if (info.type.empty()) info.type = "Battery";
    if (info.powerSource.empty()) info.powerSource = "Battery";
    if (info.chargeType.empty()) info.chargeType = "—";
    if (info.technology.empty()) info.technology = "—";
    if (info.levelPercent < 0 && info.chargeNow <= 0 && info.chargeFull <= 0) {
        info.error = "battery_unavailable";
    }
    return info;
}

std::string get_battery_snapshot_json() {
    BatteryInfo info = read_battery_info();
    std::stringstream ss;
    ss << "{";
    ss << "\"type\":\"" << escape_json(info.type) << "\",";
    ss << "\"status\":\"" << escape_json(info.status) << "\",";
    ss << "\"levelPercent\":" << info.levelPercent << ",";
    ss << "\"chargeNow\":" << info.chargeNow << ",";
    ss << "\"chargeFull\":" << info.chargeFull << ",";
    ss << "\"chargeUnit\":\"" << escape_json(info.chargeUnit) << "\",";
    ss << "\"powerSource\":\"" << escape_json(info.powerSource) << "\",";
    ss << "\"tempDeciC\":" << info.tempDeciC << ",";
    ss << "\"voltageNowUv\":" << info.voltageNowUv << ",";
    ss << "\"currentNowUa\":" << info.currentNowUa << ",";
    ss << "\"powerNowUw\":" << info.powerNowUw << ",";
    ss << "\"chargeType\":\"" << escape_json(info.chargeType) << "\",";
    ss << "\"cycleCount\":" << info.cycleCount << ",";
    ss << "\"timeToFullSec\":" << info.timeToFullSec << ",";
    ss << "\"timeToEmptySec\":" << info.timeToEmptySec << ",";
    ss << "\"technology\":\"" << escape_json(info.technology) << "\",";
    ss << "\"error\":\"" << escape_json(info.error) << "\"";
    ss << "}";
    return ss.str();
}
