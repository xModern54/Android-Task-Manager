use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use serde::Serialize;
use std::fs;
use std::path::Path;

#[derive(Serialize)]
struct ProcessInfo {
    pid: i32,
    name: String,
    ram_usage: i64,
}

fn get_process_name(pid: &str) -> String {
    let path = format!("/proc/{}/cmdline", pid);
    if let Ok(content) = fs::read_to_string(&path) {
        if !content.is_empty() {
            // cmdline separates args with null bytes. We want the first arg (package/exe name).
            // Sometimes it's "com.pkg.name\0" or "./executable\0-arg"
            let name = content.split('\0').next().unwrap_or("").trim();
            if !name.is_empty() {
                return name.to_string();
            }
        }
    }

    // Fallback to comm (short name, common for kernel threads)
    let comm_path = format!("/proc/{}/comm", pid);
    if let Ok(content) = fs::read_to_string(&comm_path) {
        let name = content.trim();
        if !name.is_empty() {
            return name.to_string();
        }
    }

    "Unknown".to_string()
}

#[no_mangle]
pub extern "system" fn Java_com_example_taskmanager_service_NativeBridge_getProcessListJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let mut processes = Vec::new();

    if let Ok(entries) = fs::read_dir("/proc") {
        for entry in entries.flatten() {
            if let Ok(name) = entry.file_name().into_string() {
                // Check if the directory name is a PID (numeric)
                if let Ok(pid) = name.parse::<i32>() {
                    let process_name = get_process_name(&name);
                    
                    processes.push(ProcessInfo {
                        pid,
                        name: process_name,
                        ram_usage: 0, // Placeholder
                    });
                }
            }
        }
    }

    // Serialize to JSON
    let json_output = serde_json::to_string(&processes).unwrap_or_else(|_| "[]".to_string());

    env.new_string(json_output)
        .expect("Couldn't create java string!")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_taskmanager_service_NativeBridge_hello(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = "Hello from Rust Root Backend!";
    env.new_string(output)
        .expect("Couldn't create java string!")
        .into_raw()
}