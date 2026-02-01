cmake_minimum_required(VERSION 3.22.1)

project("system_manager")

add_library(
        HardwareAccess
        SHARED
        native-lib.cpp
        native_utils.cpp
        system_stats.cpp
        process_detail.cpp
        process_scan.cpp
        safe_kill.cpp
        cpu_stats.cpp
        gpu_stats.cpp
        memory_stats.cpp
        net_stats.cpp
        disk_stats.cpp)

find_library(
        log-lib
        log)

find_library(
        dl-lib
        dl)

target_link_libraries(
        HardwareAccess
        ${log-lib}
        ${dl-lib})
