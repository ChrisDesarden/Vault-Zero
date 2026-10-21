# VaultZero — Zero-Knowledge Offline-First Cryptographic Vault for Android

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Encryption](https://img.shields.io/badge/Encryption-AES--256--GCM%20%2B%20PBKDF2%20(310k)-10B981)](#cryptographic-architecture)
[![Network Permissions](https://img.shields.io/badge/Permissions-Zero%20Internet%20%2F%20Air--Gapped-F59E0B)](#zero-knowledge--air-gapped-guarantees)

**VaultZero** is a zero-knowledge, air-gapped password, TOTP authenticator, payment card, SSH/API secret, and cryptographic seed vault for Android. Built with 100% Kotlin and Jetpack Compose, VaultZero operates strictly offline with **zero network permissions** (`INTERNET` permission is completely omitted from `AndroidManifest.xml`), ensuring your secrets never leave your device unencrypted.

---

## 📲 Download & Easy APK Installation

Get **VaultZero** running on your Android device in under a minute. The compiled, signed APK binary is included directly in this repository under [`/releases`](releases/README.md) for immediate one-click download on GitHub.

### Direct APK Download & Release Pages

| Release Channel | Version | Size | Minimum Android | Direct Link |
| :--- | :--- | :--- | :--- | :--- |
| **Latest Installable APK** | `v1.0.0` | `23.28 MB` | Android 7.0+ (API 24+) | [**⬇️ Download VaultZero-v1.0.0.apk**](releases/VaultZero-v1.0.0.apk?raw=true) |
| **Releases & Download Center** | `v1.0.0` | — | Android 7.0+ (API 24+) | [**📦 Open Releases & Download Page**](releases/README.md) |
| **v1.0.0 Release Notes & Spec** | `v1.0.0` | — | Android 7.0+ (API 24+) | [**📋 View v1.0.0 Release Notes**](releases/v1.0.0.md) |
| **SHA-256 Checksum File** | `v1.0.0` | `90 B` | All Platforms | [**🔐 Download VaultZero-v1.0.0.apk.sha256**](releases/VaultZero-v1.0.0.apk.sha256?raw=true) |
| **Web Download Portal (HTML)** | `v1.0.0` | — | Browser / GitHub Pages | [**🌐 View Web Download Page (`docs/index.html`)**](docs/index.html) |

### Step-by-Step Installation Guide (Android)

1. **Download the APK**: Tap [**⬇️ Download VaultZero-v1.0.0.apk**](releases/VaultZero-v1.0.0.apk?raw=true) above directly on your Android phone or tablet (or open the [Releases Center](releases/README.md) and tap **Download raw file**).
2. **Allow Installation from Browser/Files**:
   - Open the downloaded `VaultZero-v1.0.0.apk` file from your notification shade or **Files / Downloads** app.
   - If prompted with *"For your security, your phone currently isn't allowed to install unknown apps from this source"*, tap **Settings** and toggle on **Allow from this source**.
3. **Install & Launch**: Tap **Install**, then tap **Open** to launch **VaultZero**.
4. **Initialize Your Clean Vault**:
   - Your vault starts completely empty and ready for your credentials.
   - Choose a strong **Master Password** (minimum 8 characters; evaluated by the real-time entropy engine).
   - Write down your **12-Word SHA-256 Checksummed Recovery Phrase** and store it in a secure offline location.

### Verify APK Integrity (SHA-256 Checksum)

**Official `v1.0.0` SHA-256 Digest:**
```text
107ccffb4acf0cf8f6bf59d1499fb65a98c6cbc7b6d8a906c7d439facf627a8d  VaultZero-v1.0.0.apk
```

Before installing on high-security devices, you can verify the APK signature and SHA-256 checksum:

```bash
# Verify SHA-256 checksum (Linux / macOS)
shasum -a 256 VaultZero-v1.0.0.apk

# Or verify automatically against the checksum file in /releases:
cd releases && shasum -a 256 -c VaultZero-v1.0.0.apk.sha256

# Verify SHA-256 checksum (Windows PowerShell)
Get-FileHash .\VaultZero-v1.0.0.apk -Algorithm SHA256

# Verify APK signing certificate using Android SDK apksigner
apksigner verify --print-certs VaultZero-v1.0.0.apk
```

---

## 🛡️ Key Features

- **5 Specialized Secret Vault Types**:
  - **Logins & Passwords**: Website/app credentials, username, password, live RFC 6238 **TOTP 2FA code generator** with 30-second countdown ring, notes, and custom fields.
  - **Payment Cards**: Cardholder name, masked card number, brand, expiration, CVV, and billing PIN.
  - **Secure Notes**: Markdown-friendly encrypted notes for sensitive documents and recovery codes.
  - **Crypto Wallets & Seed Phrases**: 12/24-word mnemonic phrase grid viewer, network selector, public address, and private key storage.
  - **API & Developer Secrets**: Environment (`PROD`, `STAGING`, `DEV`), Bearer/API tokens, SSH keys, endpoint URLs, and expiration alerts.
- **Full Record Metadata Encryption (`_vz_meta_v2`)**:
  - Not just secret payloads—record **titles**, **categories**, and **tags** are serialized inside the AES-256-GCM encrypted envelope and redacted (`[REDACTED]`) in raw SQLite columns and backup JSON envelopes.
- **Per-Record GCM AAD Row Binding**:
  - Every record's AES-256-GCM authentication tag is cryptographically bound to its `id`, `type`, `createdAt`, `updatedAt`, and `isFavorite` metadata via Additional Authenticated Data (AAD), preventing cross-row ciphertext swapping in SQLite or backup files.
- **Dual-Envelope Key Wrapping & Instant Key Rotation**:
  - A random 256-bit **Vault Master Key (VMK)** encrypts vault records and is wrapped independently by your **Master Password KEK** and your **12-Word Recovery Phrase KEK** (`PBKDF2-HMAC-SHA256`, 310,000 iterations).
- **Hardware-Backed Biometric Unlock**:
  - Uses Android Keystore (`AES/GCM/NoPadding`, StrongBox hardware-backed where supported) paired with `BiometricPrompt.CryptoObject` for hardware-authenticated biometric unlocking.
- **Interactive Security Audit & Entropy Dashboard**:
  - Real-time calculation of Shannon + character-pool entropy (in bits), weak/reused/compromised password detection, aging secret alerts (>180 days), and 2FA coverage score.
- **Built-In Cryptographic Toolkit**:
  - High-entropy random **Password Generator** and **EFF Diceware Passphrase Generator** powered by `java.security.SecureRandom`.
- **Self-Contained Encrypted `.vzero` Backups**:
  - Export and restore portable, tamper-evident `.vzero` encrypted vault containers using Android's zero-permission Storage Access Framework (SAF), with full Master Password or 12-Word Recovery Phrase cross-device restoration.
- **Hardened Runtime Hygiene**:
  - `FLAG_SECURE` screenshot/screen-recording blocking, Android 13+ `ClipData.EXTRA_IS_SENSITIVE` clipboard masking with a 30-second auto-scrub timer, background auto-lock timeout, and Optional **Panic Wipe** after 5 consecutive failed unlock attempts.

---

## 🔐 Cryptographic Architecture

| Component | Specification |
| :--- | :--- |
| **Data Encryption Cipher** | `AES-256-GCM` (`AES/GCM/NoPadding`) with 96-bit `SecureRandom` IV and 128-bit authentication tag |
| **Key Derivation Function (KDF)** | `PBKDF2WithHmacSHA256` with **310,000 iterations** (OWASP 2023+ recommendation) and 128-bit random salt |
| **Key Hierarchy** | 256-bit random **Vault Master Key (VMK)** wrapped independently by Master Password KEK and Recovery Phrase KEK |
| **Record AAD Binding** | `VaultZero-Record-v2\|<id>\|<type>\|<createdAt>\|<updatedAt>\|<isFavorite>` |
| **Recovery Phrase Format** | 12-word mnemonic from curated 256-word cryptographic dictionary with **SHA-256 checksum** embedded in the 12th word |
| **Biometric Hardware Key** | Android Keystore `AES-256-GCM` key (`setUserAuthenticationRequired`, StrongBox preferred) via `BiometricPrompt.CryptoObject` |

---

## 🔒 Zero-Knowledge & Air-Gapped Guarantees

- **Zero Network Access**: `AndroidManifest.xml` requests **no `android.permission.INTERNET`**. The app cannot transmit telemetry, analytics, or vault data to any remote server.
- **Disabled OS Cloud Auto-Backup**: Configured with `android:allowBackup="false"` alongside strict `data_extraction_rules.xml` and `backup_rules.xml` excluding SQLite databases and shared preferences from unencrypted OS device-to-device transfers.
- **RAM Zeroization**: Derived keys and decryptedVMK byte arrays are explicitly overwritten with `0x00` (`Arrays.fill(key, 0.toByte())`) immediately when the vault locks.

---

## 🛠️ Building from Source & Running Tests

### Prerequisites
- **Android Studio** Ladybug (2024.2.1) or newer
- **JDK 11+**
- **Android SDK** API 36

### Build Debug APK via Gradle
```bash
gradle :app:assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit & Robolectric Security Suite
VaultZero includes a comprehensive JUnit 4 + Robolectric test suite verifying AES-256-GCM tamper detection, PBKDF2 key derivation, SHA-256 recovery mnemonic checksum validation, SQLite metadata redaction, AAD row-swap rejection, and portable `.vzero` backup recovery:

```bash
gradle :app:testDebugUnitTest
```
