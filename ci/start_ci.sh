# Trying to ensure WSL adb doesn't clash with windows one...

source ci/emu_support.sh
source ci/git_support.sh
source ci/test_support.sh

# return 0 if you want while to continue
ci() {
  have_new_commits || {
    return 0
  }

  emu_port=$(get_emu_port)
  start_emulator "$emu_port" &
  emulatorpid=$!

  emulator_serial="emulator-$emu_port" #$(get_emu_serial)
  time wait_emu_online "$emulator_serial"

  run_tests

  kill_emu "$emulator_serial" "$emulatorpid"

  did_tests_fail && return 10
}

preexisting_failure

while ci;do sleep 60;done
