package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricKeyManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "VaultZero_Biometric_Key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "vaultzero_biometric_secure_prefs"
    private const val PREF_KEY_ENCRYPTED_VMK = "encrypted_vault_master_key"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    /**
     * Store the Vault Master Key (VMK hex string) encrypted using the hardware-backed Keystore
     */
    fun storeVmk(context: Context, vmkHex: String): Boolean {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val cipherText = cipher.doFinal(vmkHex.toByteArray(Charsets.UTF_8))

            val buffer = ByteBuffer.allocate(iv.size + cipherText.size)
            buffer.put(iv)
            buffer.put(cipherText)

            val encoded = Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_KEY_ENCRYPTED_VMK, encoded).commit()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retrieve and decrypt the Vault Master Key using the Keystore key
     */
    fun getDecryptedVmk(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encoded = prefs.getString(PREF_KEY_ENCRYPTED_VMK, null) ?: return null

            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 12) return null

            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)

            val cipherTextSize = combined.size - 12
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, 12, cipherText, 0, cipherTextSize)

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return null

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun hasBiometricKey(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(PREF_KEY_ENCRYPTED_VMK, null).isNullOrEmpty()
    }

    fun clearBiometricKey(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(PREF_KEY_ENCRYPTED_VMK).commit()

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
