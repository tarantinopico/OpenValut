package com.openvault.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.openvault.app.OpenVaultApplication

object QuickCaptureNotificationManager {
    private const val CHANNEL_ID = "openvault_quick_capture_channel"
    private const val NOTIFICATION_ID = 4401

    const val ACTION_QUICK_NOTE = "com.openvault.app.ACTION_QUICK_NOTE"
    const val ACTION_QUICK_EVENT = "com.openvault.app.ACTION_QUICK_EVENT"
    const val ACTION_QUICK_PASSWORD = "com.openvault.app.ACTION_QUICK_PASSWORD"
    const val ACTION_OPEN_AUDIT = "com.openvault.app.ACTION_OPEN_AUDIT"
    const val ACTION_OPEN_CALENDAR = "com.openvault.app.ACTION_OPEN_CALENDAR"
    const val ACTION_OPEN_NOTES = "com.openvault.app.ACTION_OPEN_NOTES"
    const val ACTION_LOCK_NOW = "com.openvault.app.ACTION_LOCK_NOW"

    fun showIfEnabled(context: Context, enabled: Boolean, isUnlocked: Boolean) {
        if (!enabled || !isUnlocked) {
            dismiss(context)
            return
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenVault Rychlé akce",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tichá lišta pro bleskové vytvoření poznámky, události, hesla a okamžité uzamčení trezoru"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val notePending = createActivityPendingIntent(context, ACTION_QUICK_NOTE, 101)
        val eventPending = createActivityPendingIntent(context, ACTION_QUICK_EVENT, 102)
        val pwdPending = createActivityPendingIntent(context, ACTION_QUICK_PASSWORD, 103)

        val lockIntent = Intent(context, QuickActionBroadcastReceiver::class.java).apply {
            action = ACTION_LOCK_NOW
        }
        val lockPending = PendingIntent.getBroadcast(
            context,
            104,
            lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("OpenVault • Trezor odemčen (E2E)")
            .setContentText("Rychlé šifrované akce nebo okamžité zamknutí")
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(notePending)
            .addAction(0, "+ Poznámka", notePending)
            .addAction(0, "+ Událost", eventPending)
            .addAction(0, "Zamknout hned", lockPending)
            .build()

        runCatching {
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    fun dismiss(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { nm.cancel(NOTIFICATION_ID) }
    }

    private fun createActivityPendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class QuickActionBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == QuickCaptureNotificationManager.ACTION_LOCK_NOW) {
            val app = context.applicationContext as? OpenVaultApplication
            app?.container?.securityGate?.lock()
            app?.container?.repository?.refreshDatabaseSessionForUnlockState()
            QuickCaptureNotificationManager.dismiss(context)
        }
    }
}
