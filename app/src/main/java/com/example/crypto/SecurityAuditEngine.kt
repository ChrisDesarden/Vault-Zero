package com.example.crypto

import com.example.model.AuditReport
import com.example.model.LoginPayload
import com.example.model.Severity
import com.example.model.VaultItem
import com.example.model.VaultType
import com.example.model.Vulnerability
import com.example.model.VulnerabilityType

object SecurityAuditEngine {

    private const val NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000

    fun analyzeVault(items: List<VaultItem>): AuditReport {
        val vulnerabilities = mutableListOf<Vulnerability>()
        var weakCount = 0
        var reusedCount = 0
        var staleCount = 0
        var missing2faCount = 0

        val now = System.currentTimeMillis()
        val loginItems = items.filter { it.type == VaultType.LOGIN }

        // 1. Password reuse check
        val passwordOccurrences = mutableMapOf<String, MutableList<VaultItem>>()
        for (item in loginItems) {
            val payload = item.payload as? LoginPayload ?: continue
            if (payload.password.isNotEmpty()) {
                passwordOccurrences.getOrPut(payload.password) { mutableListOf() }.add(item)
            }
        }

        for ((password, itemList) in passwordOccurrences) {
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

        // 2. Weak, Stale, and Missing 2FA checks
        for (item in loginItems) {
            val payload = item.payload as? LoginPayload ?: continue

            // Weak password check
            val strength = VaultCrypto.calculateStrength(payload.password)
            if (strength.grade == VaultCrypto.StrengthGrade.VERY_WEAK || strength.grade == VaultCrypto.StrengthGrade.WEAK) {
                weakCount++
                vulnerabilities.add(
                    Vulnerability(
                        itemId = item.id,
                        itemTitle = item.title,
                        type = VulnerabilityType.WEAK_PASSWORD,
                        severity = Severity.CRITICAL,
                        description = "Password strength is ${strength.grade.label} (${strength.entropyBits} bits entropy)",
                        recommendation = "Upgrade to a 16+ character mixed-case password with symbols"
                    )
                )
            }

            // Stale check
            if (now - item.updatedAt > NINETY_DAYS_MS) {
                staleCount++
                val daysOld = ((now - item.updatedAt) / (24 * 60 * 60 * 1000)).toInt()
                vulnerabilities.add(
                    Vulnerability(
                        itemId = item.id,
                        itemTitle = item.title,
                        type = VulnerabilityType.STALE_PASSWORD,
                        severity = Severity.INFO,
                        description = "Password hasn't been rotated in $daysOld days",
                        recommendation = "Consider cycling critical credentials periodically"
                    )
                )
            }

            // Missing 2FA check
            if (payload.totpSecret.isEmpty()) {
                missing2faCount++
                vulnerabilities.add(
                    Vulnerability(
                        itemId = item.id,
                        itemTitle = item.title,
                        type = VulnerabilityType.MISSING_2FA,
                        severity = Severity.WARNING,
                        description = "No 2FA / TOTP Authenticator key configured",
                        recommendation = "Add a 2FA secret key to enable built-in one-time codes"
                    )
                )
            }
        }

        // Calculate score
        var score = 100
        score -= (weakCount * 18)
        score -= (reusedCount * 15)
        score -= (staleCount * 4)
        score -= (missing2faCount * 8)

        if (items.isEmpty()) {
            score = 100
        } else {
            score = score.coerceIn(5, 100)
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
