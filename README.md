# Password Safe

Yet another? Yeah, indeed.

This is a simple, as secure as possible, quick, nifty project to store all my passwords, credit card details, what not PIIs I need to access every now and then, just a fingerprint away.

I trust no-one, least the big vendors (nor my self :) )
I don't want my sensitive data be whisked away to a cloud provider that forgot to encrypt the database, or change the DB default password or ..was just really lazy with keymanagement iterations and decided 64 is enough for everyone!

- Biometric access (optional)
- All the data is encrypted all the time (during editing briefly decrypted)
- Utilizing android keystore that does storage and heavy lifting, masterkey won't leak
- OpenSSL decryptable backup
- Can attach photos
- Can generate new secure, totally random passwords
- Quick search
- Open Source!
- Dark mode for me!
- AES-256 all the way
- Automatic timed lock
- Can import OISafe backup
- I currently have Have I Been Pawned breach check for the passwords, but not sure if I'll put it on release version

Written in Kotlin, utilizing JetPack/Compose.

Building:
- Use Android Studio
- ./gradlew build

You need:
- Java 21+ (linter JAR built on 21)
- app/google-service.json - firebase config
- local.properties w/ instrumentationStorePassword,instrumentationKeyPassword,instrumentationKeyStore,instrumentationStoreKeyAlias
  - content of those is irrelevant, only used during unit/instrumentation testing

Actual client is in [playstore](https://play.google.com/store/apps/details?id=fi.iki.ede.safe&hl=en_US), but for sake of crypto export regulations, I'm keeping it private currently.

Inspiration from ancient (and insecure) OISafe

Check docs:
- [Crypto](docs/Crypto.md) - short summary of cipher & details
- [Export format](docs/ExportFormat.md) - export file is documented here, with OpenSSL CLI instructions
- [Google Password Manager import](docs/GPM%20Import%20Usage.txt) - How Google Password Manager import is done
- [Bash script](docs/dec.sh) - to decrypt export file
- [TODO](docs/TODO.txt) 
- [](docs/) - xxx
- [](docs/) - xxx
