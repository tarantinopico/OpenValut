package com.example

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.openvault.app.security.BiometricKeyManager
import com.openvault.app.security.DuressAction
import com.openvault.app.security.SecurityGate
import com.openvault.app.security.UnlockResult
import com.openvault.app.security.VaultLockState
import com.openvault.app.security.VaultLockedException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric integration tests covering Android Context, SecurityGate state transitions,
 * Lock Zeroization, Reboot Password Enforcement, Duress / Panic Decoy & Wipe behavior,
 * and MainActivity startup rendering without FLAG_SECURE black screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    // Scenario 11: SecurityGate lifecycle — Vault Initialization, Lock Zeroization, Reboot Policy, Master Password Unlock
    @Test
    fun scenario11_securityGateLifecycleAndZeroizationOnLock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("OpenVault", context.getString(R.string.app_name))

        val prefs = context.getSharedPreferences("test_vault_prefs_11", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val securityGate = SecurityGate(
            context = context,
            biometricKeyManager = BiometricKeyManager(context),
            customPreferences = prefs
        )

        assertTrue(securityGate.lockState.value is VaultLockState.Uninitialized)

        // Initialize new vault
        val vaultId = securityGate.initializeNewVault(
            masterPassword = "MasterPassword#2026"
        )
        assertTrue(vaultId.isNotBlank())
        assertTrue(securityGate.isUnlocked())
        assertTrue(securityGate.lockState.value is VaultLockState.Unlocked)

        // Encrypt & decrypt item via SecurityGate
        val cipherBlob = securityGate.encryptString(
            plainText = "Confidential Note Body",
            aad = "note-1"
        )
        val decrypted = securityGate.decryptString(
            ciphertext = cipherBlob.ciphertext,
            nonce = cipherBlob.nonce,
            aad = "note-1"
        )
        assertEquals("Confidential Note Body", decrypted)

        // Lock vault -> Vault Key zeroized in RAM, encryption must fail with VaultLockedException while locked
        securityGate.lock()
        assertFalse(securityGate.isUnlocked())
        assertTrue(securityGate.lockState.value is VaultLockState.Locked)
        assertThrows(VaultLockedException::class.java) {
            securityGate.encryptString("Should Fail")
        }

        // Simulate device reboot -> requirePasswordAfterReboot must be true
        securityGate.simulateDeviceReboot()
        val lockedAfterReboot = securityGate.lockState.value as VaultLockState.Locked
        assertTrue(lockedAfterReboot.requirePasswordAfterReboot)

        // Unlock with Master Password
        val outcome = securityGate.unlockWithPassword("MasterPassword#2026")
        assertTrue(outcome is UnlockResult.Success)
        assertTrue(securityGate.isUnlocked())
    }

    // Scenario 12: Duress / Panic Password triggers Decoy Vault or Cryptographic Wipe
    @Test
    fun scenario12_duressPasswordDecoyAndCryptographicWipe() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_vault_prefs_12", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val securityGate = SecurityGate(
            context = context,
            biometricKeyManager = BiometricKeyManager(context),
            customPreferences = prefs
        )

        securityGate.initializeNewVault(
            masterPassword = "PrimaryMasterPassword!99"
        )

        // Configure Duress Password in DECOY_VAULT mode
        securityGate.configureDuressPassword(
            duressPassword = "PanicDuressPassword!00",
            action = DuressAction.DECOY_VAULT
        )
        securityGate.lock()

        // Unlocking with Duress Password enters isolated Decoy mode
        val decoyOutcome = securityGate.unlockWithPassword("PanicDuressPassword!00")
        assertTrue(decoyOutcome is UnlockResult.Success)
        assertTrue((decoyOutcome as UnlockResult.Success).isDecoyMode)
        assertTrue(securityGate.isDecoyModeActive())

        // Reconfigure Duress Password to CRYPTOGRAPHIC_WIPE and verify panic wipe destroys keys
        securityGate.lock()
        securityGate.unlockWithPassword("PrimaryMasterPassword!99")
        securityGate.configureDuressPassword(
            duressPassword = "PanicWipePassword!00",
            action = DuressAction.CRYPTOGRAPHIC_WIPE
        )
        securityGate.lock()

        val wipeOutcome = securityGate.unlockWithPassword("PanicWipePassword!00")
        assertTrue(wipeOutcome is UnlockResult.DuressWipeTriggered)
        assertTrue(securityGate.lockState.value is VaultLockState.Uninitialized)
    }

    // Scenario 13: MainActivity launches cleanly without FLAG_SECURE black screen
    @Test
    fun scenario13_mainActivityLaunchAndFlagSecureDefaultCleared() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val activity = controller.get()
            assertNotNull(activity)
            val flags = activity.window.attributes.flags
            val isFlagSecureSet = (flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
            assertFalse("FLAG_SECURE must be off by default so streaming emulator is not black", isFlagSecureSet)
        }
    }
}
