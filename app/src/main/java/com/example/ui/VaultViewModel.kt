package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.BiometricKeyManager
import com.example.crypto.SecurityAuditEngine
import com.example.crypto.VaultCrypto
import com.example.data.VaultDatabase
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import com.example.model.AuditReport
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.ItemPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

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

    private val _biometricPromptEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val biometricPromptEvent: kotlinx.coroutines.flow.SharedFlow<Unit> = _biometricPromptEvent

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
        get() = repository.recoveryPhrase

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
        items.filter { item ->
            val matchesQuery = if (query.isBlank()) true else {
                item.title.contains(query, ignoreCase = true) ||
                        item.category.contains(query, ignoreCase = true) ||
                        item.tags.any { it.contains(query, ignoreCase = true) }
            }
            val matchesType = typeFilter == null || item.type == typeFilter
            val matchesFav = !favsOnly || item.isFavorite
            matchesQuery && matchesType && matchesFav
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditReport: StateFlow<AuditReport> = allItems.combine(_clockTicker) { items, _ ->
        SecurityAuditEngine.analyzeVault(items)
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
            }
        }
    }

    private fun resetAutoLockTimer() {
        autoLockJob?.cancel()
        val seconds = repository.autoLockSeconds
        if (seconds > 0 && _lockState.value == VaultLockState.UNLOCKED) {
            autoLockJob = viewModelScope.launch {
                delay(seconds * 1000L)
                lockVault()
                _clipboardStatus.value = "Vault auto-locked due to inactivity"
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
        if (password.isEmpty()) {
            _authError.value = "Please enter your Master Password"
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
                    _authError.value = "Incorrect Master Password. Please check and try again."
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

    fun onBiometricVerified() {
        viewModelScope.launch {
            _isAuthenticating.value = true
            try {
                val vmkHex = BiometricKeyManager.getDecryptedVmk(getApplication())
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
        val cleanNewPass = newPass.trim()
        val cleanConfirm = confirmPass.trim()
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
                val success = repository.recoverWithPhrase(phrase, cleanNewPass)
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
                    _authError.value = "Invalid 12-word recovery phrase"
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

    fun lockVault() {
        repository.lock()
        _lockState.value = VaultLockState.LOCKED
        _viewingItem.value = null
        _editingItem.value = null
        autoLockJob?.cancel()
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
        _viewingItem.value = item
        onUserInteraction()
    }

    fun dismissViewItem() {
        _viewingItem.value = null
        onUserInteraction()
    }

    fun startCreateItem(type: VaultType = VaultType.LOGIN) {
        val blankPayload: ItemPayload = when (type) {
            VaultType.LOGIN -> LoginPayload()
            VaultType.CARD -> CardPayload()
            VaultType.API_SECRET -> com.example.model.ApiSecretPayload()
            VaultType.CRYPTO_WALLET -> CryptoPayload()
            VaultType.NOTE -> NotePayload()
            VaultType.IDENTITY -> com.example.model.IdentityPayload()
        }
        _editingItem.value = VaultItem(
            id = "",
            title = "",
            type = type,
            payload = blankPayload
        )
        onUserInteraction()
    }

    fun startEditItem(item: VaultItem) {
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
                _clipboardStatus.value = "Saved '${item.title}' securely"
                onUserInteraction()
            } catch (e: Exception) {
                _clipboardStatus.value = "Failed to save: ${e.message}"
            }
        }
    }

    fun toggleFavorite(item: VaultItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, !item.isFavorite)
            onUserInteraction()
        }
    }

    fun deleteItem(item: VaultItem) {
        viewModelScope.launch {
            repository.deleteItem(item.id)
            _viewingItem.value = null
            _editingItem.value = null
            _clipboardStatus.value = "Deleted '${item.title}'"
            onUserInteraction()
        }
    }

    fun clearVault() {
        viewModelScope.launch {
            BiometricKeyManager.clearBiometricKey(getApplication())
            repository.clearVault()
            _lockState.value = VaultLockState.NEEDS_SETUP
            _viewingItem.value = null
            _editingItem.value = null
            _clipboardStatus.value = "Vault completely reset"
        }
    }

    // -------------------------------------------------------------------------
    // Secure Clipboard with Auto-Clear Timer
    // -------------------------------------------------------------------------

    fun copySecurely(label: String, value: String, isSensitive: Boolean = true) {
        if (value.isEmpty()) return
        val clip = ClipData.newPlainText(label, value)
        clipboardManager.setPrimaryClip(clip)

        val timeout = repository.clipboardTimeoutSeconds
        _clipboardStatus.value = "$label copied! Auto-clearing in ${timeout}s"
        onUserInteraction()

        clipboardJob?.cancel()
        if (isSensitive && timeout > 0) {
            clipboardJob = viewModelScope.launch {
                delay(timeout * 1000L)
                try {
                    val emptyClip = ClipData.newPlainText("", "")
                    clipboardManager.setPrimaryClip(emptyClip)
                    _clipboardStatus.value = "Clipboard cleared for security"
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun dismissClipboardStatus() {
        _clipboardStatus.value = null
    }

    // -------------------------------------------------------------------------
    // Password Generator
    // -------------------------------------------------------------------------

    fun updateGeneratorConfig(config: GeneratorConfig) {
        _generatorConfig.value = config
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
        _clipboardStatus.value = "Auto-lock updated"
    }

    fun updateClipboardTimeout(seconds: Int) {
        repository.setClipboardTimeoutSeconds(seconds)
        _clipboardStatus.value = "Clipboard timeout updated"
    }

    fun toggleBiometric(enabled: Boolean) {
        if (enabled) {
            val vmkHex = repository.getActiveVmkHex()
            if (vmkHex != null) {
                BiometricKeyManager.storeVmk(getApplication(), vmkHex)
                repository.setBiometricEnabled(true)
                _clipboardStatus.value = "Biometric unlock enabled"
            } else {
                _clipboardStatus.value = "Please unlock vault with Master Password first"
            }
        } else {
            BiometricKeyManager.clearBiometricKey(getApplication())
            repository.setBiometricEnabled(false)
            _clipboardStatus.value = "Biometric unlock disabled"
        }
    }


    fun toggleTheme(isDark: Boolean) {
        repository.setDarkTheme(isDark)
    }
}
