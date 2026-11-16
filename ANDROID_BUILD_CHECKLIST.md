# Safe Android build checklist

This checklist covers the build-only setup, separate from CXF synchronization.

- [x] Keep Android publishing work separate from CXF synchronization; branch from `origin/main`, with no CXF commits.
- [x] Preserve Safe's existing Git-derived version-code/version-name behavior and app/runtime code.
- [x] Add the build-only Gradle signing and AAB signature-verification tasks; no Play publishing or Google Cloud integration.
- [x] Add an owner-gated manual GitHub Actions workflow that signs and uploads an AAB artifact only.
- [x] Add the Safe-prefixed signing-secret setup helper, supporting JKS and PKCS12 without converting the source keystore.
- [x] Validate both keystore types with fake GitHub CLI writes; source file hashes stayed unchanged.
- [x] Verify Safe signing secrets and variables are set in GitHub.
- [x] Register `erkkas-mac-mini-safe` online on the same Mac mini as IndexPaint, using its own repo-scoped runner directory and `safe-release` label.
- [x] Make local signed builds use the current checkout and reuse existing Keychain credentials; use an interactive shell script for one-time Keychain setup because Gradle daemon tasks do not have a usable terminal.
- [x] Add build-logic unit tests for the PKCS12/JKS password mapping and non-empty Keychain readback.
- [x] Verify the interactive Keychain setup script prompts and reads back a harmless temporary value; remove the temporary item afterward. `storeSigningPasswords` verifies existing entries only.
- [ ] Run a real local signed AAB build after the owner enters the keystore password; verify its signature and output path.
- [x] Review workflow security/configuration and run build-logic unit tests, Android unit tests, and signed-task dry-run validation.
- [x] Push this Android-only branch and open PR #58; do not modify remote `main` or touch the CXF PRs.
- [ ] After the workflow is merged to the default branch, dispatch it against the intended source ref and verify/download the AAB artifact.
- [x] Update the personal Android build/publish skill with the build-only flow, keystore-type facts, and Safe versioning constraint; notify PublishWorkflow.

The GitHub secret uploader used hidden terminal input and the owner confirmed successful validation and upload of the Safe secrets. The local setup script runs macOS `security` directly in an interactive terminal because Gradle daemon tasks do not have a usable terminal; it preserves non-empty entries and verifies readback. The harmless temporary-value smoke test passed and its item was deleted. A real Safe Keychain entry and signed AAB still need owner-input verification. Local builds reuse a non-empty item, so setup is one-time. Never request passwords in chat. The Android build must stay separate from CXF sync, preserve Gradle's existing version calculation, and remain build-only (no Play publishing).
