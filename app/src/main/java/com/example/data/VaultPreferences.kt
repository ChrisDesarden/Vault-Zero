package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.crypto.VaultCrypto

class VaultPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vaultzero_secure_prefs", Context.MODE_PRIVATE)

    var isInitialized: Boolean
        get() = prefs.getBoolean("is_initialized", false)
        set(value) {
            prefs.edit().putBoolean("is_initialized", value).commit()
        }

    var masterPasswordHash: String
        get() = prefs.getString("master_password_hash", "") ?: ""
        set(value) {
            prefs.edit().putString("master_password_hash", value).commit()
        }

    var saltBase64: String
        get() = prefs.getString("master_salt_b64", "") ?: ""
        set(value) {
            prefs.edit().putString("master_salt_b64", value).commit()
        }

    var recoverySaltBase64: String
        get() = prefs.getString("recovery_salt_b64", "") ?: ""
        set(value) {
            prefs.edit().putString("recovery_salt_b64", value).commit()
        }

    var kdfIterations: Int
        get() = prefs.getInt("kdf_iterations", VaultCrypto.DEFAULT_ITERATIONS)
        set(value) {
            prefs.edit().putInt("kdf_iterations", value).commit()
        }

    var encryptedVmkByPassword: String
        get() = prefs.getString("encrypted_vmk_by_password", "") ?: ""
        set(value) {
            prefs.edit().putString("encrypted_vmk_by_password", value).commit()
        }

    var encryptedVmkByRecovery: String
        get() = prefs.getString("encrypted_vmk_by_recovery", "") ?: ""
        set(value) {
            prefs.edit().putString("encrypted_vmk_by_recovery", value).commit()
        }

    var encryptedRecoveryPhrase: String
        get() = prefs.getString("encrypted_recovery_phrase", "") ?: ""
        set(value) {
            prefs.edit()
                .putString("encrypted_recovery_phrase", value)
                .remove("recovery_phrase")
                .commit()
        }

    /**
     * Read-only access to legacy plaintext recovery_phrase for one-time encrypted migration.
     */
    val legacyPlaintextRecoveryPhrase: String
        get() = prefs.getString("recovery_phrase", "") ?: ""

    fun clearLegacyPlaintextRecoveryPhrase() {
        prefs.edit().remove("recovery_phrase").commit()
    }

    var recoveryPhraseHash: String
        get() = prefs.getString("recovery_phrase_hash", "") ?: ""
        set(value) {
            prefs.edit().putString("recovery_phrase_hash", value).commit()
        }

    var keyFingerprint: String
        get() = prefs.getString("key_fingerprint", "VZ-0000-0000-0000-0000") ?: "VZ-0000-0000-0000-0000"
        set(value) {
            prefs.edit().putString("key_fingerprint", value).commit()
        }

    var autoLockSeconds: Int
        get() = prefs.getInt("auto_lock_seconds", 60)
        set(value) {
            prefs.edit().putInt("auto_lock_seconds", value).commit()
        }

    var clipboardTimeoutSeconds: Int
        get() = prefs.getInt("clipboard_timeout_seconds", 30)
        set(value) {
            prefs.edit().putInt("clipboard_timeout_seconds", value).commit()
        }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean("is_biometric_enabled", false)
        set(value) {
            prefs.edit().putBoolean("is_biometric_enabled", value).commit()
        }

    var isDarkTheme: Boolean
        get() = prefs.getBoolean("is_dark_theme", true)
        set(value) {
            prefs.edit().putBoolean("is_dark_theme", value).commit()
        }

    fun saveEnvelopeCredentials(
        passwordHash: String,
        saltBase64: String,
        recoverySaltBase64: String,
        encryptedVmkByPassword: String,
        encryptedVmkByRecovery: String,
        encryptedRecoveryPhrase: String,
        recoveryPhraseHash: String,
        keyFingerprint: String,
        kdfIterations: Int = VaultCrypto.DEFAULT_ITERATIONS
    ) {
        prefs.edit()
            .putString("master_password_hash", passwordHash)
            .putString("master_salt_b64", saltBase64)
            .putString("recovery_salt_b64", recoverySaltBase64)
            .putInt("kdf_iterations", kdfIterations)
            .putString("encrypted_vmk_by_password", encryptedVmkByPassword)
            .putString("encrypted_vmk_by_recovery", encryptedVmkByRecovery)
            .putString("encrypted_recovery_phrase", encryptedRecoveryPhrase)
            .remove("recovery_phrase")
            .putString("recovery_phrase_hash", recoveryPhraseHash)
            .putString("key_fingerprint", keyFingerprint)
            .putBoolean("is_initialized", true)
            .commit()
    }

    fun resetAll() {
        prefs.edit().clear().commit()
    }
}
