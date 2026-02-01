#pragma once

#include <string>

unsigned long long get_total_system_ticks();
double getGlobalCpuUsage();

struct RamInfo {
    long total;
    long used;
};

RamInfo getGlobalRamUsage();
long get_mem_available();
