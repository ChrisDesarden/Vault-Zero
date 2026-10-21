package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.VaultCrypto
import com.example.model.ApiSecretPayload
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import com.example.ui.VaultViewModel
import com.example.ui.components.TotpLiveBadge
import com.example.ui.components.VaultTypeIcon
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.DangerRed
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay

@Composable
fun VaultListScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.filteredItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedType by viewModel.selectedTypeFilter.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val clockTicker by viewModel.clockTicker.collectAsState()
    val clipboardStatus by viewModel.clipboardStatus.collectAsState()

    var showCreateMenu by remember { mutableStateOf(false) }
    val fabBottomPadding by animateDpAsState(
        targetValue = if (clipboardStatus != null) 72.dp else 20.dp,
        label = "fabBottomPadding"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "VaultZero",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyberEmerald.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "UNLOCKED • AES-256",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberEmerald
                            )
                        }
                    }
                    Text(
                        text = "${items.size} encrypted ${if (items.size == 1) "entry" else "entries"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Quick Lock Button
                IconButton(
                    onClick = { viewModel.lockVault() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .testTag("quick_lock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Vault",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search logins, keys, cards, notes...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("vault_search_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedBorderColor = CyberEmerald
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // All Chip
                FilterChip(
                    selected = selectedType == null && !favoritesOnly,
                    onClick = {
                        viewModel.setTypeFilter(null)
                        if (favoritesOnly) viewModel.toggleFavoritesFilter()
                    },
                    label = { Text("All", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberEmerald.copy(alpha = 0.2f),
                        selectedLabelColor = CyberEmerald
                    )
                )

                // Favorites Chip
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { viewModel.toggleFavoritesFilter() },
                    leadingIcon = {
                        Icon(
                            imageVector = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = { Text("Favorites", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DangerRed.copy(alpha = 0.2f),
                        selectedLabelColor = DangerRed
                    )
                )

                // Types Chips
                for (type in VaultType.values()) {
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { viewModel.setTypeFilter(if (selectedType == type) null else type) },
                        label = { Text(type.displayName, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Items List
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No items matching query" else "Your vault is empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the + button below to securely store your logins, cards, and cryptographic secrets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        VaultItemCard(
                            item = item,
                            clockTicker = clockTicker,
                            onTap = { viewModel.viewItem(item) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            onCopyPrimary = { label, value ->
                                viewModel.copySecurely(label, value, isSensitive = true)
                            }
                        )
                    }
                }
            }
        }

        // FAB for Adding New Entry
        ExtendedFloatingActionButton(
            onClick = { showCreateMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = fabBottomPadding)
                .testTag("add_item_fab"),
            containerColor = CyberEmerald,
            contentColor = Color.Black,
            icon = { Icon(Icons.Default.Add, contentDescription = "Add Item") },
            text = { Text("Add Entry", fontWeight = FontWeight.Bold) }
        )

        // Type Selection Modal Bottom Sheet / Dialog
        if (showCreateMenu) {
            CreateTypeSelectorDialog(
                onSelect = { type ->
                    showCreateMenu = false
                    viewModel.startCreateItem(type)
                },
                onDismiss = { showCreateMenu = false }
            )
        }
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItem,
    clockTicker: Long,
    onTap: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyPrimary: (String, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onTap() }
            .testTag("vault_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VaultTypeIcon(type = item.type)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val subtitle = when (val p = item.payload) {
                        is LoginPayload -> p.username.ifEmpty { p.url }
                        is CardPayload -> if (p.cardNumber.length >= 4) "•••• ${p.cardNumber.takeLast(4)}" else p.brand
                        is ApiSecretPayload -> "${p.environment} • ${p.serviceName}"
                        is CryptoPayload -> "${p.network} • ${if (p.publicAddress.length > 10) p.publicAddress.take(6) + "..." + p.publicAddress.takeLast(4) else p.walletName}"
                        is NotePayload -> p.content.lines().firstOrNull() ?: "Encrypted Note"
                        is IdentityPayload -> "${p.idType} • ${p.fullName}"
                    }

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Favorite Toggle
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Quick Copy Button for Login/Card/Secret
                val (copyLabel, copyValue) = when (val p = item.payload) {
                    is LoginPayload -> Pair("Password", p.password)
                    is CardPayload -> Pair("Card Number", p.cardNumber)
                    is ApiSecretPayload -> Pair("API Key", p.apiKey.ifEmpty { p.secretToken })
                    is CryptoPayload -> Pair("Public Address", p.publicAddress)
                    is NotePayload -> Pair("Note", p.content)
                    is IdentityPayload -> Pair("ID Number", p.idNumber)
                }

                if (copyValue.isNotEmpty()) {
                    var justCopied by remember { mutableStateOf(false) }
                    LaunchedEffect(justCopied) {
                        if (justCopied) {
                            delay(1400L)
                            justCopied = false
                        }
                    }
                    IconButton(
                        onClick = {
                            justCopied = true
                            onCopyPrimary(copyLabel, copyValue)
                        },
                        modifier = Modifier.size(36.dp).testTag("quick_copy_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Quick Copy $copyLabel",
                            tint = CyberEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Live TOTP snippet if Login item has 2FA configured
            if (item.type == VaultType.LOGIN) {
                val login = item.payload as LoginPayload
                if (login.totpSecret.isNotEmpty()) {
                    val totp = VaultCrypto.generateTotp(login.totpSecret, clockTicker)
                    if (totp != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TotpLiveBadge(
                            totpResult = totp,
                            onCopy = { onCopyPrimary("2FA Code", totp.code) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTypeSelectorDialog(
    onSelect: (VaultType) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "New Vault Entry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select credential type to encrypt with AES-256-GCM:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                for (type in VaultType.values()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(type) }
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VaultTypeIcon(type = type)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val desc = when (type) {
                                VaultType.LOGIN -> "Websites, apps, usernames & 2FA keys"
                                VaultType.CARD -> "Credit & debit cards, CVV & PINs"
                                VaultType.API_SECRET -> "API keys, access tokens & backend secrets"
                                VaultType.CRYPTO_WALLET -> "Seed phrases, private keys & wallets"
                                VaultType.NOTE -> "Encrypted notes & private text"
                                VaultType.IDENTITY -> "Passports, IDs & legal documents"
                            }
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
