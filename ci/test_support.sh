FAILED=.failed

run_tests() {
  touch "$FAILED"
  [[ $(./gradlew connectedAndroidTest 2> >(sed 's,file:///,file://wsl.localhost/Ubuntu/,g' 1>&2)) ]] || {
    echo "Tests run successfully"
    rm "$FAILED"
    store_last_rev
  }
}

did_tests_fail() {
  [[ -f .failed ]] && return 10
  return 0
}

preexisting_failure() {
  [[ -f .failed ]] && {
    echo "Sorry, there is a previous unresolved failure, fix that or remove .failed"
    exit 10
  }
}

