# TaskManager Agent Quickstart

## Project Summary
TaskManager is an Android root-required system monitor app (Windows Task Manager style) for rooted devices.
It provides:
- Live process list with CPU/RAM usage
- Extended process details (overview, stats, modules, threads)
- Kill/force-stop actions
- Performance dashboard (CPU, GPU, Memory, Disk, Network, Battery)

Tech stack:
- Kotlin + Jetpack Compose UI
- AIDL root service (`libsu` RootService)
- Native backend in C++17 via JNI (NDK)

## Repository Layout
- `app/src/main/java/com/xmodern/taskmgmt/` - app UI, ViewModels, root service bridge
- `app/src/main/aidl/com/xmodern/taskmgmt/IRootService.aidl` - IPC contract
- `app/src/main/cpp/` - native process/performance collectors
- `app/src/main/java/com/xmodern/taskmgmt/ui/screens/processdetail/` - process detail UI
- `app/src/main/java/com/xmodern/taskmgmt/ui/screens/processlist/ProcessListViewModel.kt` - process list + deep snapshot parsing

## Current Status To Resume Fast
- `main` currently matches release tag `v1.8-Beta` in commit history.
- There is active uncommitted WIP around:
  - Battery metrics pipeline (AIDL -> JNI -> native -> Performance screen)
  - Root service/connection manager cleanup and formatting
  - Process detail screen improvements (tabs currently include `Main`, `Stats`, `Modules`, `Threads`)

## Build Environment (Mandatory)
Use JDK 17 for Gradle/Kotlin builds.

Required Java home:
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`

Recommended shell setup before build:
- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`
- `export PATH="$JAVA_HOME/bin:$PATH"`

Reason:
- Other Java versions (for example OpenJDK 25.x) can break Gradle/Kotlin script parsing in this repo.

## Device Target (Mandatory)
Primary Android device:
- Serial: `3B15BN00V9700000`
- Model: `CPH2747`

Always verify device before install:
- `adb devices -l`

## Required Workflow After Every User Task (Mandatory)
After completing **each** user request that changes project behavior or files, always run:

1. Compile and build the release APK:
- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`
- `export PATH="$JAVA_HOME/bin:$PATH"`
- `./gradlew :app:assembleRelease`

2. Run the finish script (which automates staging, committing, pushing, installing, and launching the application):
- `./FinishTask.sh "your descriptive commit message"`

Notes:
- The script automatically detects the active device serial for `CPH2747` / `3B15BN00V9700000` (including wireless prefixes).
- Report the build and script execution results explicitly in your final response.
- If the build or script fails, resolve the issue immediately, and repeat the workflow until successful.

## Practical Guardrails
- Do not revert unrelated local changes.
- Keep changes minimal and scoped.
- Prefer root-safe, read-first diagnostics unless user explicitly asks for destructive operations.
- For process detail enhancements, prefer `/proc/<pid>` data sources with low overhead first (`status`, `stat`, `io`, `fd`, `schedstat`).
