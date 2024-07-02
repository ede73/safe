ci() {
REVPARSE=.last_rev_parse

# Get the current commit hash
before_pull=$(cat $REVPARSE)

# Perform git pull
git pull

# Get the new commit hash
after_pull=$(git rev-parse HEAD)

[[ "$before_pull" == "$after_pull" ]] && {
  return 0
}

adb devices

${ANDROID_HOME}/emulator/emulator -avd Pixel_2_API_33.avd -no-audio&
emulatorpid=$!

sleep 1
emulator_serial=$(adb devices | grep emulator | cut -f1)

boot_completed=""
while [ -z "$boot_completed" ]; do
    boot_completed=$(adb -s $emulator_serial shell getprop sys.boot_completed 2>&1 | tr -d '\r')
    if [ "$boot_completed" != "1" ]; then
        echo "Waiting for emulator to boot..."
        sleep 5
    fi
done

touch .failed
[[ $(./gradlew connectedAndroidTest 2> >(sed 's,file:///,file://wsl.localhost/Ubuntu/,g' 1>&2)) ]] || {
  echo $after_pull >$REVPARSE
  rm .failed
}

adb -s $emulator_serial emu kill

wait $emulatorpid

[[ -f .failed ]] && return 10
}

[[ -f .failed ]] && {
  echo "Sorry, there is a previous unresolved failure, fix that or remove .failed"
  exit 10
}

while ci;do sleep 60;done
