# Safe export file format

When exporting passwords, Safe creates a simple XML file, with all the categories, their names
and containing all the passwords, their descriptions, website, username, password and its change
date, note and photo. Each element has iv attribute, and body contains CipherText, both in hexadecimal.

Optional elements (only present if having a value):

- photo

Password changed date in the underlying file isn't encrypted. It's not that critical information. And the whole export is encrypted anyway, so it won't be visible.

IVCipherText denotes encrypted content, for example the username.

Everything here is hexadecimals. IV's are that of master key and XML document.

```text
Salt
IV
Master key encrypted with PBKDF2 user generated key
IV
Document XML encrypted
```

And the XML looks like. If the element body is empty, then iv="""

```xml

<PasswordSafe version="1">
    <category iv_name="IV" cipher_name="CipherText">
        <item>
            <description iv="IV">CipherText</description>
            <website iv="IV">CipherText</website>
            <username iv="IV">CipherText</username>
            <password changed="Apr 9, 2024, 5:30:21&#8239;PM Pacific Daylight Time" iv="IV">
                CipherText
            </password>
            <note iv="IV">CipherText</note>
            <photo iv="IV">CipherText</photo>
        </item>
    </category>
    <category>...</category>
</PasswordSafe>
```

# Manual decryption

We're OpenSSL compliant, decryption can be done as follows.

Store this to say dec.sh

```shell
f="$1"
SALT=$(sed -n '1p' "$f")
DEC_KEY=$(openssl enc -aes-256-cbc -P -md sha256 -S "$SALT" -iter 20000 -pbkdf2 | grep key | cut -d= -f2)

IV=$(sed -n '2p' "$f")
ENCRYPTED_MASTERKEY=$(sed -n '3p' "$f")

MASTER_KEY=$(echo -n "$ENCRYPTED_MASTERKEY" | xxd -r -p -| base64 | openssl enc -aes-256-cbc -d -a -iv "$IV" -K "$DEC_KEY" -nosalt | xxd -c222 -p)

DOCUMENT_IV=$(sed -n '4p' "$f")
sed -n '5,$p' "$f" | tr -d "\n" | xxd -r -p - | base64 | openssl enc -aes-256-cbc -d -a -iv "$DOCUMENT_IV" -K "$MASTER_KEY" -nosalt
```

For example, store the script to dec.sh and

```shell
bash dec.sh password.xml
```

This is just in case I one day stop maintaining the project, no one picks up, your data can still be accessed.