package com.caravanlevel

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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

    /** File name of a photo the camera is taking, kept if Android restarts the page. */
    private var pendingPhoto: String? = null

    companion object {
        private const val TAKE_PHOTO = 1
        private const val PICK_PHOTO = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPhoto = savedInstanceState?.getString("photo")
        editing = savedInstanceState?.getBoolean("editing") ?: false
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("photo", pendingPhoto)
        out.putBoolean("editing", editing)
    }

    override fun build() {
        commits.clear()
        val v = vehicle
        photo(v)

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
            content.addView(ui.spinner(types.map { it.label }, types.indexOf(v.type), types.map { it.icon }) { changeType(types[it]) })
        } else {
            show("Name", v.name)
            show("Type", v.type.label)
        }

        // Details
        content.addView(ui.heading("Details"))
        if (editing && v.type == VehicleType.CARAVAN && KnownModels.all(this).isNotEmpty()) {
            content.addView(ui.button("Fill in from a known model", Colours.TILE) { pickKnownMake() })
        }
        detailList(v.details, "For example: Chassis number", "For example: Swift")

        // Sizes: each one in the unit it was typed in
        content.addView(ui.heading("Sizes"))
        if (editing) content.addView(ui.text("Pick the unit next to each size: mm, cm, m, in, ft or yd.", 16f, Colours.MUTED))
        fun size(key: String, label: String, get: () -> Float, set: (Float) -> Unit) {
            val u = Units.unitOf(this, v, key)
            if (!editing) {
                show(label, Units.sizeNumber(get(), u).let { if (it.isEmpty()) "" else "$it ${u.label}" })
                return
            }
            content.addView(ui.label(label))
            val f = ui.numberField(Units.sizeNumber(get(), u)).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val units = SizeUnit.values()
            var chosen = u
            val spin = ui.spinner(units.map { it.label }, units.indexOf(u)) { chosen = units[it] }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            content.addView(ui.row(f, spin))
            commits.add {
                set(Units.parseSizeIn(f.text.toString(), chosen))
                v.sizeUnits[key] = chosen.name
            }
        }
        size("length", "Overall length", { v.lengthCm }) { v.lengthCm = it }
        if (v.type == VehicleType.CARAVAN) size("bodyLength", "Body length (without the A-frame)", { v.bodyLengthCm }) { v.bodyLengthCm = it }
        size("width", "Width (body, without mirrors)", { v.widthCm }) { v.widthCm = it }
        if (v.type.motorised) size("mirrorWidth", "Width including wing mirrors", { v.mirrorWidthCm }) { v.mirrorWidthCm = it }
        size("height", "Height", { v.heightCm }) { v.heightCm = it }

        content.addView(ui.heading("Levelling sizes"))
        content.addView(ui.text("The level uses these to tell you how much to raise or lower.", 16f, Colours.MUTED))
        size("track", "Track width: wheel centre to wheel centre", { v.trackCm }) { v.trackCm = it }
        if (v.type == VehicleType.CARAVAN) {
            size("hitch", "Hitch to axle: coupling to axle centre (midpoint for twin axle)", { v.hitchCm }) { v.hitchCm = it }
        } else {
            size("wheelbase", "Wheelbase: front axle centre to rear axle centre", { v.wheelbaseCm }) { v.wheelbaseCm = it }
        }
        if (editing) content.addView(ui.button("Clear the level calibration") {
            v.rollOffset = 0f
            v.pitchOffset = 0f
            save()
            Toast.makeText(this, "Calibration cleared", Toast.LENGTH_SHORT).show()
        })

        // Weights
        content.addView(ui.heading("Weights (kg)"))
        fun weight(label: String, get: () -> Int, set: (Int) -> Unit) {
            if (!editing) {
                show(label, if (get() > 0) "${get()} kg" else "")
                return
            }
            content.addView(ui.label(label))
            val f = ui.wholeNumberField(if (get() > 0) get().toString() else "")
            content.addView(f)
            commits.add { set(f.text.toString().trim().toIntOrNull() ?: 0) }
        }
        weight(if (v.type == VehicleType.CARAVAN) "MRO / MIRO (empty weight as built)" else "Mass in running order",
            { v.mroKg }) { v.mroKg = it }
        weight("MTPLM (maximum laden weight, on the plate)", { v.mtplmKg }) { v.mtplmKg = it }
        if (v.mroKg > 0 && v.mtplmKg > v.mroKg) {
            content.addView(ui.text("Payload for your kit: about ${v.mtplmKg - v.mroKg} kg", 17f, Colours.ACCENT)
                .apply { setPadding(0, ui.dp(8), 0, 0) })
        }

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
        if (v.type.motorised) mileage(v) // caravans have no odometer

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
            // The standard lines (Make, Model, Year...) keep their names; only your own lines can be renamed
            val fixed = list === vehicle.details && d.label in Vehicle.FIXED_DETAILS
            if (fixed) {
                card.addView(ui.text(d.label, 17f, Colours.MUTED, bold = true))
                val value = ui.field(d.value, valueHint)
                card.addView(value)
                commits.add { d.value = value.text.toString().trim() }
            } else {
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
                commits.add {
                    d.label = label.text.toString().trim()
                    d.value = value.text.toString().trim()
                }
            }
            content.addView(card)
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
            addView(ui.spinner(types.map { it.label }, 0, types.map { it.icon }) { type = types[it] })
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

    /** Change caravan <-> van: the standard lines change, and offer the matching checklists. */
    private fun changeType(t: VehicleType) {
        save()
        val v = vehicle
        val old = v.type
        v.type = t
        v.applyStandardDetails()
        save()
        reload() // the name in the bar and the size names change
        val listsDiffer = DefaultLists.forType(old).map { it.name } != DefaultLists.forType(t).map { it.name } ||
            old.motorised != t.motorised
        if (listsDiffer && old != t) {
            ui.confirm(
                "Use the ${t.label.lowercase()} checklists?",
                "Replace this vehicle's checklists with the starting lists for a ${t.label.lowercase()}? " +
                    "Any changes you made to the lists will be lost. Choose Keep to leave them as they are.",
                "Replace"
            ) {
                vehicle.checklists.clear()
                vehicle.checklists.addAll(DefaultLists.forType(t))
                GarageStore.save(this, garage)
                Toast.makeText(this, "Checklists replaced", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- Known models ----------

    private fun pickKnownMake() {
        val makes = KnownModels.makes(this)
        AlertDialog.Builder(this).setTitle("Which make?")
            .setItems(makes.toTypedArray()) { _, i -> pickKnownYear(makes[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickKnownYear(make: String) {
        val years = KnownModels.years(this, make)
        AlertDialog.Builder(this).setTitle("$make: which model year?")
            .setItems(years.map { it.toString() }.toTypedArray()) { _, i -> pickKnownModel(make, years[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickKnownModel(make: String, year: Int) {
        val models = KnownModels.models(this, make, year)
        AlertDialog.Builder(this).setTitle("$make $year: which model?")
            .setItems(models.map { it.name }.toTypedArray()) { _, i -> confirmKnown(models[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmKnown(m: KnownModel) {
        val msg = "Length ${Store.fixed(m.lengthM)} m, width ${Store.fixed(m.widthM)} m, height ${Store.fixed(m.heightM)} m\n" +
            "MRO ${m.mroKg} kg, MTPLM ${m.mtplmKg} kg" +
            (if (m.mtplmUpgradeKg > m.mtplmKg) " (upgradable to ${m.mtplmUpgradeKg} kg)" else "") + "\n" +
            "${m.axles} axle${if (m.axles == 1) "" else "s"}\n\n" +
            "From: ${m.source}. These are the maker's approximate figures for a standard model; options, " +
            "dealer specials and weight upgrades change them, so check your own plate and handbook.\n\n" +
            "This fills in Make, Model, Year, the sizes and the weights, replacing what's there."
        AlertDialog.Builder(this).setTitle("${m.make} ${m.name} (${m.year})").setMessage(msg)
            .setPositiveButton("Fill in") { _, _ ->
                save()
                val v = vehicle
                fun line(name: String, value: String) {
                    val d = v.details.firstOrNull { it.label == name }
                    if (d != null) d.value = value else v.details.add(Detail(name, value))
                }
                line("Make", m.make)
                line("Model", m.name)
                line("Year", m.year.toString())
                v.lengthCm = m.lengthM * 100f
                v.widthCm = m.widthM * 100f
                v.heightCm = m.heightM * 100f
                listOf("length", "width", "height").forEach { v.sizeUnits[it] = SizeUnit.M.name }
                v.mroKg = m.mroKg
                v.mtplmKg = m.mtplmKg
                val note = "Sizes and weights filled in from ${m.source} (approximate)."
                if (!v.notes.contains(note)) v.notes = (v.notes.trim() + "\n" + note).trim()
                commits.clear() // don't let the old boxes overwrite what was just filled in
                GarageStore.save(this, garage)
                rebuild()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ---------- Photo of the vehicle ----------

    private fun photo(v: Vehicle) {
        val name = v.photo
        if (name != null) {
            val f = java.io.File(GarageStore.attachmentsDir(this), name)
            Attach.thumbnail(f, 900)?.let { bmp ->
                content.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(220))
                        .apply { setMargins(0, ui.dp(8), 0, ui.dp(4)) }
                    setOnClickListener { Attach.view(this@VehicleActivity, name) }
                })
            }
        }
        if (!editing) return
        content.addView(ui.row(
            ui.rowButton(if (name == null) "Take a photo" else "New photo", Colours.TILE) { takePhoto() },
            ui.rowButton("Choose a photo", Colours.TILE) { pickPhoto() }
        ))
        if (name != null) content.addView(ui.button("Remove the photo", Colours.RED) {
            save()
            GarageStore.deleteAttachment(this, name)
            vehicle.photo = null
            saveAndRebuild()
        })
    }

    private fun takePhoto() {
        save()
        val name = Attach.newName("${vehicle.name}.jpg")
        java.io.File(GarageStore.attachmentsDir(this), name).createNewFile()
        try {
            pendingPhoto = name
            startActivityForResult(Attach.cameraIntent(name), TAKE_PHOTO)
        } catch (_: ActivityNotFoundException) {
            pendingPhoto = null
            GarageStore.deleteAttachment(this, name)
            Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickPhoto() {
        save()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try {
            startActivityForResult(i, PICK_PHOTO)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No photo picker found", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // The page reloads from storage straight after this, so work on a fresh copy
        val g = GarageStore.load(this)
        val v = GarageStore.active(this, g)
        var newPhoto: String? = null
        when (requestCode) {
            TAKE_PHOTO -> {
                val name = pendingPhoto ?: return
                pendingPhoto = null
                val f = java.io.File(GarageStore.attachmentsDir(this), name)
                if (resultCode == RESULT_OK && f.length() > 0) newPhoto = name else f.delete()
            }
            PICK_PHOTO -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) newPhoto = Attach.copyIn(this, uri)
            }
        }
        if (newPhoto != null) {
            v.photo?.let { GarageStore.deleteAttachment(this, it) }
            v.photo = newPhoto
            GarageStore.save(this, g)
        }
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
            v.photo?.let { GarageStore.deleteAttachment(this, it) }
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
