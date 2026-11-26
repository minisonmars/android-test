package com.caravanlevel

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** The app's sections, as shown on the home page. */
enum class Section(val label: String, val icon: Icon) {
    LEVEL("Level", Icon.LEVEL),
    CHECKLISTS("Checklists", Icon.CHECKLIST),
    VEHICLE("Vehicle info", Icon.VEHICLE),
    SERVICE("Service records", Icon.SERVICE),
    REMINDERS("Reminders", Icon.REMINDER);

    val activity: Class<*>
        get() = when (this) {
            LEVEL -> LevelActivity::class.java
            CHECKLISTS -> ChecklistActivity::class.java
            VEHICLE -> VehicleActivity::class.java
            SERVICE -> ServiceActivity::class.java
            REMINDERS -> RemindersActivity::class.java
        }

    companion object {
        fun of(s: String?) = values().firstOrNull { it.name == s }
    }
}

/** App-wide settings. */
object AppPrefs {
    const val OPEN_HOME = "HOME"
    const val OPEN_LAST = "LAST"

    private fun p(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    /** Sizes in inches instead of cm. Carries over the setting from the old level screen. */
    fun inches(ctx: Context) = p(ctx).getBoolean(
        "inches",
        ctx.getSharedPreferences("level", Context.MODE_PRIVATE).getBoolean("inches", false)
    )
    fun setInches(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("inches", v).apply()

    /** Distances in km instead of miles. */
    fun km(ctx: Context) = p(ctx).getBoolean("km", false)
    fun setKm(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("km", v).apply()

    /** What the app opens on: OPEN_HOME, OPEN_LAST or a Section name. */
    fun openOn(ctx: Context): String = p(ctx).getString("openOn", OPEN_HOME) ?: OPEN_HOME
    fun setOpenOn(ctx: Context, v: String) = p(ctx).edit().putString("openOn", v).apply()

    /** The last page used: a Section name, or OPEN_HOME. */
    fun lastSection(ctx: Context): String = p(ctx).getString("last", OPEN_HOME) ?: OPEN_HOME
    fun setLastSection(ctx: Context, v: String) = p(ctx).edit().putString("last", v).apply()

    fun activeVehicle(ctx: Context): String? = p(ctx).getString("vehicle", null)
    fun setActiveVehicle(ctx: Context, id: String) = p(ctx).edit().putString("vehicle", id).apply()

    /** The checklist last shown for a vehicle. */
    fun checklist(ctx: Context, vehicleId: String): String? = p(ctx).getString("list_$vehicleId", null)
    fun setChecklist(ctx: Context, vehicleId: String, listId: String) =
        p(ctx).edit().putString("list_$vehicleId", listId).apply()

    /** We only ask for the notification permission once by ourselves. */
    fun askedNotifications(ctx: Context) = p(ctx).getBoolean("askedNotif", false)
    fun setAskedNotifications(ctx: Context) = p(ctx).edit().putBoolean("askedNotif", true).apply()
}

/** Units a size can be typed in. Each size remembers its own. */
enum class SizeUnit(val label: String, val cm: Float) {
    MM("mm", 0.1f), CM("cm", 1f), M("m", 100f),
    IN("in", 2.54f), FT("ft", 30.48f), YD("yd", 91.44f);

    companion object {
        fun of(s: String?) = values().firstOrNull { it.name == s }
    }
}

/** Showing and reading sizes, distances, money and dates in the chosen units. */
object Units {
    const val KM_PER_MILE = 1.609344

    fun sizeUnit(ctx: Context) = if (AppPrefs.inches(ctx)) "in" else "cm"

    /** cm -> text in the chosen unit with two decimals, or "" if not set. */
    fun sizeText(ctx: Context, cm: Float): String {
        if (cm <= 0f) return ""
        return Store.fixed(if (AppPrefs.inches(ctx)) cm / Store.CM_PER_INCH else cm)
    }

    /** Text in the chosen unit -> cm (0 if empty). */
    fun parseSize(ctx: Context, s: String): Float {
        val v = s.replace(',', '.').trim().toFloatOrNull() ?: return 0f
        return if (AppPrefs.inches(ctx)) v * Store.CM_PER_INCH else v
    }

    /** The unit a size is shown in: the one it was typed in, else cm or inches from Settings. */
    fun unitOf(ctx: Context, v: Vehicle, key: String): SizeUnit =
        SizeUnit.of(v.sizeUnits[key]) ?: if (AppPrefs.inches(ctx)) SizeUnit.IN else SizeUnit.CM

    /** cm -> number in the given unit with two decimals, or "" if not set. */
    fun sizeNumber(cm: Float, u: SizeUnit): String = if (cm <= 0f) "" else Store.fixed(cm / u.cm)

    /** Typed number in the given unit -> cm (0 if empty). */
    fun parseSizeIn(s: String, u: SizeUnit): Float =
        (s.replace(',', '.').trim().toFloatOrNull() ?: 0f) * u.cm

    fun distUnit(ctx: Context) = if (AppPrefs.km(ctx)) "km" else "miles"

    /** Miles -> number in the chosen unit, e.g. "12,345". */
    fun distNumber(ctx: Context, miles: Int): String {
        val v = if (AppPrefs.km(ctx)) (miles * KM_PER_MILE).roundToInt() else miles
        return String.format(Locale.UK, "%,d", v)
    }

    fun distText(ctx: Context, miles: Int) = distNumber(ctx, miles) + " " + distUnit(ctx)

    /** Text in the chosen unit -> miles, or null if empty. */
    fun parseDist(ctx: Context, s: String): Int? {
        val v = s.replace(",", "").trim().toDoubleOrNull() ?: return null
        return if (AppPrefs.km(ctx)) (v / KM_PER_MILE).roundToInt() else v.roundToInt()
    }

    fun money(s: String): String {
        val v = s.replace("£", "").replace(",", "").trim().toDoubleOrNull() ?: return s
        return String.format(Locale.UK, "£%,.2f", v)
    }

    fun moneyValue(s: String): Double =
        s.replace("£", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0

    private val dateFormat get() = SimpleDateFormat("EEE d MMM yyyy", Locale.UK)
    private val monthFormat get() = SimpleDateFormat("MMMM yyyy", Locale.UK)

    fun date(ms: Long): String = dateFormat.format(Date(ms))
    fun month(ms: Long): String = monthFormat.format(Date(ms))

    /** Midnight at the start of the given day. */
    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Whole days from today to the given day (negative = in the past). */
    fun daysFromToday(ms: Long): Int {
        val diff = startOfDay(ms) - startOfDay(System.currentTimeMillis())
        return (diff / 86_400_000.0).roundToInt()
    }

    fun inDays(days: Int): String = when {
        days == 0 -> "today"
        days == 1 -> "tomorrow"
        days == -1 -> "yesterday"
        days < 0 -> "${-days} days ago"
        else -> "in $days days"
    }

    fun addMonths(ms: Long, months: Int): Long =
        Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.MONTH, months) }.timeInMillis
}

/**
 * Estimates mileage from the last odometer reading, the estimated annual mileage and
 * how the vehicle is used through the year. Each month gets a share of the annual
 * mileage; the shares always add up to 12 months' worth.
 */
object Mileage {
    // Jan..Dec. Spring and summer = April to September.
    private fun weights(u: Usage): DoubleArray = when (u) {
        Usage.ALL_YEAR -> DoubleArray(12) { 1.0 }
        Usage.MOSTLY_SUMMER -> DoubleArray(12) { if (it in 3..8) 1.5 else 0.5 }
        Usage.SUMMER_ONLY -> DoubleArray(12) { if (it in 3..8) 2.0 else 0.0 }
    }

    private fun milesOnDay(v: Vehicle, cal: Calendar): Double {
        val month = cal.get(Calendar.MONTH)
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return v.annualMiles / 12.0 * weights(v.usage)[month] / days
    }

    /** Estimated odometer on the given day, or null with no reading. */
    fun estimateAt(v: Vehicle, ms: Long): Int? {
        val r = v.latestReading ?: return null
        if (v.annualMiles <= 0 || ms <= r.dateMs) return r.miles
        val cal = Calendar.getInstance().apply { timeInMillis = Units.startOfDay(r.dateMs) }
        val end = Units.startOfDay(ms)
        var miles = r.miles.toDouble()
        while (cal.timeInMillis < end) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            miles += milesOnDay(v, cal)
        }
        return miles.roundToInt()
    }

    fun estimateNow(v: Vehicle) = estimateAt(v, System.currentTimeMillis())

    /** Estimated day the odometer reaches the given figure, or null if it can't be worked out. */
    fun dateWhen(v: Vehicle, targetMiles: Int): Long? {
        val r = v.latestReading ?: return null
        if (targetMiles <= r.miles) return r.dateMs
        if (v.annualMiles <= 0) return null
        val cal = Calendar.getInstance().apply { timeInMillis = Units.startOfDay(r.dateMs) }
        var miles = r.miles.toDouble()
        repeat(365 * 30) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            miles += milesOnDay(v, cal)
            if (miles >= targetMiles) return cal.timeInMillis
        }
        return null
    }
}

/** When a reminder is due: by date, by estimated mileage date, whichever is first. */
object Due {
    /** The estimated or set due day, or null if unknown. */
    fun dateOf(v: Vehicle, r: Reminder): Long? {
        val byMiles = r.dueMiles?.let { Mileage.dateWhen(v, it) }
        return listOfNotNull(r.dueMs, byMiles).minOrNull()
    }

    fun days(v: Vehicle, r: Reminder): Int? = dateOf(v, r)?.let { Units.daysFromToday(it) }

    /** One line describing when it is due. */
    fun describe(ctx: Context, v: Vehicle, r: Reminder): String {
        val parts = mutableListOf<String>()
        r.dueMs?.let { parts.add("Due ${Units.date(it)} (${Units.inDays(Units.daysFromToday(it))})") }
        r.dueMiles?.let { m ->
            val est = Mileage.dateWhen(v, m)
            parts.add(
                "Due at ${Units.distText(ctx, m)}" +
                    if (est != null) " (estimated around ${Units.date(est)})"
                    else if (v.latestReading == null) " (add a mileage reading on Vehicle info for an estimate)"
                    else " (add an annual mileage on Vehicle info for an estimate)"
            )
        }
        if (parts.isEmpty()) parts.add("No due date set")
        return parts.joinToString("\n")
    }

    /** Mark as done: move on to the next due date/mileage, or return false if it doesn't repeat. */
    fun complete(v: Vehicle, r: Reminder): Boolean {
        if (r.repeatMonths <= 0 && r.repeatMiles <= 0) return false
        val today = Units.startOfDay(System.currentTimeMillis())
        r.dueMs = if (r.repeatMonths > 0) Units.addMonths(today, r.repeatMonths) else null
        r.dueMiles = if (r.repeatMiles > 0) {
            (Mileage.estimateNow(v) ?: r.dueMiles ?: 0) + r.repeatMiles
        } else null
        r.notifiedSoon = false
        r.notifiedDue = false
        return true
    }
}
