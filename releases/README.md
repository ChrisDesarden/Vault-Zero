# 📦 VaultZero — Official APK Releases & Download Center

Welcome to the official **VaultZero** APK distribution directory. The compiled, signed Android package (`.apk`) is stored directly in this directory so you can download and install it immediately from GitHub on any Android 7.0+ device.

---

## ⬇️ Latest Release: `v1.0.0` (Production Ready)

| Package Property | Value |
| :--- | :--- |
| **File Name** | [`VaultZero-v1.0.0.apk`](VaultZero-v1.0.0.apk?raw=true) |
| **Application ID** | `com.aistudio.vaultzero.secvlt` |
| **Version** | `1.0.0` (`versionCode = 1`) |
| **File Size** | `23.28 MB` (`24,407,112 bytes`) |
| **Minimum Android Version** | Android 7.0 Nougat (`API 24+`) |
| **Target Android SDK** | Android 16 (`API 36`) |
| **Network Permissions** | **None (`ZERO INTERNET PERMISSIONS`)** — 100% Air-Gapped Offline |
| **SHA-256 Checksum** | `107ccffb4acf0cf8f6bf59d1499fb65a98c6cbc7b6d8a906c7d439facf627a8d` |

### 🚀 Direct Download Links

- **[⬇️ Download `VaultZero-v1.0.0.apk` (Direct Raw Binary — 23.28 MB)](VaultZero-v1.0.0.apk?raw=true)**
- **[📄 View APK File Page on GitHub](VaultZero-v1.0.0.apk)**
- **[🔐 Download SHA-256 Checksum File (`VaultZero-v1.0.0.apk.sha256`)](VaultZero-v1.0.0.apk.sha256?raw=true)**
- **[📋 Read Full v1.0.0 Release Notes & Security Audit](v1.0.0.md)**

---

## 📲 How to Install on Android

1. **Tap the Direct Download Link**:
   - On your Android phone or tablet, tap **[⬇️ Download `VaultZero-v1.0.0.apk`](VaultZero-v1.0.0.apk?raw=true)**.
   - *(Tip: If viewing the file page on GitHub Mobile/Desktop, tap the **"Download raw file"** button or three-dots menu `⋯` → **Download**).*
2. **Open the Downloaded APK**:
   - Tap the completed download notification or open **`VaultZero-v1.0.0.apk`** in your device's **Files** / **Downloads** app.
3. **Allow Unknown App Installation (First-Time Sideload)**:
   - If Android displays *"For your security, your phone currently isn't allowed to install unknown apps from this source"*, tap **Settings** and toggle on **Allow from this source**.
4. **Install & Launch**:
   - Tap **Install**, then tap **Open**.
   - Your new vault starts completely empty and ready for your own entries—set your **Master Password** and write down your **12-Word SHA-256 Checksummed Recovery Phrase**.

---

## 🔍 Cryptographic Integrity Verification

Always verify the SHA-256 hash of downloaded security software before installing on high-assurance devices.

### Expected SHA-256 Digest
```text
107ccffb4acf0cf8f6bf59d1499fb65a98c6cbc7b6d8a906c7d439facf627a8d  VaultZero-v1.0.0.apk
```

### Verify via Terminal

**Linux / macOS:**
```bash
shasum -a 256 -c VaultZero-v1.0.0.apk.sha256
# Or manually:
shasum -a 256 VaultZero-v1.0.0.apk
```

**Windows (PowerShell):**
```powershell
Get-FileHash .\VaultZero-v1.0.0.apk -Algorithm SHA256
```

**Verify Zero Network Permissions (`aapt2` / `apkanalyzer`):**
```bash
apkanalyzer manifest permissions VaultZero-v1.0.0.apk
```
Expected permissions output:
- `android.permission.USE_BIOMETRIC`
- `android.permission.USE_FINGERPRINT`
- `android.permission.VIBRATE`
*(Notice `android.permission.INTERNET` is completely absent).*

---

## 🗂️ Release Archive

| Version | Release Date | APK Binary | Checksum | Release Notes |
| :--- | :--- | :--- | :--- | :--- |
| **`v1.0.0`** | 2026-10-20 | [`VaultZero-v1.0.0.apk`](VaultZero-v1.0.0.apk?raw=true) | [`SHA-256`](VaultZero-v1.0.0.apk.sha256?raw=true) | [`v1.0.0.md`](v1.0.0.md) |

---

[⬅️ Back to Main Repository README](../README.md)
