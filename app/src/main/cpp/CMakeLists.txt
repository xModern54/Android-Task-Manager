cmake_minimum_required(VERSION 3.22.1)

project("system_manager")

add_library(
        system_manager
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        system_manager
        ${log-lib})
