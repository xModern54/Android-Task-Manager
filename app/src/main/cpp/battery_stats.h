#pragma once

#include <string>

struct BatteryInfo {
    std::string type;
    int levelPercent = -1;
    long long chargeNow = 0;
    long long chargeFull = 0;
    std::string chargeUnit;
    std::string status;
    std::string powerSource;
    int tempDeciC = -1;
    long long voltageNowUv = 0;
    long long currentNowUa = 0;
    long long powerNowUw = 0;
    std::string chargeType;
    int cycleCount = -1;
    long long timeToFullSec = -1;
    long long timeToEmptySec = -1;
    std::string technology;
    std::string error;
};

BatteryInfo read_battery_info();
std::string get_battery_snapshot_json();
