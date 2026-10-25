package com.openvault.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.openvault.app.di.AppContainer
import com.openvault.app.notification.QuickCaptureNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OpenVaultApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Observe app background transition for automatic locking when configured
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    appScope.launch {
                        val settings = runCatching { container.settingsRepository.settingsFlow.first() }
                            .getOrNull() ?: return@launch
                        if (settings.lockOnBackground && container.securityGate.isUnlocked()) {
                            container.securityGate.lock()
                            container.repository.refreshDatabaseSessionForUnlockState()
                            QuickCaptureNotificationManager.dismiss(this@OpenVaultApplication)
                        }
                    }
                }
            })
        }

        // Lock immediately when screen turns off
        runCatching {
            val screenOffFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            ContextCompat.registerReceiver(
                this,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == Intent.ACTION_SCREEN_OFF && container.securityGate.isUnlocked()) {
                            container.securityGate.lock()
                            container.repository.refreshDatabaseSessionForUnlockState()
                            QuickCaptureNotificationManager.dismiss(this@OpenVaultApplication)
                        }
                    }
                },
                screenOffFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }
}
