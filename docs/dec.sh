f="$1"
SALT=$(sed -n '1p' "$f")
DEC_KEY=$(openssl enc -aes-256-cbc -P -md sha256 -S "$SALT" -iter 20000 -pbkdf2 | grep key | cut -d= -f2)

IV=$(sed -n '2p' "$f")
ENCRYPTED_MASTERKEY=$(sed -n '3p' "$f")

MASTER_KEY=$(echo -n "$ENCRYPTED_MASTERKEY" | xxd -r -p -| base64 | openssl enc -aes-256-cbc -d -a -iv "$IV" -K "$DEC_KEY" -nosalt | xxd -c222 -p)

DOCUMENT_IV=$(sed -n '4p' "$f")
sed -n '5,$p' "$f" | tr -d "\n" | xxd -r -p - | base64 | openssl enc -aes-256-cbc -d -a -iv "$DOCUMENT_IV" -K "$MASTER_KEY" -nosalt

