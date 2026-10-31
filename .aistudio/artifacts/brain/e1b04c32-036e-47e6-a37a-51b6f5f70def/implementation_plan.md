# OpenFile — Comprehensive Android File Manager (Revised Technical Architecture)

OpenFile is a fast, privacy-first, Material 3 Android file manager engineered for power users and everyday file organization. Inspired by KDE Dolphin and modern mobile file utilities, it unifies local storage navigation, archive creation/extraction, interactive storage analysis, application/APK inspection, local network file transfers (FTP Server, FTP/SFTP/SMB Clients), and extensible OAuth2 cloud storage into a clean, responsive Jetpack Compose experience.

---

## User Review & Critical Architectural Refinements

> [!IMPORTANT]
> This revised blueprint preserves all confirmed product features and Material 3 design choices while incorporating the 11 technical architecture refinements from the engineering review:

1. **Capability-Based `VirtualFileSystemProvider` Architecture**: Providers no longer pretend to be POSIX local filesystems. Every provider and `FileReference` explicitly advertises a `Set<ProviderCapability>` (`RANDOM_ACCESS_READ`, `STREAM_WRITE`, `ATOMIC_RENAME`, `INTRA_PROVIDER_MOVE`, `FAST_DIRECTORY_SIZE`, `TRASH_IN_PLACE`, `RESUME_TRANSFER`, `CHECKSUM_CALCULATION`, etc.).
2. **Realistic Android Scoped-Storage & SAF Boundaries**: Strictly separates `LocalFileSystemProvider` (`java.io.File`), `SafDocumentProvider` (`DocumentFile`), and `MediaStoreProvider` (`ContentResolver`). Explicitly respects Android 11+ restrictions on `/Android/data` and `/Android/obb` without promising unrestricted raw path traversal where the OS prohibits it.
3. **Validated Android Dependency Matrix & Compatibility Risks**: Evaluates bytecode/DEX, BouncyCastle/Conscrypt conflicts, and reflection risks for every archive and networking library on Android 10–15+ (`minSdk 24`, `targetSdk 36`).
4. **Explicit Separation of 7Z Extraction vs. Creation**: Supports streaming **ZIP, TAR, TAR.GZ, GZ, and XZ** for both creation and extraction, and treats **7Z as Read/Browse/Extract-only** via `commons-compress` + `xz` (due to LZMA2 heap and seekable channel constraints on mobile).
5. **`QUERY_ALL_PACKAGES` Play Policy Compliance & Fallback**: Uses `<queries>` intent filtering (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) as a zero-permission baseline fallback so the Applications Manager works even if `QUERY_ALL_PACKAGES` is restricted by Play distribution channel policies.
6. **Hardened FTP Server (Passive Mode, Interface Binding, Lifecycle)**: Adds configurable passive port ranges (`PASV` e.g., `50000–50050`), strict `InetAddress` binding to the active Wi-Fi interface, `ConnectivityManager.NetworkCallback` auto-shutdown on Wi-Fi drop/SSID change, and clear protocol distinction between unencrypted **FTP**, TLS-encrypted **FTPS**, and SSH-based **SFTP**.
7. **Direct Android Keystore Credential Vault**: Replaces deprecated `androidx.security:security-crypto` with direct `AndroidKeyStore` (`AES/GCM/NoPadding`, 256-bit hardware-backed `SecretKey`). Room stores only non-sensitive metadata and opaque key aliases—never raw passwords or OAuth tokens.
8. **Cross-Provider Copy, Move, Rename, Delete & Trash Semantics**: Formalizes how operations work across disparate backends (e.g., SAF-to-Local, Local-to-FTP, SAF Trash copy-then-delete with free-space preflight and atomic rollback).
9. **Unified Transfer Queue Engine**: Centralizes all local, archive, network, and cloud operations with concurrent job management, byte-level progress, pause/resume (where `ProviderCapability.RESUME_TRANSFER` is supported), optional post-transfer SHA-256 verification, duplicate detection, and undo support for non-destructive actions.
10. **Crash Recovery, SAF Permission Recovery & Comprehensive Testing**: Persists active transfer journals in Room to recover interrupted jobs on restart, detects revoked SAF URI grants (`SecurityException`) with a one-tap re-grant sheet, and defines unit, integration, and Robolectric UI test suites.
11. **Phased Release Milestones (1.0, 1.1, 1.2)**: Preserves the entire multi-subsystem architecture while structuring delivery into three independently testable, shippable milestones (**Release 1.0 Core + FTP**, **Release 1.1 Advanced Network & 7Z**, **Release 1.2 Cloud OAuth2**).

---

## 1. Overview & Core Concept

- **What It Does**: Provides a unified workbench for browsing internal/external storage, performing non-destructive batch file operations with conflict resolution (*Overwrite*, *Keep Both / Auto-Rename*, *Skip*), inspecting and extracting archives with ZIP-Slip and zip-bomb protection, visualizing disk usage via interactive charts, managing installed packages, hosting a local Wi-Fi FTP/FTPS server, connecting to remote FTP/SFTP/SMB shares, and linking cloud drives.
- **Target Audience / Persona**: Privacy-conscious Android phone and tablet users, developers, and system administrators who want desktop-class file management (comparable to KDE Dolphin and File Manager +) without adware, telemetry, or dark patterns.
- **Key Value**: Zero-telemetry offline reliability for local operations paired with transparent, opt-in network and cloud connectivity—built on a capability-aware virtual filesystem that never makes unsafe assumptions about underlying storage providers.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Home Dashboard & Quick Jump**:
   - Presents a real-time storage capacity overview (Internal Shared Storage, persisted SAF Document Trees, SD cards, USB OTG volumes), a 3x3 category grid (Downloads, Images, Videos, Audio, Documents, Applications, Archives, Large Files, Recent Files), active Transfer Queue banner, Network/Cloud quick links, Bookmarks, and Recently Accessed files.
2. **File Browser, Breadcrumbs, Previewer & Multi-Selection**:
   - Interactive horizontal breadcrumb bar (`Internal > Download > Projects`) with provider badge (`LOCAL`, `SAF`, `FTP`, `SFTP`, `SMB`, `CLOUD`), toggleable List/Grid views, and sort controls (Name, Size, Date, Type; Ascending/Descending).
   - Built-in **File Preview System** for instant inspection of plain text, source code (syntax-highlighted line numbers), Markdown, JSON/XML, images (pinch-to-zoom + EXIF), audio (inline controller), video, and PDF rendering (`PdfRenderer`) without leaving the app.
   - Contextual multi-selection toolbar with *Select All*, *Invert Selection*, *Copy*, *Cut*, *Delete (Move to Trash or Permanent)*, *Compress*, *Calculate Checksum (MD5/SHA-256)*, *Share*, *Bookmark*, and *Properties*.
   - Explicit **Conflict Resolution Modal** when target items exist: *Overwrite*, *Keep Both (`file (1).ext`)*, *Skip*, or *Compare Checksums*, with an *"Apply to all remaining conflicts"* checkbox.
3. **Unified Transfer Queue & Undo Snackbar**:
   - Floating mini-progress pill expands into a full **Transfer Queue Sheet** showing active, paused, queued, and completed jobs across all providers, with real-time throughput (`MB/s`), ETA, *Pause/Resume* (when supported by provider), *Cancel*, and optional *Verify SHA-256 after copy*.
   - Accidental renames, moves, or soft-deletions trigger an actionable *Undo* snackbar backed by an operation journal.
4. **Archive Inspection & Safe Extraction**:
   - Tapping a `.zip`, `.tar`, `.tar.gz`, `.tgz`, `.gz`, `.xz`, or `.7z` archive browses its internal hierarchy as a read-only virtual provider or extracts selected/all entries with live progress, Zip-Slip canonical path validation, and zip-bomb compression-ratio checks.
5. **Interactive Storage Analyzer**:
   - Asynchronous, cancellable directory scanner rendering a custom Canvas breakdown ring chart and proportional horizontal bars by category and folder size, with drill-down navigation and duplicate-file detection (matching size + partial/full SHA-256 hash).
6. **Network Hub & Local Wi-Fi FTP/FTPS Server**:
   - **FTP Server Tab**: Displays active Wi-Fi SSID and LAN IPv4 address, configurable command port (default `2121`) and passive port range (`50000–50050`), root directory selector, read-only vs. read/write toggle, username/password or explicit anonymous mode, optional TLS (FTPS) toggle, prominent security warning banner when running plain FTP, live client session log, and Start/Stop toggle tied to a Foreground Service notification.
   - **Remote Clients Tab (FTP / FTPS / SFTP / SMB2/3)**: Manage saved server profiles, interactive SSH `known_hosts` fingerprint verification modal on first SFTP connection, and remote directory browsing.

### Visual Identity & Theme

- **Aesthetic Direction**: **Precision Utilitarian Minimalism** inspired by KDE Dolphin and Material 3 guidelines—high information density, scannable metadata, crisp dividers, and zero decorative bloat.
- **Color Palette & Mood**:
  - Supports **Light**, **Dark**, **AMOLED Pitch-Black (`#000000`)**, and **Dynamic Material You** color schemes.
  - **Primary Accent**: Deep Cobalt Blue (`#0B57D0` Light / `#A8C7FA` Dark) for primary actions, active breadcrumb pills, and selection highlights.
  - **Semantic Category Accents**:
    - Folders & Archives: Warm Amber (`#E37400` / `#FFB74D`)
    - Media (Images/Video/Audio): Emerald Teal (`#00897B` / `#4DB6AC`) and Coral (`#E53935` / `#EF9A9A`)
    - Documents & Code: Indigo Slate (`#3949AB` / `#9FA8DA`)
    - Storage Warning / High Usage (>85%): Crimson Alert (`#D32F2F` / `#EF5350`)
- **Typography & Hierarchy**:
  - **Headings & Navigation Titles**: *Plus Jakarta Sans* (bundled locally in `res/font/` via `font-util`) for geometric clarity.
  - **Body & File Lists**: *Inter* for high-legibility file names, dates, and multi-line paths.
  - **Technical Metadata, Paths, Hashes & Network Logs**: *JetBrains Mono* for IP:Port addresses, POSIX permissions (`rwxr-xr-x`), SHA-256 checksums, and SSH host key fingerprints.
- **Component Styling, Layout & Accessibility**:
  - Adaptive navigation: Modal `NavigationDrawer` on compact screens (`< 600dp`) and persistent `NavigationRail` + two-pane List-Detail inspector on expanded tablet/foldable screens (`>= 600dp`).
  - Full TalkBack screen-reader semantics (`contentDescription`, state descriptions for selection/progress), keyboard/D-pad focus traversal order, and minimum `48dp x 48dp` interactive touch targets.

---

## 3. Architectural Refinements & Technical Decisions

### 1. Capability-Based Virtual Filesystem & Android Storage Boundaries

Different Android and remote storage backends do **not** behave like POSIX local files. OpenFile models every item as a structured `FileReference` and queries the provider's `Set<ProviderCapability>` before presenting UI actions or executing transfers.

```
                           OpenFile Presentation Layer (Compose UI)
                                              │
                                    ViewModels (StateFlow)
                                              │
                     ┌────────────────────────┼────────────────────────┐
                     ▼                        ▼                        ▼
             TransferQueueEngine     ArchiveSecurityEngine    StorageAnalyzerUseCase
                     └────────────────────────┼────────────────────────┘
                                              ▼
                       Capability-Aware VirtualFileSystemProvider API
                       (FileReference + Set<ProviderCapability>)
                                              │
        ┌──────────────────┬──────────────────┼──────────────────┬──────────────────┐
        ▼                  ▼                  ▼                  ▼                  ▼
  LocalProvider       SafProvider     MediaStoreProvider  NetworkProvider     CloudProvider
 (java.io.File)     (DocumentFile)    (ContentResolver)  (FTP/SFTP/SMB2/3)  (Drive/OneDrive/
                                                                                Dropbox)
```

#### Structured `FileReference` Model
Instead of passing raw `String` paths, the domain layer uses a serializable `FileReference`:
- `providerId: ProviderType` (`LOCAL`, `SAF`, `MEDIA_STORE`, `ARCHIVE_VIRTUAL`, `FTP`, `SFTP`, `SMB`, `GOOGLE_DRIVE`, `ONEDRIVE`, `DROPBOX`)
- `uriOrPath: String` (Canonical path for `LOCAL`, tree/document `content://` URI for `SAF`/`MEDIA_STORE`, or remote URI with profile ID)
- `displayName: String`
- `mimeType: String`
- `sizeBytes: Long` (`-1L` when unknown on remote streams)
- `lastModifiedEpochMs: Long`
- `isDirectory: Boolean`
- `capabilities: Set<ProviderCapability>`
- `metadata: Map<String, String>` (POSIX permissions, owner, ETag, remote file ID, or archive entry path)

#### Provider Capability Matrix & Scoped Storage Realism

| Storage Provider | Accessible Scope & OS Boundaries | Supported Capabilities | Unsupported / Restricted Operations |
| :--- | :--- | :--- | :--- |
| **`LocalFileSystemProvider` (`java.io.File`)** | App-specific directories and top-level shared storage (`/storage/emulated/0/{Download,Documents,Pictures,Music,Movies}`) where permitted. **Cannot** read `/Android/data` or `/Android/obb` on Android 11+ (API 30+). | `LIST_CHILDREN`, `RANDOM_ACCESS_READ`, `STREAM_READ`, `STREAM_WRITE`, `ATOMIC_RENAME`, `INTRA_PROVIDER_MOVE`, `FAST_DIRECTORY_SIZE`, `TRASH_IN_PLACE`, `RESUME_TRANSFER`, `POSIX_METADATA` | Cannot bypass kernel DAC or scoped-storage enforcement on protected app sandboxes. |
| **`SafDocumentProvider` (`DocumentFile`)** | User-granted directory trees via `OpenDocumentTree` (SD cards, USB OTG drives, custom folders). Android 11+ blocks selecting root `/storage/emulated/0`, `Download`, or `/Android/data` in the system picker. | `LIST_CHILDREN`, `STREAM_READ`, `STREAM_WRITE`, `CREATE_DIR`, `DELETE`, `RENAME` (if `COLUMN_FLAGS` includes `FLAG_SUPPORTS_RENAME`) | No POSIX paths, no `RandomAccessFile` (unless `ParcelFileDescriptor` supports seek), slow recursive directory sizing, no cross-tree atomic move. |
| **`MediaStoreProvider` (`ContentResolver`)** | Indexed Images, Videos, Audio, Downloads, and Documents across shared storage. | `QUERY_BY_CATEGORY`, `STREAM_READ`, `THUMBNAIL_LOAD`, `METADATA_READ`, `DELETE_WITH_USER_CONSENT` (`createDeleteRequest` on API 30+) | Not a hierarchical folder browser; cannot create arbitrary directories. |
| **`ArchiveVirtualProvider`** | Read-only virtual hierarchy inside `.zip`, `.tar`, `.tar.gz`, `.xz`, `.7z`. | `LIST_CHILDREN`, `STREAM_READ` (extract entry), `INTEGRITY_TEST` | No in-place random write or atomic entry rename (modifying a ZIP requires stream rebuild). |
| **`NetworkProvider` (FTP / FTPS / SFTP / SMB)** | Authenticated LAN or remote shares. | `LIST_CHILDREN`, `STREAM_READ`, `STREAM_WRITE`, `CREATE_DIR`, `DELETE`, `RENAME`, `RESUME_TRANSFER` (FTP `REST`, SFTP offset, SMB offset) | No fast recursive directory size (must traverse or opt-in), no local Trash bin (deletes are immediate with confirmation). |
| **`CloudProvider` (Drive / OneDrive / Dropbox)** | Authenticated user cloud drives via official REST APIs. | `LIST_CHILDREN`, `STREAM_READ`, `CHUNKED_UPLOAD`, `CREATE_DIR`, `RENAME`, `DELETE`, `REMOTE_TRASH` | No random-access local file descriptors; operations are HTTP API calls with rate limits and token refresh lifecycles. |

#### SAF Permission Recovery Flow
- Persisted URI grants (`takePersistableUriPermission`) can be revoked by the user in Android Settings or lost after device migration.
- Before any `SafDocumentProvider` operation, OpenFile checks `context.contentResolver.persistedUriPermissions`. If a `SecurityException` occurs or the permission is missing, the UI presents a dedicated **SAF Access Recovery Card** ("Folder permission expired or revoked for [Folder Name]") with a one-tap **Re-grant Access** button that launches `OpenDocumentTree` pre-targeted at the initial URI (`DocumentsContract.EXTRA_INITIAL_URI`) or allows clean removal of the stale bookmark.

---

### 2. Dependency Validation & Compatibility Risk Matrix

Every third-party library has been audited for Android 10–15+ (`minSdk 24`, `targetSdk 36`, AGP 9.x) compatibility, DEX footprint, and cryptographic provider behavior:

| Dependency | Artifact & Version Strategy | Role in OpenFile | Android Compatibility & Risk Mitigation |
| :--- | :--- | :--- | :--- |
| **Apache Commons Compress** | `org.apache.commons:commons-compress:1.27.1` | Streaming ZIP, TAR, GZ, and read-only 7Z archive handling. | **Verified Safe**. Pure Java bytecode; uses standard `InputStream`/`OutputStream` and `SeekableByteChannel` (API 24+ natively supports `java.nio.channels.SeekableByteChannel`). |
| **XZ for Java** | `org.tukaani:xz:1.10` | Required companion for `commons-compress` to decompress `.xz`, `.tar.xz`, and LZMA/LZMA2 streams inside `.7z` archives. | **Verified Safe**. Zero external dependencies, small footprint (~115 KB), fully compatible with Android ART. |
| **Apache Commons Net** | `commons-net:commons-net:3.11.1` | FTP and FTPS (explicit/implicit TLS) client implementation. | **Verified Safe**. Pure Java socket & SSLSocket implementation; works reliably on Android 24+. |
| **Apache FtpServer Core** | `org.apache.ftpserver:ftpserver-core:1.2.0` + `mina-core:2.1.8` + `slf4j-android` | Embedded local Wi-Fi FTP/FTPS server running inside `FtpServerForegroundService`. | **Low-Medium Risk (Packaging & Properties)**. Uses custom `FilesystemFactory` and `UserManager` interfaces. *Mitigation*: Implement an Android-native `VirtualFileSystemFactory` and in-memory `PropertiesUserManager` (avoiding desktop XML/Spring configuration loaders) and add `packaging { resources.excludes += "META-INF/DEPENDENCIES" }` in `build.gradle.kts`. |
| **SSHJ (SFTP Client)** | `com.hierynomus:sshj:0.38.0` | SSH2 SFTP client with password, public-key, and strict `known_hosts` fingerprint verification. | **Medium Risk (BouncyCastle Provider Collision)**. Android bundles a stripped-down system `BC` security provider. *Mitigation*: Exclude default `bcprov-jdk15on` transitive conflict if needed or register `org.bouncycastle:bcprov-jdk18on` explicitly via `Security.removeProvider("BC"); Security.insertProviderAt(BouncyCastleProvider(), 1)` before initializing `SSHClient(AndroidConfig())`. |
| **SMBJ (SMB2/3 Client)** | `com.hierynomus:smbj:0.13.0` | Modern SMB2.x / SMB3.x client for LAN shares. | **Low-Medium Risk (Shares `asn-one` & `mbassador` with SSHJ)**. Align versions of `com.hierynomus:asn-one` between `sshj` and `smbj` in `libs.versions.toml` to prevent duplicate class errors during DEX merging. |
| **Direct Android Keystore** | Platform `java.security.KeyStore` + `javax.crypto.Cipher` (`AndroidKeyStore`) | Hardware-backed AES-256-GCM encryption for network passwords, SSH passphrases, and OAuth2 tokens. | **Zero Dependency Risk**. Replaces deprecated `androidx.security:security-crypto` with zero third-party Tink/protobuf conflicts. |
| **OkHttp + Moshi** | `com.squareup.okhttp3:okhttp:4.10.0` + `moshi-kotlin:1.15.2` | HTTP engine for Cloud OAuth2 token exchange and REST API streams. | **Already in Template & Verified**. Pre-configured in `libs.versions.toml`. |

#### 4. Explicit Separation of 7Z Extraction vs. Creation
- **ZIP, TAR, TAR.GZ, GZ, XZ**: Full **Read, Browse, Extract, and Create** support via streaming `ArchiveOutputStream` and `CompressorOutputStream`.
- **7Z (`SevenZFile`)**: **Read, Browse, and Extract ONLY** (delivered in Release 1.1). Creating `.7z` archives with LZMA2 compression requires random-access `SeekableByteChannel` output and large native/JVM dictionary heaps (often 64 MB–512 MB+) that routinely trigger `OutOfMemoryError` on mid-range/low-end Android devices. The Archive Creator UI explicitly restricts output formats to `.zip`, `.tar`, `.tar.gz`, and `.tar.xz`, while the Archive Extractor supports `.7z` alongside all other formats.

---

### 3. Application Manager & `QUERY_ALL_PACKAGES` Play Policy Strategy

- **Google Play Policy Reality**: Google Play permits `QUERY_ALL_PACKAGES` for dedicated **File Managers and Device Management utilities** when declared in the Play Console App Content form, *or* requires apps in general tracks to use targeted `<queries>` blocks.
- **Resilient Two-Tier Discovery Architecture**:
  1. **Manifest Declaration**: Include `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />` **and** an explicit `<queries>` block declaring `<intent><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent>`.
  2. **Runtime Query Fallback**: `AppManagerRepository` queries `packageManager.getInstalledApplications(PackageManager.GET_META_DATA)` combined with `packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)`. Even if an environment or custom ROM restricts broad visibility, all launchable user and system apps remain discoverable without crashing.
  3. **APK Inspection without Install**: Users can also browse any `.apk` file on storage via `packageManager.getPackageArchiveInfo(apkPath, 0)` to inspect its icon, package name, version code/name, minSdk/targetSdk, and requested permissions before deciding whether to trigger Android's native `ACTION_VIEW` / `PackageInstaller` intent.

---

### 4. Hardened Local FTP/FTPS Server Architecture

The built-in FTP server (`FtpServerForegroundService`) is designed for predictable LAN reliability and transparent security:

1. **Protocol Distinction in UI & Code**:
   - **Plain FTP (RFC 959)**: Unencrypted command and data channels. Displays a high-visibility Amber/Crimson warning banner: *"Standard FTP transmits credentials and files without encryption over your local Wi-Fi. Only use on trusted home networks, or enable FTPS (TLS) below."*
   - **Explicit FTPS (FTP over TLS)**: Generates an ephemeral self-signed X.509 certificate in Android Keystore so desktop clients (FileZilla, KDE Dolphin, Cyberduck, WinSCP) can negotiate `AUTH TLS` with encrypted control and data channels.
   - **SFTP Note**: Clarifies in the UI that SFTP is SSH-based (supported in OpenFile's Remote Client tab).
2. **Passive Mode (`PASV`) & Configurable Passive Port Range**:
   - Modern desktop and mobile FTP clients default to Passive Mode (`PASV` / `EPSV`).
   - OpenFile configures `DataConnectionConfigurationFactory` with an explicit passive port range (default **`50000–50050`**, user-configurable in Settings) and sets the passive external address to the bound local Wi-Fi IPv4 address so data transfers succeed across Linux, Windows, macOS, and Android clients.
3. **Strict Local Network Interface Binding**:
   - Enumerates `NetworkInterface.getNetworkInterfaces()` to locate the active Wi-Fi (`wlan0`) or local hotspot (`ap0`/`rndis0`) private IPv4 address (`192.168.x.x`, `10.x.x.x`, `172.16-31.x.x`).
   - Binds the `ListenerFactory` strictly to that private `InetAddress` (never `0.0.0.0` when cellular/WAN is active, refusing cellular/public interfaces).
4. **Network Lifecycle, Auto-Shutdown & Process Termination**:
   - Registers a `ConnectivityManager.NetworkCallback` while the service runs. If Wi-Fi disconnects or the network interface changes, the FTP server immediately closes active sockets, stops the listener, and updates the notification.
   - Implements an idle inactivity timer (default 15 minutes, configurable) that automatically stops the server when no client sessions are active.
   - Runs as a `START_NOT_STICKY` Foreground Service (`foregroundServiceType="dataSync"` / local network transfer) with a persistent notification featuring a direct **"Stop Server"** `PendingIntent` action and `onTaskRemoved()` cleanup so the server never restarts silently after process termination.

---

### 5. Direct Android Keystore Credential Vault

- **Zero Secrets in Room or DataStore**: Room tables (`network_profiles`, `cloud_accounts`) never contain plaintext or Base64 passwords, private keys, or OAuth tokens.
- **`KeystoreCredentialVault` Implementation**:
  - Generates a hardware-backed 256-bit `AES/GCM/NoPadding` master key (`alias = "openfile_master_vault_key_v1"`) inside `AndroidKeyStore` with `KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT`.
  - Each credential is encrypted with a fresh 12-byte random IV and 128-bit GCM authentication tag (`[12-byte IV][Ciphertext + 16-byte AuthTag]`) and stored in an app-private (`Context.MODE_PRIVATE`) encrypted credential store file indexed by a UUID `secretAlias`.
  - Room stores only `secretAlias: String`. Deleting a server profile or cloud account immediately purges the corresponding entry from `KeystoreCredentialVault` and overwrites the in-memory `CharArray`/`ByteArray`.

---

### 6. Cross-Provider Operations & Trash Semantics

Because OpenFile spans `LOCAL`, `SAF`, `NETWORK`, and `CLOUD` providers, file operations follow explicit capability rules:

| Operation | Same-Provider (`LOCAL -> LOCAL`) | Cross-Provider or SAF (`SAF <-> LOCAL`, `LOCAL <-> FTP/SFTP/SMB/CLOUD`) |
| :--- | :--- | :--- |
| **Copy** | Streaming NIO `FileChannel` / `InputStream` -> `OutputStream` (64 KB buffer) with progress & conflict check. | Universal streaming bridge: reads `InputStream` from Source Provider, writes `OutputStream` to Destination Provider via `TransferQueueEngine`. |
| **Move (Cut & Paste)** | Fast atomic `File.renameTo()` if on the same mount point; falls back to Copy + verify byte size + Delete source. | Implemented as **Verified Copy + Source Delete**: streams full content to destination, verifies transferred byte count (and optional SHA-256), and **only deletes the source if the destination write succeeds 100%**. If cancelled mid-transfer, the partial destination file is cleaned up and the source remains untouched. |
| **Rename** | `File.renameTo()` in same directory with collision check. | Uses `DocumentsContract.renameDocument()` for SAF, `RNFR`/`RNTO` for FTP, `sftp.rename()` for SFTP, or REST `PATCH` for Cloud. |
| **Trash (Soft Delete)** | Moves item into `.openfile_trash/<uuid>_<filename>` on the same internal storage volume via fast rename and logs original metadata in `trash_items` Room table. | **Explicit Scoped Trash Rules**: <br>1. **SAF Files**: Checks available internal storage space first; if enabled by user, copies stream to `.openfile_trash` then deletes SAF document. If file exceeds free space threshold or user declines copy-to-trash, prompts for **Permanent Deletion**.<br>2. **Network & Cloud**: Never copies remote files across the network into local trash; prompts clearly that remote deletion is immediate (or uses remote cloud trash where supported). |
| **Restore from Trash** | Checks if original parent path/URI still exists and is writable. If conflict exists at original location, prompts *Overwrite* or *Keep Both*. If original SAF folder permission was revoked, allows restoring to any user-chosen folder. | Same safe conflict & fallback directory picker. |

---

### 7. Unified Transfer Queue, Crash Recovery & Checksum Engine

All non-trivial file operations run through a singleton `TransferQueueManager` backed by Coroutines and persisted in Room (`transfer_jobs` table):

- **Capabilities**:
  - **Queue Management**: Run jobs sequentially (to prevent disk/network thrashing) or up to 2 parallel streams, with live byte/file progress, speed (`MB/s`), and ETA.
  - **Pause / Resume**: Supported for multi-file batches (pauses between files on any provider, and mid-file byte-offset resume where `ProviderCapability.RESUME_TRANSFER` is present).
  - **Checksum Verification**: Built-in streaming `MD5` and `SHA-256` calculator. Users can verify file integrity on the File Info screen or toggle *"Verify checksum after copy"* in the Transfer Queue.
  - **Duplicate Finder**: Scans a selected directory or storage volume by grouping files of identical byte size first, then computing a fast 16 KB head/tail hash followed by full SHA-256 only for size-matched candidates.
  - **Crash & Interruption Recovery**: Active transfer jobs record their status (`RUNNING`, `PAUSED`, `INTERRUPTED`) in the `transfer_jobs` Room table. If the OS terminates the app during a large transfer, launching OpenFile surfaces an **"Interrupted Transfer Detected"** banner allowing the user to *Resume/Retry* or *Clean Up Partial Files*.
  - **Battery & Resource Discipline**: Uses background dispatchers only while user-initiated operations or the explicit Foreground FTP Server are active—zero periodic background polling, zero wake-locks when idle.

---

## 4. Database Schema (Room) & DataStore Preferences

### Room Entities (`OpenFileDatabase`)

1. **`bookmarks` (`BookmarkEntity`)**:
   - `id: Long` (PK), `title: String`, `providerType: String`, `uriOrPath: String` (Unique), `isDirectory: Boolean`, `mimeType: String`, `capabilitiesMask: Long`, `createdAt: Long`.
2. **`recent_files` (`RecentFileEntity`)**:
   - `uriOrPath: String` (PK), `providerType: String`, `displayName: String`, `mimeType: String`, `sizeBytes: Long`, `lastAccessedAt: Long`, `lastModifiedAt: Long`.
3. **`network_profiles` (`NetworkProfileEntity`)**:
   - `id: Long` (PK), `displayName: String`, `protocol: String` (`FTP`, `FTPS`, `SFTP`, `SMB`), `host: String`, `port: Int`, `username: String`, `secretAlias: String?` (reference into `KeystoreCredentialVault`), `remoteInitialPath: String`, `expectedHostKeyFingerprint: String?` (SHA-256 SSH host key), `isAnonymous: Boolean`, `usePassiveMode: Boolean`.
4. **`cloud_accounts` (`CloudAccountEntity`)**:
   - `id: Long` (PK), `provider: String` (`GOOGLE_DRIVE`, `ONEDRIVE`, `DROPBOX`), `accountEmail: String`, `displayName: String`, `tokenSecretAlias: String` (reference into `KeystoreCredentialVault`), `tokenExpiresAt: Long`.
5. **`trash_items` (`TrashItemEntity`)**:
   - `id: Long` (PK), `originalProviderType: String`, `originalUriOrPath: String`, `originalParentUriOrPath: String`, `trashLocalFilePath: String`, `displayName: String`, `mimeType: String`, `sizeBytes: Long`, `sha256Checksum: String?`, `deletedAt: Long`.
6. **`transfer_jobs` (`TransferJobEntity` — Crash Recovery & History)**:
   - `jobId: String` (PK, UUID), `operationType: String` (`COPY`, `MOVE`, `EXTRACT`, `COMPRESS`, `UPLOAD`, `DOWNLOAD`), `sourceProvider: String`, `sourceUriOrPath: String`, `destProvider: String`, `destUriOrPath: String`, `totalBytes: Long`, `transferredBytes: Long`, `status: String` (`QUEUED`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `INTERRUPTED`), `errorMessage: String?`, `createdAt: Long`, `updatedAt: Long`.
7. **`search_history` (`SearchQueryEntity`)**:
   - `query: String` (PK), `searchedAt: Long`.

---

## 5. Phased Release Milestones & Testing Strategy

To eliminate big-bang integration risk while delivering the complete OpenFile architecture, implementation is structured into three sequential, verified milestones:

### Milestone 1: Release 1.0 — Core File Manager, Archives, Analyzer & FTP Suite (Primary Build)
- **Foundation & UI Shell**: Adaptive Material 3 Navigation (`NavigationDrawer` / `NavigationRail` across all 17 destinations), Light/Dark/AMOLED/Dynamic Color themes, local Google Fonts (*Plus Jakarta Sans*, *Inter*, *JetBrains Mono*), Room DB, DataStore Preferences, and `KeystoreCredentialVault`.
- **Capability-Aware Local & SAF Engine**: `LocalFileSystemProvider`, `SafDocumentProvider` (with SAF permission recovery), and `MediaStoreProvider`.
- **Full File Browser & Transfer Queue**: Breadcrumbs, List/Grid toggle, multi-sort, multi-selection, Conflict Resolution modal (*Overwrite*, *Keep Both*, *Skip*), Unified Transfer Queue with pause/resume/cancel, SHA-256/MD5 checksum verifier, Undo snackbar, Bookmarks, Recent Files, and Trash with restore/rollback.
- **Built-in Previews & Media Browsers**: Images (zoomable + EXIF), Videos, Audio player bar, Documents/Code viewer, and PDF previewer.
- **Archives (ZIP, TAR, GZ, XZ)**: Browse, extract (with Zip-Slip and zip-bomb guards), and create archives.
- **Storage Analyzer & Duplicate Finder**: Async cancellable directory treemap/ring breakdown, largest files, and SHA-256 duplicate detector.
- **Applications & APK Manager**: Resilient installed package discovery (`QUERY_ALL_PACKAGES` + `<queries>` fallback), APK archive inspector, share, and system installer intent.
- **FTP/FTPS Suite**: Hardened Foreground Service **Local Wi-Fi FTP/FTPS Server** (passive port range, strict LAN IP binding, network callback auto-stop, live logs) + **FTP/FTPS Client** (`commons-net`).

### Milestone 2: Release 1.1 — Advanced Network Protocols & 7Z Extraction
- **SFTP Client**: SSH2 password and private-key authentication with interactive `known_hosts` SHA-256 host-key fingerprint verification.
- **SMB2/3 Client**: LAN share connection and streaming file transfers via `smbj`.
- **7Z Archive Extraction**: Read-only browsing and extraction of `.7z` archives via `SevenZFile` + `org.tukaani:xz`.

### Milestone 3: Release 1.2 — Extensible Cloud OAuth2 Providers
- **OAuth 2.0 PKCE & Token Vault**: Configurable client credential flow with automatic token refresh and `KeystoreCredentialVault` storage.
- **Cloud Providers**: Modular `CloudFileSystemProvider` implementations for **Google Drive REST API v3**, **Microsoft Graph (OneDrive)**, and **Dropbox API v2** integrated into the shared `TransferQueueEngine`.

---

### Automated Testing Matrix

| Test Layer | Scope & Verification Targets | Execution Tooling |
| :--- | :--- | :--- |
| **Unit Tests (`test/`)** | • `FileSortingAndFilterTest`: Name, size, date, extension, and hidden-file comparators.<br>• `ArchiveSecurityValidatorTest`: Verifies `../../etc/passwd` Zip-Slip rejection and zip-bomb threshold detection.<br>• `ConflictResolutionEngineTest`: Verifies auto-rename (`report (1).pdf`), skip, and overwrite semantics.<br>• `ChecksumAndDuplicateTest`: Verifies streaming SHA-256/MD5 and duplicate grouping.<br>• `CapabilityMatrixTest`: Ensures providers advertise accurate capabilities and reject unsupported mutations. | JUnit 4 + Kotlin Coroutines Test (`gradle :app:testDebugUnitTest`) |
| **Integration Tests (`test/` with Robolectric & TempFS)** | • `LocalFileTransferIntegrationTest`: Copy, move, cross-folder conflict, undo, and `.openfile_trash` restore with rollback on simulated failure.<br>• `ArchiveRoundTripTest`: Create `.zip` and `.tar.gz` from temp directory, browse entries, and extract back with byte-for-byte SHA-256 verification.<br>• `KeystoreVaultTest` & `RoomMigrationTest`: Verifies DAO flows for Bookmarks, Recents, Network Profiles, and Interrupted Transfer recovery. | Robolectric + JUnit 4 (`gradle :app:testDebugUnitTest`) |
| **UI & CUJ Tests (`ExampleRobolectricTest.kt`)** | • Navigation across Home, Internal Storage, Archives, Storage Analysis, Network/FTP Server, Applications, and Settings.<br>• Multi-selection activation, New Folder dialog, Search filtering, and Theme mode switching (Light/Dark/AMOLED). | Compose UI Test (`createComposeRule` under Robolectric) |
