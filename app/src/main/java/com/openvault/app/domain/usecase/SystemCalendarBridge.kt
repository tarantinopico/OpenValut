package com.openvault.app.domain.usecase

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.openvault.app.domain.model.CalendarEvent
import com.openvault.app.domain.model.RecurrenceRule
import java.util.TimeZone

data class SystemCalendarAccount(
    val id: Long,
    val displayName: String,
    val accountName: String
)

object SystemCalendarBridge {

    fun hasCalendarPermissions(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    /**
     * Builds the Android `CalendarContract.Events` ContentValues map from an OpenVault `CalendarEvent`.
     * When `anonymizeTitle = true`, the exported title is replaced with `"OpenVault událost"` and
     * sensitive description text is omitted so system calendar providers cannot read private notes.
     */
    fun toCalendarContractValues(
        event: CalendarEvent,
        calendarId: Long = 1L,
        anonymizeTitle: Boolean = true
    ): ContentValues {
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(
                CalendarContract.Events.TITLE,
                if (anonymizeTitle) "OpenVault událost" else event.title
            )
            put(
                CalendarContract.Events.DESCRIPTION,
                if (anonymizeTitle) "Šifrovaná událost v aplikaci OpenVault (ID: ${event.id.take(8)})"
                else event.description
            )
            put(CalendarContract.Events.DTSTART, event.startAt)
            put(CalendarContract.Events.DTEND, event.endAt.coerceAtLeast(event.startAt + 60_000L))
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            when (event.recurrenceRule) {
                RecurrenceRule.NONE -> Unit
                RecurrenceRule.DAILY -> put(CalendarContract.Events.RRULE, "FREQ=DAILY")
                RecurrenceRule.WEEKLY -> put(CalendarContract.Events.RRULE, "FREQ=WEEKLY")
                RecurrenceRule.MONTHLY -> put(CalendarContract.Events.RRULE, "FREQ=MONTHLY")
                RecurrenceRule.YEARLY -> put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            }
        }
    }

    fun queryAvailableCalendars(context: Context): List<SystemCalendarAccount> {
        if (!hasCalendarPermissions(context)) return emptyList()
        val list = mutableListOf<SystemCalendarAccount>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: "Kalendář #$id"
                    val account = cursor.getString(2) ?: "Lokální"
                    list.add(SystemCalendarAccount(id, name, account))
                }
            }
        }
        return list
    }

    fun exportEventToSystemCalendar(
        context: Context,
        event: CalendarEvent,
        calendarId: Long = 1L,
        anonymizeTitle: Boolean = true
    ): Long? {
        if (!hasCalendarPermissions(context)) return null
        val values = toCalendarContractValues(event, calendarId, anonymizeTitle)
        return runCatching {
            val existingSysId = event.systemCalendarEventId
            if (existingSysId != null && existingSysId > 0L) {
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingSysId)
                context.contentResolver.update(updateUri, values, null, null)
                existingSysId
            } else {
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                uri?.lastPathSegment?.toLongOrNull()
            }
        }.getOrNull()
    }

    fun importUpcomingEventsFromSystemCalendar(
        context: Context,
        vaultId: String,
        limit: Int = 50
    ): List<CalendarEvent> {
        if (!hasCalendarPermissions(context)) return emptyList()
        val imported = mutableListOf<CalendarEvent>()
        val now = System.currentTimeMillis() - 86_400_000L * 7
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY
        )
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.DTSTART} >= ?",
                arrayOf(now.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && imported.size < limit) {
                    val sysId = cursor.getLong(0)
                    val title = cursor.getString(1) ?: "Systémová událost"
                    if (title == "OpenVault událost") continue
                    val desc = cursor.getString(2).orEmpty()
                    val dtStart = cursor.getLong(3)
                    val dtEnd = cursor.getLong(4).let { if (it <= dtStart) dtStart + 3_600_000L else it }
                    val allDay = cursor.getInt(5) == 1
                    imported.add(
                        CalendarEvent(
                            id = java.util.UUID.randomUUID().toString(),
                            vaultId = vaultId,
                            title = title,
                            description = desc,
                            startAt = dtStart,
                            endAt = dtEnd,
                            allDay = allDay,
                            systemCalendarEventId = sysId
                        )
                    )
                }
            }
        }
        return imported
    }
}
