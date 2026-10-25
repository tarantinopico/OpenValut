package com.openvault.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.openvault.app.OpenVaultApplication
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OpenVaultApplication ?: return Result.success()
        val container = app.container
        if (!container.securityGate.isUnlocked() || container.securityGate.isDecoyModeActive()) {
            return Result.success()
        }
        return try {
            val status = container.syncEngine.performSync()
            if (status is SyncStatus.Offline) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_SYNC_WORK = "openvault_periodic_sync_work"
        private const val UNIQUE_ONETIME_SYNC_WORK = "openvault_onetime_sync_work"

        fun schedulePeriodicSync(
            context: Context,
            intervalMinutes: Int,
            wifiOnly: Boolean
        ) {
            val workManager = WorkManager.getInstance(context)
            if (intervalMinutes <= 0) {
                workManager.cancelUniqueWork(UNIQUE_PERIODIC_SYNC_WORK)
                return
            }
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes.coerceAtLeast(15).toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueImmediateSync(context: Context, wifiOnly: Boolean = false) {
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONETIME_SYNC_WORK,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
