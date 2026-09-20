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

SALT=$(sed -n '1p' "$CLEAN_FILE")
IV=$(sed -n '2p' "$CLEAN_FILE")
ENCRYPTED_MASTERKEY=$(sed -n '3p' "$CLEAN_FILE")
DOCUMENT_IV=$(sed -n '4p' "$CLEAN_FILE")

DEC_KEY=$(openssl enc -aes-256-cbc -P -md sha256 -S "$SALT" -iter 20000 -pbkdf2 -pass pass:"$PASS" 2>/dev/null | grep key | cut -d= -f2)

if [ -z "$DEC_KEY" ]; then
    echo "Error: Failed to derive decryption key." >&2
    exit 1
fi

MASTER_KEY=$(echo -n "$ENCRYPTED_MASTERKEY" | xxd -r -p | base64 | openssl enc -aes-256-cbc -d -a -iv "$IV" -K "$DEC_KEY" -nosalt 2>/dev/null | xxd -p | tr -d '\n')

if [ -z "$MASTER_KEY" ]; then
    echo "Error: Master key decryption failed (incorrect password?)." >&2
    exit 1
fi

sed -n '5,$p' "$CLEAN_FILE" | tr -d '\n' | xxd -r -p | base64 | openssl enc -aes-256-cbc -d -a -iv "$DOCUMENT_IV" -K "$MASTER_KEY" -nosalt
echo ""

