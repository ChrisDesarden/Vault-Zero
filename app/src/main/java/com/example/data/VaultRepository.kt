package com.example.data

import android.util.Base64
import com.example.crypto.VaultCrypto
import com.example.model.ApiSecretPayload
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.ItemPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class VaultRepository(
    private val dao: VaultDao,
    private val prefs: VaultPreferences
) {
    @Volatile
    private var activeSecretKey: SecretKey? = null

    val isInitialized: Boolean
        get() = prefs.isInitialized

    val isUnlocked: Boolean
        get() = activeSecretKey != null

    val keyFingerprint: String
        get() = prefs.keyFingerprint

    val recoveryPhrase: String
        get() = prefs.recoveryPhrase

    val autoLockSeconds: Int
        get() = prefs.autoLockSeconds

    fun setAutoLockSeconds(seconds: Int) {
        prefs.autoLockSeconds = seconds
    }

    val clipboardTimeoutSeconds: Int
        get() = prefs.clipboardTimeoutSeconds

    fun setClipboardTimeoutSeconds(seconds: Int) {
        prefs.clipboardTimeoutSeconds = seconds
    }

    fun isDarkTheme(): Boolean = prefs.isDarkTheme

    fun setDarkTheme(enabled: Boolean) {
        prefs.isDarkTheme = enabled
    }

    fun isBiometricEnabled(): Boolean = prefs.isBiometricEnabled

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.isBiometricEnabled = enabled
    }

    /**
     * Initializes the Vault with a Master Password and creates a recovery kit using envelope encryption.
     * Returns Pair(recoveryPhrase, vmkHex)
     */
    suspend fun setupMasterPassword(password: String): Pair<String, String> = withContext(Dispatchers.Default) {
        val cleanPassword = password.trim()

        // 1. Generate random 256-bit Vault Master Key (VMK)
        val vmk = VaultCrypto.generateRandomKey()
        val vmkHex = VaultCrypto.bytesToHex(vmk.encoded)

        // 2. Derive Password Key and encrypt VMK
        val passwordSalt = VaultCrypto.generateSalt()
        val passwordSaltB64 = Base64.encodeToString(passwordSalt, Base64.NO_WRAP)
        val passwordKey = VaultCrypto.deriveKey(cleanPassword, passwordSalt)
        val encVmkPassword = VaultCrypto.encrypt(vmkHex, passwordKey)
        val passwordHash = VaultCrypto.sha256(cleanPassword + passwordSaltB64)

        // 3. Generate 12-Word Recovery Phrase, derive Recovery Key, and encrypt VMK
        val recoveryPhrase = VaultCrypto.generateRecoveryPhrase()
        val recoverySalt = VaultCrypto.generateSalt()
        val recoverySaltB64 = Base64.encodeToString(recoverySalt, Base64.NO_WRAP)
        val recoveryKey = VaultCrypto.deriveKeyFromPhrase(recoveryPhrase, recoverySalt)
        val encVmkRecovery = VaultCrypto.encrypt(vmkHex, recoveryKey)
        val recoveryHash = VaultCrypto.sha256(recoveryPhrase.trim().lowercase())

        // 4. Generate key fingerprint from VMK
        val fingerprint = VaultCrypto.generateFingerprint(vmkHex)

        // 5. Atomic persistence of envelope credentials
        prefs.saveEnvelopeCredentials(
            passwordHash = passwordHash,
            saltBase64 = passwordSaltB64,
            recoverySaltBase64 = recoverySaltB64,
            encryptedVmkByPassword = encVmkPassword,
            encryptedVmkByRecovery = encVmkRecovery,
            recoveryPhrase = recoveryPhrase,
            recoveryPhraseHash = recoveryHash,
            keyFingerprint = fingerprint
        )

        activeSecretKey = vmk

        // Seed with realistic demo items (encrypted using VMK)
        seedInitialDemoItems()

        Pair(recoveryPhrase, vmkHex)
    }

    fun getActiveVmkHex(): String? {
        val key = activeSecretKey ?: return null
        return VaultCrypto.bytesToHex(key.encoded)
    }

    /**
     * Unlocks the vault directly with hardware-decrypted VMK (from Biometric Keystore)
     */
    suspend fun unlockWithVmkHex(vmkHex: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val keyBytes = VaultCrypto.hexToBytes(vmkHex)
            if (keyBytes.size == 32) {
                activeSecretKey = SecretKeySpec(keyBytes, "AES")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Unlocks the vault with Master Password by decrypting the Vault Master Key (VMK).
     */
    suspend fun unlock(password: String): Boolean = withContext(Dispatchers.Default) {
        if (!prefs.isInitialized) return@withContext false
        val saltB64 = prefs.saltBase64
        val expectedHash = prefs.masterPasswordHash

        if (saltB64.isEmpty() || expectedHash.isEmpty()) return@withContext false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)

        // Check both exact input and trimmed input for robustness
        val candidates = listOf(password, password.trim()).distinct()
        for (candidate in candidates) {
            if (candidate.isEmpty()) continue
            val computedHash = VaultCrypto.sha256(candidate + saltB64)
            if (computedHash == expectedHash) {
                val passwordKey = VaultCrypto.deriveKey(candidate, salt)

                if (prefs.encryptedVmkByPassword.isNotEmpty()) {
                    try {
                        val vmkHex = VaultCrypto.decrypt(prefs.encryptedVmkByPassword, passwordKey)
                        activeSecretKey = SecretKeySpec(VaultCrypto.hexToBytes(vmkHex), "AES")
                        return@withContext true
                    } catch (e: Exception) {
                        // Attempt next candidate if decryption failed
                    }
                } else {
                    activeSecretKey = passwordKey
                    return@withContext true
                }
            }
        }
        false
    }


    /**
     * Recovers vault using the 12-word recovery phrase, restoring the exact Vault Master Key (VMK).
     */
    suspend fun recoverWithPhrase(phrase: String, newPassword: String): Boolean = withContext(Dispatchers.Default) {
        val cleanPhrase = phrase.trim().lowercase().split("\\s+".toRegex()).joinToString(" ")
        val cleanNewPassword = newPassword.trim()
        val computedHash = VaultCrypto.sha256(cleanPhrase)

        if (computedHash == prefs.recoveryPhraseHash && prefs.encryptedVmkByRecovery.isNotEmpty() && prefs.recoverySaltBase64.isNotEmpty()) {
            try {
                // 1. Recover original VMK using recovery key
                val recoverySalt = Base64.decode(prefs.recoverySaltBase64, Base64.NO_WRAP)
                val recoveryKey = VaultCrypto.deriveKeyFromPhrase(cleanPhrase, recoverySalt)
                val vmkHex = VaultCrypto.decrypt(prefs.encryptedVmkByRecovery, recoveryKey)
                val vmk = SecretKeySpec(VaultCrypto.hexToBytes(vmkHex), "AES")

                // 2. Re-wrap VMK with new password
                val newSalt = VaultCrypto.generateSalt()
                val newSaltB64 = Base64.encodeToString(newSalt, Base64.NO_WRAP)
                val newPasswordKey = VaultCrypto.deriveKey(cleanNewPassword, newSalt)
                val newEncVmkPassword = VaultCrypto.encrypt(vmkHex, newPasswordKey)
                val newPasswordHash = VaultCrypto.sha256(cleanNewPassword + newSaltB64)

                // 3. Atomically update preferences with new password envelope
                prefs.saveEnvelopeCredentials(
                    passwordHash = newPasswordHash,
                    saltBase64 = newSaltB64,
                    recoverySaltBase64 = prefs.recoverySaltBase64,
                    encryptedVmkByPassword = newEncVmkPassword,
                    encryptedVmkByRecovery = prefs.encryptedVmkByRecovery,
                    recoveryPhrase = prefs.recoveryPhrase,
                    recoveryPhraseHash = prefs.recoveryPhraseHash,
                    keyFingerprint = prefs.keyFingerprint
                )

                activeSecretKey = vmk
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    /**
     * Locks vault by purging the active secret key from RAM
     */
    fun lock() {
        activeSecretKey = null
    }

    /**
     * Flow of all decrypted vault items
     */
    fun getVaultItemsFlow(): Flow<List<VaultItem>> {
        return dao.getAllFlow().map { entities ->
            val key = activeSecretKey
            if (key == null) {
                emptyList()
            } else {
                entities.mapNotNull { entity ->
                    try {
                        val decryptedPayloadJson = VaultCrypto.decrypt(entity.encryptedPayload, key)
                        val type = VaultType.valueOf(entity.type)
                        val payload = VaultItem.parsePayload(type, decryptedPayloadJson)
                        val tags = parseTags(entity.tagsJson)

                        VaultItem(
                            id = entity.id,
                            title = entity.title,
                            type = type,
                            category = entity.category,
                            isFavorite = entity.isFavorite,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt,
                            tags = tags,
                            payload = payload
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun saveItem(item: VaultItem) = withContext(Dispatchers.Default) {
        val key = activeSecretKey ?: throw IllegalStateException("Vault is locked")
        val plainPayload = item.payloadToJsonString()
        val encrypted = VaultCrypto.encrypt(plainPayload, key)

        val entity = VaultItemEntity(
            id = item.id.ifEmpty { UUID.randomUUID().toString() },
            title = item.title,
            type = item.type.name,
            category = item.category,
            encryptedPayload = encrypted,
            isFavorite = item.isFavorite,
            createdAt = if (item.createdAt == 0L) System.currentTimeMillis() else item.createdAt,
            updatedAt = System.currentTimeMillis(),
            tagsJson = serializeTags(item.tags)
        )
        dao.insertOrUpdate(entity)
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.updateFavorite(id, isFavorite)
    }

    suspend fun deleteItem(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun clearVault() = withContext(Dispatchers.IO) {
        dao.clearAll()
        prefs.resetAll()
        activeSecretKey = null
    }

    /**
     * Export all items as encrypted JSON backup
     */
    suspend fun exportEncryptedBackup(): String = withContext(Dispatchers.Default) {
        val key = activeSecretKey ?: throw IllegalStateException("Vault is locked")
        val entities = dao.getAllList()
        val backupJson = JSONObject()
        backupJson.put("version", "3.0")
        backupJson.put("format", "VaultZero_Encrypted_Envelope")
        backupJson.put("fingerprint", prefs.keyFingerprint)
        backupJson.put("exportedAt", System.currentTimeMillis())
        backupJson.put("salt", prefs.saltBase64)
        backupJson.put("recoverySalt", prefs.recoverySaltBase64)
        backupJson.put("encryptedVmkByPassword", prefs.encryptedVmkByPassword)
        backupJson.put("encryptedVmkByRecovery", prefs.encryptedVmkByRecovery)
        backupJson.put("verifier", prefs.masterPasswordHash)

        val itemsArray = JSONArray()
        for (entity in entities) {
            val itemObj = JSONObject().apply {
                put("id", entity.id)
                put("title", entity.title)
                put("type", entity.type)
                put("category", entity.category)
                put("encryptedPayload", entity.encryptedPayload)
                put("isFavorite", entity.isFavorite)
                put("createdAt", entity.createdAt)
                put("updatedAt", entity.updatedAt)
                put("tagsJson", entity.tagsJson)
            }
            itemsArray.put(itemObj)
        }
        backupJson.put("items", itemsArray)
        backupJson.toString(2)
    }

    /**
     * Import encrypted backup into vault
     */
    suspend fun importEncryptedBackup(jsonString: String): Int = withContext(Dispatchers.Default) {
        val key = activeSecretKey ?: throw IllegalStateException("Vault is locked")
        val backupJson = JSONObject(jsonString)
        val itemsArray = backupJson.optJSONArray("items") ?: return@withContext 0
        var importedCount = 0
        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.getJSONObject(i)
            val entity = VaultItemEntity(
                id = itemObj.getString("id"),
                title = itemObj.getString("title"),
                type = itemObj.getString("type"),
                category = itemObj.getString("category"),
                encryptedPayload = itemObj.getString("encryptedPayload"),
                isFavorite = itemObj.getBoolean("isFavorite"),
                createdAt = itemObj.getLong("createdAt"),
                updatedAt = itemObj.getLong("updatedAt"),
                tagsJson = itemObj.optString("tagsJson", "[]")
            )
            try {
                VaultCrypto.decrypt(entity.encryptedPayload, key)
                dao.insertOrUpdate(entity)
                importedCount++
            } catch (e: Exception) {
                // Skip records that don't belong to this vault key
            }
        }
        importedCount
    }

    private suspend fun seedInitialDemoItems() {
        val key = activeSecretKey ?: return

        val demoItems = listOf(
            VaultItem(
                id = UUID.randomUUID().toString(),
                title = "GitHub Developer Account",
                type = VaultType.LOGIN,
                category = "Work",
                isFavorite = true,
                tags = listOf("code", "git", "work"),
                payload = LoginPayload(
                    username = "chris.desarden",
                    password = "g!t_V@ultZ3r0#2026_SecureKey",
                    url = "https://github.com/ChrisDesarden/vaultzero",
                    totpSecret = "JBSWY3DPEHPK3PXP", // Real RFC test secret
                    notes = "Primary zero-knowledge code repository access"
                )
            ),
            VaultItem(
                id = UUID.randomUUID().toString(),
                title = "AWS Production Root API",
                type = VaultType.API_SECRET,
                category = "Cloud",
                isFavorite = true,
                tags = listOf("aws", "cloud", "production"),
                payload = ApiSecretPayload(
                    serviceName = "Amazon Web Services",
                    apiKey = "AKIAIOSFODNN7EXAMPLE",
                    secretToken = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                    environment = "Production",
                    scopes = "AdministratorAccess, KMSFullAccess",
                    endpointUrl = "https://aws.amazon.com",
                    notes = "Requires Hardware MFA token for rotation"
                )
            ),
            VaultItem(
                id = UUID.randomUUID().toString(),
                title = "Ethereum Cold Storage Wallet",
                type = VaultType.CRYPTO_WALLET,
                category = "Crypto",
                isFavorite = true,
                tags = listOf("eth", "web3", "cold-storage"),
                payload = CryptoPayload(
                    walletName = "VaultZero Sovereign Wallet",
                    network = "Ethereum (Mainnet)",
                    publicAddress = "0x71C...8494",
                    seedPhrase = "alpha beacon cipher matrix orbit plasma silver timber haven nova quartz zenith",
                    privateKey = "0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d",
                    derivationPath = "m/44'/60'/0'/0/0",
                    notes = "Backed up in stainless steel safe deposit plate"
                )
            ),
            VaultItem(
                id = UUID.randomUUID().toString(),
                title = "Titanium Corporate Visa",
                type = VaultType.CARD,
                category = "Finance",
                isFavorite = false,
                tags = listOf("finance", "corporate", "card"),
                payload = CardPayload(
                    cardholderName = "CHRISTOPHER DESARDEN",
                    cardNumber = "4532 8812 9014 3829",
                    expMonth = "08",
                    expYear = "29",
                    cvv = "842",
                    pin = "9201",
                    brand = "Visa Infinite",
                    notes = "Tap to pay enabled, 100k daily limit"
                )
            ),
            VaultItem(
                id = UUID.randomUUID().toString(),
                title = "Post-Quantum Security Protocol Notes",
                type = VaultType.NOTE,
                category = "Security",
                isFavorite = true,
                tags = listOf("specs", "encryption", "docs"),
                payload = NotePayload(
                    content = "VaultZero Architecture:\n- AES-256-GCM symmetric payload authenticated encryption\n- PBKDF2WithHmacSHA256 (100,000 rounds) key derivation\n- Local hardware Keystore & zero telemetric transmissions\n- Emergency Recovery Mnemonic stored client-side only.",
                    isPinned = true
                )
            )
        )

        for (item in demoItems) {
            val plain = item.payloadToJsonString()
            val enc = VaultCrypto.encrypt(plain, key)
            val entity = VaultItemEntity(
                id = item.id,
                title = item.title,
                type = item.type.name,
                category = item.category,
                encryptedPayload = enc,
                isFavorite = item.isFavorite,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                tagsJson = serializeTags(item.tags)
            )
            dao.insertOrUpdate(entity)
        }
    }

    private fun serializeTags(tags: List<String>): String {
        val array = JSONArray()
        tags.forEach { array.put(it) }
        return array.toString()
    }

    private fun parseTags(json: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }
}
