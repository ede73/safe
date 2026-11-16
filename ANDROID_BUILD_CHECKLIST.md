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
- [x] Make local signed builds use the current checkout; reuse existing Keychain credentials, and make `storeSigningPasswords` one-time, non-overwriting, with a non-empty readback check.
- [ ] Run a real local signed AAB build after the owner enters the keystore password; verify its signature and output path.
- [x] Review workflow security/configuration and run Android unit-test plus signed-task dry-run validation.
- [x] Push this Android-only branch and open PR #56; do not modify remote `main` or touch the CXF PRs.
- [ ] After the workflow is merged to the default branch, dispatch it against the intended source ref and verify/download the AAB artifact.
- [x] Update the personal Android build/publish skill with the build-only flow, keystore-type facts, and Safe versioning constraint; notify PublishWorkflow.

The GitHub secret uploader used hidden terminal input and the owner confirmed successful validation and upload of the Safe secrets. Local builds use the existing Keychain item; `storeSigningPasswords` is needed only if that item is absent. A real signed build still needs to be run. Never request passwords in chat. The Android build must stay separate from CXF sync, preserve Gradle's existing version calculation, and remain build-only (no Play publishing).
