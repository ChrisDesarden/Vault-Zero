package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.VaultDatabase
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VaultZero", appName)
  }

  @Test
  fun `vault setup lock and unlock flow`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = VaultDatabase.getInstance(context)
    val prefs = VaultPreferences(context)
    prefs.resetAll()

    val repository = VaultRepository(db.vaultDao(), prefs)
    assertFalse(repository.isInitialized)
    assertFalse(repository.isUnlocked)

    val masterPassword = "TestMasterPassword123!"
    val (recoveryPhrase, vmkHex) = repository.setupMasterPassword(masterPassword)

    assertTrue(repository.isInitialized)
    assertTrue(repository.isUnlocked)
    assertTrue(recoveryPhrase.split(" ").size == 12)
    assertEquals(64, vmkHex.length)

    // Lock vault
    repository.lock()
    assertFalse(repository.isUnlocked)

    // Unlock with wrong password should fail
    val wrongUnlock = repository.unlock("WrongPassword123!")
    assertFalse(wrongUnlock)
    assertFalse(repository.isUnlocked)

    // Unlock with correct password should succeed
    val correctUnlock = repository.unlock(masterPassword)
    assertTrue(correctUnlock)
    assertTrue(repository.isUnlocked)

    // Lock and test VMK hex unlock (biometrics route)
    repository.lock()
    assertFalse(repository.isUnlocked)
    val vmkUnlock = repository.unlockWithVmkHex(vmkHex)
    assertTrue(vmkUnlock)
    assertTrue(repository.isUnlocked)
  }
}


