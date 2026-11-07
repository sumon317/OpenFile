# OpenFile — Capability-Aware Android File Manager

[![Android CI](https://github.com/sumon317/OpenFile/actions/workflows/android-ci.yml/badge.svg)](https://github.com/sumon317/OpenFile/actions/workflows/android-ci.yml)
[![Latest Release](https://img.shields.io/github/v/release/sumon317/OpenFile?label=Release&logo=github)](https://github.com/sumon317/OpenFile/releases)
[![Platform](https://img.shields.io/badge/Android-7.0%2B%20(API%2024--36)-3DDC84?logo=android&logoColor=white)](https://github.com/sumon317/OpenFile)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2%20%E2%80%A2%20Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)

**OpenFile** (`com.openfile.manager`) is a fast, privacy-first, capability-aware file manager for Android built with **Kotlin** and **Jetpack Compose (Material 3)**. It unifies local internal storage, removable SD cards & USB OTG drives, restricted `/Android/obb` & `/Android/data` app directories, multi-format archives, LAN network shares (`FTP`, `FTPS`, `SFTP`, `SMB2/3`), and an embedded Wi-Fi FTP Server into a single 60/120fps interface.

---

## 📥 Download & Built-In Auto-Update

Download the latest signed APK directly from **[GitHub Releases](https://github.com/sumon317/OpenFile/releases)**.

### Native In-App Auto-Updater (No Obtainium Required)
OpenFile includes a built-in **GitHub Releases Auto-Updater** (`AppUpdateEngine`):
- **Automatic Startup Check**: Queries `https://api.github.com/repos/sumon317/OpenFile/releases/latest` in the background and notifies you with a banner on the Home screen whenever a newer semantic version tag (`vX.Y.Z`) is published.
- **Direct In-App Download & Install**: Streams the release `.apk` directly inside the app with live byte progress and launches Android's native `PackageInstaller` via `FileProvider`—no third-party updater apps like Obtainium or F-Droid required.
- **Manual Check Anytime**: Open **Settings → App Updates (GitHub Releases)** and tap **Check for Updates**.

---

## ✨ Key Features

### 1. Complete Local & Scoped Storage Access
- **Full Internal & External Storage**: Browse `/storage/emulated/0`, physical SD cards, and USB OTG volumes via `StorageManager` and `MANAGE_EXTERNAL_STORAGE` (*All Files Access* on Android 11+).
- **`/Android/obb` & `/Android/data` Support**: Automatically discovers installed app package directories inside `/Android/obb` and `/Android/data`, launches targeted 1-tap SAF folder grants (`DocumentsContract.EXTRA_INITIAL_URI`), and supports root (`su`) fallback on rooted devices.
- **Adaptive Responsive UI**: Symmetrical 3-column category dashboard on phones, collapsible navigation drawer, and canonical two-pane inspector + persistent `NavigationRail` on tablets and foldables.

### 2. Conflict-Safe File Transfer & Integrity Engine
- **Copy, Cut/Move, Rename & Multi-Select**: Full contextual bottom action bar with batch operations.
- **Safety Guards**: Automatic duplicate renaming (`file (1).ext`) when copying into the same directory and self-subfolder recursion guards.
- **Soft-Delete Trash & 1-Tap Undo**: Move deleted files to an internal Trash bin with instant restoration to original paths.
- **Cryptographic Verification**: Streaming `MD5` and `SHA-256` file checksum calculator and post-transfer integrity verification.

### 3. Multi-Format Archive Engine (`Zip-Slip` Hardened)
- **Inspect & Extract**: Browse virtual archive entries inside `ZIP`, `TAR`, `TAR.GZ`, `TAR.BZ2`, `TAR.XZ`, and `7Z` archives without full extraction, or extract selected/all entries.
- **Create Archives**: Compress any selection of files and folders into `ZIP`, `TAR`, `TAR.GZ`, or `TAR.XZ`.
- **Security**: Enforces canonical path validation (`validateSafeTargetFile`) to block `../../` Zip-Slip path traversal attacks.

### 4. Network File Sharing & Remote Clients
- **Access from PC (Wi-Fi FTP / FTPS Server)**: Foreground service FTP server (`FtpServerForegroundService`) with configurable port (`2121`), passive port range, read/write permissions, explicit TLS, and strict canonical root sandboxing.
- **Remote Network Clients**: Connect to remote **FTP / FTPS** (`Apache Commons Net`), **SFTP** (`JSch` with interactive `SHA-256` SSH host-key verification and pinning), and **SMB2/3** (`SMBJ` for Windows/NAS shares) with built-in LAN host discovery.

### 5. Storage Analyzer & Parallel Duplicate Finder
- **Visual Breakdown**: Inspect storage usage by category and locate large space-hogging files.
- **Two-Stage Parallel Duplicate Detection**: Groups candidate files by byte size, performs a fast 4 KB head+tail partial hash pass, and verifies exact matches with full `SHA-256` digests across CPU cores.

### 6. High-Frame-Rate (60/120fps) Compose Architecture
- **Zero-Jank Lazy Lists**: Stable item keys, `contentType` reuse, hoisted `CompositionLocal`s, unified draw-phase selection highlights, `ThreadLocal` + ring-buffer date formatting cache, and `RGB_565` hardware-backed `Coil` thumbnails.
- **Zero-Syscall MediaStore Indexing**: Direct cursor-to-model mapping and single-pass shared supplemental directory caching.
- **Material 3 Themes**: Instant 0ms switching between `System`, `Light`, `Dark`, and `Pure Black (AMOLED)` themes with optional Android 12+ Dynamic Color.

---

## 🛠️ Technology Stack

| Layer | Technology |
| :--- | :--- |
| **Language & Concurrency** | Kotlin `2.2.10`, Kotlin Coroutines & `StateFlow` |
| **UI & Design System** | Jetpack Compose (BOM `2026.01.01`), Material 3, Adaptive Navigation |
| **Local Persistence** | Room Database (`2.8.4` w/ KSP), Jetpack DataStore Preferences (`1.1.7`) |
| **Security & Credentials** | Android Keystore `AES/GCM/NoPadding` (`KeystoreCredentialVault`) |
| **Networking & Update Engine** | OkHttp `4.12.0`, Moshi `1.15.2`, Native GitHub Releases Updater (`AppUpdateEngine`) |
| **Archive Formats** | Apache Commons Compress `1.26.1`, Tukaani XZ `1.9` (`7Z`, `ZIP`, `TAR`, `GZ`, `XZ`) |
| **Network Protocols** | Apache Commons Net `3.10.0` (`FTP`/`FTPS`), JSch `0.2.17` (`SFTP`), SMBJ `0.13.0` (`SMB2/3`) |
| **Image Loading** | Coil Compose `2.7.0` (`RGB_565` hardware bitmaps) |
| **Testing** | JUnit 4, Robolectric `4.16`, Compose UI Test, AndroidX Runner / Espresso |

---

## 🚀 Building & Running Locally

### Prerequisites
- **Android Studio** (Meerkat / Ladybug or newer)
- **JDK 17 or 21**
- **Android SDK 36** (minimum supported device API is **24 / Android 7.0**)

### Build Commands
```bash
# Compile & assemble the Debug APK
./gradlew assembleDebug

# Run fast JVM & Robolectric unit tests
./gradlew testDebugUnitTest

# Assemble signed Release APK (uses KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD)
./gradlew assembleRelease
```

> [!IMPORTANT]
> **Production Release Policy — Never Send Debug APKs to Releases**:
> Under no circumstances should a debug APK (`app-debug.apk` or `*-debug.apk`) ever be attached to GitHub Releases or distributed to users. Debug builds have `android:debuggable="true"`, bypass R8 dead-code stripping and obfuscation, run significantly slower, and are signed with insecure development keys. All public distribution and updates must strictly use the signed, minified `OpenFile-*-release.apk`.


---

## 🔒 Privacy & Security
- **Zero Telemetry**: No analytics, no crash-reporting SDKs, and no advertisement libraries.
- **Hardware-Backed Secrets**: All FTP, SFTP, and SMB credentials are encrypted using `AES-256-GCM` keys stored in the Android Keystore (`KeystoreCredentialVault`).
