# GitHub Release & Asset Management Policy

## 1. Strictly Immutable Releases & Assets
- **NEVER update, overwrite, or clobber existing APKs or release assets on GitHub.**
  - Under no circumstances should you run `gh release upload ... --clobber` or delete/replace an already uploaded artifact or APK on a published GitHub release or tag.
  - Once a release binary (e.g., `.apk`, `.aab`, `.zip`) is uploaded to a GitHub release, it is considered immutable. If subsequent changes or bugfixes are made, they belong in a future release version or local build—never an overwrite of an existing release asset.

## 2. No Releases Without Explicit User Permission
- **NEVER create a GitHub release or push release git tags without explicit user permission.**
  - Do NOT run `gh release create`, `git tag <version>`, or push release tags to remote repositories unless the user explicitly commands it in their prompt (e.g., "release vX.Y.Z").
  - Automated tasks, unit testing, emulator verification, and local artifact assembly (e.g., `./gradlew assembleRelease`, copying to `dist/`) do NOT grant permission to publish a release.
  - If a build succeeds and verification passes, report the results to the user and wait for their explicit directive before creating any release.

## 3. Local Verification Protocol
- All release testing must be performed locally using the emulator or connected device via ADB (`adb install -r <apk>`).
- Local release builds should reside in `dist/` or `app/build/outputs/apk/release/` and remain local until the user explicitly requests publication.
