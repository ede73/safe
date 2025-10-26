# Password Safe

Yet another? Yeah, indeed.

This is a simple, as secure as possible, quick, nifty project to store all my passwords, credit card
details, what not PIIs I need to access every now and then, just a fingerprint away.

I trust no-one, least the big vendors (nor my self :) )
I don't want my sensitive data be whisked away to a cloud provider that forgot to encrypt the
database, or change the DB default password or ..was just really lazy with keymanagement iterations
and decided 64 is enough for everyone!

- Biometric access (optional)
- All the data is encrypted all the time (during editing briefly decrypted)
- Utilizing android keystore that does storage and heavy lifting, masterkey won't leak
- OpenSSL decryptable backup
- Can attach photos (encrypted too!)
- Can generate new secure, totally random passwords
- Quick search
- Open Source!
- Dark mode for me!
- AES-256 all the way
- Automatic timed lock
- I currently have Have I Been Pawned breach check for the passwords, but not sure if I'll put it on
  release version
- Plugin modules
- Trashcan for deleted passwords, and automatic emptying set to user preferred date
- Passwords can have extensions (tags/shared meta information), like phone number, which
  authenticator used etc.
- When creating new (updated) password, can copy&paste old & new separately
- Google Password Manager import - continuous import, with drag and drop (from GPM to Safe) and
  heuristic autoimport
- Firebase integrated for run time analysis
- Extensive unit tests, bouncycastle, UI tests

Code, showcasing asynchronous database access, every lengthy operation runs in a coroutine, using
TPM (ie. might not work on cheapo-phones)

+ other hidden gems, like automatically entering PIN to emulator, or running instrumentation tests
  in Windows Subsystem Linux box (check ci folder)

Written in Kotlin, utilizing JetPack/Compose.

Building:

- Use Android Studio
- ./gradlew build

You need:

- Java 21+ (linter JAR built on 21)
- app/google-service.json - firebase config
- local.properties w/
  instrumentationStorePassword,instrumentationKeyPassword,instrumentationKeyStore,instrumentationStoreKeyAlias
    - content of those is irrelevant, only used during unit/instrumentation testing

Actual client is
in [playstore](https://play.google.com/store/apps/details?id=fi.iki.ede.safe&hl=en_US), but for sake
of crypto export regulations, I'm keeping it private currently.

Inspiration from ancient (and insecure) OISafe

Check docs:

- [Crypto](docs/Crypto.md) - short summary of cipher & details
- [Export format](docs/ExportFormat.md) - export file is documented here, with OpenSSL CLI
  instructions
- [Google Password Manager import](docs/GPM%20Import%20Usage.txt) - How Google Password Manager
  import is done
- [Bash script](docs/dec.sh) - to decrypt export file
- [TODO](docs/TODO.txt) 

