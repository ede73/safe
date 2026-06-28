# Password Safe (Kotlin Multiplatform now, Android, Desktop, iOS)

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
- Have I Been Pawned breach check for the passwords, but not sure if I'll put it on release version
- Plugin modules
- Trashcan for deleted passwords, and automatic emptying set to user preferred date
- Passwords can have extensions (tags/shared meta information), like phone number, which
  authenticator used etc.
- When creating new (updated) password, can copy&paste old & new separately
- Google Password Manager import - continuous import, with drag and drop (from GPM to Safe) and
  heuristic auto-import
- Firebase integrated for run time analysis
- Extensive unit tests, bouncycastle, UI tests

Code, showcasing asynchronous database access, every lengthy operation runs in a coroutine, using
TPM (ie. might not work on cheapo-phones)

+ other hidden gems, like automatically entering PIN to emulator, or running instrumentation tests
  in Windows Subsystem Linux box (check CI folder)

Written in Kotlin, utilizing JetPack/Compose, partially KMP (crypto pending, and alas avoiding
fragments for KMP)

Building:
- Use Android Studio
- ```./gradlew :app:assembleDebug``` (see notes about you need obv. **local.properties** and a fake app/google-services.json just for firebase, for debug builds no real keys needed)
- There's working WiP desktop client  ```./gradlew :desktop:run```
  - works on Windows/Mac/Linux, missing biometrics, TPM, some other integrations, but UI, import/export/cryptos all there
- There's working [WiP iOS](https://github.com/ede73/safe/pull/new/ios_import_restore) build too (merging soon, during July/2026)
  - missing biometrics, TPM, some other integrations, but UI, import/export/cryptos all there

You need:
- Java 21+ (linter JAR built on 21)
- **app/google-service.json** - firebase config
- **local.properties** w/
  instrumentationStorePassword,instrumentationKeyPassword,instrumentationKeyStore,instrumentationStoreKeyAlias
    - content of those is irrelevant, only used during unit/instrumentation testing

Actual client is in [playstore](https://play.google.com/store/apps/details?id=fi.iki.ede.safe&hl=en_US), but for sake
of crypto export regulations, I'm keeping it private currently.

| What | Android | iOS | Desktop |
| :--- | :--- | :--- | :--- |
| **Login** |Same, just f-print view <br/> <img width="100" height="300" alt="image" src="https://github.com/ede73/safe/blob/main/playstore/screenshots/phone/fingerprint.png" /> | <img width="100" height="300" alt="image" src="https://github.com/user-attachments/assets/39c5b3e3-d799-44b2-adb2-388ef121fefa" /> | <img width="100" height="300" alt="image" src="https://github.com/user-attachments/assets/727cf7fa-48cb-4f4a-bd8a-ce73f5f6dfbd" /> |
| **Categories** | <img width="100" height="300" alt="image" src="https://github.com/ede73/safe/blob/main/playstore/screenshots/phone/category_view.png"/> | <img width="100" height="300" alt="image" src="https://github.com/user-attachments/assets/6e19507a-9655-43f8-a18c-320fef98a6ca" /> | <img width="100" height="300" alt="image" src="https://github.com/user-attachments/assets/ab85d965-2ab1-47d9-8681-31751bbd7fe8" /> |
| **SiteEntries** | <img width="100" height="300" alt="image" src="https://github.com/ede73/safe/blob/main/playstore/screenshots/phone/password_list.png"/> | you get the | point already..|

Inspiration from ancient (and insecure) OISafe

Check docs:
- [Crypto](docs/Crypto.md) - short summary of cipher & details
- [Export format](docs/ExportFormat.md) - export file is documented here, with OpenSSL CLI
  instructions
- [Google Password Manager import](docs/GPM%20Import%20Usage.txt) - How Google Password Manager
  import is done
- [Bash script](docs/dec.sh) - to decrypt export file
- [TODO](docs/TODO.txt) 

