# Android Task Manager

Android Task Manager is a root-required system monitor for Android. It provides a Windows Task Manager-style process list, detailed per-process information, and a Performance dashboard with live CPU/GPU/Memory/Disk/Network charts.

This project targets low-level, system-oriented visibility on rooted devices and includes a native (C++17) backend accessed via JNI and an AIDL root service.

## Features
- Live process list with CPU/RAM usage, search, and sorting
- Per-process details (threads, modules, and stats)
- Safe kill review/transaction flow
- Performance screen with live charts for CPU, GPU, Memory, Disk, and Network
- Native backend (NDK) for system metrics and root-only access

## Screenshots

<p>
  <img src="docs/images/process_list.png" alt="Process List" width="460" style="max-width: 100%; height: auto;" />
  <img src="docs/images/performance.png" alt="Performance" width="460" style="max-width: 100%; height: auto;" />
</p>

## Requirements
These versions are used by this project:
- Java: 17
- Android SDK Platform: 34 (compileSdk/targetSdk)
- Android Build-tools: 34.0.0 (install via SDK Manager)
- NDK: 26.1.10909125
- CMake: 3.22.1
- Gradle: 8.7 (wrapper)
- Android Gradle Plugin (AGP): 8.3.1
- Kotlin: 1.9.23

## Build
From repo root:

```bash
./gradlew :app:assembleRelease
```

APK output:
```
app/build/outputs/apk/release/app-release.apk
```

## Root Requirement
The app requires root for process and performance metrics (libsu RootService). Without root, the app will not function as intended.

## Project Structure
- `app/src/main/java/` - Kotlin/Compose UI and ViewModels
- `app/src/main/aidl/` - AIDL root service interface
- `app/src/main/cpp/` - Native C++17 backend (NDK)
- `scripts/` - Deploy/build helper scripts

## License
See repository for licensing details.
