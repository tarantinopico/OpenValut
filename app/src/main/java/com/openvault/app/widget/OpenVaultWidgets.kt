package com.openvault.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.openvault.app.notification.QuickActionBroadcastReceiver
import com.openvault.app.notification.QuickCaptureNotificationManager

private fun buildLaunchPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

class QuickNoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_note)
            views.setOnClickPendingIntent(
                R.id.widget_root_quick_note,
                buildLaunchPendingIntent(context, QuickCaptureNotificationManager.ACTION_QUICK_NOTE, 201)
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}

class QuickActionsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val lockIntent = Intent(context, QuickActionBroadcastReceiver::class.java).apply {
            action = QuickCaptureNotificationManager.ACTION_LOCK_NOW
        }
        val lockPending = PendingIntent.getBroadcast(
            context,
            205,
            lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)
            views.setOnClickPendingIntent(
                R.id.btn_widget_note,
                buildLaunchPendingIntent(context, QuickCaptureNotificationManager.ACTION_QUICK_NOTE, 202)
            )
            views.setOnClickPendingIntent(
                R.id.btn_widget_password,
                buildLaunchPendingIntent(context, QuickCaptureNotificationManager.ACTION_QUICK_PASSWORD, 203)
            )
            views.setOnClickPendingIntent(
                R.id.btn_widget_event,
                buildLaunchPendingIntent(context, QuickCaptureNotificationManager.ACTION_QUICK_EVENT, 204)
            )
            views.setOnClickPendingIntent(R.id.btn_widget_lock, lockPending)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}

class TodayAgendaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_today_agenda)
            views.setOnClickPendingIntent(
                R.id.widget_root_today,
                buildLaunchPendingIntent(context, QuickCaptureNotificationManager.ACTION_OPEN_CALENDAR, 206)
            )
            views.setOnClickPendingIntent(
                R.id.btn_widget_add_event,
                buildLaunchPendingIntent(context, QuickCaptureNotificationManager.ACTION_QUICK_EVENT, 207)
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}

class RecentNotesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_recent_notes)
            views.setOnClickPendingIntent(
                R.id.widget_root_recent_notes,
                buildLaunchPendingIntent(context, QuickCaptureNotificationManager.ACTION_OPEN_NOTES, 208)
            )
            views.setOnClickPendingIntent(
                R.id.btn_widget_new_note,
                buildLaunchPendingIntent(context, QuickCaptureNotificationManager.ACTION_QUICK_NOTE, 209)
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
