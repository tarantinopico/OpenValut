package com.openvault.app.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.openvault.app.data.remote.SupabaseRemoteDataSource
import com.openvault.app.data.repository.OpenVaultRepository
import com.openvault.app.data.repository.SettingsRepository
import com.openvault.app.security.BiometricKeyManager
import com.openvault.app.security.ClipboardSecurityHelper
import com.openvault.app.security.DeviceIntegrityChecker
import com.openvault.app.security.SecurityGate
import com.openvault.app.sync.SyncEngine

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val biometricKeyManager = BiometricKeyManager(appContext)
    val securityGate = SecurityGate(appContext, biometricKeyManager)
    val clipboardHelper = ClipboardSecurityHelper(appContext)
    val deviceIntegrityChecker = DeviceIntegrityChecker(appContext, biometricKeyManager)
    val settingsRepository = SettingsRepository(appContext)

    val remoteDataSource = SupabaseRemoteDataSource(
        vaultIdProvider = { securityGate.getVaultIdOrNull() }
    )

    lateinit var repository: OpenVaultRepository
        private set

    lateinit var syncEngine: SyncEngine
        private set

    init {
        repository = OpenVaultRepository(
            context = appContext,
            securityGate = securityGate,
            settingsRepository = settingsRepository,
            syncEngineProvider = { syncEngine }
        )
        syncEngine = SyncEngine(
            daoProvider = { repository.getActiveDao() },
            remoteApi = remoteDataSource,
            securityGate = securityGate,
            isNetworkAvailableProvider = { isNetworkConnected(appContext) }
        )
    }

    companion object {
        fun isNetworkConnected(context: Context): Boolean {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return true
                val activeNetwork = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (_: Exception) {
                true
            }
        }
    }
}
