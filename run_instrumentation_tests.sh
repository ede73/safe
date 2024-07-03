REVPARSE=.last_rev_parse
# Trying to ensure WSL adb doesn't clash with windows one...
export ADB_SERVER_SOCKET=tcp:localhost:6600

have_new_commits() {
  # Get the current commit hash
  before_pull=$(cat $REVPARSE)

  # Perform git pull (or rather..fetch from remote as such
  git fetch origin
  git reset --hard origin/main

  # Get the new commit hash
  after_pull=$(git rev-parse HEAD)

  [[ "$before_pull" == "$after_pull" ]] && {
    return 1
  }
  return 0
}

start_emulator() {
  adb devices
  "${ANDROID_HOME}/emulator/emulator" -avd Pixel_2_API_33.avd -no-audio >/dev/null 2>&1
}

get_emu_serial() {
  sleep 1
  adb devices | grep emulator | cut -f1
}

wait_emu_online() {
  emulator_serial="$1"

  boot_completed=""
  while [ -z "$boot_completed" ]; do
      boot_completed=$(adb -s "$emulator_serial" shell getprop sys.boot_completed 2>&1 | tr -d '\r')
      if [ "$boot_completed" != "1" ]; then
          echo "Waiting for emulator to boot..."
          sleep 1
          boot_completed=""
      fi
  done
  sleep 1
}

kill_emu() {
  emulator_serial="$1"
  emulatorpid="$2"
  ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL=5 adb -s "$emulator_serial" emu kill
  kill $emulatorpid
  wait $emulatorpid
}

# return 0 if you want while to continue
ci() {
  have_new_commits || {
    return 0
  }

  start_emulator&
  emulatorpid=$!

  emulator_serial=$(get_emu_serial)
  time wait_emu_online "$emulator_serial"

  touch .failed
  [[ $(./gradlew -q connectedAndroidTest 2> >(sed 's,file:///,file://wsl.localhost/Ubuntu/,g' 1>&2)) ]] || {
    echo "Tests run successfully"
    echo "$after_pull" >$REVPARSE
    rm .failed
  }

  kill_emu "$emulator_serial" "$emulatorpid"

  [[ -f .failed ]] && return 10
}

[[ -f .failed ]] && {
  echo "Sorry, there is a previous unresolved failure, fix that or remove .failed"
  exit 10
}

while ci;do sleep 60;done
