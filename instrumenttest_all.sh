#!/bin/bash

# Resolve SDK directory from local.properties or environments
SDK_DIR=""
if [ -f local.properties ]; then
    SDK_DIR=$(grep 'sdk.dir' local.properties | cut -d'=' -f2 | tr -d '\r' | xargs)
fi

if [ -z "$SDK_DIR" ]; then
    if [ -n "$ANDROID_HOME" ]; then
        SDK_DIR="$ANDROID_HOME"
    else
        SDK_DIR="$HOME/Library/Android/sdk"
    fi
fi

ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"

# Fallback to path if not exists
if [ ! -f "$ADB" ]; then
    ADB="adb"
fi
if [ ! -f "$EMULATOR" ]; then
    EMULATOR="emulator"
fi

# Check if there is an active device/emulator already connected
ACTIVE_DEVICES=$($ADB devices 2>/dev/null | grep -v "List of devices attached" | grep -v "^$" | grep -v "offline" | grep -v "unauthorized" | wc -l | xargs)

EMU_PID=""
EMU_SERIAL="emulator-5554"

if [ "$ACTIVE_DEVICES" -gt 0 ]; then
    echo "Found active Android device(s) / emulator(s). Running tests directly..."
else
    echo "No active Android device found. Starting emulator..."

    # List AVDs and pick the first available one
    AVD_LIST=$($EMULATOR -list-avds 2>/dev/null)
    if [ -z "$AVD_LIST" ]; then
        echo "Error: No Android Emulators (AVD) found. Please create one in Android Studio."
        exit 1
    fi
    AVD_NAME=$(echo "$AVD_LIST" | head -n 1)
    echo "Starting emulator AVD: $AVD_NAME on port 5554"

    # Start emulator in background
    $EMULATOR -avd "$AVD_NAME" -no-audio -no-boot-anim -port 5554 >/dev/null 2>&1 &
    EMU_PID=$!

    # Wait for emulator to show up in adb and be in 'device' state (not offline)
    echo "Waiting for emulator $EMU_SERIAL to show up online in adb..."
    COUNT=0
    while ! $ADB devices 2>/dev/null | grep "$EMU_SERIAL" | grep -q -w "device"; do
        sleep 2
        COUNT=$((COUNT + 1))
        if [ $COUNT -gt 30 ]; then
            echo "Timeout waiting for emulator to show up online in adb."
            kill $EMU_PID 2>/dev/null
            exit 1
        fi
    done

    # Wait for emulator to boot
    echo "Waiting for emulator to boot..."
    COUNT=0
    while [ "$($ADB -s "$EMU_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | xargs)" != "1" ]; do
        sleep 2
        COUNT=$((COUNT + 1))
        if [ $COUNT -gt 60 ]; then
            echo "Timeout waiting for emulator to boot."
            $ADB -s "$EMU_SERIAL" emu kill 2>/dev/null
            kill $EMU_PID 2>/dev/null
            exit 1
        fi
    done
    echo "Emulator is booted and ready!"
fi

# Run the instrumentation tests
echo "Running Android Instrumentation Tests..."
./gradlew :app:connectedAndroidTest
TEST_RESULT=$?

# Cleanup emulator if we started it
if [ -n "$EMU_PID" ]; then
    echo "Stopping emulator..."
    $ADB -s "$EMU_SERIAL" emu kill 2>/dev/null
    kill $EMU_PID 2>/dev/null
    wait $EMU_PID 2>/dev/null
fi

exit $TEST_RESULT
