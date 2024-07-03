REVPARSE=.last_rev_parse

pull_latest() {
  # Pull is actually bit iffy, we never have local changes, so..
  git fetch origin
  git reset --hard origin/main
}


have_new_commits() {
  # Get the current commit hash
  before_pull=$(cat $REVPARSE)

  # Perform git pull (or rather..fetch from remote as such
  pull_latest

  # Get the new commit hash
  after_pull=$(git rev-parse HEAD)

  [[ "$before_pull" == "$after_pull" ]] && {
    return 1
  }
  return 0
}

store_last_rev() {
  git rev-parse HEAD > "$REVPARSE"
}
