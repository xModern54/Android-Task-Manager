#include <jni.h>
#include <string>
#include <sstream>
#include <signal.h>
#include <iomanip>
#include <unordered_map>
#include <vector>

#include "process_scan.h"
#include "process_detail.h"
#include "safe_kill.h"
#include "system_stats.h"
#include "cpu_stats.h"
#include "gpu_stats.h"
#include "memory_stats.h"
#include "disk_stats.h"
#include "net_stats.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getProcessList(
        JNIEnv* env,
        jobject /* this */) {
    std::string result = build_process_list();
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getProcessExtendedInfo(
        JNIEnv* env,
        jobject /* this */,
        jint pid) {

    std::stringstream ss;
    std::string pid_str = std::to_string(pid);

    std::string name = getProcessName(pid_str);
    std::string ppid = get_status_field(pid_str, "PPid:");
    std::string uid_full = get_status_field(pid_str, "Uid:");
    std::string uid = uid_full.substr(0, uid_full.find('\t'));
    if (uid.empty()) uid = uid_full.substr(0, uid_full.find(' '));

    std::string state = get_status_field(pid_str, "State:");
    std::string threads = get_status_field(pid_str, "Threads:");

    std::string priority = "0", nice = "0";
    get_sched_info(pid_str, priority, nice);

    long elapsed_sec = get_process_elapsed_time(pid_str);
    long h = elapsed_sec / 3600;
    long m = (elapsed_sec % 3600) / 60;
    long s = elapsed_sec % 60;
    std::stringstream time_ss;
    time_ss << std::setfill('0') << std::setw(2) << h << ":"
            << std::setfill('0') << std::setw(2) << m << ":"
            << std::setfill('0') << std::setw(2) << s;

    std::string oom = get_oom_score(pid_str);
    std::string path = get_exe_path(pid_str);

    ss << "Name=" << name << "|"
       << "PID=" << pid << "|"
       << "PPID=" << ppid << "|"
       << "User=" << uid << "|"
       << "State=" << state << "|"
       << "Threads=" << threads << "|"
       << "Nice=" << nice << "|"
       << "Priority=" << priority << "|"
       << "OomScore=" << oom << "|"
       << "ElapsedTime=" << time_ss.str() << "|"
       << "ExePath=" << path;

    return env->NewStringUTF(ss.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getProcessDeepSnapshot(
        JNIEnv* env,
        jobject /* this */,
        jint pid) {

    std::stringstream ss;
    std::string pid_str = std::to_string(pid);

    // --- OVERVIEW SECTION ---
    std::string name = getProcessName(pid_str);
    std::string ppid = get_status_field(pid_str, "PPid:");
    std::string uid_full = get_status_field(pid_str, "Uid:");
    std::string uid = uid_full.substr(0, uid_full.find('\t'));
    if (uid.empty()) uid = uid_full.substr(0, uid_full.find(' '));

    std::string state = get_status_field(pid_str, "State:");
    std::string threads_count = get_status_field(pid_str, "Threads:");
    std::string oom = get_oom_score(pid_str);
    std::string path = get_exe_path(pid_str);

    std::string priority = "0", nice = "0";
    get_sched_info(pid_str, priority, nice);

    long elapsed_sec = get_process_elapsed_time(pid_str);
    long h = elapsed_sec / 3600;
    long m = (elapsed_sec % 3600) / 60;
    long s = elapsed_sec % 60;
    std::stringstream time_ss;
    time_ss << std::setfill('0') << std::setw(2) << h << ":"
            << std::setfill('0') << std::setw(2) << m << ":"
            << std::setfill('0') << std::setw(2) << s;

    ss << "OVERVIEW:Name=" << name << "|"
       << "PID=" << pid << "|"
       << "PPID=" << ppid << "|"
       << "User=" << uid << "|"
       << "State=" << state << "|"
       << "Nice=" << nice << "|"
       << "Priority=" << priority << "|"
       << "OomScore=" << oom << "|"
       << "ElapsedTime=" << time_ss.str() << "|"
       << "ExePath=" << path << "\n";

    // --- STATS SECTION ---
    std::unordered_map<std::string, std::string> stats;
    get_detailed_status(pid_str, stats);
    get_page_faults(pid_str, stats);

    ss << "STATS:VoluntaryCtxSwitches=" << stats["voluntary_ctxt_switches"] << "|"
       << "NonVoluntaryCtxSwitches=" << stats["nonvoluntary_ctxt_switches"] << "|"
       << "MinorPageFaults=" << stats["minflt"] << "|"
       << "MajorPageFaults=" << stats["majflt"] << "\n";

    // --- MODULES SECTION ---
    std::vector<std::string> modules = get_modules_list(pid_str);
    ss << "MODULES:";
    for (size_t i = 0; i < modules.size(); ++i) {
        ss << modules[i];
        if (i < modules.size() - 1) ss << ";";
    }
    ss << "\n";

    // --- THREADS SECTION ---
    std::vector<std::string> threads = get_threads_list(pid_str);
    ss << "THREADS:";
    for (size_t i = 0; i < threads.size(); ++i) {
        ss << threads[i];
        if (i < threads.size() - 1) ss << "|";
    }

    return env->NewStringUTF(ss.str().c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_taskmanager_service_NativeBridge_sendSignal(
        JNIEnv* env,
        jobject /* this */,
        jint pid,
        jint signal) {
    int res = kill((pid_t)pid, (int)signal);
    return (res == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getKillCandidates(
        JNIEnv* env,
        jobject /* this */) {
    std::string res = get_kill_candidates();
    return env->NewStringUTF(res.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_taskmanager_service_NativeBridge_executeKillTransaction(
        JNIEnv* env,
        jobject /* this */,
        jstring packagesStr) {

    const char* packagesChars = env->GetStringUTFChars(packagesStr, 0);
    std::string packages(packagesChars);
    env->ReleaseStringUTFChars(packagesStr, packagesChars);

    long freed = execute_kill_transaction(packages);
    return (jlong)freed;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_taskmanager_service_NativeBridge_getFreeRam(
        JNIEnv* env,
        jobject /* this */) {
    RamInfo ram = getGlobalRamUsage();
    return (jlong)(ram.total - ram.used);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_hello(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF("Hello from C++ Native Backend!");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getCpuSnapshotJson(
        JNIEnv* env,
        jobject /* this */) {
    std::string json = get_cpu_snapshot_json();
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getVulkanInfoJson(
        JNIEnv* env,
        jobject /* this */) {
    std::string json = get_vulkan_info_json();
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getGpuSnapshotJson(
        JNIEnv* env,
        jobject /* this */) {
    std::string json = get_gpu_snapshot_json();
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getMemorySnapshotJson(
        JNIEnv* env,
        jobject /* this */) {
    std::string json = get_memory_snapshot_json();
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getDiskSnapshotJson(
        JNIEnv* env,
        jobject /* this */) {
    std::string json = get_disk_snapshot_json();
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_taskmanager_service_NativeBridge_getNetSnapshotJson(
        JNIEnv* env,
        jobject /* this */) {
    std::string json = get_net_snapshot_json();
    return env->NewStringUTF(json.c_str());
}
