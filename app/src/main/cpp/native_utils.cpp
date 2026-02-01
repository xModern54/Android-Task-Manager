#include "native_utils.h"
#include "native_common.h"

#include <fstream>
#include <sstream>
#include <sys/system_properties.h>
#include <array>
#include <memory>
#include <cstdio>
#include <cctype>

std::string read_first_line(const std::string& path) {
    std::ifstream file(path);
    if (!file.is_open()) return "";
    std::string line;
    std::getline(file, line);
    return line;
}

std::string trim(const std::string& s) {
    size_t start = s.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return "";
    size_t end = s.find_last_not_of(" \t\r\n");
    return s.substr(start, end - start + 1);
}

std::string get_system_property(const char* key) {
    char value[PROP_VALUE_MAX] = {0};
    int len = __system_property_get(key, value);
    if (len > 0) return std::string(value, len);
    return "";
}

long read_long_from_file(const std::string& path) {
    std::ifstream file(path);
    if (!file.is_open()) return -1;
    long value = -1;
    file >> value;
    return value;
}

std::string escape_json(const std::string& s) {
    std::string out;
    out.reserve(s.size());
    for (char c : s) {
        switch (c) {
            case '\\': out += "\\\\"; break;
            case '\"': out += "\\\\\""; break;
            case '\n': out += "\\\\n"; break;
            case '\r': out += "\\\\r"; break;
            case '\t': out += "\\\\t"; break;
            default: out += c; break;
        }
    }
    return out;
}

std::string to_lower(const std::string& s) {
    std::string out = s;
    for (char& c : out) {
        c = (char)std::tolower(static_cast<unsigned char>(c));
    }
    return out;
}

std::string lower_str(const std::string& s) {
    std::string out = s;
    for (char& c : out) {
        c = (char)std::tolower(static_cast<unsigned char>(c));
    }
    return out;
}

bool contains_any(const std::string& s, const std::vector<std::string>& keys) {
    for (const auto& k : keys) {
        if (s.find(k) != std::string::npos) return true;
    }
    return false;
}

double parse_temp_c(long raw) {
    if (raw <= 0) return -1.0;
    double c = (raw >= 1000) ? (raw / 1000.0) : (double)raw;
    if (c < -50.0 || c > 200.0) return -1.0;
    return c;
}

std::string execute_shell_command(const char* cmd) {
    std::array<char, 128> buffer;
    std::string result;
    std::unique_ptr<FILE, decltype(&pclose)> pipe(popen(cmd, "r"), pclose);
    if (!pipe) {
        LOGE("popen() failed!");
        return "";
    }
    while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
        result += buffer.data();
    }
    return result;
}

std::string read_file_string(const std::string& path) {
    std::ifstream file(path);
    if (!file.is_open()) return "";
    std::stringstream ss;
    ss << file.rdbuf();
    return ss.str();
}

int parse_first_int(const std::string& s) {
    std::stringstream ss(s);
    int v = 0;
    if (ss >> v) return v;
    return -1;
}
