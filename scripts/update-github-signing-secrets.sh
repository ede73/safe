#!/usr/bin/env bash
set -euo pipefail

# Authentication is checked before any other interactive prompt.
if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required." >&2
  exit 1
fi
gh auth status

# Check every external tool before asking for the local keystore credentials.
for tool in expect keytool openssl shasum awk cp chmod mkdir mktemp base64 tr rm dirname basename pwd cat; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Required command not found: $tool" >&2; exit 1; }
done

repo="ede73/safe"
prefix="SAFE"
keystore_arg=""
keystore_type="${SAFE_KEYSTORE_TYPE:-PKCS12}"
while (($#)); do
  case "$1" in
    --repo) repo="${2:?Missing value for --repo}"; shift 2 ;;
    --prefix) prefix="${2:?Missing value for --prefix}"; shift 2 ;;
    --type) keystore_type="${2:?Missing value for --type}"; shift 2 ;;
    --help)
      echo "Usage: $0 [--repo OWNER/REPO] [--prefix SECRET_PREFIX] [--type JKS|PKCS12] [keystore-file]"
      exit 0
      ;;
    --*) echo "Unknown option: $1" >&2; exit 2 ;;
    *)
      if [[ -n "$keystore_arg" ]]; then echo "Only one keystore path may be supplied." >&2; exit 2; fi
      keystore_arg="$1"
      shift
      ;;
  esac
done

if [[ ! "$repo" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]]; then
  echo "Repository must be OWNER/REPO; got '$repo'." >&2
  exit 2
fi
if [[ ! "$prefix" =~ ^[A-Z][A-Z0-9_]*$ ]]; then
  echo "Secret prefix must use uppercase letters, digits, and underscores." >&2
  exit 2
fi
keystore_type="$(printf '%s' "$keystore_type" | tr '[:lower:]' '[:upper:]')"
if [[ "$keystore_type" != "JKS" && "$keystore_type" != "PKCS12" ]]; then
  echo "Keystore type must be JKS or PKCS12; got '$keystore_type'." >&2
  exit 2
fi
if ! gh secret list --repo "$repo" >/dev/null; then
  echo "Could not access Actions secrets for $repo; verify repository permissions before entering keystore passwords." >&2
  exit 1
fi
if ! gh variable list --repo "$repo" >/dev/null; then
  echo "Could not access Actions variables for $repo; verify repository permissions before entering keystore passwords." >&2
  exit 1
fi
secret_keystore="${prefix}_UPLOAD_KEYSTORE_BASE64"
secret_store_password="${prefix}_KEYSTORE_PASSWORD"
secret_key_password="${prefix}_KEY_PASSWORD"
variable_alias="${prefix}_KEY_ALIAS"
variable_type="${prefix}_KEYSTORE_TYPE"

if [[ -z "$keystore_arg" ]]; then
  while :; do
    IFS= read -r -p "Path to the existing local upload keystore: " keystore_arg
    [[ -f "$keystore_arg" ]] && break
    echo "Sorry, couldn't find that file. Try again."
  done
elif [[ ! -f "$keystore_arg" ]]; then
  echo "Keystore not found: $keystore_arg" >&2
  exit 1
fi
keystore="$(cd "$(dirname "$keystore_arg")" && pwd)/$(basename "$keystore_arg")"
alias="${SAFE_KEY_ALIAS:-ede_release}"
IFS= read -r -p "Key alias [$alias]: " alias_input
alias="${alias_input:-$alias}"
ci_keystore_type="$keystore_type"

IFS= read -r -s -p "Current Safe upload keystore password: " old_store_password; echo
[[ -n "$old_store_password" ]] || { echo "Keystore password cannot be empty; GitHub was not changed." >&2; exit 1; }
if [[ "$keystore_type" == "PKCS12" ]]; then
  echo "PKCS12 uses the keystore password for its private key; keytool cannot set a separate key password."
  old_key_password="$old_store_password"
else
  IFS= read -r -s -p "Current Safe upload private-key password: " old_key_password; echo
  [[ -n "$old_key_password" ]] || { echo "Private-key password cannot be empty; GitHub was not changed." >&2; exit 1; }
fi

tmp="$(mktemp -d "${TMPDIR:-/tmp}/github-signing-secrets.XXXXXX")"
chmod 700 "$tmp"
ci_keystore_copy="$tmp/ci-upload.$([[ "$ci_keystore_type" == "PKCS12" ]] && echo p12 || echo jks)"
cleanup() {
  local exit_status=$?
  trap - EXIT HUP INT TERM
  unset old_store_password old_key_password new_store_password new_key_password keystore_b64
  if ! rm -rf -- "$tmp"; then
    echo "WARNING: Could not remove temporary directory: $tmp" >&2
    exit_status=1
  fi
  exit "$exit_status"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

expect_store_password_change() {
  OLD_STORE_PASSWORD="$old_store_password" \
  NEW_STORE_PASSWORD="$new_store_password" \
  KEYSTORE_COPY="$ci_keystore_copy" \
  KEYSTORE_TYPE="$ci_keystore_type" \
  LC_ALL=C expect <<'EXPECT'
log_user 0
spawn keytool -storepasswd -keystore $env(KEYSTORE_COPY) -storetype $env(KEYSTORE_TYPE)
expect {
  -re {Enter keystore password:} { send -- "$env(OLD_STORE_PASSWORD)\r" }
  timeout { exit 1 }
  eof { exit 1 }
}
expect {
  -re {New keystore password:} { send -- "$env(NEW_STORE_PASSWORD)\r" }
  timeout { exit 1 }
  eof { exit 1 }
}
expect {
  -re {Re-enter new keystore password:} { send -- "$env(NEW_STORE_PASSWORD)\r" }
  timeout { exit 1 }
  eof { exit 1 }
}
expect eof
set result [wait]
if {[lindex $result 2] != 0 || [lindex $result 3] != 0} { exit 1 }
EXPECT
}

expect_key_password_change() {
  OLD_KEY_PASSWORD="$old_key_password" \
  NEW_STORE_PASSWORD="$new_store_password" \
  NEW_KEY_PASSWORD="$new_key_password" \
  KEYSTORE_COPY="$ci_keystore_copy" \
  KEYSTORE_TYPE="$ci_keystore_type" \
  KEY_ALIAS="$alias" \
  LC_ALL=C expect <<'EXPECT'
log_user 0
spawn keytool -keypasswd -alias $env(KEY_ALIAS) -keystore $env(KEYSTORE_COPY) -storetype $env(KEYSTORE_TYPE)
expect {
  -re {Enter keystore password:} { send -- "$env(NEW_STORE_PASSWORD)\r" }
  timeout { exit 1 }
  eof { exit 1 }
}
expect {
  -re {Enter key password.*} { send -- "$env(OLD_KEY_PASSWORD)\r" }
  timeout { exit 1 }
  eof { exit 1 }
}
expect {
  -re {New key password.*} { send -- "$env(NEW_KEY_PASSWORD)\r" }
  timeout { exit 1 }
  eof { exit 1 }
}
expect {
  -re {Re-enter new key password.*} { send -- "$env(NEW_KEY_PASSWORD)\r" }
  timeout { exit 1 }
  eof { exit 1 }
}
expect eof
set result [wait]
if {[lindex $result 2] != 0 || [lindex $result 3] != 0} { exit 1 }
EXPECT
}

certificate_fingerprint() {
  local path="$1"
  local password="$2"
  local store_type="$3"
  KEYSTORE_PATH="$path" \
  KEYSTORE_PASSWORD="$password" \
  KEYSTORE_TYPE="$store_type" \
  KEY_ALIAS="$alias" \
  LC_ALL=C expect <<'EXPECT' | awk -F ': ' '/SHA1:/{print $2; exit}'
log_user 1
spawn keytool -list -v -alias $env(KEY_ALIAS) -keystore $env(KEYSTORE_PATH) -storetype $env(KEYSTORE_TYPE)
expect {
  -re {Enter keystore password:} { send -- "$env(KEYSTORE_PASSWORD)\r" }
  timeout { exit 1 }
  eof { exit 1 }
}
expect eof
set result [wait]
if {[lindex $result 2] != 0 || [lindex $result 3] != 0} { exit 1 }
EXPECT
}

verify_private_key_password() {
  KEYSTORE_COPY="$ci_keystore_copy" \
  KEYSTORE_PASSWORD="$new_store_password" \
  KEY_PASSWORD="$new_key_password" \
  KEYSTORE_TYPE="$ci_keystore_type" \
  KEY_ALIAS="$alias" \
  CSR_PATH="$tmp/verify.csr" \
  LC_ALL=C expect <<'EXPECT'
log_user 0
spawn keytool -certreq -alias $env(KEY_ALIAS) -keystore $env(KEYSTORE_COPY) -storetype $env(KEYSTORE_TYPE) -file $env(CSR_PATH)
expect {
  -re {Enter keystore password:} { send -- "$env(KEYSTORE_PASSWORD)\r" }
  timeout { exit 1 }
  eof { exit 1 }
}
if {$env(KEYSTORE_TYPE) eq "PKCS12"} {
  expect eof
} else {
  expect {
    -re {Enter key password.*} { send -- "$env(KEY_PASSWORD)\r" }
    timeout { exit 1 }
    eof { exit 1 }
  }
  expect eof
}
set result [wait]
if {[lindex $result 2] != 0 || [lindex $result 3] != 0} { exit 1 }
EXPECT
  [[ -s "$tmp/verify.csr" ]]
}

source_hash="$(shasum -a 256 "$keystore" | awk '{print $1}')"
new_store_password="$(openssl rand -hex 32)"
if [[ "$keystore_type" == "PKCS12" ]]; then
  new_key_password="$new_store_password"
else
  new_key_password="$(openssl rand -hex 32)"
fi
copy="$ci_keystore_copy"
cp "$keystore" "$copy"
chmod 600 "$copy"
if ! expect_store_password_change; then
  echo "Could not re-encrypt the temporary copy with its new keystore password. GitHub was not changed." >&2
  exit 1
fi
if [[ "$keystore_type" != "PKCS12" ]]; then
  if ! expect_key_password_change; then
    echo "Could not re-encrypt the temporary copy with its new private-key password. GitHub was not changed." >&2
    exit 1
  fi
fi

source_fingerprint="$(certificate_fingerprint "$keystore" "$old_store_password" "$keystore_type")"
[[ -n "$source_fingerprint" ]] || { echo "Could not read the source certificate fingerprint." >&2; exit 1; }
copy_fingerprint="$(certificate_fingerprint "$copy" "$new_store_password" "$ci_keystore_type")"
if [[ -z "$copy_fingerprint" || "$copy_fingerprint" != "$source_fingerprint" ]]; then
  echo "Temporary keystore validation failed: certificate fingerprint does not match the source." >&2
  exit 1
fi
if ! verify_private_key_password; then
  echo "Temporary keystore validation failed: the private key could not sign a certificate request with its new password." >&2
  exit 1
fi
if [[ "$(shasum -a 256 "$keystore" | awk '{print $1}')" != "$source_hash" ]]; then
  echo "The source keystore changed unexpectedly; refusing to update GitHub." >&2
  exit 1
fi

keystore_b64="$(base64 < "$copy" | tr -d '\n')"
if (( ${#keystore_b64} > 48000 )); then
  echo "Base64 keystore exceeds the conservative GitHub Actions secret size limit." >&2
  exit 1
fi

echo "Validated the upload keystore for $repo (alias $alias, source type $keystore_type, CI type $ci_keystore_type)."
echo "Writing project-prefixed signing secrets and variables; the source keystore will remain unchanged."

set_secret() {
  local name="$1"
  local value="$2"
  echo "Setting GitHub Actions secret $name..."
  if ! printf '%s' "$value" | gh secret set "$name" --repo "$repo"; then
    echo "Partial signing-secret update: $name failed after earlier writes may have succeeded. Do not build yet; rerun this script from the unchanged local keystore to replace all three secrets." >&2
    return 1
  fi
}
if ! set_secret "$secret_keystore" "$keystore_b64"; then exit 1; fi
if ! set_secret "$secret_store_password" "$new_store_password"; then exit 1; fi
if ! set_secret "$secret_key_password" "$new_key_password"; then exit 1; fi
if ! gh variable set "$variable_alias" --repo "$repo" --body "$alias"; then
  echo "Signing alias configuration failed after the secrets were written. Do not build yet; rerun this script from the unchanged local keystore." >&2
  exit 1
fi
if ! gh variable set "$variable_type" --repo "$repo" --body "$ci_keystore_type"; then
  echo "Keystore type configuration failed after the secrets were written. Do not build yet; rerun this script from the unchanged local keystore." >&2
  exit 1
fi

# Remove obsolete combined-secret names from earlier iterations, if present.
for old_secret in "${prefix}_SIGNING_BUNDLE" "${prefix}_SIGNING_SECRET"; do
  if gh secret delete "$old_secret" --repo "$repo" >/dev/null 2>&1; then
    echo "Removed obsolete GitHub Actions secret $old_secret."
  fi
done

if [[ "$(shasum -a 256 "$keystore" | awk '{print $1}')" != "$source_hash" ]]; then
  echo "ERROR: source keystore hash changed during the update. Do not build; rerun after checking the source keystore." >&2
  exit 1
fi
echo "GitHub Actions signing secrets updated: $secret_keystore, $secret_store_password, and $secret_key_password. Original local keystore is unchanged."
