# Trying to ensure WSL adb doesn't clash with windows one...
# Fucksake, what ever tried, emulator stubbornly connects to hosts adb
#ADB_SERVER_SOCKET=tcp:$(grep nameserver /etc/resolv.conf |cut -d" " -f2):6600
#export ADB_SERVER_SOCKET

#adb start-server

get_emu_port() {
  echo "6900"
}

start_emulator() {
  emu_port="$1"
  "${ANDROID_HOME}/emulator/emulator" -avd Pixel_2_API_33.avd -no-audio -port "$emu_port" >/dev/null 2>&1
}

get_emu_serial() {
  emulator_serial=""
  while [ -z "$emulator_serial" ]; do
    emulator_serial=$(adb devices | grep emulator | cut -f1)
    if [ "$emulator_serial" == "" ]; then
      sleep 1
    fi
  done
  echo "$emulator_serial"
}

wait_emu_online() {
  emulator_serial="$1"

  boot_completed=""
  while [ -z "$boot_completed" ]; do
      boot_completed=$(adb -s "$emulator_serial" shell getprop sys.boot_completed 2>&1 | tr -d '\r')
      if [ "$boot_completed" != "1" ]; then
          echo "Waiting for emulator to boot...($boot_completed"
          adb devices
          sleep 1
          boot_completed=""
      fi
  done
  sleep 1
}

kill_emu() {
  emulator_serial="$1"
  emulator_pid="$2"
  ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL=5 adb -s "$emulator_serial" emu kill
  kill "$emulator_pid"
  wait "$emulator_pid"
}
