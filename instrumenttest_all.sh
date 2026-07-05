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
        if [ $COUNT -gt 60 ]; then
            echo "Timeout waiting for emulator to show up online in adb (120 seconds)."
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
        if [ $COUNT -gt 90 ]; then
            echo "Timeout waiting for emulator to boot (180 seconds)."
            $ADB -s "$EMU_SERIAL" emu kill 2>/dev/null
            kill $EMU_PID 2>/dev/null
            exit 1
        fi
    done
    echo "Emulator is booted and ready!"
fi

# Run the instrumentation tests
echo "Installing debug and test APKs to grant permissions..."
./gradlew :app:installDebug :app:installDebugAndroidTest

echo "Granting POST_NOTIFICATIONS permission to prevent prompts..."
# Get active device serials and grant permission to all of them
DEVICE_LIST=$($ADB devices 2>/dev/null | grep -v "List of devices attached" | grep -v "^$" | grep -v "offline" | grep -v "unauthorized" | cut -f1)
for DEV in $DEVICE_LIST; do
    echo "Granting permission on device: $DEV"
    $ADB -s "$DEV" shell pm grant fi.iki.ede.safe.debug android.permission.POST_NOTIFICATIONS 2>/dev/null || true
done

# Find all test classes dynamically under app/src/androidTest/java
TEST_CLASSES=()
while read -r FILE; do
    if grep -q -E '@Test|@RunWith' "$FILE"; then
        PKG=$(grep "^package " "$FILE" | head -n 1 | cut -d' ' -f2 | tr -d '\r' | tr -d ';')
        while read -r CLS; do
            CLS_CLEAN=$(echo "$CLS" | tr -d '\r')
            if [[ "$CLS_CLEAN" =~ [Tt]est$ || "$CLS_CLEAN" == "LoginScreenAfterAutoBackupRestore" ]]; then
                TEST_CLASSES+=("$PKG.$CLS_CLEAN")
            fi
        done < <(grep -E '^[[:space:]]*(class|object)[[:space:]]+[A-Za-z0-9_]+' "$FILE" | sed -E 's/^[[:space:]]*(class|object)[[:space:]]+([A-Za-z0-9_]+).*/\2/')
    fi
done < <(find app/src/androidTest/java -name "*.kt")

FAILED_CLASSES=()
for TEST_CLASS in "${TEST_CLASSES[@]}"; do
    echo "=================================================="
    echo "Running Test Class: $TEST_CLASS"
    echo "=================================================="
    # If EMU_SERIAL is set, use it. Otherwise, target default device.
    if [ -n "$EMU_SERIAL" ]; then
        $ADB -s "$EMU_SERIAL" shell am instrument -w -e class "$TEST_CLASS" fi.iki.ede.safe.debug.test/androidx.test.runner.AndroidJUnitRunner
    else
        $ADB shell am instrument -w -e class "$TEST_CLASS" fi.iki.ede.safe.debug.test/androidx.test.runner.AndroidJUnitRunner
    fi
    RET=$?
    if [ $RET -ne 0 ]; then
        echo "Test class $TEST_CLASS returned exit code $RET. Retrying once in case of temporary ADB drop..."
        sleep 3
        if [ -n "$EMU_SERIAL" ]; then
            $ADB -s "$EMU_SERIAL" shell am instrument -w -e class "$TEST_CLASS" fi.iki.ede.safe.debug.test/androidx.test.runner.AndroidJUnitRunner
        else
            $ADB shell am instrument -w -e class "$TEST_CLASS" fi.iki.ede.safe.debug.test/androidx.test.runner.AndroidJUnitRunner
        fi
        RET=$?
    fi
    if [ $RET -ne 0 ]; then
        FAILED_CLASSES+=("$TEST_CLASS")
    fi
    sleep 3
done

if [ ${#FAILED_CLASSES[@]} -eq 0 ]; then
    echo "All tests passed successfully!"
    TEST_RESULT=0
else
    echo "The following test classes failed: ${FAILED_CLASSES[*]}"
    TEST_RESULT=1
fi

# Cleanup emulator if we started it
if [ -n "$EMU_PID" ]; then
    echo "Stopping emulator..."
    $ADB -s "$EMU_SERIAL" emu kill 2>/dev/null
    kill $EMU_PID 2>/dev/null
    wait $EMU_PID 2>/dev/null
fi

exit $TEST_RESULT
