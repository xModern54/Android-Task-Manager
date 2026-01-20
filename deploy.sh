#!/bin/bash
echo "Building and Installing TaskManager (Release)..."
./gradlew :app:assembleRelease

if [ $? -eq 0 ]; then
    echo "Launch..."
    adb install -r app/build/outputs/apk/release/app-release.apk
    adb shell am start -n com.example.taskmanager/.MainActivity
else
    echo "Build failed."
fi
