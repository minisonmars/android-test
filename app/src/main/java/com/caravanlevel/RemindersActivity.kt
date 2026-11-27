package com.caravanlevel

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Reminders for the chosen vehicle, by date, by mileage, or both (whichever comes first).
 * Mileage reminders use the estimate from Vehicle info to work out roughly when they're due.
 */
class RemindersActivity : Page() {
    override val section = Section.REMINDERS

    companion object {
        private const val CALENDAR_PERMISSION = 2
    }

    override fun onResume() {
        super.onResume()
        askForNotifications()
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && vehicle.reminders.isNotEmpty() &&
            !Reminders.notificationsAllowed(this) && !AppPrefs.askedNotifications(this)
        ) {
            AppPrefs.setAskedNotifications(this)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALENDAR_PERMISSION) {
            if (CalendarSync.allowed(this)) chooseCalendar()
            else ui.confirm("Calendar not allowed", "To show reminders in a calendar, allow Calendar for Caravan Leveller in the phone's settings.", "Open settings") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            return
        }
        Reminders.checkNow(this)
        reload()
    }

    override fun build() {
        val v = vehicle
        content.addView(ui.button("Add a reminder", Colours.ACCENT) { edit(null) })

        if (!Reminders.notificationsAllowed(this)) {
            content.addView(ui.button("Notifications are off for this app. Tap to turn them on.", Colours.RED) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
        } else {
            content.addView(ui.text(
                "You'll get a notification ${Reminders.SOON_DAYS} days before something is due, and again on the day.",
                16f, Colours.MUTED
            ).apply { setPadding(0, ui.dp(6), 0, ui.dp(6)) })
        }

        // Phone calendar
        val calName = CalendarSync.currentName(this)
        content.addView(ui.button(
            if (CalendarSync.enabled(this) && calName != null) "Reminders with a date also go in your \"$calName\" calendar. Tap to change."
            else "Show reminders with a date in a phone calendar"
        ) { calendarTapped() })

        if (v.reminders.isEmpty()) {
            content.addView(ui.text("No reminders yet for ${v.name}.", 18f, Colours.MUTED).apply {
                setPadding(0, ui.dp(12), 0, 0)
            })
            return
        }

        // Soonest first; ones without a due date at the end
        v.reminders.sortedBy { Due.dateOf(v, it) ?: Long.MAX_VALUE }.forEach { r ->
            val days = Due.days(v, r)
            val colour = when {
                days == null -> Colours.CARD
                days < 0 -> Colours.RED_CARD
                days <= 30 -> Colours.AMBER_CARD
                else -> Colours.CARD
            }
            val card = ui.card(colour)
            card.addView(ui.text(r.title, 20f, bold = true))
            card.addView(ui.text(Due.describe(this, v, r), 17f))
            val repeat = listOfNotNull(
                r.repeatMonths.takeIf { it > 0 }?.let { "every $it month${if (it == 1) "" else "s"}" },
                r.repeatMiles.takeIf { it > 0 }?.let { "every ${Units.distText(this, it)}" }
            )
            if (repeat.isNotEmpty()) card.addView(ui.text("Repeats " + repeat.joinToString(" or "), 16f))
            if (r.notes.isNotBlank()) card.addView(ui.text(r.notes, 16f))
            card.addView(ui.row(
                ui.rowButton("Done", Colours.GREEN) { done(r) },
                ui.rowButton("Change") { edit(r) },
                ui.binButton {
                    ui.confirm("Delete \"${r.title}\"?", "", "Delete") {
                        v.reminders.remove(r)
                        saveAndRebuild()
                    }
                }
            ))
            content.addView(card)
        }
    }

    private fun calendarTapped() {
        if (!CalendarSync.allowed(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR), CALENDAR_PERMISSION)
        } else chooseCalendar()
    }

    /** Pick a calendar: a new one of the app's own, or one already on the phone. */
    private fun chooseCalendar() {
        val cals = try { CalendarSync.writableCalendars(this) } catch (_: Exception) { emptyList() }
        val items = mutableListOf("A new \"Caravan Leveller\" calendar on this phone")
        items += cals.map { if (it.account.isNotBlank() && it.account != it.name) "${it.name} (${it.account})" else it.name }
        val on = CalendarSync.enabled(this)
        if (on) items += "Stop adding reminders to a calendar"
        AlertDialog.Builder(this)
            .setTitle("Which calendar?")
            .setItems(items.toTypedArray()) { _, i ->
                when {
                    i == 0 -> {
                        val id = try { CalendarSync.makeOwnCalendar(this) } catch (_: Exception) { null }
                        if (id == null) ui.confirm("Couldn't make the calendar", "Try choosing one of your existing calendars instead.", "OK") {}
                        else CalendarSync.choose(this, id) { reload() }
                    }
                    i <= cals.size -> CalendarSync.choose(this, cals[i - 1].id) { reload() }
                    else -> CalendarSync.turnOff(this) { reload() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun done(r: Reminder) {
        if (Due.complete(vehicle, r)) {
            saveAndRebuild()
        } else {
            ui.confirm("Done: ${r.title}", "This reminder doesn't repeat, so it will be removed.", "Remove") {
                vehicle.reminders.remove(r)
                saveAndRebuild()
            }
        }
    }

    /** Common reminders to start from, depending on the vehicle type. */
    private fun presets(t: VehicleType): List<Reminder> {
        val list = mutableListOf<Reminder>()
        if (t != VehicleType.CAR) list.add(Reminder("Habitation service", repeatMonths = 12))
        if (t.motorised) {
            list.add(Reminder("MOT", repeatMonths = 12))
            list.add(Reminder("Road tax", repeatMonths = 12))
            list.add(Reminder("Vehicle service", repeatMonths = 12, repeatMiles = 10000))
        }
        list.add(Reminder("Insurance renewal", repeatMonths = 12))
        if (t != VehicleType.CAR) {
            list.add(Reminder("Check the tyres and tyre pressures", repeatMonths = 1))
            list.add(Reminder("Charge the leisure battery (in storage)", repeatMonths = 1))
            list.add(Reminder("Check the fire extinguisher and alarms", repeatMonths = 12))
        }
        if (t == VehicleType.CARAVAN) list.add(Reminder("Check the breakaway cable", repeatMonths = 6))
        if (t == VehicleType.CAR) list.add(Reminder("Check the tow bar and electrics", repeatMonths = 12))
        return list
    }

    private fun edit(existing: Reminder?) {
        val r = existing ?: Reminder("")
        val box = ui.column(20)
        val title = ui.field(r.title, "What, for example: Habitation service")
        var dueMs = r.dueMs
        val dateBtn = ui.button("") {}
        fun showDate() { dateBtn.text = dueMs?.let { "Due date: ${Units.date(it)}" } ?: "Due date: none (tap to set)" }
        showDate()
        dateBtn.setOnClickListener { ui.pickDate(dueMs) { dueMs = it; showDate() } }
        val clearDate = ui.button("No due date") { dueMs = null; showDate() }

        val unit = Units.distUnit(this)
        val miles = ui.wholeNumberField(r.dueMiles?.let { Units.distNumber(this, it).replace(",", "") } ?: "", "Due at mileage ($unit), optional")
        val months = ui.wholeNumberField(if (r.repeatMonths > 0) r.repeatMonths.toString() else "", "Repeat every ... months (optional)")
        val repeatMiles = ui.wholeNumberField(if (r.repeatMiles > 0) Units.distNumber(this, r.repeatMiles).replace(",", "") else "", "Repeat every ... $unit (optional)")
        val notes = ui.field(r.notes, "Notes (optional)")

        if (existing == null) {
            val ps = presets(vehicle.type)
            box.addView(ui.label("Start from a common one"))
            box.addView(ui.spinner(listOf("Choose...") + ps.map { it.title }, 0) { i ->
                if (i == 0) return@spinner
                val p = ps[i - 1]
                title.setText(p.title)
                months.setText(if (p.repeatMonths > 0) p.repeatMonths.toString() else "")
                repeatMiles.setText(if (p.repeatMiles > 0) Units.distNumber(this, p.repeatMiles).replace(",", "") else "")
                if (dueMs == null && p.repeatMonths > 0) {
                    dueMs = Units.addMonths(Units.startOfDay(System.currentTimeMillis()), p.repeatMonths) + 12 * 3600_000L
                    showDate()
                }
            })
        }
        box.addView(ui.label("What"))
        box.addView(title)
        box.addView(dateBtn)
        box.addView(clearDate)
        val byMiles = vehicle.type.motorised // caravans have no odometer
        if (byMiles) {
            box.addView(ui.label("Due at mileage ($unit)"))
            box.addView(miles)
        }
        box.addView(ui.label("Repeats"))
        box.addView(months)
        if (byMiles) box.addView(repeatMiles)
        box.addView(notes)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New reminder" else "Change reminder")
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                r.title = title.text.toString().trim().ifEmpty { "Reminder" }
                val newDue = dueMs
                val newMiles = Units.parseDist(this, miles.text.toString())
                if (newDue != r.dueMs || newMiles != r.dueMiles) { r.notifiedSoon = false; r.notifiedDue = false }
                r.dueMs = newDue
                r.dueMiles = newMiles
                r.repeatMonths = months.text.toString().toIntOrNull() ?: 0
                r.repeatMiles = Units.parseDist(this, repeatMiles.text.toString()) ?: 0
                r.notes = notes.text.toString().trim()
                if (existing == null) vehicle.reminders.add(r)
                save()
                Reminders.checkNow(this) // may mark it as notified, so load again after
                reload()
                askForNotifications()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
