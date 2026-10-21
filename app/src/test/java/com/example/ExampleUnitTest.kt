package com.example

import com.example.crypto.SecurityAuditEngine
import com.example.crypto.VaultCrypto
import com.example.model.LoginPayload
import com.example.model.VaultItem
import com.example.model.VaultType
import com.example.model.VulnerabilityType
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `rfc6238 totp official test vectors and leading zeros`() {
        // Official RFC 6238 / RFC 4226 ASCII seed "12345678901234567890" in Base32:
        val rfcSeedBase32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        // Vector 1: t = 59s -> 8-digit 94287082, 6-digit 287082, remaining = 1s
        val res59 = VaultCrypto.generateTotp(rfcSeedBase32, timeMillis = 59_000L)
        assertNotNull(res59)
        assertEquals("287082", res59!!.code)
        assertEquals(1, res59.remainingSeconds)

        // Vector 2: t = 1111111109s -> 8-digit 07081804, 6-digit 081804 (leading zero preserved), 1111111109 % 30 = 29 -> 1s remaining
        val res111 = VaultCrypto.generateTotp(rfcSeedBase32, timeMillis = 1_111_111_109_000L)
        assertNotNull(res111)
        assertEquals("081804", res111!!.code)
        assertEquals(1, res111.remainingSeconds)

        // Vector 3: t = 1234567890s -> 8-digit 89005924, 6-digit 005924 (two leading zeros preserved)
        val res123 = VaultCrypto.generateTotp(rfcSeedBase32, timeMillis = 1_234_567_890_000L)
        assertNotNull(res123)
        assertEquals("005924", res123!!.code)
        assertEquals(30, res123.remainingSeconds)
    }

    @Test
    fun `totp rejects malformed base32 and invalid characters`() {
        assertNull(VaultCrypto.generateTotp(""))
        assertNull(VaultCrypto.generateTotp("   "))
        assertNull(VaultCrypto.generateTotp("INVALID_CHARS_0189!@#"))
        assertNull(VaultCrypto.generateTotp("JBSWY3DP=EHPK3PXP")) // invalid internal padding
    }

    @Test
    fun `password generator enforces character sets length and ambiguity rules`() {
        // Length bounds
        for (len in listOf(8, 16, 32, 64)) {
            val pwd = VaultCrypto.generatePassword(length = len)
            assertEquals(len, pwd.length)
        }

        // Upper only
        val upperOnly = VaultCrypto.generatePassword(
            length = 24,
            includeUpper = true,
            includeLower = false,
            includeDigits = false,
            includeSymbols = false,
            avoidAmbiguous = true
        )
        assertTrue(upperOnly.all { it.isUpperCase() && it !in "IO" })

        // Digits only without ambiguous (0, 1 excluded)
        val digitsNoAmbig = VaultCrypto.generatePassword(
            length = 20,
            includeUpper = false,
            includeLower = false,
            includeDigits = true,
            includeSymbols = false,
            avoidAmbiguous = true
        )
        assertTrue(digitsNoAmbig.all { it in "23456789" })

        // All character sets disabled must throw IllegalArgumentException
        assertThrows(IllegalArgumentException::class.java) {
            VaultCrypto.generatePassword(
                length = 16,
                includeUpper = false,
                includeLower = false,
                includeDigits = false,
                includeSymbols = false
            )
        }
    }

    @Test
    fun `passphrase generator and recovery wordlist checksum integrity`() {
        // Verify wordlists have no duplicates
        assertEquals(VaultCrypto.RECOVERY_WORDLIST.size, VaultCrypto.RECOVERY_WORDLIST.distinct().size)
        assertEquals(VaultCrypto.PASSPHRASE_WORDLIST.size, VaultCrypto.PASSPHRASE_WORDLIST.distinct().size)

        val phrase4 = VaultCrypto.generatePassphrase(wordCount = 4, separator = "-")
        assertEquals(4, phrase4.split("-").size)

        val recovery = VaultCrypto.generateRecoveryPhrase()
        val words = recovery.split(" ")
        assertEquals(12, words.size)
        assertTrue(VaultCrypto.validateRecoveryPhrase(recovery))
        assertTrue(VaultCrypto.verifyRecoveryPhraseChecksum(recovery))

        // Corrupting the 12th checksum word to a different valid word must fail checksum verification
        val differentChecksumWord = VaultCrypto.RECOVERY_WORDLIST.first { it != words[11] }
        val corruptedPhrase = (words.subList(0, 11) + differentChecksumWord).joinToString(" ")
        assertTrue(VaultCrypto.validateRecoveryPhrase(corruptedPhrase, enforceChecksum = false))
        assertFalse(VaultCrypto.verifyRecoveryPhraseChecksum(corruptedPhrase))
        assertFalse(VaultCrypto.validateRecoveryPhrase(corruptedPhrase, enforceChecksum = true))

        assertFalse(VaultCrypto.validateRecoveryPhrase("not a valid twelve word phrase"))
        assertFalse(VaultCrypto.validateRecoveryPhrase("alpha beacon cipher matrix orbit plasma silver timber haven nova quartz invalidword"))
    }

    @Test
    fun `entropy and strength meter penalizes weak patterns and rewards true randomness`() {
        val empty = VaultCrypto.calculateStrength("")
        assertEquals(0, empty.entropyBits)
        assertEquals(VaultCrypto.StrengthGrade.VERY_WEAK, empty.grade)

        // Repeated character string must NOT be rated Strong despite length 16
        val repeated = VaultCrypto.calculateStrength("aaaaaaaaaaaaaaaa")
        assertEquals(0, repeated.shannonBits)
        assertTrue(
            repeated.grade == VaultCrypto.StrengthGrade.VERY_WEAK ||
                repeated.grade == VaultCrypto.StrengthGrade.WEAK
        )

        // Common dictionary / keyboard walk password must be penalized
        val common = VaultCrypto.calculateStrength("Password123456!")
        assertEquals(VaultCrypto.StrengthGrade.VERY_WEAK, common.grade)

        // Sequential run must be penalized
        val sequential = VaultCrypto.calculateStrength("1234567890123456")
        assertTrue(
            sequential.grade == VaultCrypto.StrengthGrade.VERY_WEAK ||
                sequential.grade == VaultCrypto.StrengthGrade.WEAK
        )

        // High-entropy random password must be FORTIFIED
        val strongRandom = VaultCrypto.calculateStrength("k9#mP2\$vL8@xQ4!zR7&w")
        assertEquals(VaultCrypto.StrengthGrade.FORTIFIED, strongRandom.grade)
        assertTrue(strongRandom.entropyBits >= 85)
    }

    @Test
    fun `security audit engine accurately scores weak reused stale and malformed totp without penalizing optional blank 2fa`() {
        val now = 1_700_000_000_000L
        val ninetyOneDaysAgo = now - (91L * 24 * 60 * 60 * 1000L)
        val tenDaysAgo = now - (10L * 24 * 60 * 60 * 1000L)

        // Empty vault -> 100 A+
        val emptyReport = SecurityAuditEngine.analyzeVault(emptyList(), now)
        assertEquals(100, emptyReport.score)
        assertEquals("A+", emptyReport.grade)

        // Fortified login with valid TOTP and recent update -> 100 A+
        val healthyItemWithTotp = VaultItem(
            id = "1",
            title = "Healthy Login With 2FA",
            type = VaultType.LOGIN,
            updatedAt = tenDaysAgo,
            payload = LoginPayload(
                username = "alice",
                password = "k9#mP2\$vL8@xQ4!zR7&w",
                totpSecret = "JBSWY3DPEHPK3PXP"
            )
        )
        // Fortified login WITHOUT 2FA (blank optional TOTP) -> must also be 100 A+ with zero action items
        val healthyItemWithoutTotp = VaultItem(
            id = "1b",
            title = "Healthy Login Without 2FA",
            type = VaultType.LOGIN,
            updatedAt = tenDaysAgo,
            payload = LoginPayload(
                username = "bob",
                password = "x7!qW9#zM4\$pL2@nK8&v",
                totpSecret = ""
            )
        )
        val healthyReport = SecurityAuditEngine.analyzeVault(
            listOf(healthyItemWithTotp, healthyItemWithoutTotp),
            now
        )
        assertEquals(100, healthyReport.score)
        assertEquals("A+", healthyReport.grade)
        assertEquals(0, healthyReport.missing2faCount)
        assertTrue(healthyReport.vulnerabilities.isEmpty())

        // Vulnerable logins: reused weak password, stale timestamp, and malformed TOTP secret
        val vuln1 = VaultItem(
            id = "2",
            title = "Site A",
            type = VaultType.LOGIN,
            updatedAt = ninetyOneDaysAgo,
            payload = LoginPayload(username = "user1", password = "password123", totpSecret = "")
        )
        val vuln2 = VaultItem(
            id = "3",
            title = "Site B",
            type = VaultType.LOGIN,
            updatedAt = tenDaysAgo,
            payload = LoginPayload(username = "user2", password = "password123", totpSecret = "INVALID_BASE32_!@#")
        )

        val audit = SecurityAuditEngine.analyzeVault(listOf(vuln1, vuln2), now)
        assertEquals(2, audit.totalChecked)
        assertEquals(2, audit.weakCount)
        assertEquals(2, audit.reusedCount)
        assertEquals(1, audit.staleCount)
        assertEquals(1, audit.missing2faCount)
        assertTrue(audit.vulnerabilities.any { it.type == VulnerabilityType.REUSED_PASSWORD })
        assertTrue(audit.vulnerabilities.any { it.type == VulnerabilityType.WEAK_PASSWORD })
        assertTrue(audit.vulnerabilities.any { it.type == VulnerabilityType.STALE_PASSWORD })
        assertTrue(audit.vulnerabilities.any { it.type == VulnerabilityType.MISSING_2FA })
        assertTrue(audit.score < 50)
        assertEquals("F", audit.grade)
    }
}
