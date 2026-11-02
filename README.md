# OpenFile

An open file manager for Android — browse and manage local storage, external volumes
(SD card, USB OTG), and cloud providers (Google Drive, OneDrive, Dropbox) from a single app.

> ## ⚠️ Work in Progress
> This project is under active development. Features may change, break, or disappear without
> notice. Not recommended for production use.

## Requirements

- Android Studio (latest stable)
- An Android device or emulator running Android 8.0+ (API 26+)

## Run locally

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. Run the app on an emulator or physical device

## Notes

- Full-device storage access (SD card, root folders) requires granting
  **All Files Access** when prompted (Android 11+).
- Google Drive sign-in uses the on-device Google account picker backed by Google Play
  services; no browser redirect or pasted authorization code is needed.
- Cloud provider client IDs (OneDrive, Dropbox) are supplied via `local.properties` /
  Build Config fields during development.
