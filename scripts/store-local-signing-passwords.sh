#!/usr/bin/env bash
set -euo pipefail

if [[ ! -t 0 ]]; then
  echo "Run this script from an interactive Terminal; signing passwords must not be piped." >&2
  exit 2
fi

account="$(id -un)"
keystore_type="${SAFE_KEYSTORE_TYPE:-PKCS12}"
keystore_type_upper="$(printf '%s' "$keystore_type" | tr '[:lower:]' '[:upper:]')"
case "$keystore_type_upper" in
  PKCS12)
    services=("Safe Android upload keystore password")
    ;;
  JKS)
    services=("Safe Android upload keystore password" "Safe Android upload key password")
    ;;
  *)
    echo "Unsupported keystore type: $keystore_type (expected PKCS12 or JKS)." >&2
    exit 2
    ;;
esac

for service in "${services[@]}"; do
  if stored_password="$(/usr/bin/security find-generic-password -w -a "$account" -s "$service" 2>/dev/null)" && [[ -n "$stored_password" ]]; then
    unset stored_password
    echo "Keeping existing Keychain item: $service"
    continue
  fi
  unset stored_password || true

  echo "Enter the password for '$service' in the hidden macOS security prompt."
  /usr/bin/security add-generic-password -U -a "$account" -s "$service" -w

  if stored_password="$(/usr/bin/security find-generic-password -w -a "$account" -s "$service" 2>/dev/null)" && [[ -n "$stored_password" ]]; then
    unset stored_password
    echo "Stored and verified Keychain item: $service"
  else
    unset stored_password || true
    /usr/bin/security delete-generic-password -a "$account" -s "$service" >/dev/null 2>&1 || true
    echo "Keychain did not store a non-empty password for '$service'." >&2
    exit 1
  fi
done
