package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VaultType
import com.example.ui.GeneratorConfig
import com.example.ui.VaultViewModel
import com.example.ui.components.StrengthMeter
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun GeneratorScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.generatorConfig.collectAsState()
    val password by viewModel.generatedPassword.collectAsState()
    val strength by viewModel.passwordStrength.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Password Generator",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Generate high-entropy, mathematically uncrackable credentials",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Result Display Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                CyberEmerald.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GENERATED CREDENTIAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = { viewModel.regeneratePassword() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberEmerald.copy(alpha = 0.15f))
                            .testTag("regenerate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            tint = CyberEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = password,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                StrengthMeter(strength = strength)

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: Copy & Save to Vault
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.copySecurely("Password", password, isSensitive = true)
                        },
                        modifier = Modifier.weight(1f).testTag("copy_generated_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }

                    Button(
                        onClick = {
                            viewModel.startCreateItem(VaultType.LOGIN)
                        },
                        modifier = Modifier.weight(1.2f).testTag("save_generated_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Vault", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Mode Selector: Random vs Passphrase
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = !config.isPassphraseMode,
                onClick = { viewModel.updateGeneratorConfig(config.copy(isPassphraseMode = false)) },
                label = { Text("Random String") },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                    selectedLabelColor = CyberCyan
                )
            )

            FilterChip(
                selected = config.isPassphraseMode,
                onClick = { viewModel.updateGeneratorConfig(config.copy(isPassphraseMode = true)) },
                label = { Text("Memorable Passphrase") },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                    selectedLabelColor = CyberCyan
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Options Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                if (!config.isPassphraseMode) {
                    // Length Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Password Length",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${config.length} chars",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald
                        )
                    }

                    Slider(
                        value = config.length.toFloat(),
                        onValueChange = {
                            viewModel.updateGeneratorConfig(config.copy(length = it.toInt()))
                        },
                        valueRange = 8f..64f,
                        steps = 56,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberEmerald,
                            activeTrackColor = CyberEmerald
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggles
                    GeneratorToggleRow(
                        title = "Uppercase Letters (A-Z)",
                        checked = config.includeUpper,
                        onCheckedChange = { viewModel.updateGeneratorConfig(config.copy(includeUpper = it)) }
                    )
                    GeneratorToggleRow(
                        title = "Lowercase Letters (a-z)",
                        checked = config.includeLower,
                        onCheckedChange = { viewModel.updateGeneratorConfig(config.copy(includeLower = it)) }
                    )
                    GeneratorToggleRow(
                        title = "Numbers (0-9)",
                        checked = config.includeDigits,
                        onCheckedChange = { viewModel.updateGeneratorConfig(config.copy(includeDigits = it)) }
                    )
                    GeneratorToggleRow(
                        title = "Special Symbols (!@#$)",
                        checked = config.includeSymbols,
                        onCheckedChange = { viewModel.updateGeneratorConfig(config.copy(includeSymbols = it)) }
                    )
                    GeneratorToggleRow(
                        title = "Avoid Ambiguous (1, l, 0, O)",
                        checked = config.avoidAmbiguous,
                        onCheckedChange = { viewModel.updateGeneratorConfig(config.copy(avoidAmbiguous = it)) }
                    )
                } else {
                    // Passphrase options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Word Count",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${config.passphraseWords} words",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald
                        )
                    }

                    Slider(
                        value = config.passphraseWords.toFloat(),
                        onValueChange = {
                            viewModel.updateGeneratorConfig(config.copy(passphraseWords = it.toInt()))
                        },
                        valueRange = 3f..8f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberEmerald,
                            activeTrackColor = CyberEmerald
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Passphrases use easy-to-remember words while delivering exceptional cryptographic entropy against dictionary and brute-force attacks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratorToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberEmerald,
                checkedTrackColor = CyberEmerald.copy(alpha = 0.3f)
            )
        )
    }
}
