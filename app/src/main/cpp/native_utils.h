#pragma once

#include <string>
#include <vector>

std::string read_first_line(const std::string& path);
std::string trim(const std::string& s);
std::string get_system_property(const char* key);
long read_long_from_file(const std::string& path);
std::string escape_json(const std::string& s);
std::string to_lower(const std::string& s);
std::string lower_str(const std::string& s);
bool contains_any(const std::string& s, const std::vector<std::string>& keys);
double parse_temp_c(long raw);
std::string execute_shell_command(const char* cmd);
std::string read_file_string(const std::string& path);
int parse_first_int(const std::string& s);
