package com.caravanlevel

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Puts reminders that have a due date into a phone calendar as all-day events, and keeps
 * them matching: changed when a reminder changes, removed when it's deleted or turned off.
 * The calendar is either one the user already has (for example their Google calendar) or
 * a "Caravan Leveller" calendar this app makes on the phone.
 *
 * Which event belongs to which reminder is kept in the "calendar" settings on this phone.
 */
object CalendarSync {
    private const val OWN_NAME = "Caravan Leveller"
    private const val OFF = -1L
    private val worker = Executors.newSingleThreadExecutor()

    class Cal(val id: Long, val name: String, val account: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("calendar", Context.MODE_PRIVATE)
    private fun appPrefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    fun calendarId(ctx: Context) = appPrefs(ctx).getLong("calendarId", OFF)
    fun enabled(ctx: Context) = calendarId(ctx) != OFF

    fun allowed(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Calendars on the phone that events can be added to (not counting our own). */
    fun writableCalendars(ctx: Context): List<Cal> {
        val list = mutableListOf<Cal>()
        ctx.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE),
            "${Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${Calendars.VISIBLE} = 1",
            arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(3) == CalendarContract.ACCOUNT_TYPE_LOCAL && c.getString(2) == OWN_NAME) continue
                list.add(Cal(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: ""))
            }
        }
        return list
    }

    /** The name of the calendar in use, for the Reminders page. */
    fun currentName(ctx: Context): String? {
        val id = calendarId(ctx)
        if (id == OFF || !allowed(ctx)) return null
        return try {
            ctx.contentResolver.query(
                ContentUris.withAppendedId(Calendars.CONTENT_URI, id),
                arrayOf(Calendars.CALENDAR_DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null }
    }

    private fun asSyncAdapter(uri: Uri) = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, OWN_NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun ownCalendarId(ctx: Context): Long? = ctx.contentResolver.query(
        Calendars.CONTENT_URI, arrayOf(Calendars._ID),
        "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
        arrayOf(OWN_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL), null
    )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    /** Finds or makes the app's own calendar on the phone. */
    fun makeOwnCalendar(ctx: Context): Long {
        ownCalendarId(ctx)?.let { return it }
        val v = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, OWN_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, OWN_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, OWN_NAME)
            put(Calendars.CALENDAR_COLOR, Colours.ACCENT)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, OWN_NAME)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val uri = ctx.contentResolver.insert(asSyncAdapter(Calendars.CONTENT_URI), v) ?: error("Couldn't make the calendar")
        return ContentUris.parseId(uri)
    }

    /** Switches to another calendar (or OFF): moves every event over by removing and adding again. */
    fun choose(ctx: Context, newId: Long, done: () -> Unit) {
        val app = ctx.applicationContext
        worker.execute {
            try {
                removeAll(app)
                appPrefs(app).edit().putLong("calendarId", newId).commit()
                if (newId == OFF) ownCalendarId(app)?.let {
                    app.contentResolver.delete(asSyncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, it)), null, null)
                }
                syncNow(app)
            } catch (_: Exception) {}
            android.os.Handler(android.os.Looper.getMainLooper()).post(done)
        }
    }

    fun turnOff(ctx: Context, done: () -> Unit) = choose(ctx, OFF, done)

    /** Brings the calendar up to date in the background. Called after every save. */
    fun syncLater(ctx: Context) {
        if (!enabled(ctx)) return
        val app = ctx.applicationContext
        worker.execute { try { syncNow(app) } catch (_: Exception) {} }
    }

    private fun removeAll(ctx: Context) {
        if (!allowed(ctx)) { prefs(ctx).edit().clear().commit(); return }
        val p = prefs(ctx)
        p.all.values.forEach { id ->
            (id as? Long)?.let { ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, it), null, null) }
        }
        p.edit().clear().commit()
    }

    private fun eventExists(ctx: Context, id: Long): Boolean = ctx.contentResolver.query(
        ContentUris.withAppendedId(Events.CONTENT_URI, id), arrayOf(Events._ID, Events.DELETED), null, null, null
    )?.use { c -> c.moveToFirst() && c.getInt(1) == 0 } ?: false

    private fun syncNow(ctx: Context) {
        val calId = calendarId(ctx)
        if (calId == OFF || !allowed(ctx)) return
        val garage = GarageStore.load(ctx)
        val p = prefs(ctx)
        val known = p.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
        val e = p.edit()
        val wanted = mutableSetOf<String>()

        garage.vehicles.forEach { v ->
            v.reminders.forEach { r ->
                val due = r.dueMs ?: return@forEach
                wanted.add(r.id)
                val values = ContentValues().apply {
                    put(Events.CALENDAR_ID, calId)
                    put(Events.TITLE, "${r.title} (${v.name})")
                    put(Events.DESCRIPTION, listOf(r.notes, "From Caravan Leveller").filter { it.isNotBlank() }.joinToString("\n\n"))
                    val start = utcMidnight(due)
                    put(Events.DTSTART, start)
                    put(Events.DTEND, start + 86_400_000L)
                    put(Events.ALL_DAY, 1)
                    put(Events.EVENT_TIMEZONE, "UTC")
                }
                val existing = known[r.id]
                if (existing != null && eventExists(ctx, existing)) {
                    ctx.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, existing), values, null, null)
                } else {
                    ctx.contentResolver.insert(Events.CONTENT_URI, values)?.let { e.putLong(r.id, ContentUris.parseId(it)) }
                }
            }
        }
        // Reminders that were deleted, or no longer have a due date
        known.forEach { (rid, eid) ->
            if (rid !in wanted) {
                ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eid), null, null)
                e.remove(rid)
            }
        }
        e.commit()
    }

    /** All-day events are stored as midnight UTC of that date. */
    private fun utcMidnight(localMs: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMs }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

}
