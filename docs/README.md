# Password Safe

Yet another? Yeah, indeed!

This is a simple, as secure as possible, quick, nifty project to store all my passwords, credit card details, what not PIIs I need to access every now and then, just a fingerprint away.

I trust no-one, least the big vendors (nor my self :) )
I don't want my sensitive data be whisked away to a cloud provider that forgot up encrypt the database, or change the DB default password or ..was just really lazy with keymanagement iterations and decided 64 if enough for everyone!

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

[Crypto details](Crypto.md)

[Export format and OpenSSL decryption](ExportFormat.md)
