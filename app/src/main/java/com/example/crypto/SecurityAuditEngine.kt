package com.example.crypto

import com.example.model.AuditReport
import com.example.model.LoginPayload
import com.example.model.Severity
import com.example.model.VaultItem
import com.example.model.VaultType
import com.example.model.Vulnerability
import com.example.model.VulnerabilityType

object SecurityAuditEngine {

    const val NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000L

    fun analyzeVault(
        items: List<VaultItem>,
        nowMillis: Long = System.currentTimeMillis()
    ): AuditReport {
        val vulnerabilities = mutableListOf<Vulnerability>()
        var weakCount = 0
        var reusedCount = 0
        var staleCount = 0
        var missing2faCount = 0

        val loginItems = items.filter { it.type == VaultType.LOGIN }

        // 1. Password reuse check (exact plaintext match across distinct login entries)
        val passwordOccurrences = mutableMapOf<String, MutableList<VaultItem>>()
        for (item in loginItems) {
            val payload = item.payload as? LoginPayload ?: continue
            if (payload.password.isNotEmpty()) {
                passwordOccurrences.getOrPut(payload.password) { mutableListOf() }.add(item)
            }
        }

        for ((_, itemList) in passwordOccurrences) {
            if (itemList.size > 1) {
                reusedCount += itemList.size
                for (item in itemList) {
                    val otherNames = itemList.filter { it.id != item.id }.joinToString(", ") { it.title }
                    vulnerabilities.add(
                        Vulnerability(
                            itemId = item.id,
                            itemTitle = item.title,
                            type = VulnerabilityType.REUSED_PASSWORD,
                            severity = Severity.CRITICAL,
                            description = "Password is also used by: $otherNames",
                            recommendation = "Generate a unique high-entropy password for each service"
                        )
                    )
                }
            }
        }

        // 2. Weak, Stale, and Unconfigured Local TOTP checks
        for (item in loginItems) {
            val payload = item.payload as? LoginPayload ?: continue

            // Weak password check
            val strength = VaultCrypto.calculateStrength(payload.password)
            if (strength.grade == VaultCrypto.StrengthGrade.VERY_WEAK ||
                strength.grade == VaultCrypto.StrengthGrade.WEAK
            ) {
                weakCount++
                vulnerabilities.add(
                    Vulnerability(
                        itemId = item.id,
                        itemTitle = item.title,
                        type = VulnerabilityType.WEAK_PASSWORD,
                        severity = Severity.CRITICAL,
                        description = "Password strength is ${strength.grade.label} (${strength.entropyBits} bits effective entropy)",
                        recommendation = "Upgrade to a 16+ character mixed-case password with symbols or a multi-word passphrase"
                    )
                )
            }

            // Stale check (> 90 days since record last updated; ignores future clock skew)
            val ageMs = nowMillis - item.updatedAt
            if (item.updatedAt > 0L && ageMs > NINETY_DAYS_MS) {
                staleCount++
                val daysOld = (ageMs / (24L * 60 * 60 * 1000)).toInt()
                vulnerabilities.add(
                    Vulnerability(
                        itemId = item.id,
                        itemTitle = item.title,
                        type = VulnerabilityType.STALE_PASSWORD,
                        severity = Severity.INFO,
                        description = "Credential record has not been updated in $daysOld days",
                        recommendation = "Review and rotate long-lived credentials periodically"
                    )
                )
            }

            // Local TOTP configuration check (distinguishes local TOTP secret from external account 2FA status)
            val hasValidLocalTotp = payload.totpSecret.isNotBlank() &&
                    VaultCrypto.generateTotp(payload.totpSecret, nowMillis) != null
            if (!hasValidLocalTotp) {
                missing2faCount++
                vulnerabilities.add(
                    Vulnerability(
                        itemId = item.id,
                        itemTitle = item.title,
                        type = VulnerabilityType.MISSING_2FA,
                        severity = Severity.WARNING,
                        description = if (payload.totpSecret.isBlank()) {
                            "No local TOTP secret stored in VaultZero (external 2FA status unverified)"
                        } else {
                            "Stored TOTP secret is malformed Base32"
                        },
                        recommendation = "If this service supports TOTP 2FA, add a valid Base32 secret key to generate one-time codes"
                    )
                )
            }
        }

        // Calculate deterministic health score
        var score = 100
        score -= (weakCount * 18)
        score -= (reusedCount * 15)
        score -= (staleCount * 4)
        score -= (missing2faCount * 8)

        score = if (loginItems.isEmpty()) {
            100
        } else {
            score.coerceIn(5, 100)
        }

        val grade = when {
            score >= 95 -> "A+"
            score >= 85 -> "A"
            score >= 70 -> "B"
            score >= 50 -> "C"
            else -> "F"
        }

        return AuditReport(
            score = score,
            grade = grade,
            totalChecked = loginItems.size,
            weakCount = weakCount,
            reusedCount = reusedCount,
            staleCount = staleCount,
            missing2faCount = missing2faCount,
            vulnerabilities = vulnerabilities
        )
    }
}
