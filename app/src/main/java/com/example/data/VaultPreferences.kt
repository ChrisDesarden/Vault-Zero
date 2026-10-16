package com.example.data

import android.content.Context
import android.content.SharedPreferences

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

    var recoveryPhrase: String
        get() = prefs.getString("recovery_phrase", "") ?: ""
        set(value) {
            prefs.edit().putString("recovery_phrase", value).commit()
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
        recoveryPhrase: String,
        recoveryPhraseHash: String,
        keyFingerprint: String
    ) {
        prefs.edit()
            .putString("master_password_hash", passwordHash)
            .putString("master_salt_b64", saltBase64)
            .putString("recovery_salt_b64", recoverySaltBase64)
            .putString("encrypted_vmk_by_password", encryptedVmkByPassword)
            .putString("encrypted_vmk_by_recovery", encryptedVmkByRecovery)
            .putString("recovery_phrase", recoveryPhrase)
            .putString("recovery_phrase_hash", recoveryPhraseHash)
            .putString("key_fingerprint", keyFingerprint)
            .putBoolean("is_initialized", true)
            .commit()
    }

    fun resetAll() {
        prefs.edit().clear().commit()
    }
}
