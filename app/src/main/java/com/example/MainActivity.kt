package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.VaultLockState
import com.example.ui.VaultNavTab
import com.example.ui.VaultViewModel
import com.example.ui.components.VaultStatusPill
import com.example.ui.screens.AuditScreen
import com.example.ui.screens.GeneratorScreen
import com.example.ui.screens.ItemDetailDialog
import com.example.ui.screens.ItemEditorDialog
import com.example.ui.screens.LockScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VaultListScreen
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.VaultZeroTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Protect all sensitive vault screens from screenshots, screen recording, and Recent Apps previews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        // Listen for Biometric Prompt trigger events from ViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.biometricPromptEvent.collect {
                    launchNativeBiometricPrompt()
                }
            }
        }

        setContent {
            VaultZeroTheme(darkTheme = true) {
                VaultZeroApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAutoLockTimeout()
    }

    private fun launchNativeBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuth = biometricManager.canAuthenticate(authenticators)

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock VaultZero")
                .setSubtitle("Confirm device fingerprint, face, or screen lock")
                .setAllowedAuthenticators(authenticators)
                .build()

            val prompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        viewModel.onBiometricVerified(result.cryptoObject?.cipher)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            viewModel.onBiometricError(errString.toString())
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        // Biometric not recognized, system prompt stays visible for retry
                    }
                }
            )
            prompt.authenticate(promptInfo)
        } else if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            viewModel.onBiometricError("No biometric or screen lock enrolled. Please set up a fingerprint or screen lock in device Settings.")
        } else {
            viewModel.onBiometricError("Biometrics currently unavailable. Enter your Master Password to unlock.")
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.onUserInteraction()
    }
}

@Composable
fun VaultZeroApp(viewModel: VaultViewModel) {
    val lockState by viewModel.lockState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val viewingItem by viewModel.viewingItem.collectAsState()
    val editingItem by viewModel.editingItem.collectAsState()
    val clipboardStatus by viewModel.clipboardStatus.collectAsState()

    // Handle back button
    BackHandler(enabled = viewingItem != null || editingItem != null || currentTab != VaultNavTab.VAULT) {
        if (editingItem != null) {
            viewModel.dismissEditItem()
        } else if (viewingItem != null) {
            viewModel.dismissViewItem()
        } else if (currentTab != VaultNavTab.VAULT) {
            viewModel.setTab(VaultNavTab.VAULT)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = lockState,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "lockStateTransition"
        ) { state ->
            if (state != VaultLockState.UNLOCKED) {
                LockScreen(
                    viewModel = viewModel,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        VaultBottomNavigationBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.setTab(it) }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "tabTransition"
                        ) { tab ->
                            when (tab) {
                                VaultNavTab.VAULT -> VaultListScreen(viewModel = viewModel)
                                VaultNavTab.GENERATOR -> GeneratorScreen(viewModel = viewModel)
                                VaultNavTab.AUDIT -> AuditScreen(viewModel = viewModel)
                                VaultNavTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for viewing decrypted item details
        viewingItem?.let { item ->
            ItemDetailDialog(
                item = item,
                viewModel = viewModel,
                onDismiss = { viewModel.dismissViewItem() },
                onEdit = { viewModel.startEditItem(item) }
            )
        }

        // Dialog for creating or editing items
        editingItem?.let { item ->
            ItemEditorDialog(
                item = item,
                onSave = { updatedItem -> viewModel.saveItem(updatedItem) },
                onDismiss = { viewModel.dismissEditItem() }
            )
        }

        // Non-intrusive Bottom Floating Pill for Clipboard & Status Feedback
        AnimatedVisibility(
            visible = clipboardStatus != null && viewingItem == null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    bottom = if (lockState == VaultLockState.UNLOCKED) 92.dp else 28.dp,
                    start = 20.dp,
                    end = 20.dp
                )
        ) {
            clipboardStatus?.let { status ->
                VaultStatusPill(
                    status = status,
                    onDismiss = { viewModel.dismissClipboardStatus() }
                )
            }
        }
    }
}

@Composable
fun VaultBottomNavigationBar(
    currentTab: VaultNavTab,
    onTabSelected: (VaultNavTab) -> Unit
) {
    NavigationBar(
        modifier = Modifier.testTag("vault_bottom_navigation"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        NavigationBarItem(
            selected = currentTab == VaultNavTab.VAULT,
            onClick = { onTabSelected(VaultNavTab.VAULT) },
            icon = {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "Vault",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = { Text("Vault", fontSize = 12.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = CyberEmerald,
                indicatorColor = CyberEmerald,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.testTag("nav_vault")
        )

        NavigationBarItem(
            selected = currentTab == VaultNavTab.GENERATOR,
            onClick = { onTabSelected(VaultNavTab.GENERATOR) },
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Generator",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = { Text("Generator", fontSize = 12.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = CyberEmerald,
                indicatorColor = CyberEmerald,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.testTag("nav_generator")
        )

        NavigationBarItem(
            selected = currentTab == VaultNavTab.AUDIT,
            onClick = { onTabSelected(VaultNavTab.AUDIT) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Audit",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = { Text("Audit", fontSize = 12.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = CyberEmerald,
                indicatorColor = CyberEmerald,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.testTag("nav_audit")
        )

        NavigationBarItem(
            selected = currentTab == VaultNavTab.SETTINGS,
            onClick = { onTabSelected(VaultNavTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = { Text("Settings", fontSize = 12.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = CyberEmerald,
                indicatorColor = CyberEmerald,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.testTag("nav_settings")
        )
    }
}
