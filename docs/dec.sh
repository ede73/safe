#!/usr/bin/env bash
set -e

f="$1"
if [ -z "$f" ] || [ ! -f "$f" ]; then
    echo "Usage: $0 <backup_xml_file> [password]"
    exit 1
fi

PASS="$2"
if [ -z "$PASS" ]; then
    read -sp "Enter backup password: " PASS
    echo ""
fi

# Remove any DOS carriage returns (\r)
CLEAN_FILE=$(mktemp)
tr -d '\r' < "$f" > "$CLEAN_FILE"
trap 'rm -f "$CLEAN_FILE"' EXIT

SALT=$(sed -n '1p' "$CLEAN_FILE" | tr -d '\r\n ')
IV=$(sed -n '2p' "$CLEAN_FILE" | tr -d '\r\n ')
ENCRYPTED_MASTERKEY=$(sed -n '3p' "$CLEAN_FILE" | tr -d '\r\n ')
DOCUMENT_IV=$(sed -n '4p' "$CLEAN_FILE" | tr -d '\r\n ')

DEC_KEY=$(openssl enc -aes-256-cbc -P -md sha256 -S "$SALT" -iter 20000 -pbkdf2 -pass pass:"$PASS" 2>/dev/null | grep -i key | cut -d= -f2 | tr -d '\r\n ')

if [ -z "$DEC_KEY" ]; then
    echo "Error: Failed to derive decryption key." >&2
    exit 1
fi

MASTER_KEY_FILE=$(mktemp)
trap 'rm -f "$CLEAN_FILE" "$MASTER_KEY_FILE"' EXIT

if ! echo -n "$ENCRYPTED_MASTERKEY" | xxd -r -p | openssl enc -aes-256-cbc -d -K "$DEC_KEY" -iv "$IV" -nosalt > "$MASTER_KEY_FILE" 2>/dev/null; then
    echo "Error: Master key decryption failed (incorrect password?)." >&2
    exit 1
fi

MASTER_KEY=$(xxd -p "$MASTER_KEY_FILE" | tr -d '\r\n ')

if [ -z "$MASTER_KEY" ] || [ "${#MASTER_KEY}" -ne 64 ]; then
    echo "Error: Master key decryption failed (incorrect password?)." >&2
    exit 1
fi

tail -n +5 "$CLEAN_FILE" | tr -d '\r\n ' | xxd -r -p | openssl enc -aes-256-cbc -d -K "$MASTER_KEY" -iv "$DOCUMENT_IV" -nosalt
echo ""


