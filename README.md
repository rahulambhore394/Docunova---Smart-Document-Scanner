<div align="center">

# 📑 Docunova — Smart AI Document Scanner

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![ML Kit](https://img.shields.io/badge/AI-Google%20ML%20Kit-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/ml-kit)
[![Google Drive](https://img.shields.io/badge/Cloud-Google%20Drive%20v3-FBBC04?style=for-the-badge&logo=googledrive&logoColor=white)](https://developers.google.com/drive)
[![Google Sign-In](https://img.shields.io/badge/Auth-Google%20Sign--In-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/identity)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

**An intelligent, offline-capable document scanner and OCR engine for Android.**  
Digitize paper documents, extract formatted text, translate into dozens of languages offline, and synchronize seamlessly across Google Drive and local Room database.

[Key Features](#-key-features) • [Tech Stack](#-technology-stack) • [Architecture](#-architecture) • [Getting Started](#-getting-started) • [Cloud Configuration](#-cloud-configuration) • [Screenshots](#-screenshots)

</div>

---

## 🌟 Key Features

### 📷 Smart Document Scanning
* **Auto Edge Detection & Crop**: Powered by Google Play Services ML Kit Document Scanner API for automatic border detection, perspective straightening, and shadow reduction.
* **Filter Presets**: Crisp black-and-white, color enhancement, and grayscale optimization tailored for text readability.
* **Batch Multi-Page Scanning**: Digitize multi-page contracts, notes, and receipts into a unified document in seconds.

### 🔍 OCR & Smart Text Extraction
* **On-Device Optical Character Recognition (OCR)**: Extract text instantly from camera captures, gallery photos, and multi-page PDFs using Google ML Kit Text Recognition.
* **Multi-Page PDF Parsing**: Fallback extraction engine utilizing `iText7` to pull raw or formatted text from complex digital PDF documents.
* **Interactive Text Formatter**: Built-in editor supporting Bold, Italic, Headings, Bullet Lists, and Numbered Lists with real-time preview.
* **Multi-Format Document Export**: Save digitized text as professional **PDF** files (via `iText7`) or editable Microsoft Word **DOCX** documents (via `Apache POI`).

### 🌐 On-Device Neural Translation
* **Offline Multilingual Translation**: Translate extracted document content into dozens of global languages using Google ML Kit Translate.
* **Automatic Language Identification**: Detects source language automatically with confidence scoring.
* **Side-by-Side Verification**: Split-view editor to review original text and translated text simultaneously before exporting.

### ☁️ Cloud Sync & File Explorer
* **Google Drive API v3**: Direct OAuth2 synchronization with user Google Drive account. View live cloud storage quota, file counts, and download/open files on demand.
* **Google Sign-In Authentication**: Instant, secure authentication with Google OAuth2.
* **Local Offline Caching**: Embedded Android Jetpack **Room DB** (`AppDatabase`) caches recent files, thumbnail paths, and metadata for instant offline access.
* **Advanced File Explorer**: Toggle between Grid and List views, live search query filtering, and sort by Name (A-Z/Z-A), Date (Newest/Oldest), or File Size.

### 🎨 Modern Material 3 UI/UX
* **Skeleton Loaders**: Polished Facebook Shimmer placeholder loading states for cloud data and file grids.
* **Lottie Animations**: Fluid vector animations for dialogs and loading indicators.
* **Adaptive Theming**: Full support for Day/Night system dark mode with high-contrast accessibility tokens.

---

## 🛠 Technology Stack

| Layer | Technology | Description |
| :--- | :--- | :--- |
| **Language** | Kotlin 1.9+ | Modern, safe Android programming with Coroutines & Flow |
| **Target SDK** | Android 14 / 15 (API 35) | Minimum SDK: 28 (Android 9.0 Pie) |
| **Authentication** | Google Play Services Auth | Google Sign-In with OAuth 2.0 |
| **AI / ML** | Google ML Kit | Document Scanner, Text Recognition OCR, Language ID, Translate |
| **Document Processing** | iText7 Core 7.2.5 | High-performance PDF generation, rendering, and parsing |
| **Office Automation** | Apache POI 5.2.3 | Microsoft Word (`.docx`) file synthesis and XMLBeans |
| **Cloud Storage & Sync** | Google Drive REST API v3 | OAuth2 drive client for automated backup and quota tracking |
| **Local Database** | Android Jetpack Room 2.6.1 | SQLite ORM caching recent scans and document metadata |
| **Image Loading** | Bumptech Glide 4.16.0 | Image thumbnail caching and background decoding |
| **UI Components** | Material Components 3 | Shimmer loaders, Lottie 6.0, SwipeRefreshLayout, CircleImageView |

---

## 📐 Architecture & Project Structure

Docunova adheres to modern Android architectural principles featuring modular component separation, ViewBinding, and reactive State Management:

```text
Docunova/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/developer_rahul/docunova/
│   │   │   │   ├── Fragments/
│   │   │   │   │   ├── Home/
│   │   │   │   │   │   ├── HomeFragment.kt              # Dashboard, ML Kit scanner & stats
│   │   │   │   │   │   ├── RecentFileAdapter.kt         # Recent document carousel adapter
│   │   │   │   │   │   ├── TypoProcessingActivity.kt    # OCR, text editor, PDF/Word export
│   │   │   │   │   │   └── TranslationActivity.kt       # ML Kit translation & dual view
│   │   │   │   │   ├── Files/
│   │   │   │   │   │   ├── FilesFragment.kt             # Cloud file explorer (grid/list/sort)
│   │   │   │   │   │   ├── FileAdapter.kt               # RecyclerView list/grid adapter
│   │   │   │   │   │   ├── FileItemGrid.kt              # Grid view representation
│   │   │   │   │   │   └── DriveFileModel.kt            # Google Drive file entity model
│   │   │   │   │   ├── Setting/
│   │   │   │   │   │   └── SettingFragment.kt           # User profile, Drive storage quota, info
│   │   │   │   │   └── SharedViewModel.kt               # Inter-fragment state communication
│   │   │   │   ├── RoomDB/
│   │   │   │   │   ├── AppDatabase.kt               # SQLite database singleton with migrations
│   │   │   │   │   ├── RecentFile.kt                # Document entity table
│   │   │   │   │   ├── RecentFileDao.kt             # Data Access Object queries
│   │   │   │   │   └── StorageUtils.kt              # File size, path & URI utilities
│   │   │   │   ├── DriveServiceHelper.kt            # Google Drive REST API execution helper
│   │   │   │   ├── GoogleDriveClient.kt             # OAuth2 Google Drive client
│   │   │   │   ├── MainActivity.kt                  # Bottom navigation shell
│   │   │   │   ├── SplashActivity.kt                # Animated launch & auto-login check
│   │   │   │   ├── LoginActivity.kt                 # Google Sign-In authentication
│   │   │   │   ├── HelpAndSupportActivity.kt        # In-app support FAQs & contact links
│   │   │   │   ├── Privacy_Policy_Activity.kt       # Privacy policy compliance view
│   │   │   │   ├── SecurityActivity.kt              # Data protection & encryption guide
│   │   │   │   └── ProcessingDialog.kt              # Animated async task progress dialog
│   │   │   ├── res/
│   │   │   │   ├── layout/                          # XML layouts (Material 3 cards, shimmers)
│   │   │   │   ├── drawable/                        # Vector assets, icons & custom gradients
│   │   │   │   ├── values/                          # Design tokens, color palettes & styles
│   │   │   │   └── xml/                             # FileProvider, security & backup rules
│   │   │   └── AndroidManifest.xml
│   │   └── test/                                    # Unit and instrumented tests
│   └── build.gradle.kts                             # App module build configuration
├── gradle/
│   └── libs.versions.toml                           # Centralized dependency version catalog
├── build.gradle.kts                                 # Root build configuration
├── settings.gradle.kts                              # Repository & plugin settings
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio**: Ladybug (2024.2+) or Koala Recommended
* **JDK**: OpenJDK 17 or Oracle JDK 11+
* **Android Device / Emulator**: Running Android 9.0 (API level 28) or higher with Google Play Services installed

### 1. Clone the Repository
```bash
git clone https://github.com/rahulambhore394/Docunova---Smart-Document-Scanner.git
cd "Docunova---Smart-Document-Scanner"
```

### 2. Open with Android Studio
1. Launch **Android Studio**.
2. Select **File > Open...** and navigate to the cloned project folder.
3. Allow Gradle to download dependencies and sync the project.

### 3. Build & Run
Connect an Android device with USB debugging enabled or start an Android Virtual Device (AVD), then click **Run (Shift + F10)** or build via terminal:

```bash
# Generate Debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug
```

---

## 🔑 Cloud Configuration

### Google Cloud (Google Drive API & Google Sign-In)
1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project or select an existing one.
3. Enable the **Google Drive API**.
4. Configure the **OAuth Consent Screen** with scopes:
   - `https://www.googleapis.com/auth/drive.file`
5. Generate an **OAuth 2.0 Client ID** for Android:
   - Package Name: `com.developer_rahul.docunova`
   - Add your debug and release SHA-1 certificate fingerprints (`./gradlew signingReport`).

---

## 🔒 Security & Privacy

* **On-Device Machine Learning**: Text recognition (OCR) and document scanning execute completely on-device using ML Kit without transmitting image data to external third-party servers.
* **Scoped Storage & FileProvider**: All local files utilize Android's `androidx.core.content.FileProvider` (`file_paths2.xml`) with restricted URI grants, ensuring files cannot be accessed by unauthorized applications.
* **Direct Cloud Authorization**: Google Drive sync uses official OAuth2 authorization grants scoped strictly to files created by the application (`DriveScopes.DRIVE_FILE`).

---

## 👨‍💻 Author

**Rahul Ambhore**
* **GitHub**: [@rahulambhore394](https://github.com/rahulambhore394)
* **Email**: [rahulambhore394@gmail.com](mailto:rahulambhore394@gmail.com)

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
