#!/bin/bash

# Exit immediately if any command fails
set -e

# Verify commit message parameter is provided
if [ -z "$1" ]; then
    echo "Error: Please provide a commit message."
    echo "Usage: ./FinishTask.sh \"Your commit message\""
    exit 1
fi

COMMIT_MSG="$1"

echo "=== 1. Staging changes ==="
git add .

echo "=== 2. Committing changes ==="
git commit -m "$COMMIT_MSG"

echo "=== 3. Pushing to origin main ==="
# Unset GITHUB_TOKEN to ensure local keyring/gh is used for authentication
unset GITHUB_TOKEN
git push origin main

echo "=== 4. Detecting Target Device ==="
DEVICE_SERIAL=$(adb devices -l | grep -E "3B15BN00V9700000|CPH2747" | awk '{print $1}')
if [ -z "$DEVICE_SERIAL" ]; then
    DEVICE_SERIAL=$(adb devices | grep -v "List of devices" | grep "device" | head -n 1 | awk '{print $1}')
fi

if [ -z "$DEVICE_SERIAL" ]; then
    echo "Error: No active Android device detected via ADB."
    exit 1
fi

echo "Target Device: $DEVICE_SERIAL"

echo "=== 5. Installing Release APK ==="
APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "Error: Release APK not found at $APK_PATH."
    echo "Please compile it first using: ./gradlew :app:assembleRelease"
    exit 1
fi

adb -s "$DEVICE_SERIAL" install -r "$APK_PATH"

echo "=== 6. Waiting 2 seconds for installation to settle ==="
sleep 2

echo "=== 7. Launching TaskManager App ==="
adb -s "$DEVICE_SERIAL" shell am start -n com.xmodern.taskmgmt/.MainActivity

echo "=== Task finished successfully! ==="
