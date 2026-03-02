package com.example.speach_recognotion_llm.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.provider.CalendarContract
import com.example.assistant.models.CalendarEvent
import com.example.assistant.repository.CalendarRepository
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class AndroidCalendarRepository(
    private val contentResolver: ContentResolver
) : CalendarRepository {

    override suspend fun createEvent(
        title: String,
        startTime: String,
        endTime: String,
        goalId: String?
    ): String {
        val calendarId = getPrimaryCalendarId() ?: throw IllegalStateException("No calendar found")
        val description = if (goalId != null) "[GOAL:$goalId]" else ""

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, isoToMillis(startTime))
            put(CalendarContract.Events.DTEND, isoToMillis(endTime))
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.currentSystemDefault().id)
        }

        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: throw IllegalStateException("Failed to create event")
        return ContentUris.parseId(uri).toString()
    }

    override suspend fun updateEvent(
        eventId: String,
        title: String?,
        startTime: String?,
        endTime: String?
    ) {
        val values = ContentValues()
        title?.let { values.put(CalendarContract.Events.TITLE, it) }
        startTime?.let { values.put(CalendarContract.Events.DTSTART, isoToMillis(it)) }
        endTime?.let { values.put(CalendarContract.Events.DTEND, isoToMillis(it)) }

        if (values.size() > 0) {
            val uri = ContentUris.withAppendedId(
                CalendarContract.Events.CONTENT_URI, eventId.toLong()
            )
            contentResolver.update(uri, values, null, null)
        }
    }

    override suspend fun deleteEvent(eventId: String) {
        val uri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI, eventId.toLong()
        )
        contentResolver.delete(uri, null, null)
    }

    override suspend fun getEvents(startDate: String, endDate: String): List<CalendarEvent> {
        val startMillis = dateToStartOfDayMillis(startDate)
        val endMillis = dateToStartOfDayMillis(endDate) + 86400000L // end of day

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END
        )

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMillis.toString())
            .appendPath(endMillis.toString())
            .build()

        val cursor: Cursor? = contentResolver.query(
            uri, projection, null, null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )

        val events = mutableListOf<CalendarEvent>()
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0).toString()
                val title = it.getString(1) ?: ""
                val description = it.getString(2) ?: ""
                val begin = it.getLong(3)
                val end = it.getLong(4)

                val goalId = parseGoalId(description)

                events.add(
                    CalendarEvent(
                        eventId = id,
                        title = title,
                        description = description,
                        startTime = millisToIso(begin),
                        endTime = millisToIso(end),
                        goalId = goalId
                    )
                )
            }
        }
        return events
    }

    private fun getPrimaryCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return null
    }

    private fun parseGoalId(description: String): String? {
        val regex = Regex("""\[GOAL:(.+?)]""")
        return regex.find(description)?.groupValues?.get(1)
    }

    private fun isoToMillis(iso: String): Long {
        return try {
            Instant.parse(iso).toEpochMilliseconds()
        } catch (_: Exception) {
            try {
                val ldt = LocalDateTime.parse(iso)
                ldt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            } catch (_: Exception) {
                val ld = LocalDate.parse(iso)
                ld.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            }
        }
    }

    private fun dateToStartOfDayMillis(date: String): Long {
        return try {
            val ld = LocalDate.parse(date)
            ld.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        } catch (_: Exception) {
            isoToMillis(date)
        }
    }

    private fun millisToIso(millis: Long): String {
        return Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .toString()
    }
}
