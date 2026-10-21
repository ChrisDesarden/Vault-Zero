package com.example.data

import android.os.SystemClock
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

    @Volatile
    private var failedUnlockAttempts: Int = 0

    @Volatile
    private var lockoutUntilElapsedRealtime: Long = 0L

    companion object {
        const val MAX_FAILED_ATTEMPTS_BEFORE_COOLDOWN = 5
        const val COOLDOWN_DURATION_MS = 30_000L
        const val REDACTED_TITLE = "[ENCRYPTED]"
        const val REDACTED_CATEGORY = "Encrypted"
        const val REDACTED_TAGS_JSON = "[]"
    }

    val isInitialized: Boolean
        get() = prefs.isInitialized

    val isUnlocked: Boolean
        get() = activeSecretKey != null

    val keyFingerprint: String
        get() = prefs.keyFingerprint

    /**
     * Decrypts and returns the 12-word recovery phrase ONLY when the vault is currently unlocked.
     * Never exposes or stores the recovery phrase in plaintext on disk.
     */
    fun getDecryptedRecoveryPhrase(): String {
        val key = activeSecretKey ?: return ""
        val enc = prefs.encryptedRecoveryPhrase
        if (enc.isNotEmpty()) {
            return try {
                VaultCrypto.decrypt(enc, key)
            } catch (e: Exception) {
                ""
            }
        }
        val legacy = prefs.legacyPlaintextRecoveryPhrase
        if (legacy.isNotEmpty()) {
            return try {
                prefs.encryptedRecoveryPhrase = VaultCrypto.encrypt(legacy, key)
                prefs.clearLegacyPlaintextRecoveryPhrase()
                legacy
            } catch (e: Exception) {
                legacy
            }
        }
        return ""
    }

    fun getRemainingLockoutSeconds(nowElapsedRealtime: Long = SystemClock.elapsedRealtime()): Int {
        val remainingMs = lockoutUntilElapsedRealtime - nowElapsedRealtime
        return if (remainingMs > 0L) ((remainingMs + 999L) / 1000L).toInt() else 0
    }

    fun resetRateLimiter() {
        failedUnlockAttempts = 0
        lockoutUntilElapsedRealtime = 0L
    }

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
    suspend fun setupMasterPassword(
        password: String
    ): Pair<String, String> = withContext(Dispatchers.Default) {
        val cleanPassword = password.trim()
        require(cleanPassword.length >= 8) { "Master Password must be at least 8 characters" }

        val iterations = VaultCrypto.DEFAULT_ITERATIONS

        // 1. Generate random 256-bit Vault Master Key (VMK)
        val vmk = VaultCrypto.generateRandomKey()
        val vmkHex = VaultCrypto.bytesToHex(vmk.encoded)

        // 2. Derive Password Key via PBKDF2-HMAC-SHA256 and encrypt VMK
        val passwordSalt = VaultCrypto.generateSalt()
        val passwordSaltB64 = Base64.encodeToString(passwordSalt, Base64.NO_WRAP)
        val passwordKey = VaultCrypto.deriveKey(cleanPassword, passwordSalt, iterations)
        val encVmkPassword = VaultCrypto.encrypt(vmkHex, passwordKey)
        val passwordVerifier = VaultCrypto.createKeyVerifier(passwordKey, "PASSWORD")

        // 3. Generate Checksummed 12-Word Recovery Phrase, derive Recovery Key, and encrypt VMK
        val recoveryPhrase = VaultCrypto.generateRecoveryPhrase()
        val recoverySalt = VaultCrypto.generateSalt()
        val recoverySaltB64 = Base64.encodeToString(recoverySalt, Base64.NO_WRAP)
        val recoveryKey = VaultCrypto.deriveKeyFromPhrase(recoveryPhrase, recoverySalt, iterations)
        val encVmkRecovery = VaultCrypto.encrypt(vmkHex, recoveryKey)
        val recoveryVerifier = VaultCrypto.createKeyVerifier(recoveryKey, "RECOVERY")

        // 4. Encrypt recovery phrase using VMK so plaintext phrase is never stored on disk
        val encRecoveryPhrase = VaultCrypto.encrypt(recoveryPhrase, vmk)

        // 5. Generate key fingerprint from VMK
        val fingerprint = VaultCrypto.generateFingerprint(vmkHex)

        // 6. Atomic persistence of envelope credentials
        prefs.saveEnvelopeCredentials(
            passwordHash = passwordVerifier,
            saltBase64 = passwordSaltB64,
            recoverySaltBase64 = recoverySaltB64,
            encryptedVmkByPassword = encVmkPassword,
            encryptedVmkByRecovery = encVmkRecovery,
            encryptedRecoveryPhrase = encRecoveryPhrase,
            recoveryPhraseHash = recoveryVerifier,
            keyFingerprint = fingerprint,
            kdfIterations = iterations
        )

        activeSecretKey = vmk
        resetRateLimiter()

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
            if (keyBytes.size == VaultCrypto.KEY_LENGTH_BYTES) {
                val vmk = SecretKeySpec(keyBytes, "AES")
                activeSecretKey = vmk
                resetRateLimiter()
                purgeLegacyDemoItemsIfPresent(vmk)
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
     * Enforces rate-limiting cooldown after repeated failed guesses.
     */
    suspend fun unlock(
        password: String,
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime()
    ): Boolean = withContext(Dispatchers.Default) {
        if (!prefs.isInitialized) return@withContext false
        if (getRemainingLockoutSeconds(nowElapsedRealtime) > 0) {
            return@withContext false
        }

        val saltB64 = prefs.saltBase64
        val expectedVerifier = prefs.masterPasswordHash
        if (saltB64.isEmpty() || expectedVerifier.isEmpty()) return@withContext false

        val salt = try {
            Base64.decode(saltB64, Base64.NO_WRAP)
        } catch (e: Exception) {
            return@withContext false
        }
        if (salt.size < VaultCrypto.SALT_LENGTH_BYTES) return@withContext false

        val iterations = prefs.kdfIterations.coerceAtLeast(10_000)

        // Check both exact input and trimmed input for robustness
        val candidates = listOf(password, password.trim()).distinct()
        for (candidate in candidates) {
            if (candidate.isEmpty()) continue
            val passwordKey = VaultCrypto.deriveKey(candidate, salt, iterations)
            val v2Verifier = VaultCrypto.createKeyVerifier(passwordKey, "PASSWORD")
            val legacyV1Verifier = VaultCrypto.sha256(candidate + saltB64)

            if (v2Verifier == expectedVerifier || legacyV1Verifier == expectedVerifier) {
                if (prefs.encryptedVmkByPassword.isNotEmpty()) {
                    try {
                        val vmkHex = VaultCrypto.decrypt(prefs.encryptedVmkByPassword, passwordKey)
                        val vmkBytes = VaultCrypto.hexToBytes(vmkHex)
                        if (vmkBytes.size == VaultCrypto.KEY_LENGTH_BYTES) {
                            val vmk = SecretKeySpec(vmkBytes, "AES")
                            activeSecretKey = vmk
                            resetRateLimiter()

                            // Transparently upgrade legacy v1 verifier or plaintext recovery phrase if present
                            if (legacyV1Verifier == expectedVerifier) {
                                prefs.masterPasswordHash = v2Verifier
                            }
                            val legacyPhrase = prefs.legacyPlaintextRecoveryPhrase
                            if (legacyPhrase.isNotEmpty()) {
                                prefs.encryptedRecoveryPhrase = VaultCrypto.encrypt(legacyPhrase, vmk)
                                prefs.clearLegacyPlaintextRecoveryPhrase()
                            }
                            purgeLegacyDemoItemsIfPresent(vmk)
                            return@withContext true
                        }
                    } catch (e: Exception) {
                        // Authenticated decryption failed, continue to next candidate
                    }
                } else {
                    activeSecretKey = passwordKey
                    resetRateLimiter()
                    purgeLegacyDemoItemsIfPresent(passwordKey)
                    return@withContext true
                }
            }
        }

        failedUnlockAttempts++
        if (failedUnlockAttempts >= MAX_FAILED_ATTEMPTS_BEFORE_COOLDOWN) {
            lockoutUntilElapsedRealtime = nowElapsedRealtime + COOLDOWN_DURATION_MS
        }
        false
    }

    /**
     * Recovers vault using the 12-word recovery phrase, restoring the exact Vault Master Key (VMK).
     */
    suspend fun recoverWithPhrase(phrase: String, newPassword: String): Boolean = withContext(Dispatchers.Default) {
        if (!prefs.isInitialized) return@withContext false
        val cleanPhrase = VaultCrypto.normalizeRecoveryPhrase(phrase)
        val cleanNewPassword = newPassword.trim()
        if (cleanNewPassword.length < 8) return@withContext false
        // Must be 12 valid words from the recovery wordlist
        if (!VaultCrypto.validateRecoveryPhrase(cleanPhrase, enforceChecksum = false)) return@withContext false
        if (prefs.encryptedVmkByRecovery.isEmpty() || prefs.recoverySaltBase64.isEmpty()) return@withContext false

        try {
            val iterations = prefs.kdfIterations.coerceAtLeast(10_000)
            val recoverySalt = Base64.decode(prefs.recoverySaltBase64, Base64.NO_WRAP)
            if (recoverySalt.size < VaultCrypto.SALT_LENGTH_BYTES) return@withContext false

            val recoveryKey = VaultCrypto.deriveKeyFromPhrase(cleanPhrase, recoverySalt, iterations)
            val v2RecoveryVerifier = VaultCrypto.createKeyVerifier(recoveryKey, "RECOVERY")
            val legacyV1RecoveryVerifier = VaultCrypto.sha256(cleanPhrase)

            if (prefs.recoveryPhraseHash.isNotEmpty() &&
                v2RecoveryVerifier != prefs.recoveryPhraseHash &&
                legacyV1RecoveryVerifier != prefs.recoveryPhraseHash
            ) {
                return@withContext false
            }

            // 1. Recover original VMK using recovery key (authenticated AES-256-GCM decryption)
            val vmkHex = VaultCrypto.decrypt(prefs.encryptedVmkByRecovery, recoveryKey)
            val vmkBytes = VaultCrypto.hexToBytes(vmkHex)
            if (vmkBytes.size != VaultCrypto.KEY_LENGTH_BYTES) return@withContext false
            val vmk = SecretKeySpec(vmkBytes, "AES")

            // 2. Re-wrap VMK with new password
            val newSalt = VaultCrypto.generateSalt()
            val newSaltB64 = Base64.encodeToString(newSalt, Base64.NO_WRAP)
            val newPasswordKey = VaultCrypto.deriveKey(cleanNewPassword, newSalt, iterations)
            val newEncVmkPassword = VaultCrypto.encrypt(vmkHex, newPasswordKey)
            val newPasswordVerifier = VaultCrypto.createKeyVerifier(newPasswordKey, "PASSWORD")
            val encRecoveryPhrase = VaultCrypto.encrypt(cleanPhrase, vmk)

            // 3. Atomically update preferences with new password envelope
            prefs.saveEnvelopeCredentials(
                passwordHash = newPasswordVerifier,
                saltBase64 = newSaltB64,
                recoverySaltBase64 = prefs.recoverySaltBase64,
                encryptedVmkByPassword = newEncVmkPassword,
                encryptedVmkByRecovery = prefs.encryptedVmkByRecovery,
                encryptedRecoveryPhrase = encRecoveryPhrase,
                recoveryPhraseHash = v2RecoveryVerifier,
                keyFingerprint = prefs.keyFingerprint,
                kdfIterations = iterations
            )

            activeSecretKey = vmk
            resetRateLimiter()
            purgeLegacyDemoItemsIfPresent(vmk)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Locks vault by purging the active secret key reference from RAM
     */
    fun lock() {
        activeSecretKey = null
    }

    /**
     * Encrypts full record metadata (title, category, tags) + secret payload into an AAD-bound
     * AES-256-GCM envelope so SQLite columns store only redacted placeholders.
     */
    private fun encryptRecordEnvelope(item: VaultItem, recordId: String, key: SecretKey): String {
        val tagsArray = JSONArray()
        item.tags.forEach { tagsArray.put(it) }

        val envelopeJson = JSONObject().apply {
            put("_vz_meta_v2", true)
            put("id", recordId)
            put("type", item.type.name)
            put("title", item.title)
            put("category", item.category)
            put("tags", tagsArray)
            put("payload", JSONObject(item.payloadToJsonString()))
        }
        val aad = VaultCrypto.recordAssociatedData(recordId, item.type.name)
        return VaultCrypto.encrypt(envelopeJson.toString(), key, aad)
    }

    /**
     * Decrypts a VaultItemEntity using v2 AAD-bound metadata envelope first, with seamless
     * fallback to v1 legacy format.
     */
    private fun decryptRecordEntity(entity: VaultItemEntity, key: SecretKey): VaultItem {
        val type = VaultType.valueOf(entity.type)
        val aad = VaultCrypto.recordAssociatedData(entity.id, entity.type)

        // Try v2 AAD-bound full-metadata envelope first
        try {
            val decryptedEnvelopeStr = VaultCrypto.decrypt(entity.encryptedPayload, key, aad)
            val json = JSONObject(decryptedEnvelopeStr)
            if (json.optBoolean("_vz_meta_v2", false)) {
                val envId = json.optString("id", entity.id)
                val envType = json.optString("type", entity.type)
                require(envId == entity.id && envType == entity.type) {
                    "Record AAD envelope mismatch"
                }
                val decryptedTitle = json.optString("title", entity.title)
                val decryptedCategory = json.optString("category", entity.category)
                val decryptedTags = mutableListOf<String>()
                val tagsArr = json.optJSONArray("tags")
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        decryptedTags.add(tagsArr.getString(i))
                    }
                }
                val payloadObj = json.optJSONObject("payload") ?: JSONObject()
                val payload = VaultItem.parsePayload(type, payloadObj.toString())

                return VaultItem(
                    id = entity.id,
                    title = decryptedTitle,
                    type = type,
                    category = decryptedCategory,
                    isFavorite = entity.isFavorite,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    tags = decryptedTags,
                    payload = payload
                )
            }
        } catch (e: Exception) {
            // Fall through to legacy v1 decryption (no AAD)
        }

        val decryptedPayloadJson = VaultCrypto.decrypt(entity.encryptedPayload, key)
        val payload = VaultItem.parsePayload(type, decryptedPayloadJson)
        val tags = parseTags(entity.tagsJson)

        return VaultItem(
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
                        decryptRecordEntity(entity, key)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun saveItem(item: VaultItem) = withContext(Dispatchers.Default) {
        val key = activeSecretKey ?: throw IllegalStateException("Vault is locked")
        val recordId = item.id.ifEmpty { UUID.randomUUID().toString() }
        val encryptedEnvelope = encryptRecordEnvelope(item, recordId, key)
        val now = System.currentTimeMillis()

        val entity = VaultItemEntity(
            id = recordId,
            title = REDACTED_TITLE,
            type = item.type.name,
            category = REDACTED_CATEGORY,
            encryptedPayload = encryptedEnvelope,
            isFavorite = item.isFavorite,
            createdAt = if (item.createdAt == 0L) now else item.createdAt,
            updatedAt = if (item.updatedAt > 0L && item.id.isEmpty()) item.updatedAt else now,
            tagsJson = REDACTED_TAGS_JSON
        )
        dao.insertOrUpdate(entity)
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (!isUnlocked) throw IllegalStateException("Vault is locked")
        dao.updateFavorite(id, isFavorite)
    }

    suspend fun deleteItem(id: String) = withContext(Dispatchers.IO) {
        if (!isUnlocked) throw IllegalStateException("Vault is locked")
        dao.deleteById(id)
    }

    suspend fun clearVault() = withContext(Dispatchers.IO) {
        dao.clearAll()
        prefs.resetAll()
        activeSecretKey = null
        resetRateLimiter()
    }

    /**
     * Export all items as an authenticated encrypted JSON backup envelope.
     * Outer SQLite/JSON metadata fields remain redacted ("[ENCRYPTED]") while full record metadata
     * and secret payloads are inside the AAD-bound AES-256-GCM ciphertext.
     */
    suspend fun exportEncryptedBackup(): String = withContext(Dispatchers.Default) {
        val key = activeSecretKey ?: throw IllegalStateException("Vault is locked")
        VaultCrypto.validateAes256Key(key)
        val entities = dao.getAllList()
        val backupJson = JSONObject()
        backupJson.put("version", "3.2")
        backupJson.put("format", "VaultZero_Encrypted_Envelope")
        backupJson.put("fingerprint", prefs.keyFingerprint)
        backupJson.put("exportedAt", System.currentTimeMillis())
        backupJson.put("kdfIterations", prefs.kdfIterations)
        backupJson.put("salt", prefs.saltBase64)
        backupJson.put("recoverySalt", prefs.recoverySaltBase64)
        backupJson.put("encryptedVmkByPassword", prefs.encryptedVmkByPassword)
        backupJson.put("encryptedVmkByRecovery", prefs.encryptedVmkByRecovery)
        backupJson.put("verifier", prefs.masterPasswordHash)

        val itemsArray = JSONArray()
        for (entity in entities) {
            val itemObj = JSONObject().apply {
                put("id", entity.id)
                put("title", REDACTED_TITLE)
                put("type", entity.type)
                put("category", REDACTED_CATEGORY)
                put("encryptedPayload", entity.encryptedPayload)
                put("isFavorite", entity.isFavorite)
                put("createdAt", entity.createdAt)
                put("updatedAt", entity.updatedAt)
                put("tagsJson", REDACTED_TAGS_JSON)
            }
            itemsArray.put(itemObj)
        }
        backupJson.put("items", itemsArray)
        backupJson.toString(2)
    }

    /**
     * Import encrypted backup into the currently unlocked vault.
     * If the backup was encrypted under a different vault key and [backupPassword] is provided,
     * unwraps the backup's VMK using [backupPassword], decrypts each item (supporting both v2
     * AAD-bound metadata envelopes and v1 legacy envelopes), and re-encrypts it under the active
     * vault's VMK with full metadata redaction.
     */
    suspend fun importEncryptedBackup(
        jsonString: String,
        backupPassword: String? = null
    ): Int = withContext(Dispatchers.Default) {
        val activeKey = activeSecretKey ?: throw IllegalStateException("Vault is locked")
        val backupJson = JSONObject(jsonString)
        val itemsArray = backupJson.optJSONArray("items") ?: return@withContext 0

        // Optional foreign backup key unwrap if backupPassword is provided
        var foreignBackupKey: SecretKey? = null
        if (!backupPassword.isNullOrBlank()) {
            val backupSaltB64 = backupJson.optString("salt", "")
            val backupEncVmk = backupJson.optString("encryptedVmkByPassword", "")
            val backupIterations = backupJson.optInt("kdfIterations", VaultCrypto.DEFAULT_ITERATIONS)
            if (backupSaltB64.isNotEmpty() && backupEncVmk.isNotEmpty()) {
                try {
                    val salt = Base64.decode(backupSaltB64, Base64.NO_WRAP)
                    val passKey = VaultCrypto.deriveKey(backupPassword.trim(), salt, backupIterations)
                    val unwrappedVmkHex = VaultCrypto.decrypt(backupEncVmk, passKey)
                    foreignBackupKey = SecretKeySpec(VaultCrypto.hexToBytes(unwrappedVmkHex), "AES")
                } catch (e: Exception) {
                    foreignBackupKey = null
                }
            }
        }

        var importedCount = 0
        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.optJSONObject(i) ?: continue
            val id = itemObj.optString("id", "").ifEmpty { UUID.randomUUID().toString() }
            val title = itemObj.optString("title", REDACTED_TITLE)
            val typeStr = itemObj.optString("type", "")
            val category = itemObj.optString("category", REDACTED_CATEGORY)
            val encPayload = itemObj.optString("encryptedPayload", "")
            if (typeStr.isEmpty() || encPayload.isEmpty()) continue
            val validType = runCatching { VaultType.valueOf(typeStr) }.getOrNull() ?: continue

            val tempEntity = VaultItemEntity(
                id = id,
                title = title,
                type = validType.name,
                category = category,
                encryptedPayload = encPayload,
                isFavorite = itemObj.optBoolean("isFavorite", false),
                createdAt = itemObj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = itemObj.optLong("updatedAt", System.currentTimeMillis()),
                tagsJson = itemObj.optString("tagsJson", REDACTED_TAGS_JSON)
            )

            try {
                // First try decrypting with the active vault key, then foreignBackupKey if provided
                val decryptedItem = try {
                    decryptRecordEntity(tempEntity, activeKey)
                } catch (e: Exception) {
                    val altKey = foreignBackupKey ?: throw e
                    decryptRecordEntity(tempEntity, altKey)
                }

                // Re-encrypt under activeKey using v2 AAD-bound metadata envelope
                val reEncryptedEnvelope = encryptRecordEnvelope(decryptedItem, id, activeKey)
                val entityToSave = VaultItemEntity(
                    id = id,
                    title = REDACTED_TITLE,
                    type = validType.name,
                    category = REDACTED_CATEGORY,
                    encryptedPayload = reEncryptedEnvelope,
                    isFavorite = decryptedItem.isFavorite,
                    createdAt = decryptedItem.createdAt,
                    updatedAt = decryptedItem.updatedAt,
                    tagsJson = REDACTED_TAGS_JSON
                )
                dao.insertOrUpdate(entityToSave)
                importedCount++
            } catch (e: Exception) {
                // Skip records whose authentication tag fails under both active and backup keys
            }
        }
        importedCount
    }

    private suspend fun purgeLegacyDemoItemsIfPresent(key: SecretKey) {
        try {
            val entities = dao.getAllList()
            for (entity in entities) {
                val item = try {
                    decryptRecordEntity(entity, key)
                } catch (e: Exception) {
                    null
                } ?: continue

                val isLegacyDemo = when (val p = item.payload) {
                    is LoginPayload -> item.title == "GitHub Developer Account" && p.password == "g!t_V@ultZ3r0#2026_SecureKey"
                    is ApiSecretPayload -> item.title == "AWS Production Root API" && p.apiKey == "AKIAIOSFODNN7EXAMPLE"
                    is CryptoPayload -> item.title == "Ethereum Cold Storage Wallet" && p.publicAddress == "0x71C...8494"
                    is CardPayload -> item.title == "Titanium Corporate Visa" && p.cardNumber == "4532 8812 9014 3829"
                    is NotePayload -> item.title == "Post-Quantum Security Protocol Notes" && p.content.startsWith("VaultZero Architecture:")
                    else -> false
                }
                if (isLegacyDemo) {
                    dao.deleteById(entity.id)
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun parseTags(json: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            // ignore malformed tag JSON
        }
        return list
    }
}
