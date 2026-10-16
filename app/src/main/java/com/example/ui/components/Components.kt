package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.VaultCrypto
import com.example.model.VaultType
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.DangerRed
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.WarningAmber

@Composable
fun VaultTypeIcon(type: VaultType, modifier: Modifier = Modifier) {
    val (icon, tint, bg) = when (type) {
        VaultType.LOGIN -> Triple(Icons.Default.VpnKey, CyberEmerald, CyberEmerald.copy(alpha = 0.15f))
        VaultType.CARD -> Triple(Icons.Default.CreditCard, CyberCyan, CyberCyan.copy(alpha = 0.15f))
        VaultType.API_SECRET -> Triple(Icons.Default.Code, RoyalPurple, RoyalPurple.copy(alpha = 0.15f))
        VaultType.CRYPTO_WALLET -> Triple(Icons.Default.CurrencyBitcoin, WarningAmber, WarningAmber.copy(alpha = 0.15f))
        VaultType.NOTE -> Triple(Icons.Default.Description, Color(0xFF60A5FA), Color(0xFF60A5FA).copy(alpha = 0.15f))
        VaultType.IDENTITY -> Triple(Icons.Default.Badge, Color(0xFFF472B6), Color(0xFFF472B6).copy(alpha = 0.15f))
    }

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.displayName,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun StrengthMeter(
    strength: VaultCrypto.PasswordStrength,
    modifier: Modifier = Modifier
) {
    val color = when (strength.grade) {
        VaultCrypto.StrengthGrade.VERY_WEAK -> DangerRed
        VaultCrypto.StrengthGrade.WEAK -> DangerRed.copy(alpha = 0.85f)
        VaultCrypto.StrengthGrade.FAIR -> WarningAmber
        VaultCrypto.StrengthGrade.STRONG -> CyberCyan
        VaultCrypto.StrengthGrade.FORTIFIED -> CyberEmerald
    }

    val animatedColor by animateColorAsState(targetValue = color, label = "color")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(animatedColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strength.grade.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = animatedColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "${strength.entropyBits} bits entropy",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4-segment progress bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val activeSegments = when (strength.grade) {
                VaultCrypto.StrengthGrade.VERY_WEAK -> 1
                VaultCrypto.StrengthGrade.WEAK -> 2
                VaultCrypto.StrengthGrade.FAIR -> 3
                VaultCrypto.StrengthGrade.STRONG -> 4
                VaultCrypto.StrengthGrade.FORTIFIED -> 4
            }

            for (i in 1..4) {
                val isActive = i <= activeSegments
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isActive) animatedColor else MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.3f
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun TotpLiveBadge(
    totpResult: VaultCrypto.TotpResult,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = totpResult.progress
    val warning = totpResult.remainingSeconds <= 5
    val ringColor = if (warning) DangerRed else CyberCyan

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, ringColor.copy(alpha = 0.3f)),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCopy() }
            .testTag("totp_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(24.dp),
                    color = ringColor,
                    strokeWidth = 2.5.dp,
                    trackColor = ringColor.copy(alpha = 0.2f)
                )
                Text(
                    text = "${totpResult.remainingSeconds}",
                    fontSize = 9.sp,
                    fontFamily = JetBrainsMonoFamily,
                    color = ringColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "2FA AUTH CODE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val formatted = if (totpResult.code.length == 6) {
                    "${totpResult.code.substring(0, 3)} ${totpResult.code.substring(3)}"
                } else totpResult.code
                Text(
                    text = formatted,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    color = CyberEmerald
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy 2FA code",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SensitiveValueRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    isMonospace: Boolean = true,
    isMaskable: Boolean = true
) {
    var isRevealed by remember { mutableStateOf(!isMaskable) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMaskable) {
                    IconButton(
                        onClick = { isRevealed = !isRevealed },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isRevealed) "Hide" else "Reveal",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp).testTag("copy_${label.lowercase()}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        tint = CyberEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        val displayText = if (isRevealed) value else "•".repeat(value.length.coerceIn(8, 20))
        Text(
            text = displayText,
            style = if (isMonospace) MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFamily) else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ZeroKnowledgeSecurityBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CyberEmerald.copy(alpha = 0.12f))
            .border(1.dp, CyberEmerald.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = CyberEmerald,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "ZERO-KNOWLEDGE • AES-256-GCM",
            fontSize = 10.sp,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            color = CyberEmerald,
            letterSpacing = 0.5.sp
        )
    }
}
