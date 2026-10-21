package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.BiometricKeyManager
import com.example.crypto.SecurityAuditEngine
import com.example.crypto.VaultCrypto
import com.example.data.VaultDatabase
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import com.example.model.ApiSecretPayload
import com.example.model.AuditReport
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.ItemPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VaultLockState {
    NEEDS_SETUP,
    LOCKED,
    UNLOCKED,
    RECOVERING
}

enum class VaultNavTab {
    VAULT,
    GENERATOR,
    AUDIT,
    SETTINGS
}

data class GeneratorConfig(
    val length: Int = 18,
    val includeUpper: Boolean = true,
    val includeLower: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val avoidAmbiguous: Boolean = true,
    val isPassphraseMode: Boolean = false,
    val passphraseWords: Int = 4
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VaultRepository
    private val clipboardManager =
        application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val _lockState = MutableStateFlow(VaultLockState.LOCKED)
    val lockState: StateFlow<VaultLockState> = _lockState.asStateFlow()

    private val _currentTab = MutableStateFlow(VaultNavTab.VAULT)
    val currentTab: StateFlow<VaultNavTab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<VaultType?>(null)
    val selectedTypeFilter: StateFlow<VaultType?> = _selectedTypeFilter.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _viewingItem = MutableStateFlow<VaultItem?>(null)
    val viewingItem: StateFlow<VaultItem?> = _viewingItem.asStateFlow()

    private val _editingItem = MutableStateFlow<VaultItem?>(null)
    val editingItem: StateFlow<VaultItem?> = _editingItem.asStateFlow()

    private val _newRecoveryPhrase = MutableStateFlow<String?>(null)
    val newRecoveryPhrase: StateFlow<String?> = _newRecoveryPhrase.asStateFlow()

    private val _clipboardStatus = MutableStateFlow<String?>(null)
    val clipboardStatus: StateFlow<String?> = _clipboardStatus.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _biometricPromptEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val biometricPromptEvent: SharedFlow<Unit> = _biometricPromptEvent

    private val _generatorConfig = MutableStateFlow(GeneratorConfig())
    val generatorConfig: StateFlow<GeneratorConfig> = _generatorConfig.asStateFlow()

    private val _generatedPassword = MutableStateFlow("")
    val generatedPassword: StateFlow<String> = _generatedPassword.asStateFlow()

    private val _passwordStrength = MutableStateFlow(
        VaultCrypto.calculateStrength("")
    )
    val passwordStrength: StateFlow<VaultCrypto.PasswordStrength> = _passwordStrength.asStateFlow()

    // Real-time second clock for live TOTP animations
    private val _clockTicker = MutableStateFlow(System.currentTimeMillis())
    val clockTicker: StateFlow<Long> = _clockTicker.asStateFlow()

    private var clipboardJob: Job? = null
    private var autoLockJob: Job? = null
    private var statusDismissJob: Job? = null

    @Volatile
    private var lastInteractionRealtime: Long = SystemClock.elapsedRealtime()

    @Volatile
    private var lastCopiedSensitiveValue: String? = null

    private fun postStatusMessage(message: String, durationMs: Long = 2200L) {
        _clipboardStatus.value = message
        statusDismissJob?.cancel()
        statusDismissJob = viewModelScope.launch {
            delay(durationMs)
            if (_clipboardStatus.value == message) {
                _clipboardStatus.value = null
            }
        }
    }

    init {
        val database = VaultDatabase.getInstance(application)
        val prefs = VaultPreferences(application)
        repository = VaultRepository(database.vaultDao(), prefs)

        if (!repository.isInitialized) {
            _lockState.value = VaultLockState.NEEDS_SETUP
        } else {
            _lockState.value = VaultLockState.LOCKED
        }

        regeneratePassword()
        startClockTicker()
    }

    val keyFingerprint: String
        get() = repository.keyFingerprint

    val recoveryPhrase: String
        get() = repository.getDecryptedRecoveryPhrase()

    val autoLockSeconds: Int
        get() = repository.autoLockSeconds

    val clipboardTimeoutSeconds: Int
        get() = repository.clipboardTimeoutSeconds

    val isDarkTheme: Boolean
        get() = repository.isDarkTheme()

    val isBiometricEnabled: Boolean
        get() = repository.isBiometricEnabled()

    val allItems: StateFlow<List<VaultItem>> = repository.getVaultItemsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredItems: StateFlow<List<VaultItem>> = combine(
        allItems,
        _searchQuery,
        _selectedTypeFilter,
        _favoritesOnly
    ) { items, query, typeFilter, favsOnly ->
        val cleanQuery = query.trim()
        items.filter { item ->
            val matchesQuery = if (cleanQuery.isEmpty()) true else {
                val payloadMatch = when (val p = item.payload) {
                    is LoginPayload -> p.username.contains(cleanQuery, ignoreCase = true) ||
                            p.url.contains(cleanQuery, ignoreCase = true) ||
                            p.notes.contains(cleanQuery, ignoreCase = true)
                    is CardPayload -> p.cardholderName.contains(cleanQuery, ignoreCase = true) ||
                            p.brand.contains(cleanQuery, ignoreCase = true) ||
                            p.cardNumber.takeLast(4).contains(cleanQuery, ignoreCase = true) ||
                            p.notes.contains(cleanQuery, ignoreCase = true)
                    is ApiSecretPayload -> p.serviceName.contains(cleanQuery, ignoreCase = true) ||
                            p.environment.contains(cleanQuery, ignoreCase = true) ||
                            p.scopes.contains(cleanQuery, ignoreCase = true) ||
                            p.endpointUrl.contains(cleanQuery, ignoreCase = true)
                    is CryptoPayload -> p.walletName.contains(cleanQuery, ignoreCase = true) ||
                            p.network.contains(cleanQuery, ignoreCase = true) ||
                            p.publicAddress.contains(cleanQuery, ignoreCase = true)
                    is NotePayload -> p.content.contains(cleanQuery, ignoreCase = true)
                    is IdentityPayload -> p.fullName.contains(cleanQuery, ignoreCase = true) ||
                            p.idType.contains(cleanQuery, ignoreCase = true)
                }
                item.title.contains(cleanQuery, ignoreCase = true) ||
                        item.category.contains(cleanQuery, ignoreCase = true) ||
                        item.tags.any { it.contains(cleanQuery, ignoreCase = true) } ||
                        payloadMatch
            }
            val matchesType = typeFilter == null || item.type == typeFilter
            val matchesFav = !favsOnly || item.isFavorite
            matchesQuery && matchesType && matchesFav
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditReport: StateFlow<AuditReport> = allItems.combine(_clockTicker) { items, now ->
        SecurityAuditEngine.analyzeVault(items, now)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AuditReport(100, "A+", 0, 0, 0, 0, 0, emptyList())
    )

    private fun startClockTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _clockTicker.value = System.currentTimeMillis()
                checkAutoLockTimeout()
            }
        }
    }

    private fun resetAutoLockTimer() {
        lastInteractionRealtime = SystemClock.elapsedRealtime()
        autoLockJob?.cancel()
        val seconds = repository.autoLockSeconds
        if (seconds > 0 && _lockState.value == VaultLockState.UNLOCKED) {
            autoLockJob = viewModelScope.launch {
                delay(seconds * 1000L)
                lockVault(autoLocked = true)
            }
        }
    }

    /**
     * Checks monotonic elapsed time since last user interaction and locks vault if timeout elapsed.
     * Safe against wall-clock tampering and device sleep/background pauses.
     */
    fun checkAutoLockTimeout(nowRealtime: Long = SystemClock.elapsedRealtime()) {
        if (_lockState.value == VaultLockState.UNLOCKED) {
            val seconds = repository.autoLockSeconds
            if (seconds > 0 && (nowRealtime - lastInteractionRealtime) >= seconds * 1000L) {
                lockVault(autoLocked = true)
            }
        }
    }

    fun onUserInteraction() {
        if (_lockState.value == VaultLockState.UNLOCKED) {
            resetAutoLockTimer()
        }
    }

    // -------------------------------------------------------------------------
    // Auth & Lock Operations
    // -------------------------------------------------------------------------

    fun clearAuthError() {
        _authError.value = null
    }

    fun setupMasterPassword(password: String, confirm: String) {
        val cleanPassword = password.trim()
        val cleanConfirm = confirm.trim()
        if (cleanPassword.length < 8) {
            _authError.value = "Master Password must be at least 8 characters"
            return
        }
        if (cleanPassword != cleanConfirm) {
            _authError.value = "Passwords do not match"
            return
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val (phrase, vmkHex) = repository.setupMasterPassword(cleanPassword)
                if (repository.isBiometricEnabled()) {
                    BiometricKeyManager.storeVmk(getApplication(), vmkHex)
                }
                _newRecoveryPhrase.value = phrase
                _authError.value = null
            } catch (e: Exception) {
                _authError.value = "Setup failed: ${e.message}"
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun completeSetupAndEnterVault() {
        _newRecoveryPhrase.value = null
        _lockState.value = VaultLockState.UNLOCKED
        _authError.value = null
        resetAutoLockTimer()
    }

    fun unlockVault(password: String) {
        if (password.isBlank()) {
            _authError.value = "Please enter your Master Password"
            return
        }
        val lockoutSec = repository.getRemainingLockoutSeconds()
        if (lockoutSec > 0) {
            _authError.value = "Too many failed attempts. Try again in ${lockoutSec}s."
            return
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val success = repository.unlock(password)
                if (success) {
                    if (repository.isBiometricEnabled()) {
                        val vmkHex = repository.getActiveVmkHex()
                        if (vmkHex != null) {
                            BiometricKeyManager.storeVmk(getApplication(), vmkHex)
                        }
                    }
                    _lockState.value = VaultLockState.UNLOCKED
                    _authError.value = null
                    resetAutoLockTimer()
                } else {
                    val newLockoutSec = repository.getRemainingLockoutSeconds()
                    if (newLockoutSec > 0) {
                        _authError.value = "Too many failed attempts. Locked out for ${newLockoutSec}s."
                    } else {
                        _authError.value = "Incorrect Master Password. Please check and try again."
                    }
                }
            } catch (e: Exception) {
                _authError.value = "Authentication error: ${e.message}"
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun requestBiometricUnlock() {
        if (!repository.isBiometricEnabled()) {
            _authError.value = "Biometric unlock is disabled in Settings. Unlock with Master Password first."
            return
        }
        if (!BiometricKeyManager.hasBiometricKey(getApplication())) {
            _authError.value = "Biometric credentials need to be configured. Unlock with Master Password first."
            return
        }
        _authError.value = null
        _biometricPromptEvent.tryEmit(Unit)
    }

    fun onBiometricVerified(authenticatedCipher: javax.crypto.Cipher? = null) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val vmkHex = BiometricKeyManager.decryptVmkWithCipher(getApplication(), authenticatedCipher)
                if (vmkHex != null) {
                    val success = repository.unlockWithVmkHex(vmkHex)
                    if (success) {
                        _lockState.value = VaultLockState.UNLOCKED
                        _authError.value = null
                        resetAutoLockTimer()
                        return@launch
                    }
                }
                _authError.value = "Biometric authentication failed. Please enter Master Password."
            } catch (e: Exception) {
                _authError.value = "Biometric unlock error: ${e.message}"
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun onBiometricError(message: String) {
        _authError.value = message
    }

    fun recoverVault(phrase: String, newPass: String, confirmPass: String) {
        val cleanPhrase = VaultCrypto.normalizeRecoveryPhrase(phrase)
        val cleanNewPass = newPass.trim()
        val cleanConfirm = confirmPass.trim()
        if (!VaultCrypto.validateRecoveryPhrase(cleanPhrase, enforceChecksum = false)) {
            _authError.value = "Recovery phrase must be 12 valid words from your Emergency Recovery Kit"
            return
        }
        if (!VaultCrypto.verifyRecoveryPhraseChecksum(cleanPhrase)) {
            _authError.value = "Recovery phrase checksum mismatch — check for a mistyped word or wrong word order"
            return
        }
        if (cleanNewPass.length < 8) {
            _authError.value = "New password must be at least 8 characters"
            return
        }
        if (cleanNewPass != cleanConfirm) {
            _authError.value = "Passwords do not match"
            return
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val success = repository.recoverWithPhrase(cleanPhrase, cleanNewPass)
                if (success) {
                    if (repository.isBiometricEnabled()) {
                        val vmkHex = repository.getActiveVmkHex()
                        if (vmkHex != null) {
                            BiometricKeyManager.storeVmk(getApplication(), vmkHex)
                        }
                    }
                    _lockState.value = VaultLockState.UNLOCKED
                    _authError.value = null
                    resetAutoLockTimer()
                } else {
                    _authError.value = "Recovery phrase does not match this vault"
                }
            } catch (e: Exception) {
                _authError.value = "Recovery error: ${e.message}"
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun setLockState(state: VaultLockState) {
        _lockState.value = state
        _authError.value = null
    }

    fun lockVault(autoLocked: Boolean = false) {
        repository.lock()
        _lockState.value = VaultLockState.LOCKED
        _viewingItem.value = null
        _editingItem.value = null
        _newRecoveryPhrase.value = null
        autoLockJob?.cancel()
        clearSensitiveClipboardIfOwned()
        if (autoLocked) {
            postStatusMessage("Vault auto-locked due to inactivity")
        } else {
            dismissClipboardStatus()
        }
    }

    fun dismissRecoveryPhraseModal() {
        _newRecoveryPhrase.value = null
    }

    // -------------------------------------------------------------------------
    // Navigation & Filtering
    // -------------------------------------------------------------------------

    fun setTab(tab: VaultNavTab) {
        _currentTab.value = tab
        onUserInteraction()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        onUserInteraction()
    }

    fun setTypeFilter(type: VaultType?) {
        _selectedTypeFilter.value = type
        onUserInteraction()
    }

    fun toggleFavoritesFilter() {
        _favoritesOnly.value = !_favoritesOnly.value
        onUserInteraction()
    }

    // -------------------------------------------------------------------------
    // Item CRUD
    // -------------------------------------------------------------------------

    fun viewItem(item: VaultItem) {
        if (_lockState.value != VaultLockState.UNLOCKED) return
        _viewingItem.value = item
        onUserInteraction()
    }

    fun dismissViewItem() {
        _viewingItem.value = null
        onUserInteraction()
    }

    fun startCreateItem(type: VaultType = VaultType.LOGIN, prefilledPassword: String = "") {
        if (_lockState.value != VaultLockState.UNLOCKED) return
        val blankPayload: ItemPayload = when (type) {
            VaultType.LOGIN -> LoginPayload(password = prefilledPassword)
            VaultType.CARD -> CardPayload()
            VaultType.API_SECRET -> ApiSecretPayload(secretToken = prefilledPassword)
            VaultType.CRYPTO_WALLET -> CryptoPayload()
            VaultType.NOTE -> NotePayload()
            VaultType.IDENTITY -> IdentityPayload()
        }
        _editingItem.value = VaultItem(
            id = "",
            title = "",
            type = type,
            payload = blankPayload
        )
        onUserInteraction()
    }

    fun saveGeneratedPasswordToVault() {
        val currentGenerated = _generatedPassword.value
        startCreateItem(type = VaultType.LOGIN, prefilledPassword = currentGenerated)
    }

    fun startEditItem(item: VaultItem) {
        if (_lockState.value != VaultLockState.UNLOCKED) return
        _viewingItem.value = null
        _editingItem.value = item
        onUserInteraction()
    }

    fun dismissEditItem() {
        _editingItem.value = null
        onUserInteraction()
    }

    fun saveItem(item: VaultItem) {
        viewModelScope.launch {
            try {
                repository.saveItem(item)
                _editingItem.value = null
                _viewingItem.value = null
                postStatusMessage("Saved '${item.title}' securely")
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage("Failed to save: ${e.message}")
            }
        }
    }

    fun toggleFavorite(item: VaultItem) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(item.id, !item.isFavorite)
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage("Cannot update favorite while locked")
            }
        }
    }

    fun deleteItem(item: VaultItem) {
        viewModelScope.launch {
            try {
                repository.deleteItem(item.id)
                _viewingItem.value = null
                _editingItem.value = null
                postStatusMessage("Deleted '${item.title}'")
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage("Failed to delete: ${e.message}")
            }
        }
    }

    fun clearVault() {
        viewModelScope.launch {
            BiometricKeyManager.clearBiometricKey(getApplication())
            clearSensitiveClipboardIfOwned()
            repository.clearVault()
            _lockState.value = VaultLockState.NEEDS_SETUP
            _viewingItem.value = null
            _editingItem.value = null
            _newRecoveryPhrase.value = null
            postStatusMessage("Vault completely reset")
        }
    }

    // -------------------------------------------------------------------------
    // Encrypted Backup Export & Import
    // -------------------------------------------------------------------------

    fun exportBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = repository.exportEncryptedBackup()
                onResult(json)
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage("Export failed: ${e.message}")
            }
        }
    }

    fun importBackupJson(json: String, backupPassword: String? = null) {
        viewModelScope.launch {
            try {
                val count = repository.importEncryptedBackup(json, backupPassword)
                val msg = if (count > 0) {
                    "Restored $count encrypted ${if (count == 1) "entry" else "entries"}"
                } else {
                    "No compatible records found (verify backup password or format)"
                }
                postStatusMessage(msg)
                onUserInteraction()
            } catch (e: Exception) {
                postStatusMessage("Import failed: invalid backup JSON format")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Secure Clipboard with Ownership-Aware Auto-Clear & Sensitive Flag
    // -------------------------------------------------------------------------

    fun copySecurely(label: String, value: String, isSensitive: Boolean = true) {
        if (value.isEmpty()) return
        val clip = ClipData.newPlainText(label, value)
        if (isSensitive) {
            try {
                val extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
                clip.description.extras = extras
            } catch (e: Exception) {
                // Ignore on older unit test mocks where PersistableBundle is stubbed
            }
        }
        clipboardManager.setPrimaryClip(clip)

        val timeout = repository.clipboardTimeoutSeconds
        val statusMsg = if (isSensitive && timeout > 0) {
            "$label copied • Auto-clears in ${timeout}s"
        } else {
            "$label copied to clipboard"
        }
        postStatusMessage(statusMsg)
        onUserInteraction()

        clipboardJob?.cancel()
        if (isSensitive) {
            lastCopiedSensitiveValue = value
            if (timeout > 0) {
                clipboardJob = viewModelScope.launch {
                    delay(timeout * 1000L)
                    clearSensitiveClipboardIfOwned()
                }
            }
        }
    }

    /**
     * Clears the clipboard ONLY if it still contains the sensitive value placed by VaultZero,
     * preserving unrelated user clipboard contents from other applications.
     */
    fun clearSensitiveClipboardIfOwned(): Boolean {
        val tracked = lastCopiedSensitiveValue ?: return false
        return try {
            val currentClip = clipboardManager.primaryClip
            val currentText = if (currentClip != null && currentClip.itemCount > 0) {
                currentClip.getItemAt(0).text?.toString()
            } else {
                null
            }
            if (currentText == tracked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboardManager.clearPrimaryClip()
                } else {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                lastCopiedSensitiveValue = null
                true
            } else {
                lastCopiedSensitiveValue = null
                false
            }
        } catch (e: Exception) {
            lastCopiedSensitiveValue = null
            false
        }
    }

    fun dismissClipboardStatus() {
        statusDismissJob?.cancel()
        _clipboardStatus.value = null
    }

    // -------------------------------------------------------------------------
    // Password Generator
    // -------------------------------------------------------------------------

    fun updateGeneratorConfig(config: GeneratorConfig) {
        if (!config.isPassphraseMode &&
            !config.includeUpper &&
            !config.includeLower &&
            !config.includeDigits &&
            !config.includeSymbols
        ) {
            postStatusMessage("At least one character set must remain enabled")
            return
        }
        val clampedConfig = config.copy(
            length = config.length.coerceIn(8, 64),
            passphraseWords = config.passphraseWords.coerceIn(3, 8)
        )
        _generatorConfig.value = clampedConfig
        regeneratePassword()
        onUserInteraction()
    }

    fun regeneratePassword() {
        val config = _generatorConfig.value
        val password = if (config.isPassphraseMode) {
            VaultCrypto.generatePassphrase(config.passphraseWords)
        } else {
            VaultCrypto.generatePassword(
                length = config.length,
                includeUpper = config.includeUpper,
                includeLower = config.includeLower,
                includeDigits = config.includeDigits,
                includeSymbols = config.includeSymbols,
                avoidAmbiguous = config.avoidAmbiguous
            )
        }
        _generatedPassword.value = password
        _passwordStrength.value = VaultCrypto.calculateStrength(password)
    }

    // -------------------------------------------------------------------------
    // Settings
    // -------------------------------------------------------------------------

    fun updateAutoLock(seconds: Int) {
        repository.setAutoLockSeconds(seconds)
        resetAutoLockTimer()
        postStatusMessage("Auto-lock updated")
    }

    fun updateClipboardTimeout(seconds: Int) {
        repository.setClipboardTimeoutSeconds(seconds)
        postStatusMessage("Clipboard timeout updated")
    }

    fun toggleBiometric(enabled: Boolean) {
        if (enabled) {
            val vmkHex = repository.getActiveVmkHex()
            if (vmkHex != null) {
                BiometricKeyManager.storeVmk(getApplication(), vmkHex)
                repository.setBiometricEnabled(true)
                postStatusMessage("Biometric unlock enabled")
            } else {
                postStatusMessage("Please unlock vault with Master Password first")
            }
        } else {
            BiometricKeyManager.clearBiometricKey(getApplication())
            repository.setBiometricEnabled(false)
            postStatusMessage("Biometric unlock disabled")
        }
    }

    fun toggleTheme(isDark: Boolean) {
        repository.setDarkTheme(isDark)
    }
}
