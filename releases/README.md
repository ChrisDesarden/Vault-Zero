# 📦 VaultZero — Official APK Releases & Verification Center

Welcome to the official **VaultZero** release documentation directory. To keep the Git source tree 100% clean of binary blobs for **F-Droid** and **IzzyOnDroid** compliance, compiled `.apk` packages are distributed exclusively through [**GitHub Releases**](https://github.com/ChrisDesarden/Vault-Zero/releases).

---

## ⬇️ Latest Stable Release: `v1.0.0`

| Asset / Page | File / Link | Size | Description |
| :--- | :--- | :--- | :--- |
| **🚀 Direct APK Download (GitHub Releases)** | [**⬇️ Download `VaultZero-v1.0.0.apk`**](https://github.com/ChrisDesarden/Vault-Zero/releases/latest/download/VaultZero-v1.0.0.apk) | `2.04 MB` (`2,137,846 bytes`) | Signed, R8-shrunk installable APK for Android 7.0+ (`API 24+`) |
| **📦 All Release Assets (`v1.0.0`)** | [**Open GitHub Releases (`v1.0.0`)**](https://github.com/ChrisDesarden/Vault-Zero/releases/latest) | — | Official GitHub Release page with APK and checksum assets |
| **🔐 SHA-256 Checksum File** | [**`VaultZero-v1.0.0.apk.sha256`**](VaultZero-v1.0.0.apk.sha256) | `87 B` | Official SHA-256 cryptographic digest file |
| **📋 v1.0.0 Release Notes** | [**View `v1.0.0` Release Notes (`v1.0.0.md`)**](v1.0.0.md) | `2.5 KB` | Full changelog, cryptographic parameters, and verification guide |
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

1. Tap [**⬇️ Download `VaultZero-v1.0.0.apk`**](https://github.com/ChrisDesarden/Vault-Zero/releases/latest/download/VaultZero-v1.0.0.apk) above.
2. Open the downloaded `VaultZero-v1.0.0.apk` file on your Android device.
3. If prompted by Android, enable **Allow from this source** for your browser or file manager.
4. Tap **Install** and launch **VaultZero**.
5. Set your **Master Password** and write down your **12-Word SHA-256 Checksummed Recovery Phrase**. Your vault starts completely clean with zero preloaded example records.
