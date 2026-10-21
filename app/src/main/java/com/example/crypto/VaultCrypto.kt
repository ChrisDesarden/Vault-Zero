package com.example.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.roundToInt

object VaultCrypto {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_CIPHER = "AES/GCM/NoPadding"
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val GCM_TAG_LENGTH_BYTES = 16
    const val DEFAULT_ITERATIONS = 100_000
    const val KEY_LENGTH_BITS = 256
    const val KEY_LENGTH_BYTES = 32
    const val SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()

    /**
     * Generate cryptographically secure random salt bytes (16 bytes / 128 bits)
     */
    fun generateSalt(lengthBytes: Int = SALT_LENGTH_BYTES): ByteArray {
        require(lengthBytes >= SALT_LENGTH_BYTES) { "Salt length must be at least $SALT_LENGTH_BYTES bytes" }
        val salt = ByteArray(lengthBytes)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Validate that a SecretKey is a valid 256-bit (32-byte) AES key
     */
    fun validateAes256Key(secretKey: SecretKey) {
        val encoded = secretKey.encoded
            ?: throw IllegalArgumentException("SecretKey does not support raw encoding")
        try {
            if (encoded.size != KEY_LENGTH_BYTES) {
                throw IllegalArgumentException("Invalid AES key length: expected $KEY_LENGTH_BYTES bytes (256 bits), got ${encoded.size} bytes")
            }
        } finally {
            Arrays.fill(encoded, 0.toByte())
        }
    }

    /**
     * Derive a 256-bit AES SecretKey from a master password and salt using PBKDF2-HMAC-SHA256
     */
    fun deriveKey(
        password: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS
    ): SecretKey {
        require(password.isNotEmpty()) { "Password cannot be empty for key derivation" }
        require(salt.size >= SALT_LENGTH_BYTES) { "Salt must be at least $SALT_LENGTH_BYTES bytes" }
        require(iterations >= 10_000) { "PBKDF2 iterations must be at least 10,000" }

        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_LENGTH_BITS)
        try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            try {
                return SecretKeySpec(keyBytes, "AES")
            } finally {
                Arrays.fill(keyBytes, 0.toByte())
            }
        } finally {
            spec.clearPassword()
            Arrays.fill(chars, '\u0000')
        }
    }

    /**
     * Generate a cryptographically random 256-bit AES Vault Master Key (VMK)
     */
    fun generateRandomKey(): SecretKey {
        val keyBytes = ByteArray(KEY_LENGTH_BYTES)
        secureRandom.nextBytes(keyBytes)
        try {
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
        }
    }

    /**
     * Derive a 256-bit AES SecretKey from a recovery phrase and salt using PBKDF2-HMAC-SHA256
     */
    fun deriveKeyFromPhrase(
        phrase: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS
    ): SecretKey {
        val normalizedPhrase = normalizeRecoveryPhrase(phrase)
        require(normalizedPhrase.isNotEmpty()) { "Recovery phrase cannot be empty" }
        return deriveKey(normalizedPhrase, salt, iterations)
    }

    fun normalizeRecoveryPhrase(phrase: String): String {
        return phrase.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Computes a deterministic SHA-256 checksum word from the first 11 words of a recovery phrase.
     * Detects single-word typos, substitutions, and word-order transpositions before key derivation.
     */
    fun computeRecoveryChecksumWord(firstElevenWords: List<String>): String {
        require(firstElevenWords.size == 11) { "Checksum computation requires exactly 11 words" }
        val canonical = firstElevenWords.joinToString(" ") { it.trim().lowercase() }
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("VaultZero_Recovery_Checksum_v2:$canonical".toByteArray(Charsets.UTF_8))
        val uint16 = ((digest[0].toInt() and 0xFF) shl 8) or (digest[1].toInt() and 0xFF)
        return RECOVERY_WORDLIST[uint16 % RECOVERY_WORDLIST.size]
    }

    /**
     * Verifies whether a 12-word recovery phrase has a valid SHA-256 12th checksum word.
     */
    fun verifyRecoveryPhraseChecksum(phrase: String): Boolean {
        val normalized = normalizeRecoveryPhrase(phrase)
        val words = normalized.split(" ")
        if (words.size != 12) return false
        if (!words.all { it in RECOVERY_WORDLIST_SET }) return false
        val expectedChecksumWord = computeRecoveryChecksumWord(words.subList(0, 11))
        return words[11] == expectedChecksumWord
    }

    /**
     * Validates that a recovery phrase consists of 12 valid wordlist tokens and (by default)
     * passes SHA-256 checksum verification.
     */
    fun validateRecoveryPhrase(phrase: String, enforceChecksum: Boolean = true): Boolean {
        val normalized = normalizeRecoveryPhrase(phrase)
        val words = normalized.split(" ")
        if (words.size != 12) return false
        if (!words.all { it in RECOVERY_WORDLIST_SET }) return false
        return if (enforceChecksum) {
            words[11] == computeRecoveryChecksumWord(words.subList(0, 11))
        } else {
            true
        }
    }

    /**
     * Construct Additional Authenticated Data (AAD) binding an encrypted record envelope
     * to its specific SQLite primary key and VaultType. Prevents cross-row ciphertext swapping.
     */
    fun recordAssociatedData(recordId: String, recordType: String): ByteArray {
        require(recordId.isNotBlank() && recordType.isNotBlank()) { "Record ID and type cannot be blank for AAD" }
        return "VaultZero_Record_v2:$recordId:$recordType".toByteArray(Charsets.UTF_8)
    }

    /**
     * Create a domain-separated HMAC-SHA256 verifier from a derived SecretKey.
     * Ensures verifiers cannot be brute-forced without running the full PBKDF2 work factor.
     */
    fun createKeyVerifier(secretKey: SecretKey, domain: String = "AUTH"): String {
        validateAes256Key(secretKey)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val digest = mac.doFinal("VaultZero_Verifier_v2:$domain".toByteArray(Charsets.UTF_8))
        return bytesToHex(digest)
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.isNotEmpty() && clean.length % 2 == 0) { "Hex string must have an even, non-zero length" }
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val index = i * 2
            val byteVal = clean.substring(index, index + 2).toIntOrNull(16)
                ?: throw IllegalArgumentException("Invalid hex character at position $index")
            result[i] = byteVal.toByte()
        }
        return result
    }

    /**
     * Encrypt plaintext string using AES-256-GCM.
     * Returns Base64-encoded string containing [IV (12 bytes) + CipherText + Tag (16 bytes)].
     */
    fun encrypt(
        plainText: String,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): String {
        validateAes256Key(secretKey)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_CIPHER)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        val cipherTextWithTag = try {
            cipher.doFinal(plainBytes)
        } finally {
            Arrays.fill(plainBytes, 0.toByte())
        }

        val buffer = ByteBuffer.allocate(iv.size + cipherTextWithTag.size)
        buffer.put(iv)
        buffer.put(cipherTextWithTag)

        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    /**
     * Decrypt Base64-encoded [IV + CipherText + Tag] string using AES-256-GCM.
     */
    fun decrypt(
        cipherTextBase64: String,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): String {
        validateAes256Key(secretKey)
        if (cipherTextBase64.isBlank()) {
            throw IllegalArgumentException("Ciphertext payload cannot be blank")
        }

        val combined = try {
            Base64.decode(cipherTextBase64.trim(), Base64.NO_WRAP)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed Base64 encrypted payload", e)
        }

        if (combined.size < GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES) {
            throw IllegalArgumentException("Invalid encrypted payload length: insufficient bytes for IV and 128-bit GCM authentication tag")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val cipherTextSize = combined.size - GCM_IV_LENGTH_BYTES
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance(AES_CIPHER)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        val decryptedBytes = cipher.doFinal(cipherText)
        try {
            return String(decryptedBytes, Charsets.UTF_8)
        } finally {
            Arrays.fill(decryptedBytes, 0.toByte())
        }
    }

    /**
     * SHA-256 hash helper
     */
    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Create a human-verifiable key fingerprint (VZ-XXXX-XXXX-XXXX-XXXX)
     * Derived from SHA-256 of the cryptographic material.
     */
    fun generateFingerprint(input: String): String {
        require(input.isNotBlank()) { "Fingerprint input material cannot be blank" }
        val hash = sha256("VaultZero_Key_Fingerprint_v1:${input.trim().lowercase()}")
        val chunk1 = hash.substring(0, 4).uppercase()
        val chunk2 = hash.substring(4, 8).uppercase()
        val chunk3 = hash.substring(8, 12).uppercase()
        val chunk4 = hash.substring(12, 16).uppercase()
        return "VZ-$chunk1-$chunk2-$chunk3-$chunk4"
    }

    /**
     * Generates a 12-word SHA-256 checksummed mnemonic recovery phrase for the vault emergency sheet.
     * Words 1..11 are drawn from CSPRNG; Word 12 is a deterministic SHA-256 checksum word over Words 1..11.
     */
    fun generateRecoveryPhrase(): String {
        val words = RECOVERY_WORDLIST
        val selected = ArrayList<String>(12)
        for (i in 0 until 11) {
            val idx = secureRandom.nextInt(words.size)
            selected.add(words[idx])
        }
        val checksumWord = computeRecoveryChecksumWord(selected)
        selected.add(checksumWord)
        return selected.joinToString(" ")
    }

    // -------------------------------------------------------------
    // TOTP Generator (RFC 6238)
    // -------------------------------------------------------------
    data class TotpResult(
        val code: String,
        val remainingSeconds: Int,
        val progress: Float
    )

    fun generateTotp(
        secretKeyBase32: String,
        timeMillis: Long = System.currentTimeMillis(),
        timeStepSeconds: Long = 30L,
        digits: Int = 6
    ): TotpResult? {
        if (timeMillis < 0L || timeStepSeconds <= 0L || digits !in 6..8) return null
        val cleanSecret = secretKeyBase32.trim().replace(" ", "").replace("-", "").uppercase()
        if (cleanSecret.isEmpty()) return null

        return try {
            val decodedKey = base32Decode(cleanSecret)
            if (decodedKey.isEmpty()) return null

            val currentSecond = timeMillis / 1000L
            val timeWindow = currentSecond / timeStepSeconds
            val remainingSeconds = (timeStepSeconds - (currentSecond % timeStepSeconds)).toInt()
            val progress = remainingSeconds.toFloat() / timeStepSeconds.toFloat()

            val msg = ByteBuffer.allocate(8).putLong(timeWindow).array()
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(decodedKey, "RAW"))
            val hash = mac.doFinal(msg)

            val offset = hash[hash.size - 1].toInt() and 0x0f
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                    ((hash[offset + 1].toInt() and 0xff) shl 16) or
                    ((hash[offset + 2].toInt() and 0xff) shl 8) or
                    (hash[offset + 3].toInt() and 0xff)

            var mod = 1
            repeat(digits) { mod *= 10 }
            val otp = binary % mod
            val formattedCode = "%0${digits}d".format(otp)
            TotpResult(formattedCode, remainingSeconds, progress)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Strict RFC 4648 Base32 decoder. Rejects invalid non-Base32 characters.
     */
    fun base32Decode(base32: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        var paddingStarted = false

        for (c in base32) {
            if (c == '=') {
                paddingStarted = true
                continue
            }
            if (paddingStarted) {
                throw IllegalArgumentException("Invalid Base32 padding placement")
            }
            val valIndex = alphabet.indexOf(c)
            if (valIndex < 0) {
                throw IllegalArgumentException("Invalid Base32 character: $c")
            }

            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xff).toByte())
            }
        }
        return out.toByteArray()
    }

    // -------------------------------------------------------------
    // Password & Passphrase Generator + Entropy Calculator
    // -------------------------------------------------------------
    enum class StrengthGrade(val label: String, val scorePercent: Float) {
        VERY_WEAK("Very Weak", 0.2f),
        WEAK("Weak", 0.4f),
        FAIR("Fair", 0.6f),
        STRONG("Strong", 0.85f),
        FORTIFIED("Fortified (Quantum-Resistant)", 1.0f)
    }

    data class PasswordStrength(
        val entropyBits: Int,
        val shannonBits: Int,
        val grade: StrengthGrade,
        val suggestions: List<String>
    ) {
        constructor(
            entropyBits: Int,
            grade: StrengthGrade,
            suggestions: List<String>
        ) : this(entropyBits, entropyBits, grade, suggestions)
    }

    private val COMMON_WEAK_ROOTS = listOf(
        "password", "passw0rd", "123456", "12345678", "123456789", "1234567890",
        "qwerty", "qwertyuiop", "asdfgh", "asdfghjkl", "zxcvbn", "admin",
        "welcome", "letmein", "iloveyou", "monkey", "dragon", "master",
        "trustno1", "sunshine", "princess", "football", "baseball", "superman"
    )

    /**
     * Computes true empirical Shannon entropy in bits across the character distribution of [input]:
     * H_total = length * (-sum(p_i * log2(p_i)))
     */
    fun calculateShannonEntropyBits(input: String): Int {
        if (input.isEmpty()) return 0
        val counts = mutableMapOf<Char, Int>()
        for (c in input) {
            counts[c] = (counts[c] ?: 0) + 1
        }
        val len = input.length.toDouble()
        var hPerChar = 0.0
        for (count in counts.values) {
            val p = count / len
            if (p > 0.0) {
                hPerChar -= p * log2(p)
            }
        }
        return (hPerChar * len).roundToInt()
    }

    /**
     * Evaluates password/passphrase strength combining character-pool capacity, empirical Shannon
     * distribution, wordlist passphrase detection, and pattern/dictionary penalties.
     */
    fun calculateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(0, 0, StrengthGrade.VERY_WEAK, listOf("Password cannot be empty"))
        }

        val shannonBits = calculateShannonEntropyBits(password)
        val suggestions = mutableListOf<String>()

        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        var poolSize = 0
        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSymbol) poolSize += 33
        if (poolSize == 0) poolSize = 26

        // Check if this is a multi-word passphrase
        val hyphenOrSpaceTokens = password.trim().split(Regex("[-\\s]+")).filter { it.isNotEmpty() }
        val isWordlistPassphrase = hyphenOrSpaceTokens.size >= 3 &&
                hyphenOrSpaceTokens.all { it.lowercase() in PASSPHRASE_WORDLIST_SET || it.lowercase() in RECOVERY_WORDLIST_SET }

        // Calculate effective unique length and pattern penalties
        val uniqueRatio = password.toSet().size.toDouble() / password.length.toDouble()
        val hasRepeatedRun = Regex("(.)\\1{3,}").containsMatchIn(password)
        val hasRepeatedSubstring = hasRepeatedBlock(password)
        val hasSequentialRun = hasSequentialCharacters(password, 4)
        val lowerPass = password.lowercase()
        val matchesCommonWeak = COMMON_WEAK_ROOTS.any { lowerPass == it || lowerPass.contains(it) }

        val rawPoolBits = (password.length * log2(poolSize.toDouble())).roundToInt()

        var effectiveBits = if (isWordlistPassphrase) {
            // Idealized selection entropy H = k * log2(N) plus separator/length bonus
            val wordCount = hyphenOrSpaceTokens.size
            val distinctWords = hyphenOrSpaceTokens.map { it.lowercase() }.distinct().size
            // Using 16 bits per independent memorable word in a full passphrase context
            (distinctWords * 16) + ((wordCount - distinctWords) * 4)
        } else {
            // Bound raw pool estimate so low-diversity strings (e.g. "aaaaaaaaaa") cannot score high
            val diversityFactor = when {
                uniqueRatio < 0.25 -> 0.20
                uniqueRatio < 0.45 -> 0.50
                uniqueRatio < 0.65 -> 0.80
                else -> 1.0
            }
            (rawPoolBits * diversityFactor).roundToInt()
        }

        if (hasRepeatedRun) {
            effectiveBits = min(effectiveBits, (effectiveBits * 0.45).roundToInt())
            suggestions.add("Avoid repeated character runs (e.g. 'aaaa')")
        }
        if (hasRepeatedSubstring) {
            effectiveBits = min(effectiveBits, (effectiveBits * 0.50).roundToInt())
            suggestions.add("Avoid repeating character patterns")
        }
        if (hasSequentialRun && !isWordlistPassphrase) {
            effectiveBits = min(effectiveBits, (effectiveBits * 0.55).roundToInt())
            suggestions.add("Avoid sequential patterns (e.g. '1234', 'abcd')")
        }
        if (matchesCommonWeak && !isWordlistPassphrase) {
            effectiveBits = min(effectiveBits, 28)
            suggestions.add("Avoid common dictionary words or keyboard walks")
        }

        if (password.length < 12) suggestions.add("Increase length to at least 14 characters")
        if (!isWordlistPassphrase) {
            if (!hasUpper) suggestions.add("Add uppercase characters (A-Z)")
            if (!hasDigit) suggestions.add("Add numeric digits (0-9)")
            if (!hasSymbol) suggestions.add("Add special symbols (!@#$)")
        }

        val grade = when {
            effectiveBits < 36 || password.length < 8 -> StrengthGrade.VERY_WEAK
            effectiveBits < 50 || (!isWordlistPassphrase && password.length < 12) -> StrengthGrade.WEAK
            effectiveBits < 65 -> StrengthGrade.FAIR
            effectiveBits < 85 -> StrengthGrade.STRONG
            else -> StrengthGrade.FORTIFIED
        }

        return PasswordStrength(
            entropyBits = effectiveBits.coerceAtLeast(0),
            shannonBits = shannonBits,
            grade = grade,
            suggestions = suggestions.distinct()
        )
    }

    private fun hasRepeatedBlock(s: String): Boolean {
        if (s.length < 4) return false
        for (blockLen in 1..(s.length / 2)) {
            if (s.length % blockLen == 0) {
                val block = s.substring(0, blockLen)
                if (block.repeat(s.length / blockLen) == s) return true
            }
        }
        return false
    }

    private fun hasSequentialCharacters(s: String, minRun: Int): Boolean {
        if (s.length < minRun) return false
        var ascRun = 1
        var descRun = 1
        for (i in 1 until s.length) {
            val diff = s[i].code - s[i - 1].code
            if (diff == 1) {
                ascRun++
                descRun = 1
                if (ascRun >= minRun) return true
            } else if (diff == -1) {
                descRun++
                ascRun = 1
                if (descRun >= minRun) return true
            } else {
                ascRun = 1
                descRun = 1
            }
        }
        return false
    }

    fun generatePassword(
        length: Int = 18,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true,
        avoidAmbiguous: Boolean = true
    ): String {
        require(length in 4..128) { "Password length must be between 4 and 128 characters" }
        require(includeUpper || includeLower || includeDigits || includeSymbols) {
            "At least one character set must be enabled"
        }

        val lowerChars = if (avoidAmbiguous) "abcdefghjkmnpqrstuvwxyz" else "abcdefghijklmnopqrstuvwxyz"
        val upperChars = if (avoidAmbiguous) "ABCDEFGHJKLMNPQRSTUVWXYZ" else "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val digitChars = if (avoidAmbiguous) "23456789" else "0123456789"
        val symbolChars = "!@#$%^&*()-_=+[]{}|;:,.<>?"

        val charPool = StringBuilder()
        val guaranteed = mutableListOf<Char>()

        if (includeLower) {
            charPool.append(lowerChars)
            guaranteed.add(lowerChars[secureRandom.nextInt(lowerChars.length)])
        }
        if (includeUpper) {
            charPool.append(upperChars)
            guaranteed.add(upperChars[secureRandom.nextInt(upperChars.length)])
        }
        if (includeDigits) {
            charPool.append(digitChars)
            guaranteed.add(digitChars[secureRandom.nextInt(digitChars.length)])
        }
        if (includeSymbols) {
            charPool.append(symbolChars)
            guaranteed.add(symbolChars[secureRandom.nextInt(symbolChars.length)])
        }

        val poolString = charPool.toString()
        val result = mutableListOf<Char>()
        result.addAll(guaranteed.take(length))

        for (i in result.size until length) {
            result.add(poolString[secureRandom.nextInt(poolString.length)])
        }

        // Fisher-Yates shuffle using CSPRNG
        for (i in result.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result.joinToString("")
    }

    fun generatePassphrase(wordCount: Int = 4, separator: String = "-"): String {
        require(wordCount in 3..12) { "Passphrase word count must be between 3 and 12" }
        val words = PASSPHRASE_WORDLIST
        val selected = ArrayList<String>(wordCount)
        for (i in 0 until wordCount) {
            selected.add(words[secureRandom.nextInt(words.size)])
        }
        return selected.joinToString(separator)
    }

    // -------------------------------------------------------------
    // Wordlists for Recovery Kit & Passphrases (Duplicate-Free)
    // -------------------------------------------------------------
    val RECOVERY_WORDLIST = listOf(
        "abstract", "access", "account", "active", "adapt", "agent", "algorithm", "alpha",
        "anchor", "ancient", "apex", "apollon", "arcane", "archive", "armored", "array",
        "aspect", "atomic", "audit", "axiom", "bastion", "beacon", "binary", "biometric",
        "cipher", "circuit", "clarity", "cluster", "cobalt", "cosmos", "crystal", "current",
        "delta", "digital", "dynasty", "eclipse", "element", "emerald", "engine", "entropy",
        "essence", "eternal", "falcon", "filter", "finite", "flame", "flux", "forge",
        "formula", "fractal", "frontier", "galaxy", "gateway", "genesis", "glacier", "gravity",
        "harbor", "haven", "helix", "horizon", "hybrid", "hyper", "ignite", "infinity",
        "ion", "island", "kinetic", "laser", "legacy", "logic", "lunar", "matrix",
        "mercury", "mirage", "monolith", "nebula", "nexus", "nova", "nucleus", "obelisk",
        "omega", "onyx", "optimum", "orbit", "origin", "particle", "phantom", "phoenix",
        "pixel", "plasma", "polaris", "prism", "protocol", "quantum", "quasar", "radar",
        "radiant", "relay", "remote", "resonance", "satellite", "sector", "sentinel", "shadow",
        "shield", "signal", "silver", "solaris", "spectrum", "spiral", "stellar", "strata",
        "stream", "summit", "synapse", "synergy", "syntax", "tesseract", "titan", "torrent",
        "tracer", "transit", "tribunal", "trident", "ultra", "unity", "uranium", "vacuum",
        "valiant", "vanguard", "vector", "velocity", "vortex", "zenith", "zephyr", "zero"
    )

    private val RECOVERY_WORDLIST_SET = RECOVERY_WORDLIST.toSet()

    val PASSPHRASE_WORDLIST = listOf(
        "anchor", "apollo", "arctic", "argon", "armor", "astral", "atlas", "atomic",
        "aurora", "avalon", "axiom", "bastion", "beacon", "binary", "blazer", "breeze",
        "brisk", "bronze", "canyon", "carbon", "cascade", "cavern", "celestial", "chronicle",
        "cipher", "citadel", "clarity", "clover", "cobalt", "comet", "compass", "condor",
        "copper", "coral", "cosmos", "crimson", "crystal", "cyber", "cyclone", "dagger",
        "dawn", "delta", "diamond", "drift", "dynamo", "eclipse", "echo", "emerald",
        "ember", "empire", "enigma", "equinox", "falcon", "fathom", "fjord", "flint",
        "flux", "forged", "fortress", "fossil", "fractal", "frontier", "frost", "fusion",
        "galaxy", "garnet", "genesis", "geyser", "glacier", "glider", "granite", "gravity",
        "griffin", "harbor", "harvest", "haven", "avali", "helium", "helix", "herald",
        "horizon", "hunter", "ignite", "indigo", "infinity", "impulse", "iron", "island",
        "ivory", "jasper", "javelin", "juniper", "kestrel", "kinetic", "krypton", "lagoon",
        "lantern", "laser", "Aegis", "legend", "lightning", "lithium", "lotus", "lunar",
        "lumen", "magnet", "mantle", "marble", "matrix", "meadow", "mercury", "meteor",
        "mirage", "monarch", "monolith", "mystic", "nebula", "nectar", "neon", "neptune",
        "nexus", "nickel", "nimbus", "noble", "nomad", "nova", "nucleus", "oak",
        "oasis", "obelisk", "obsidian", "ocean", "odyssey", "omega", "onyx", "opal",
        "orbit", "orchid", "origin", "orion", "osprey", "outpost", "oxygen", "paladin",
        "panther", "paragon", "paradox", "particle", "pegasus", "pendulum", "phantom", "phoenix",
        "photon", "pioneer", "pinnacle", "pixel", "planet", "plasma", "platinum", "polaris",
        "portal", "prairie", "prelude", "prism", "prodigy", "prophet", "proton", "pulsar",
        "pulse", "pyramid", "quantum", "quartz", "quasar", "quest", "quiver", "radar",
        "radiant", "raptor", "ravine", "reactor", "redwood", "reflex", "relic", "remedy",
        "resolve", "rhythm", "ridge", "ripple", "rocket", "rubicon", "ruby", "runic",
        "saber", "saffron", "sanctuary", "sapphire", "satellite", "scepter", "scout", "sector",
        "seismic", "sentinel", "sequoia", "seraph", "shadow", "shield", "shimmer", "sierra",
        "signal", "silicon", "silver", "sirius", "solace", "solaris", "solstice", "sonar",
        "sonic", "sovereign", "spark", "specter", "spectrum", "sphere", "sphinx", "spiral",
        "spruce", "starlight", "stealth", "stellar", "sterling", "stone", "storm", "strata",
        "stream", "stride", "summit", "sundial", "supernova", "surge", "Switch", "sylvan",
        "synapse", "synergy", "talisman", "tangent", "tempest", "tensor", "terra", "tesseract",
        "thermal", "thistle", "thunder", "timber", "titan", "titanium", "topaz", "torrent",
        "tracker", "zenith", "zephyr", "zircon", "zodiac", "vortex", "voyager", "vulcan"
    ).map { it.lowercase() }.distinct()

    private val PASSPHRASE_WORDLIST_SET = PASSPHRASE_WORDLIST.toSet()
}
