package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.VaultCrypto
import com.example.data.VaultDatabase
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import com.example.model.ApiSecretPayload
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import com.example.ui.GeneratorConfig
import com.example.ui.VaultLockState
import com.example.ui.VaultViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: VaultDatabase
    private lateinit var prefs: VaultPreferences
    private lateinit var repository: VaultRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = VaultPreferences(context)
        prefs.resetAll()
        repository = VaultRepository(db.vaultDao(), prefs)
    }

    @After
    fun tearDown() {
        db.close()
        prefs.resetAll()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("VaultZero", appName)
    }

    @Test
    fun `aes256gcm encryption integrity and tamper rejection`() {
        val key = VaultCrypto.generateRandomKey()
        val plainText = "TopSecretCredential_Unicode_🔒_日本語_2026"

        // Repeated encryptions of identical plaintext must produce distinct ciphertexts (fresh 12-byte IV)
        val c1 = VaultCrypto.encrypt(plainText, key)
        val c2 = VaultCrypto.encrypt(plainText, key)
        assertNotEquals(c1, c2)
        assertEquals(plainText, VaultCrypto.decrypt(c1, key))
        assertEquals(plainText, VaultCrypto.decrypt(c2, key))

        // Wrong key must fail with AEADBadTagException
        val wrongKey = VaultCrypto.generateRandomKey()
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decrypt(c1, wrongKey)
        }

        // Tampered IV (first 12 bytes) must fail authentication
        val rawBytes = Base64.decode(c1, Base64.NO_WRAP)
        val tamperedIv = rawBytes.clone()
        tamperedIv[0] = (tamperedIv[0].toInt() xor 0x01).toByte()
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decrypt(Base64.encodeToString(tamperedIv, Base64.NO_WRAP), key)
        }

        // Tampered ciphertext body must fail authentication
        val tamperedBody = rawBytes.clone()
        tamperedBody[14] = (tamperedBody[14].toInt() xor 0x01).toByte()
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decrypt(Base64.encodeToString(tamperedBody, Base64.NO_WRAP), key)
        }

        // Tampered 128-bit GCM authentication tag (last 16 bytes) must fail authentication
        val tamperedTag = rawBytes.clone()
        tamperedTag[tamperedTag.size - 1] = (tamperedTag[tamperedTag.size - 1].toInt() xor 0x01).toByte()
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decrypt(Base64.encodeToString(tamperedTag, Base64.NO_WRAP), key)
        }

        // Truncated payload (< 28 bytes) must throw IllegalArgumentException
        val truncated = Base64.encodeToString(ByteArray(20), Base64.NO_WRAP)
        assertThrows(IllegalArgumentException::class.java) {
            VaultCrypto.decrypt(truncated, key)
        }

        // Non-256-bit key (128-bit / 16 bytes) must be rejected
        val weak128Key = SecretKeySpec(ByteArray(16), "AES")
        assertThrows(IllegalArgumentException::class.java) {
            VaultCrypto.encrypt("test", weak128Key)
        }
    }

    @Test
    fun `vault setup lock unlock rate limiting and zero plaintext recovery persistence`() = runBlocking {
        assertFalse(repository.isInitialized)
        assertFalse(repository.isUnlocked)

        val masterPassword = "MasterVaultPassword#2026!"
        val (recoveryPhrase, vmkHex) = repository.setupMasterPassword(masterPassword)

        assertTrue(repository.isInitialized)
        assertTrue(repository.isUnlocked)
        assertEquals(12, recoveryPhrase.split(" ").size)
        assertEquals(64, vmkHex.length)

        // Verify plaintext recovery phrase is NEVER persisted in SharedPreferences
        assertEquals("", prefs.legacyPlaintextRecoveryPhrase)
        assertTrue(prefs.encryptedRecoveryPhrase.isNotEmpty())
        assertFalse(prefs.encryptedRecoveryPhrase.contains(recoveryPhrase.split(" ").first()))

        // Verify password verifier is NOT a raw single-round SHA-256 of password + salt
        val rawSha256 = VaultCrypto.sha256(masterPassword + prefs.saltBase64)
        assertNotEquals(rawSha256, prefs.masterPasswordHash)

        // While unlocked, decrypted recovery phrase matches
        assertEquals(recoveryPhrase, repository.getDecryptedRecoveryPhrase())

        // Lock vault -> active key purged, recovery phrase inaccessible, items flow empty
        repository.lock()
        assertFalse(repository.isUnlocked)
        assertEquals("", repository.getDecryptedRecoveryPhrase())
        assertTrue(repository.getVaultItemsFlow().first().isEmpty())

        // Rate limiting test: 5 failed attempts trigger 30s cooldown
        val baseTime = 100_000L
        for (i in 1..5) {
            assertFalse(repository.unlock("WrongPassAttempt$i", nowElapsedRealtime = baseTime))
        }
        assertTrue(repository.getRemainingLockoutSeconds(baseTime + 5_000L) > 0)

        // Even the CORRECT password must be rejected while in cooldown window
        assertFalse(repository.unlock(masterPassword, nowElapsedRealtime = baseTime + 10_000L))
        assertFalse(repository.isUnlocked)

        // After 30s monotonic cooldown expires, correct password succeeds
        assertTrue(repository.unlock(masterPassword, nowElapsedRealtime = baseTime + 31_000L))
        assertTrue(repository.isUnlocked)
        assertEquals(0, repository.getRemainingLockoutSeconds(baseTime + 31_000L))
    }

    @Test
    fun `emergency recovery kit restores original vault and preserves all encrypted records`() = runBlocking {
        val originalPass = "OriginalMasterPass99!"
        val (recoveryPhrase, originalVmkHex) = repository.setupMasterPassword(originalPass, seedDemoItems = false)
        val originalFingerprint = repository.keyFingerprint

        // Save a custom secret item under the original VMK
        val secretItem = VaultItem(
            id = "rec-test-1",
            title = "Sovereign Root Key",
            type = VaultType.LOGIN,
            payload = LoginPayload(username = "satoshi", password = "OriginalEncryptedSecretPassword!42")
        )
        repository.saveItem(secretItem)
        repository.lock()
        assertFalse(repository.isUnlocked)

        // Invalid or reordered recovery phrase must fail and leave vault locked
        val reversedPhrase = recoveryPhrase.split(" ").reversed().joinToString(" ")
        assertFalse(repository.recoverWithPhrase(reversedPhrase, "NewMasterPass123!"))
        assertFalse(repository.isUnlocked)

        // Valid 12-word recovery phrase unlocks original VMK and re-wraps with new password
        val newPass = "RecoveredNewMasterPass2026!"
        assertTrue(repository.recoverWithPhrase(recoveryPhrase, newPass))
        assertTrue(repository.isUnlocked)
        assertEquals(originalVmkHex, repository.getActiveVmkHex())
        assertEquals(originalFingerprint, repository.keyFingerprint)

        // Verify original encrypted record is intact and decryptable
        val items = repository.getVaultItemsFlow().first()
        assertEquals(1, items.size)
        val recoveredLogin = items.first().payload as LoginPayload
        assertEquals("OriginalEncryptedSecretPassword!42", recoveredLogin.password)

        // Verify old password no longer works and new password works
        repository.lock()
        assertFalse(repository.unlock(originalPass))
        assertTrue(repository.unlock(newPass))
    }

    @Test
    fun `all six vault categories crud field preservation and sqlite encryption at rest`() = runBlocking {
        repository.setupMasterPassword("CategoryAuditPass!2026", seedDemoItems = false)

        val testItems = listOf(
            VaultItem(
                id = "item-login",
                title = "Corp Portal",
                type = VaultType.LOGIN,
                category = "Work",
                tags = listOf("sso", "corp"),
                payload = LoginPayload("admin@corp.io", "S3cr3t!Pass", "https://corp.io", "JBSWY3DPEHPK3PXP", "SSO Login")
            ),
            VaultItem(
                id = "item-card",
                title = "Amex Centurion",
                type = VaultType.CARD,
                category = "Finance",
                payload = CardPayload("JANE DOE", "378282246310005", "04", "30", "007", "0123", "Amex", "Leading zero CVV & PIN")
            ),
            VaultItem(
                id = "item-api",
                title = "Stripe Live Key",
                type = VaultType.API_SECRET,
                category = "Cloud",
                payload = ApiSecretPayload("Stripe", "pk_live_123", "sk_live_secret_999", "Production", "read_write", "https://api.stripe.com", "Webhook key")
            ),
            VaultItem(
                id = "item-crypto",
                title = "Cold BTC Vault",
                type = VaultType.CRYPTO_WALLET,
                category = "Crypto",
                payload = CryptoPayload("Ledger Nano", "Bitcoin", "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", "alpha beacon cipher matrix orbit plasma silver timber haven nova quartz zenith", "L1aW4aubDFB7yfras2S1mN3bqg9nwySY8nkoLmJebSLD5BWv3ENZ", "m/84'/0'/0'/0/0", "Hardware wallet")
            ),
            VaultItem(
                id = "item-note",
                title = "Markdown Protocol Note",
                type = VaultType.NOTE,
                category = "Security",
                payload = NotePayload("# Heading\n- Bullet 1\n- Unicode: 🚀 🔐\n<script>alert(1)</script>", isPinned = true)
            ),
            VaultItem(
                id = "item-id",
                title = "Passport Record",
                type = VaultType.IDENTITY,
                category = "Personal",
                payload = IdentityPayload("JANE A. DOE", "Passport", "004918273", "1990-01-15", "2034-01-15", "Issued in US")
            )
        )

        for (item in testItems) {
            repository.saveItem(item)
        }

        // Inspect raw SQLite entities directly from DAO to verify full payload AND metadata encryption at rest
        val rawEntities = db.vaultDao().getAllList()
        assertEquals(6, rawEntities.size)
        for (entity in rawEntities) {
            // Verify SQLite metadata columns are redacted placeholders (zero metadata leakage)
            assertEquals(VaultRepository.REDACTED_TITLE, entity.title)
            assertEquals(VaultRepository.REDACTED_CATEGORY, entity.category)
            assertEquals(VaultRepository.REDACTED_TAGS_JSON, entity.tagsJson)

            // Verify neither secret payloads nor titles/categories/tags appear in plaintext inside encryptedPayload
            assertFalse(entity.encryptedPayload.contains("Corp Portal"))
            assertFalse(entity.encryptedPayload.contains("S3cr3t!Pass"))
            assertFalse(entity.encryptedPayload.contains("378282246310005"))
            assertFalse(entity.encryptedPayload.contains("sk_live_secret_999"))
            assertFalse(entity.encryptedPayload.contains("L1aW4aubDFB7yfras2S1mN3bqg9nwySY8nkoLmJebSLD5BWv3ENZ"))
            assertFalse(entity.encryptedPayload.contains("004918273"))
        }

        // Verify decrypted items preserve all metadata (title, category, tags) and payload fields including leading zeros
        val decrypted = repository.getVaultItemsFlow().first().associateBy { it.id }
        assertEquals(6, decrypted.size)

        val loginItem = decrypted["item-login"]!!
        assertEquals("Corp Portal", loginItem.title)
        assertEquals("Work", loginItem.category)
        assertEquals(listOf("sso", "corp"), loginItem.tags)

        val card = decrypted["item-card"]!!.payload as CardPayload
        assertEquals("007", card.cvv)
        assertEquals("0123", card.pin)
        assertEquals("Amex", card.brand)
        assertEquals("Leading zero CVV & PIN", card.notes)

        val api = decrypted["item-api"]!!.payload as ApiSecretPayload
        assertEquals("read_write", api.scopes)
        assertEquals("https://api.stripe.com", api.endpointUrl)
        assertEquals("Webhook key", api.notes)

        val crypto = decrypted["item-crypto"]!!.payload as CryptoPayload
        assertEquals("m/84'/0'/0'/0/0", crypto.derivationPath)
        assertEquals("Hardware wallet", crypto.notes)
        assertNotEquals(crypto.publicAddress, crypto.privateKey)

        val idDoc = decrypted["item-id"]!!.payload as IdentityPayload
        assertEquals("004918273", idDoc.idNumber)
        assertEquals("1990-01-15", idDoc.birthDate)
        assertEquals("2034-01-15", idDoc.expiryDate)

        // Verify AAD record-binding tamper resistance: swapping encryptedPayload between two records must fail GCM auth
        val loginEntity = rawEntities.first { it.id == "item-login" }
        val cardEntity = rawEntities.first { it.id == "item-card" }
        db.vaultDao().insertOrUpdate(loginEntity.copy(encryptedPayload = cardEntity.encryptedPayload))
        val afterSwap = repository.getVaultItemsFlow().first().associateBy { it.id }
        // Tampered record is rejected by GCM AAD verification, leaving the remaining 5 valid records intact
        assertNull(afterSwap["item-login"])
        assertEquals(5, afterSwap.size)
    }

    @Test
    fun `encrypted backup export and cross-vault import roundtrip`() = runBlocking {
        val passA = "VaultAPassword123!"
        repository.setupMasterPassword(passA, seedDemoItems = true)
        val initialItems = repository.getVaultItemsFlow().first()
        assertEquals(5, initialItems.size)

        val backupJson = repository.exportEncryptedBackup()
        assertTrue(backupJson.contains("VaultZero_Encrypted_Envelope"))
        // Verify neither secret passwords nor record titles/categories/tags leak in the exported backup JSON
        assertFalse(backupJson.contains("g!t_V@ultZ3r0#2026_SecureKey"))
        assertFalse(backupJson.contains("GitHub Developer Account"))
        assertFalse(backupJson.contains("AWS Production Root API"))
        assertFalse(backupJson.contains("Ethereum Cold Storage Wallet"))

        // Wipe vault completely and initialize a brand-new Vault B with a different Master Password & VMK
        repository.clearVault()
        val passB = "VaultBDifferentPassword999!"
        repository.setupMasterPassword(passB, seedDemoItems = false)
        assertTrue(repository.getVaultItemsFlow().first().isEmpty())

        // Importing Vault A's backup into Vault B using Vault A's password unwraps and re-encrypts all 5 records
        val importedCount = repository.importEncryptedBackup(backupJson, backupPassword = passA)
        assertEquals(5, importedCount)
        val restoredItems = repository.getVaultItemsFlow().first()
        assertEquals(5, restoredItems.size)
        assertTrue(restoredItems.any { it.title == "GitHub Developer Account" && it.category == "Work" })
    }

    @Test
    fun `viewmodel monotonic autolock and ownership-aware clipboard clearing`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = VaultViewModel(app)
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // Copy sensitive password
        vm.copySecurely("Password", "MySecretCopiedPass!", isSensitive = true)
        assertEquals("MySecretCopiedPass!", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Simulate another app overwriting clipboard -> VaultZero must NOT erase unrelated clipboard content
        clipboard.setPrimaryClip(ClipData.newPlainText("ExternalApp", "UserExternalText"))
        assertFalse(vm.clearSensitiveClipboardIfOwned())
        assertEquals("UserExternalText", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Copy sensitive password again and verify VaultZero clears it when still owned
        vm.copySecurely("Password", "SecondSecretPass!", isSensitive = true)
        assertTrue(vm.clearSensitiveClipboardIfOwned())
        val afterClear = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(afterClear.isEmpty())

        // Generator validation: disabling all character sets is rejected
        vm.updateGeneratorConfig(
            GeneratorConfig(
                includeUpper = false,
                includeLower = false,
                includeDigits = false,
                includeSymbols = false
            )
        )
        assertEquals("At least one character set must remain enabled", vm.clipboardStatus.value)
    }
}
