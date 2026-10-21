# 📦 VaultZero — Official APK Releases & Download Center

Welcome to the official **VaultZero** APK distribution directory. The R8-minified, signed Android package (`.apk`, **2.04 MB**) is stored directly in this directory (`releases/VaultZero-v1.0.0.apk`) alongside its portable Base64 text mirror (`releases/VaultZero-v1.0.0.apk.base64`).

---

## ⬇️ Latest Stable Release: `v1.0.0`

| Asset / Page | File / Link | Size | Description |
| :--- | :--- | :--- | :--- |
| **📱 Direct Android APK Binary** | [**⬇️ Download `VaultZero-v1.0.0.apk`**](VaultZero-v1.0.0.apk?raw=true) | `2.04 MB` (`2,137,846 bytes`) | Signed, R8-shrunk installable APK for Android 7.0+ (`API 24+`) |
| **🚀 GitHub Releases Asset** | [**⬇️ Download from GitHub Releases (`v1.0.0`)**](../../releases/latest/download/VaultZero-v1.0.0.apk) | `2.04 MB` | Published automatically by GitHub Actions on push |
| **📄 Base64 Text Mirror** | [**📄 View `VaultZero-v1.0.0.apk.base64`**](VaultZero-v1.0.0.apk.base64) | `2.72 MB` | Text-encoded APK mirror (`base64 -d VaultZero-v1.0.0.apk.base64 > VaultZero-v1.0.0.apk`) |
| **🔐 SHA-256 Checksum File** | [**`VaultZero-v1.0.0.apk.sha256`**](VaultZero-v1.0.0.apk.sha256) | `87 B` | Official SHA-256 cryptographic digest file |
| **📋 v1.0.0 Release Notes** | [**View `v1.0.0` Release Notes (`v1.0.0.md`)**](v1.0.0.md) | `2.8 KB` | Full changelog, cryptographic parameters, and verification guide |
| **🌐 Web Download Portal** | [**Open HTML Download Page (`../docs/index.html`)**](../docs/index.html) | `6.1 KB` | Standalone HTML download portal for GitHub Pages / browsers |

---

## 🔐 Cryptographic Verification (`v1.0.0`)

Always verify the integrity of your downloaded APK before installing on a production device:

- **File Name:** `VaultZero-v1.0.0.apk`
- **Package ID (`applicationId`):** `com.aistudio.vaultzero.secvlt`
- **Version:** `1.0` (`versionCode = 1`)
- **Minimum SDK:** Android 7.0 Nougat (`API 24`)
- **Target / Compile SDK:** Android 16 (`API 36`)
- **Network Permissions:** **NONE** (`INTERNET` and `ACCESS_NETWORK_STATE` strictly omitted)
- **SHA-256 Checksum:**
  ```text
  7918afaeb8f7007d9503f665110ea83d084397e704c082eb35e914d0c2002270  VaultZero-v1.0.0.apk
  ```
- **APK Signing Certificate SHA-256 Digest:**
  ```text
  8ca7a654beccba6c7d329293763b9a2c47c3a3a81f7597220c4b37159b957c5d
  ```

### Verify via Terminal

```bash
# macOS / Linux
shasum -a 256 -c VaultZero-v1.0.0.apk.sha256

# Windows PowerShell
Get-FileHash .\VaultZero-v1.0.0.apk -Algorithm SHA256

# Verify Android V2/V3 APK Signature
apksigner verify --print-certs VaultZero-v1.0.0.apk
```

---

## 📲 How to Install on Android

1. Tap [**⬇️ Download `VaultZero-v1.0.0.apk`**](VaultZero-v1.0.0.apk?raw=true) above (or click the **Download raw file** button if viewing `VaultZero-v1.0.0.apk` in the GitHub file viewer).
2. Open the downloaded `VaultZero-v1.0.0.apk` file on your Android device.
3. If prompted by Android, enable **Allow from this source** for your browser or file manager.
4. Tap **Install** and launch **VaultZero**.
5. Set your **Master Password** and write down your **12-Word SHA-256 Checksummed Recovery Phrase**. Your vault starts completely clean with zero preloaded example records.
