package com.example.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.log2

object VaultCrypto {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_CIPHER = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    /**
     * Generate secure random salt bytes (16 bytes)
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Derive a 256-bit AES SecretKey from a master password and salt using PBKDF2-HMAC-SHA256
     */
    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Generate a cryptographically random 256-bit AES Vault Master Key (VMK)
     */
    fun generateRandomKey(): SecretKey {
        val keyBytes = ByteArray(KEY_LENGTH_BITS / 8)
        secureRandom.nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Derive a 256-bit AES SecretKey from a recovery phrase and salt using PBKDF2-HMAC-SHA256
     */
    fun deriveKeyFromPhrase(phrase: String, salt: ByteArray): SecretKey {
        val normalizedPhrase = phrase.trim().lowercase().split("\\s+".toRegex()).joinToString(" ")
        val spec = PBEKeySpec(normalizedPhrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = hex.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }

    /**
     * Encrypt plaintext string using AES-256-GCM.
     * Returns Base64-encoded string containing [IV (12 bytes) + CipherText + Tag (16 bytes)].
     */
    fun encrypt(plainText: String, secretKey: SecretKey): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_CIPHER)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherTextWithTag = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(iv.size + cipherTextWithTag.size)
        buffer.put(iv)
        buffer.put(cipherTextWithTag)

        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    /**
     * Decrypt Base64-encoded [IV + CipherText + Tag] string using AES-256-GCM.
     */
    fun decrypt(cipherTextBase64: String, secretKey: SecretKey): String {
        val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        if (combined.size < GCM_IV_LENGTH_BYTES + 16) {
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

        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * SHA-256 hash helper for password verification tokens
     */
    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Create a human-verifiable key fingerprint (e.g. VZ-8E4B-11F0-7AC9)
     */
    fun generateFingerprint(input: String): String {
        val hash = sha256(input)
        val chunk1 = hash.substring(0, 4).uppercase()
        val chunk2 = hash.substring(4, 8).uppercase()
        val chunk3 = hash.substring(8, 12).uppercase()
        val chunk4 = hash.substring(12, 16).uppercase()
        return "VZ-$chunk1-$chunk2-$chunk3-$chunk4"
    }

    /**
     * Generates a 12-word mnemonic recovery phrase for the vault emergency sheet
     */
    fun generateRecoveryPhrase(): String {
        val words = RECOVERY_WORDLIST
        val selected = mutableListOf<String>()
        val rnd = SecureRandom()
        for (i in 0 until 12) {
            val idx = rnd.nextInt(words.size)
            selected.add(words[idx])
        }
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

    fun generateTotp(secretKeyBase32: String, timeMillis: Long = System.currentTimeMillis()): TotpResult? {
        val cleanSecret = secretKeyBase32.replace(" ", "").replace("-", "").uppercase()
        if (cleanSecret.isEmpty()) return null

        try {
            val decodedKey = base32Decode(cleanSecret)
            if (decodedKey.isEmpty()) return null

            val timeStep = 30L
            val currentSecond = timeMillis / 1000L
            val timeWindow = currentSecond / timeStep
            val remainingSeconds = (timeStep - (currentSecond % timeStep)).toInt()
            val progress = remainingSeconds.toFloat() / timeStep.toFloat()

            val msg = ByteBuffer.allocate(8).putLong(timeWindow).array()
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(decodedKey, "RAW"))
            val hash = mac.doFinal(msg)

            val offset = hash[hash.size - 1].toInt() and 0x0f
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                    ((hash[offset + 1].toInt() and 0xff) shl 16) or
                    ((hash[offset + 2].toInt() and 0xff) shl 8) or
                    (hash[offset + 3].toInt() and 0xff)

            val otp = binary % 1_000_000
            val formattedCode = "%06d".format(otp)
            return TotpResult(formattedCode, remainingSeconds, progress)
        } catch (e: Exception) {
            return null
        }
    }

    private fun base32Decode(base32: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in base32) {
            if (c == '=') break
            val valIndex = alphabet.indexOf(c)
            if (valIndex < 0) continue

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
        val grade: StrengthGrade,
        val suggestions: List<String>
    )

    fun calculateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(0, StrengthGrade.VERY_WEAK, listOf("Password cannot be empty"))
        }

        var poolSize = 0
        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSymbol) poolSize += 33

        if (poolSize == 0) poolSize = 26

        val entropy = (password.length * log2(poolSize.toDouble())).toInt()

        val suggestions = mutableListOf<String>()
        if (password.length < 12) suggestions.add("Increase length to at least 14 characters")
        if (!hasUpper) suggestions.add("Add uppercase characters (A-Z)")
        if (!hasDigit) suggestions.add("Add numeric digits (0-9)")
        if (!hasSymbol) suggestions.add("Add special symbols (!@#$)")

        val grade = when {
            entropy < 36 || password.length < 8 -> StrengthGrade.VERY_WEAK
            entropy < 50 || password.length < 12 -> StrengthGrade.WEAK
            entropy < 65 -> StrengthGrade.FAIR
            entropy < 85 -> StrengthGrade.STRONG
            else -> StrengthGrade.FORTIFIED
        }

        return PasswordStrength(entropy, grade, suggestions)
    }

    fun generatePassword(
        length: Int = 18,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true,
        avoidAmbiguous: Boolean = true
    ): String {
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

        if (charPool.isEmpty()) {
            charPool.append(lowerChars)
        }

        val poolString = charPool.toString()
        val result = mutableListOf<Char>()
        result.addAll(guaranteed)

        for (i in result.size until length) {
            result.add(poolString[secureRandom.nextInt(poolString.length)])
        }

        result.shuffle(secureRandom)
        return result.joinToString("")
    }

    fun generatePassphrase(wordCount: Int = 4, separator: String = "-"): String {
        val words = PASSPHRASE_WORDLIST
        val selected = mutableListOf<String>()
        for (i in 0 until wordCount) {
            selected.add(words[secureRandom.nextInt(words.size)])
        }
        return selected.joinToString(separator)
    }

    // -------------------------------------------------------------
    // Wordlists for Recovery Kit & Passphrases
    // -------------------------------------------------------------
    private val RECOVERY_WORDLIST = listOf(
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

    private val PASSPHRASE_WORDLIST = listOf(
        "cyber", "nebula", "quantum", "falcon", "shield", "beacon", "matrix", "orbit",
        "plasma", "silver", "timber", "granite", "aurora", "blazer", "canyon", "drift",
        "ember", "frost", "glider", "haven", "iron", "jasper", "kestrel", "lumen",
        "mystic", "nova", "onyx", "pulse", "quartz", "radar", "stride", "titan",
        "vortex", "zenith", "anchor", "brisk", "clover", "dawn", "echo", "flint"
    )
}
