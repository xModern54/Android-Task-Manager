#pragma once

#include <string>
#include <vector>
#include <unordered_map>

std::string getProcessName(const std::string& pid);
long getProcessRamBytes(const std::string& pid, long pageSize);
std::string get_status_field(const std::string& pid, const std::string& field);
std::string get_exe_path(const std::string& pid);
std::string get_oom_score(const std::string& pid);
long get_system_uptime();
long get_process_elapsed_time(const std::string& pid);
void get_sched_info(const std::string& pid, std::string& priority, std::string& nice);

std::vector<std::string> get_modules_list(const std::string& pid);
std::vector<std::string> get_threads_list(const std::string& pid);
void get_detailed_status(const std::string& pid, std::unordered_map<std::string, std::string>& out);
void get_page_faults(const std::string& pid, std::unordered_map<std::string, std::string>& out);
