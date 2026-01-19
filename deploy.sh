#!/bin/bash
echo "Building and Installing TaskManager (Release)..."
./gradlew installRelease

if [ $? -eq 0 ]; then
    echo "Launch..."
    adb shell am start -n com.example.taskmanager/.MainActivity
else
    echo "Build or Install failed."
fi
