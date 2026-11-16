# Android signed AAB build

This workflow is separate from CXF synchronization. It only builds and signs an Android App Bundle with Safe's existing upload key. It does not query Google Play, change Safe's Gradle version-code behavior, publish to a testing track, or publish to Production.

## Build locally with Gradle

Run the local Gradle build from the checkout whose changes you want in the AAB. The build reads the password from the macOS login Keychain and does not prompt for or change it. If the Keychain item has not been set up on this Mac, run this one-time setup from an interactive Terminal:

```sh
./gradlew storeSigningPasswords
```

The `storeSigningPasswords` task preserves any existing non-empty item, prompts only when the expected item is absent, and reads back the result to reject an empty Keychain value. The macOS `security` CLI does not put the password in command arguments. Safe's source keystore is PKCS12, so only one password is needed for both the store and private key. Then build and verify the signed bundle:

```sh
./gradlew --no-configuration-cache generateSignedBundle
```

The build validates the alias and private key before bundling, then verifies the generated AAB's signer. The Keychain write runs inside the `storeSigningPasswords` task action, following the IndexPaint Gradle pattern; signing credentials are read for the subsequent build. Local signed tasks disable Gradle's configuration cache so signing passwords are not persisted there. This does not use a Swift `Security.framework` helper.

Build-logic unit tests cover the one-password PKCS12 mapping, separate JKS passwords, and rejection of empty or failed Keychain reads. They run with `./gradlew -p buildSrc test`.

The AAB is built from the current local checkout, including uncommitted source changes. It is not uploaded anywhere. Gradle keeps Safe's existing Git-derived version-code/version-name behavior; local edits do not change the version count until they are committed. The resulting AAB is at:

```text
app/build/outputs/bundle/release/app-release.aab
```

Gradle prints the absolute output path when the build finishes.

The task does not query Play, publish to a testing track, or publish to Production.

## Prepare GitHub Actions signing secrets

The helper preserves the source keystore and creates a temporary copy with newly generated CI passwords. It validates that copy and sends its base64 bytes/passwords to Actions secrets without printing them. Alias and keystore type are repository variables, not secrets.

```sh
./scripts/update-github-signing-secrets.sh \
  --repo ede73/safe --prefix SAFE settings/ede_release.keystore.gpg
```

The helper prompts directly for the current PKCS12 password with hidden terminal input. It copies the source keystore, changes only the temporary copy's password, validates that the certificate fingerprint is unchanged, and confirms that the private key can produce a CSR. PKCS12 uses the keystore password for private-key access, so the two CI password secrets have the same generated value and the configured type remains `PKCS12`. It does not read or depend on the separate Gradle Keychain task. It writes the `SAFE`-prefixed GitHub secrets and variables only after those checks pass. The source file remains unchanged.

It sets these project-specific names:

- Actions secrets: `SAFE_UPLOAD_KEYSTORE_BASE64`, `SAFE_KEYSTORE_PASSWORD`, `SAFE_KEY_PASSWORD`
- Repository variables: `SAFE_KEY_ALIAS`, `SAFE_KEYSTORE_TYPE`

The original ignored keystore remains unchanged. No Play service-account credential is needed for a signed build.

## Build from GitHub Actions

The manual **Android signed AAB build** workflow follows IndexPaint's owner-gated Mac mini runner path. It uses the runner labels `self-hosted`, `macOS`, and `safe-release`, accepts a branch, tag, commit SHA, or pull-request head as `source_ref`, builds through `generateSignedBundle`, verifies the signature, and uploads the AAB with a SHA-256 checksum and source provenance. It passes GitHub secrets to Gradle as inputs, temporarily materializes the keystore with mode 600, and removes it after the job. It disables Gradle's configuration cache for the signed build so signing credentials are not persisted in that cache.

Safe's repository-scoped runner `erkkas-mac-mini-safe` is registered and online on the same Mac mini that hosts IndexPaint's runner. Each registration has a separate runner directory and work folder; the Android SDK and Gradle cache are on the shared Mac. GitHub runners are repository-scoped, so another personal-account repository needs its own runner registration on this same host. No organization or extra repository is required.

GitHub only accepts `workflow_dispatch` after this workflow file exists on the default branch. Once it is merged there, dispatch a build from the command line, choosing the source ref independently:

```sh
gh workflow run android-signed-build.yml --repo ede73/safe --ref main -f source_ref=main
```

For a pull request, set `source_ref` to `refs/pull/NUMBER/head`. After the run completes, download its `safe-signed-aab` artifact:

```sh
gh run list --repo ede73/safe --workflow android-signed-build.yml --limit 5
gh run download RUN_ID --repo ede73/safe --name safe-signed-aab --dir app/release/safe-signed-aab
```

The `RUN_ID` is the ID of the build just started. Nothing in this workflow uploads the AAB to Google Play.

The Android build change is kept separate from the CXF sync work and must use its own pull request. Do not merge the CXF PR to enable this workflow.

## Current setup status

See [ANDROID_BUILD_CHECKLIST.md](ANDROID_BUILD_CHECKLIST.md) for the tracked implementation and setup status.
