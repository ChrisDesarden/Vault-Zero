package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.crypto.VaultCrypto
import com.example.model.ApiSecretPayload
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import com.example.ui.components.StrengthMeter
import com.example.ui.components.VaultTypeIcon
import com.example.ui.theme.CyberEmerald

@Composable
fun ItemEditorDialog(
    item: VaultItem,
    onSave: (VaultItem) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var category by remember { mutableStateOf(item.category) }

    // Login state
    val loginPayload = (item.payload as? LoginPayload) ?: LoginPayload()
    var username by remember { mutableStateOf(loginPayload.username) }
    var password by remember { mutableStateOf(loginPayload.password) }
    var url by remember { mutableStateOf(loginPayload.url) }
    var totpSecret by remember { mutableStateOf(loginPayload.totpSecret) }
    var notes by remember { mutableStateOf(loginPayload.notes) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Card state
    val cardPayload = (item.payload as? CardPayload) ?: CardPayload()
    var cardholderName by remember { mutableStateOf(cardPayload.cardholderName) }
    var cardNumber by remember { mutableStateOf(cardPayload.cardNumber) }
    var expMonth by remember { mutableStateOf(cardPayload.expMonth) }
    var expYear by remember { mutableStateOf(cardPayload.expYear) }
    var cvv by remember { mutableStateOf(cardPayload.cvv) }
    var pin by remember { mutableStateOf(cardPayload.pin) }
    var brand by remember { mutableStateOf(cardPayload.brand) }
    var cardNotes by remember { mutableStateOf(cardPayload.notes) }

    // API state
    val apiPayload = (item.payload as? ApiSecretPayload) ?: ApiSecretPayload()
    var serviceName by remember { mutableStateOf(apiPayload.serviceName) }
    var apiKey by remember { mutableStateOf(apiPayload.apiKey) }
    var secretToken by remember { mutableStateOf(apiPayload.secretToken) }
    var environment by remember { mutableStateOf(apiPayload.environment) }
    var scopes by remember { mutableStateOf(apiPayload.scopes) }
    var endpointUrl by remember { mutableStateOf(apiPayload.endpointUrl) }
    var apiNotes by remember { mutableStateOf(apiPayload.notes) }

    // Crypto state
    val cryptoPayload = (item.payload as? CryptoPayload) ?: CryptoPayload()
    var walletName by remember { mutableStateOf(cryptoPayload.walletName) }
    var network by remember { mutableStateOf(cryptoPayload.network) }
    var publicAddress by remember { mutableStateOf(cryptoPayload.publicAddress) }
    var seedPhrase by remember { mutableStateOf(cryptoPayload.seedPhrase) }
    var privateKey by remember { mutableStateOf(cryptoPayload.privateKey) }
    var derivationPath by remember { mutableStateOf(cryptoPayload.derivationPath) }
    var cryptoNotes by remember { mutableStateOf(cryptoPayload.notes) }

    // Note state
    val notePayload = (item.payload as? NotePayload) ?: NotePayload()
    var noteContent by remember { mutableStateOf(notePayload.content) }
    val isPinned by remember { mutableStateOf(notePayload.isPinned) }

    // Identity state
    val idPayload = (item.payload as? IdentityPayload) ?: IdentityPayload()
    var fullName by remember { mutableStateOf(idPayload.fullName) }
    var idType by remember { mutableStateOf(idPayload.idType) }
    var idNumber by remember { mutableStateOf(idPayload.idNumber) }
    var birthDate by remember { mutableStateOf(idPayload.birthDate) }
    var expiryDate by remember { mutableStateOf(idPayload.expiryDate) }
    var idNotes by remember { mutableStateOf(idPayload.notes) }

    val strength = remember(password) { VaultCrypto.calculateStrength(password) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 20.dp)
                .testTag("item_editor_card")
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VaultTypeIcon(type = item.type)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (item.id.isEmpty()) "New ${item.type.displayName}" else "Edit Entry",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Encrypted client-side with AES-256-GCM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close editor")
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Common fields: Title & Category
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Label") },
                    placeholder = { Text("e.g. GitHub, AWS Prod, Personal Card") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("editor_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category / Tag") },
                    placeholder = { Text("e.g. Work, Finance, Personal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Type-Specific Fields
                when (item.type) {
                    VaultType.LOGIN -> {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username or Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("editor_username_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        password = VaultCrypto.generatePassword(18)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Generate strong password",
                                            tint = CyberEmerald
                                        )
                                    }
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isPasswordVisible) "Hide" else "Show"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("editor_password_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (password.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            StrengthMeter(strength = strength)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = totpSecret,
                            onValueChange = { totpSecret = it },
                            label = { Text("2FA Authenticator Secret (Optional Base32)") },
                            placeholder = { Text("e.g. JBSWY3DPEHPK3PXP") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Website URL (Optional)") },
                            placeholder = { Text("https://example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Private Notes (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2
                        )
                    }

                    VaultType.CARD -> {
                        OutlinedTextField(
                            value = cardholderName,
                            onValueChange = { cardholderName = it },
                            label = { Text("Cardholder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = cardNumber,
                            onValueChange = { cardNumber = it },
                            label = { Text("Card Number") },
                            placeholder = { Text("4532 8812 9014 3829") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = expMonth,
                                onValueChange = { expMonth = it },
                                label = { Text("Month (MM)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = expYear,
                                onValueChange = { expYear = it },
                                label = { Text("Year (YY)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = cvv,
                                onValueChange = { cvv = it },
                                label = { Text("CVV") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                label = { Text("ATM PIN (Optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("Card Network / Brand") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = cardNotes,
                            onValueChange = { cardNotes = it },
                            label = { Text("Card Notes (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    VaultType.API_SECRET -> {
                        OutlinedTextField(
                            value = serviceName,
                            onValueChange = { serviceName = it },
                            label = { Text("Provider / Platform") },
                            placeholder = { Text("e.g. OpenAI, Stripe, AWS") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key / Client ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = secretToken,
                            onValueChange = { secretToken = it },
                            label = { Text("Secret Token / Private Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = environment,
                            onValueChange = { environment = it },
                            label = { Text("Environment") },
                            placeholder = { Text("Production / Staging / Dev") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = scopes,
                            onValueChange = { scopes = it },
                            label = { Text("Scopes / Permissions (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = endpointUrl,
                            onValueChange = { endpointUrl = it },
                            label = { Text("Endpoint URL (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = apiNotes,
                            onValueChange = { apiNotes = it },
                            label = { Text("Notes (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    VaultType.CRYPTO_WALLET -> {
                        OutlinedTextField(
                            value = walletName,
                            onValueChange = { walletName = it },
                            label = { Text("Wallet Name") },
                            placeholder = { Text("e.g. Ledger Cold Vault") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = network,
                            onValueChange = { network = it },
                            label = { Text("Blockchain Network") },
                            placeholder = { Text("Ethereum, Solana, Bitcoin") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = publicAddress,
                            onValueChange = { publicAddress = it },
                            label = { Text("Public Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = seedPhrase,
                            onValueChange = { seedPhrase = it },
                            label = { Text("Seed Phrase (12 or 24 words)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = privateKey,
                            onValueChange = { privateKey = it },
                            label = { Text("Private Key (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = derivationPath,
                            onValueChange = { derivationPath = it },
                            label = { Text("Derivation Path (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = cryptoNotes,
                            onValueChange = { cryptoNotes = it },
                            label = { Text("Notes (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    VaultType.NOTE -> {
                        OutlinedTextField(
                            value = noteContent,
                            onValueChange = { noteContent = it },
                            label = { Text("Secure Note (AES-256 Encrypted)") },
                            modifier = Modifier.fillMaxWidth().testTag("editor_note_input"),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 6
                        )
                    }

                    VaultType.IDENTITY -> {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Full Legal Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = idType,
                            onValueChange = { idType = it },
                            label = { Text("Document Type") },
                            placeholder = { Text("Passport, National ID, Driver's License") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = idNumber,
                            onValueChange = { idNumber = it },
                            label = { Text("Document / License Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = birthDate,
                                onValueChange = { birthDate = it },
                                label = { Text("Birth Date (Optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = expiryDate,
                                onValueChange = { expiryDate = it },
                                label = { Text("Expiry Date (Optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = idNotes,
                            onValueChange = { idNotes = it },
                            label = { Text("Notes (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                title = when (item.type) {
                                    VaultType.LOGIN -> username.ifEmpty { "New Login" }
                                    VaultType.CARD -> brand.ifEmpty { "Payment Card" }
                                    VaultType.API_SECRET -> serviceName.ifEmpty { "API Key" }
                                    VaultType.CRYPTO_WALLET -> walletName.ifEmpty { "Crypto Wallet" }
                                    VaultType.NOTE -> "Secure Note"
                                    VaultType.IDENTITY -> fullName.ifEmpty { "Identity Document" }
                                }
                            }

                            val newPayload = when (item.type) {
                                VaultType.LOGIN -> LoginPayload(username, password, url, totpSecret, notes)
                                VaultType.CARD -> CardPayload(cardholderName, cardNumber, expMonth, expYear, cvv, pin, brand, cardNotes)
                                VaultType.API_SECRET -> ApiSecretPayload(serviceName, apiKey, secretToken, environment, scopes, endpointUrl, apiNotes)
                                VaultType.CRYPTO_WALLET -> CryptoPayload(walletName, network, publicAddress, seedPhrase, privateKey, derivationPath, cryptoNotes)
                                VaultType.NOTE -> NotePayload(noteContent, isPinned)
                                VaultType.IDENTITY -> IdentityPayload(fullName, idType, idNumber, birthDate, expiryDate, idNotes)
                            }

                            val updatedItem = item.copy(
                                title = title,
                                category = category.ifEmpty { "Personal" },
                                payload = newPayload,
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(updatedItem)
                        },
                        modifier = Modifier.weight(1f).testTag("save_item_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Encrypted", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
