package com.caravanlevel

import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast

/**
 * Everything about the chosen vehicle: name and type, details (make, model, CRiS...),
 * sizes (also used by the level), equipment, mileage and notes.
 * Changes are kept as soon as you leave the page.
 */
class VehicleActivity : Page() {
    override val section = Section.VEHICLE

    /** Runs before each save to copy typed text into the vehicle. */
    private val commits = mutableListOf<() -> Unit>()

    override fun commit() = commits.forEach { it() }

    /** Read-only until you tap Edit, so nothing gets changed by accident. */
    private var editing = false

    override fun build() {
        commits.clear()
        val v = vehicle
        val unit = Units.sizeUnit(this)

        content.addView(if (editing) ui.button("Done editing", Colours.ACCENT) { finishEditing() }
        else ui.button("Edit", Colours.ACCENT) { editing = true; rebuild() })

        // Vehicle
        content.addView(ui.heading("Vehicle"))
        if (editing) {
            content.addView(ui.label("Name"))
            val name = ui.field(v.name)
            content.addView(name)
            commits.add { name.text.toString().trim().takeIf { it.isNotEmpty() }?.let { v.name = it } }
            content.addView(ui.label("Type"))
            val types = VehicleType.values()
            content.addView(ui.spinner(types.map { it.label }, types.indexOf(v.type)) {
                save()
                v.type = types[it]
                save()
                reload() // the name in the bar and the size names change
            })
        } else {
            show("Name", v.name)
            show("Type", v.type.label)
        }

        // Details
        content.addView(ui.heading("Details"))
        detailList(v.details, "For example: Chassis number", "For example: Swift")

        // Sizes
        content.addView(ui.heading("Sizes ($unit)"))
        fun size(label: String, get: () -> Float, set: (Float) -> Unit) {
            if (!editing) {
                show(label, Units.sizeText(this, get()).let { if (it.isEmpty()) "" else "$it $unit" })
                return
            }
            content.addView(ui.label(label))
            val f = ui.numberField(Units.sizeText(this, get()))
            content.addView(f)
            commits.add { set(Units.parseSize(this, f.text.toString())) }
        }
        size("Overall length", { v.lengthCm }) { v.lengthCm = it }
        if (v.type == VehicleType.CARAVAN) size("Body length (without the A-frame)", { v.bodyLengthCm }) { v.bodyLengthCm = it }
        size("Width (body, without mirrors)", { v.widthCm }) { v.widthCm = it }
        size("Height", { v.heightCm }) { v.heightCm = it }

        content.addView(ui.heading("Levelling sizes ($unit)"))
        content.addView(ui.text("The level uses these to tell you how much to raise or lower.", 16f, Colours.MUTED))
        size("Track width: wheel centre to wheel centre", { v.trackCm }) { v.trackCm = it }
        if (v.type == VehicleType.CARAVAN) {
            size("Hitch to axle: coupling to axle centre (midpoint for twin axle)", { v.hitchCm }) { v.hitchCm = it }
        } else {
            size("Wheelbase: front axle centre to rear axle centre", { v.wheelbaseCm }) { v.wheelbaseCm = it }
        }
        if (editing) content.addView(ui.button("Clear the level calibration") {
            v.rollOffset = 0f
            v.pitchOffset = 0f
            save()
            Toast.makeText(this, "Calibration cleared", Toast.LENGTH_SHORT).show()
        })

        // Equipment
        content.addView(ui.heading("Equipment"))
        content.addView(ui.text("Makes, models and serial numbers, handy for spares and repairs.", 16f, Colours.MUTED))
        detailList(v.equipment, "For example: Fridge", "For example: Thetford N4112E, serial 12345")
        if (editing && v.equipment.isEmpty()) {
            content.addView(ui.button("Add the usual items (fridge, heater, water heater...)") {
                save()
                v.equipment.addAll(defaultEquipment(v.type).map { Detail(it, "") })
                saveAndRebuild()
            })
        }

        // Mileage
        mileage(v)

        // Notes
        content.addView(ui.heading("Notes"))
        if (editing) {
            val notes = ui.multiline(v.notes, "Anything else worth remembering")
            content.addView(notes)
            commits.add { v.notes = notes.text.toString() }
        } else {
            content.addView(ui.text(v.notes.ifBlank { "None" }, 18f, if (v.notes.isBlank()) Colours.MUTED else Colours.TEXT))
        }

        if (editing) {
            content.addView(ui.button("Done editing", Colours.ACCENT) { finishEditing() })
            content.addView(ui.button("Delete this vehicle", Colours.RED) { deleteVehicle() })
        } else {
            content.addView(ui.button("Edit", Colours.ACCENT) { editing = true; rebuild() })
        }
        content.addView(ui.button("Add another vehicle", Colours.TILE) { addVehicle() })
    }

    private fun finishEditing() {
        save()
        editing = false
        rebuild()
    }

    /** One read-only line: a small name above the value. */
    private fun show(label: String, value: String) {
        content.addView(ui.text(label, 15f, Colours.MUTED).apply { setPadding(0, ui.dp(10), 0, 0) })
        content.addView(ui.text(value.ifBlank { "Not set" }, 19f, if (value.isBlank()) Colours.MUTED else Colours.TEXT))
    }

    /**
     * Named lines such as "Make: Swift" or "Fridge: Thetford N4112E". Read-only until Edit,
     * then both the name and what it says can be changed, and lines added or removed.
     */
    private fun detailList(list: MutableList<Detail>, example: String, valueHint: String) {
        if (!editing) {
            if (list.isEmpty()) content.addView(ui.text("Nothing yet. Tap Edit to add some.", 17f, Colours.MUTED))
            list.forEach { show(it.label.ifBlank { "(no name)" }, it.value) }
            return
        }
        list.toList().forEach { d ->
            val card = ui.card()
            card.addView(ui.text("Name", 14f, Colours.MUTED))
            val label = ui.field(d.label, example)
            card.addView(label)
            card.addView(ui.text("What it says", 14f, Colours.MUTED).apply { setPadding(0, ui.dp(6), 0, 0) })
            val value = ui.field(d.value, valueHint)
            card.addView(value)
            card.addView(ui.button("Remove this line", Colours.RED) {
                save()
                list.remove(d)
                saveAndRebuild()
            })
            content.addView(card)
            commits.add {
                d.label = label.text.toString().trim()
                d.value = value.text.toString().trim()
            }
        }
        content.addView(ui.button("Add a line", Colours.TILE) {
            save()
            list.add(Detail("", ""))
            saveAndRebuild()
        })
    }

    private fun mileage(v: Vehicle) {
        val unit = Units.distUnit(this)
        content.addView(ui.heading(if (v.type == VehicleType.CARAVAN) "Towing mileage" else "Mileage"))
        content.addView(ui.text(
            "Used to estimate when mileage reminders are due. " +
                if (v.type == VehicleType.CARAVAN) "A caravan has no odometer, so start from 0 or your best guess." else "",
            16f, Colours.MUTED
        ))

        val r = v.latestReading
        content.addView(ui.text(
            if (r == null) "No reading yet" else "Last reading: ${Units.distText(this, r.miles)} on ${Units.date(r.dateMs)}",
            18f
        ).apply { setPadding(0, ui.dp(8), 0, 0) })
        Mileage.estimateNow(v)?.let { est ->
            if (r != null && est != r.miles) content.addView(ui.text("Estimated today: ${Units.distText(this, est)}", 18f, Colours.ACCENT))
        }
        content.addView(ui.button("Add a mileage reading", Colours.TILE) { addReading(v) })

        if (!editing) {
            show("Estimated mileage per year", if (v.annualMiles > 0) Units.distText(this, v.annualMiles) else "")
            show("When you use it", v.usage.label)
            return
        }
        content.addView(ui.label("Estimated mileage per year ($unit)"))
        val annual = ui.wholeNumberField(if (v.annualMiles > 0) Units.distNumber(this, v.annualMiles).replace(",", "") else "")
        content.addView(annual)
        commits.add { v.annualMiles = Units.parseDist(this, annual.text.toString()) ?: 0 }

        content.addView(ui.label("When do you use it?"))
        val usages = Usage.values()
        content.addView(ui.spinner(usages.map { it.label }, usages.indexOf(v.usage)) {
            v.usage = usages[it]
            saveAndRebuild()
        })
    }

    private fun addReading(v: Vehicle) {
        val f: EditText = ui.wholeNumberField(hint = Units.distUnit(this))
        var dateMs = System.currentTimeMillis()
        val dateBtn = ui.button("Date: ${Units.date(dateMs)}") {}
        dateBtn.setOnClickListener {
            ui.pickDate(dateMs) { dateMs = it; dateBtn.text = "Date: ${Units.date(it)}" }
        }
        val box = ui.column(20).apply { addView(f); addView(dateBtn) }
        AlertDialog.Builder(this).setTitle("Mileage reading (${Units.distUnit(this)})").setView(box)
            .setPositiveButton("Add") { _, _ ->
                val miles = Units.parseDist(this, f.text.toString()) ?: return@setPositiveButton
                save()
                v.mileage.add(MileageEntry(dateMs, miles))
                saveAndRebuild()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun addVehicle() {
        val name = ui.field(hint = "Name, for example: Swift Challenger")
        val types = VehicleType.values()
        var type = VehicleType.CARAVAN
        val box = ui.column(20).apply {
            addView(name)
            addView(ui.spinner(types.map { it.label }, 0) { type = types[it] })
        }
        AlertDialog.Builder(this).setTitle("Add a vehicle").setView(box)
            .setPositiveButton("Add") { _, _ ->
                save()
                val v = Vehicle.create(name.text.toString().trim().ifEmpty { "My ${type.label.lowercase()}" }, type)
                garage.vehicles.add(v)
                AppPrefs.setActiveVehicle(this, v.id)
                GarageStore.save(this, garage)
                editing = true
                reload()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteVehicle() {
        if (garage.vehicles.size <= 1) {
            Toast.makeText(this, "You need at least one vehicle", Toast.LENGTH_LONG).show()
            return
        }
        ui.confirm(
            "Delete ${vehicle.name}?",
            "Its details, checklists, service records, photos and reminders will all be deleted. This can't be undone.",
            "Delete"
        ) {
            val v = vehicle
            v.services.flatMap { it.attachments }.forEach { GarageStore.deleteAttachment(this, it) }
            garage.vehicles.remove(v)
            AppPrefs.setActiveVehicle(this, garage.vehicles[0].id)
            GarageStore.save(this, garage)
            editing = false
            reload()
        }
    }

    private fun defaultEquipment(t: VehicleType) = when (t) {
        VehicleType.CAR -> listOf("Tow bar", "Electrics (7 or 13 pin)", "Towing mirrors")
        else -> listOf(
            "Fridge", "Space heater", "Water heater", "Hob and oven", "Water pump",
            "Leisure battery", "Battery charger / power supply", "Toilet",
            if (t == VehicleType.CARAVAN) "Motor mover" else "Awning",
            "Tyres (size)", "Gas bottles (type and size)"
        )
    }
}
