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
        private const val TAKE_FILE = 3
        private const val PICK_FILE = 4
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
        if (editing && KnownModels.of(this, v.type).isNotEmpty()) {
            content.addView(ui.button("Fill in from a known model", Colours.TILE) { pickKnownMake() })
        }
        detailList(v.details, "For example: Chassis number", "For example: Swift")
        dealerSpecial(v)

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
        when (v.type) {
            VehicleType.CARAVAN -> weight("MRO / MIRO (empty weight as built)", { v.mroKg }) { v.mroKg = it }
            VehicleType.CAR -> weight("Kerb weight (in the handbook or on the V5C)", { v.mroKg }) { v.mroKg = it }
            else -> weight("Mass in running order", { v.mroKg }) { v.mroKg = it }
        }
        weight(if (v.type == VehicleType.CAR) "Gross vehicle weight (on the plate)" else "MTPLM (maximum laden weight, on the plate)",
            { v.mtplmKg }) { v.mtplmKg = it }
        if (v.mroKg > 0 && v.mtplmKg > v.mroKg) {
            content.addView(ui.text("Payload for your kit: about ${v.mtplmKg - v.mroKg} kg", 17f, Colours.ACCENT)
                .apply { setPadding(0, ui.dp(8), 0, 0) })
        }
        if (v.type == VehicleType.CAR) {
            weight("Maximum towing weight (braked)", { v.towLimitKg }) { v.towLimitKg = it }
            weight("Maximum noseweight for the tow bar", { v.noseLimitKg }) { v.noseLimitKg = it }
        }
        if (v.type == VehicleType.CARAVAN) towing(v, ::weight)

        tyres(v)

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

        files(v)

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
            content.addView(ui.iconRow(ui.binButton { deleteVehicle() }, end = true))
        } else {
            content.addView(ui.button("Edit", Colours.ACCENT) { editing = true; rebuild() })
        }
        content.addView(ui.button("Add another vehicle", Colours.TILE) { addVehicle() })
    }

    /** Caravan: noseweight and how it matches the tow car (the 85% guide). */
    private fun towing(v: Vehicle, weight: (String, () -> Int, (Int) -> Unit) -> Unit) {
        content.addView(ui.heading("Towing"))
        weight("Actual loaded weight, if weighed (otherwise MTPLM is used)", { v.loadedKg }) { v.loadedKg = it }
        weight("Maximum noseweight (caravan handbook)", { v.noseLimitKg }) { v.noseLimitKg = it }
        weight("Noseweight measured when loaded", { v.noseKg }) { v.noseKg = it }

        val cars = garage.vehicles.filter { it.type == VehicleType.CAR }
        if (cars.isEmpty()) {
            content.addView(ui.text("Add your tow car as another vehicle (type: Tow car) with its weights to check the match.", 16f, Colours.MUTED))
            return
        }
        val car = cars.firstOrNull { it.id == v.towCarId } ?: cars[0]
        if (editing && cars.size > 1) {
            content.addView(ui.label("Tow car"))
            var picked = car
            content.addView(ui.spinner(cars.map { it.name }, cars.indexOf(car)) { picked = cars[it] })
            commits.add { v.towCarId = picked.id }
        }
        if (editing) return

        val lines = Towing.check(v, car)
        content.addView(ui.text("With ${car.name}", 17f, Colours.MUTED).apply { setPadding(0, ui.dp(10), 0, 0) })
        lines.forEach { (ok, text) ->
            content.addView(ui.text(text, 17f, when (ok) { true -> Colours.ACCENT; false -> Colours.RED; null -> Colours.MUTED })
                .apply { setPadding(0, ui.dp(6), 0, 0) })
        }
    }

    /** Reminders for when the tyres (and spare) reach 5 years old, from their DOT codes. */
    private fun tyreReminders(v: Vehicle) {
        val car = v.type == VehicleType.CAR
        val added = mutableListOf<String>()
        fun one(title: String, code: String) {
            val due = Tyres.replaceMs(code) ?: return
            // already past 5 years: due tomorrow so it still shows and notifies
            val d = maxOf(due, Units.startOfDay(System.currentTimeMillis()) + 36 * 3600_000L)
            v.setReminder(title, d, repeatMonths = if (car) 12 else 0,
                notes = "Tyres dated $code (DOT)." + if (car) " Check for cracking every year from 5 years old." else "")
            added.add("$title: ${Units.date(d)}")
        }
        one(if (car) "Check the tyres (5+ years old)" else "Replace the tyres (5 years old)", v.tyreDot)
        one(if (car) "Check the spare tyre (5+ years old)" else "Replace the spare tyre (5 years old)", v.spareDot)
        save()
        Reminders.checkNow(this)
        ui.confirm("Tyre reminders set", added.joinToString("\n") + "\n\nYou can see and change them in Reminders.", "Open Reminders") {
            startActivity(Intent(this, RemindersActivity::class.java))
        }
    }

    /** Tyre size, pressures (psi or bar) and the age from the DOT date code. */
    private fun tyres(v: Vehicle) {
        content.addView(ui.heading("Tyres"))
        val twoPressures = v.type != VehicleType.CARAVAN
        fun shown(psi: Float, bar: Boolean) = if (psi <= 0f) "" else
            if (bar) "%.2f bar (%.1f psi)".format(psi / Tyres.PSI_PER_BAR, psi)
            else "%.1f psi (%.2f bar)".format(psi, psi / Tyres.PSI_PER_BAR)
        if (!editing) {
            show("Tyre size", v.tyreSize)
            if (twoPressures) {
                show("Front pressure", shown(v.tyrePsiFront, v.tyreBar))
                show("Rear pressure", shown(v.tyrePsiRear, v.tyreBar))
            } else show("Pressure", shown(v.tyrePsiFront, v.tyreBar))
            fun age(label: String, code: String) {
                val years = Tyres.ageYears(code)
                show(label, if (years == null) code else "%s (about %.1f years old)".format(code, years))
                if (years != null && years >= Tyres.REPLACE_YEARS) {
                    content.addView(ui.text(Tyres.oldAdvice(v.type), 16f, Colours.RED))
                }
            }
            age("Tyre date code (DOT)", v.tyreDot)
            age("Spare tyre date code (DOT)", v.spareDot)
            if (Tyres.madeMs(v.tyreDot) != null || Tyres.madeMs(v.spareDot) != null) {
                content.addView(ui.button("Add tyre reminders (5 years old)", Colours.TILE) { tyreReminders(v) })
            }
            return
        }
        content.addView(ui.label("Tyre size (on the sidewall, e.g. 185/80 R14 C)"))
        val size = ui.field(v.tyreSize, "For example: 185/80 R14 C", android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        content.addView(size)
        content.addView(ui.label("Pressure unit"))
        var bar = v.tyreBar
        val fields = mutableListOf<Pair<android.widget.EditText, (Float) -> Unit>>()
        content.addView(ui.spinner(listOf("psi", "bar"), if (bar) 1 else 0) { pick ->
            val nowBar = pick == 1
            if (nowBar != bar) {
                // convert what's typed so the numbers stay right
                fields.forEach { (f, _) ->
                    f.text.toString().trim().toFloatOrNull()?.let { n ->
                        f.setText(if (nowBar) "%.2f".format(n / Tyres.PSI_PER_BAR) else "%.1f".format(n * Tyres.PSI_PER_BAR))
                    }
                }
                bar = nowBar
            }
        })
        fun pressure(label: String, psi: Float, set: (Float) -> Unit) {
            content.addView(ui.label(label))
            val text = if (psi <= 0f) "" else if (bar) "%.2f".format(psi / Tyres.PSI_PER_BAR) else "%.1f".format(psi)
            val f = ui.numberField(text, "From the handbook or the plate")
            content.addView(f)
            fields.add(f to set)
        }
        if (twoPressures) {
            pressure("Front pressure", v.tyrePsiFront) { v.tyrePsiFront = it }
            pressure("Rear pressure", v.tyrePsiRear) { v.tyrePsiRear = it }
        } else pressure("Pressure", v.tyrePsiFront) { v.tyrePsiFront = it }
        content.addView(ui.label("Tyre date code (DOT): the last 4 numbers in the oval on the sidewall, week then year, e.g. 2321"))
        val dot = ui.wholeNumberField(v.tyreDot, "For example: 2321")
        content.addView(dot)
        content.addView(ui.label("Spare tyre date code (DOT), if you have a spare"))
        val spare = ui.wholeNumberField(v.spareDot, "For example: 2321")
        content.addView(spare)
        commits.add {
            v.tyreSize = size.text.toString().trim()
            v.tyreBar = bar
            fields.forEach { (f, set) ->
                val n = f.text.toString().trim().replace(',', '.').toFloatOrNull() ?: 0f
                set(if (bar) n * Tyres.PSI_PER_BAR else n)
            }
            v.tyreDot = dot.text.toString().trim()
            v.spareDot = spare.text.toString().trim()
        }
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
            list.forEach { d ->
                show(d.label.ifBlank { "(no name)" }, d.value)
                if (list === vehicle.equipment) {
                    val bits = mutableListOf(if (d.aftermarket) "Aftermarket" else "Factory fitted")
                    if (d.aftermarket) {
                        d.installedMs?.let { bits.add("fitted ${Units.date(it)}") }
                        d.cost.takeIf { it.isNotBlank() }?.let { bits.add(Units.money(it)) }
                    }
                    content.addView(ui.text(bits.joinToString("  ·  "), 15f, Colours.MUTED))
                    val end = d.warrantyEndMs
                    if (d.aftermarket && d.warrantyMonths > 0) {
                        val text = when {
                            end == null -> "Warranty ${d.warrantyMonths} months"
                            end < System.currentTimeMillis() -> "Warranty ended ${Units.date(end)}"
                            else -> "Under warranty until ${Units.date(end)}"
                        }
                        content.addView(ui.text(text, 15f, if (end != null && end >= System.currentTimeMillis()) Colours.ACCENT else Colours.MUTED))
                    }
                }
            }
            return
        }
        list.toList().forEach { d ->
            val card = ui.card()
            // The standard lines (Make, Model, Year...) keep their names; only your own lines can be renamed
            val fixed = list === vehicle.details && d.label in Vehicle.FIXED_DETAILS
            if (fixed) {
                card.addView(ui.text(d.label, 17f, Colours.MUTED, bold = true))
                val value = when (d.label) {
                    "Year" -> ui.wholeNumberField(d.value, "For example: 2021")
                    "Registration", "CRiS number", Vehicle.VIN -> ui.field(d.value, "", android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply {
                        filters = arrayOf(android.text.InputFilter.AllCaps())
                    }
                    else -> ui.field(d.value, valueHint)
                }
                card.addView(value)
                commits.add { d.value = value.text.toString().trim() }
            } else {
                card.addView(ui.text("Name", 14f, Colours.MUTED))
                val label = ui.field(d.label, example)
                card.addView(label)
                card.addView(ui.text("What it says", 14f, Colours.MUTED).apply { setPadding(0, ui.dp(6), 0, 0) })
                val value = ui.field(d.value, valueHint)
                card.addView(value)
                if (list === vehicle.equipment) equipmentExtras(card, d)
                card.addView(ui.iconRow(ui.binButton {
                    save()
                    list.remove(d)
                    saveAndRebuild()
                }, end = true))
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

    /** Dealer special: the standard model it's built on and the dealer it was made for. */
    private fun dealerSpecial(v: Vehicle) {
        if (!editing) {
            if (v.dealerSpecial) {
                show("Dealer special", listOfNotNull(
                    v.baseModel.takeIf { it.isNotBlank() }?.let { "Based on $it" },
                    v.dealer.takeIf { it.isNotBlank() }?.let { "made for $it" }
                ).joinToString(", ").ifBlank { "Yes" })
            }
            return
        }
        val box = android.widget.CheckBox(this).apply {
            text = "Dealer special (a version made for one dealer)"
            textSize = 17f
            setTextColor(Colours.TEXT)
            isChecked = v.dealerSpecial
            setPadding(0, ui.dp(10), 0, ui.dp(6))
        }
        content.addView(box)
        val extras = ui.column(0)
        extras.addView(ui.label("Base model it's built on"))
        val base = ui.field(v.baseModel, "For example: Swift Challenger 580")
        extras.addView(base)
        extras.addView(ui.label("Original dealer"))
        val dealer = ui.field(v.dealer, "For example: Lowdhams")
        extras.addView(dealer)
        content.addView(extras)
        extras.visibility = if (v.dealerSpecial) android.view.View.VISIBLE else android.view.View.GONE
        box.setOnCheckedChangeListener { _, on -> extras.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE }
        commits.add {
            v.dealerSpecial = box.isChecked
            v.baseModel = base.text.toString().trim()
            v.dealer = dealer.text.toString().trim()
        }
    }

    /** Factory fitted or aftermarket; aftermarket items get date fitted, cost and warranty. */
    private fun equipmentExtras(card: LinearLayout, d: Detail) {
        val extras = ui.column(0)
        val after = android.widget.CheckBox(this).apply {
            text = "Aftermarket (added after it was built)"
            textSize = 17f
            setTextColor(Colours.TEXT)
            isChecked = d.aftermarket
            setPadding(0, ui.dp(8), 0, ui.dp(8))
        }
        card.addView(after)
        var installed = d.installedMs
        val dateBtn = ui.button("") {}
        fun showDate() { dateBtn.text = installed?.let { "Fitted: ${Units.date(it)}" } ?: "Date fitted (tap to set)" }
        showDate()
        dateBtn.setOnClickListener { ui.pickDate(installed) { installed = it; showDate() } }
        extras.addView(dateBtn)
        extras.addView(ui.text("Cost (£)", 14f, Colours.MUTED))
        val cost = ui.numberField(d.cost, "0.00")
        extras.addView(cost)
        extras.addView(ui.text("Warranty (months)", 14f, Colours.MUTED))
        val warranty = ui.wholeNumberField(if (d.warrantyMonths > 0) d.warrantyMonths.toString() else "", "For example: 24")
        extras.addView(warranty)
        card.addView(extras)
        extras.visibility = if (d.aftermarket) android.view.View.VISIBLE else android.view.View.GONE
        after.setOnCheckedChangeListener { _, on -> extras.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE }
        commits.add {
            d.aftermarket = after.isChecked
            d.installedMs = installed
            d.cost = cost.text.toString().trim()
            d.warrantyMonths = warranty.text.toString().trim().toIntOrNull() ?: 0
        }
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
        val makes = KnownModels.makes(this, vehicle.type)
        AlertDialog.Builder(this).setTitle("Which make?")
            .setItems(makes.toTypedArray()) { _, i -> pickKnownYear(makes[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickKnownYear(make: String) {
        val years = KnownModels.years(this, vehicle.type, make)
        AlertDialog.Builder(this).setTitle("$make: which model year?")
            .setItems(years.map { it.toString() }.toTypedArray()) { _, i -> pickKnownModel(make, years[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickKnownModel(make: String, year: Int) {
        val models = KnownModels.models(this, vehicle.type, make, year)
        AlertDialog.Builder(this).setTitle("$make $year: which model?")
            .setItems(models.map { it.name }.toTypedArray()) { _, i -> confirmKnown(models[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmKnown(m: KnownModel) {
        fun size(label: String, v: Float) = if (v > 0) "$label ${Store.fixed(v)} m" else "$label not published"
        fun kg(label: String, v: Int) = if (v > 0) "$label $v kg" else "$label not published"
        val missing = m.lengthM <= 0 || m.widthM <= 0 || m.heightM <= 0 || m.mroKg <= 0 || m.mtplmKg <= 0
        val msg = "${size("Length", m.lengthM)}, ${size("width", m.widthM)}, ${size("height", m.heightM)}\n" +
            "${kg("MRO", m.mroKg)}, ${kg("MTPLM", m.mtplmKg)}" +
            (if (m.mtplmUpgradeKg > m.mtplmKg && m.mtplmKg > 0) " (upgradable to ${m.mtplmUpgradeKg} kg)" else "") + "\n" +
            (if (m.type == VehicleType.CARAVAN) (if (m.axles > 0) "${m.axles} axle${if (m.axles == 1) "" else "s"}\n" else "") + "\n"
            else (if (m.wheelbaseM > 0) "Wheelbase ${Store.fixed(m.wheelbaseM)} m\n" else "") +
                (if (m.baseVehicle.isNotBlank()) "Base vehicle: ${m.baseVehicle}\n" else "") + "\n") +
            "From: ${m.source}. These are the maker's approximate figures for a standard model; options, " +
            "dealer specials and weight upgrades change them, so check your own plate and handbook.\n\n" +
            "This fills in Make, Model, Year, the sizes and the weights, replacing what's there." +
            (if (missing) " Anything the maker didn't publish is left as it is." else "")
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
                if (m.lengthM > 0) v.lengthCm = m.lengthM * 100f
                if (m.widthM > 0) v.widthCm = m.widthM * 100f
                if (m.heightM > 0) v.heightCm = m.heightM * 100f
                listOf("length", "width", "height").forEach { v.sizeUnits[it] = SizeUnit.M.name }
                if (m.wheelbaseM > 0) { v.wheelbaseCm = m.wheelbaseM * 100f; v.sizeUnits["wheelbase"] = SizeUnit.M.name }
                if (m.mroKg > 0) v.mroKg = m.mroKg
                if (m.mtplmKg > 0) v.mtplmKg = m.mtplmKg
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
        content.addView(ui.iconRow(*listOfNotNull(
            ui.iconButton(Icon.CAMERA) { takePhoto() },
            ui.iconButton(Icon.PHOTO) { pickPhoto() },
            name?.let { n -> ui.binButton {
                save()
                GarageStore.deleteAttachment(this, n)
                vehicle.photo = null
                saveAndRebuild()
            } }
        ).toTypedArray()))
    }

    /** Documents and pictures such as sales details. Add any time; remove while editing. */
    private fun files(v: Vehicle) {
        content.addView(ui.heading("Documents and pictures"))
        if (v.files.isEmpty()) content.addView(ui.text("Sales details, V5C, warranties, receipts... Add a photo or file.", 16f, Colours.MUTED))
        v.files.toList().forEach { name ->
            val card = ui.card()
            if (AttachmentProvider.mime(name).startsWith("image/")) {
                Attach.thumbnail(java.io.File(GarageStore.attachmentsDir(this), name))?.let { bmp ->
                    card.addView(ImageView(this).apply {
                        setImageBitmap(bmp)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(180))
                    })
                }
            }
            val title = ui.text(AttachmentProvider.displayName(name), 16f)
            if (editing) {
                title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                card.addView(ui.row(title, ui.binButton {
                    ui.confirm("Remove this file?", AttachmentProvider.displayName(name), "Remove") {
                        save()
                        v.files.remove(name)
                        GarageStore.deleteAttachment(this, name)
                        saveAndRebuild()
                    }
                }))
            } else card.addView(title)
            card.isClickable = true
            card.setOnClickListener { Attach.view(this, name) }
            content.addView(card)
        }
        content.addView(ui.iconRow(
            ui.iconButton(Icon.CAMERA) { takeFile() },
            ui.iconButton(Icon.ATTACH) { pickFile() }
        ))
    }

    private fun takeFile() {
        save()
        val name = Attach.newName("${vehicle.name} document.jpg")
        java.io.File(GarageStore.attachmentsDir(this), name).createNewFile()
        try {
            pendingPhoto = name
            startActivityForResult(Attach.cameraIntent(name), TAKE_FILE)
        } catch (_: ActivityNotFoundException) {
            pendingPhoto = null
            GarageStore.deleteAttachment(this, name)
            Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickFile() {
        save()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            startActivityForResult(i, PICK_FILE)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No file picker found", Toast.LENGTH_LONG).show()
        }
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
            TAKE_FILE -> {
                val name = pendingPhoto ?: return
                pendingPhoto = null
                val f = java.io.File(GarageStore.attachmentsDir(this), name)
                if (resultCode == RESULT_OK && f.length() > 0) { v.files.add(name); GarageStore.save(this, g) } else f.delete()
                return
            }
            PICK_FILE -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) Attach.copyIn(this, uri)?.let { v.files.add(it); GarageStore.save(this, g) }
                return
            }
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
            "Its details, checklists, services and repairs, photos, documents and reminders will all be deleted. This can't be undone.",
            "Delete"
        ) {
            val v = vehicle
            v.services.flatMap { it.attachments }.forEach { GarageStore.deleteAttachment(this, it) }
            v.photo?.let { GarageStore.deleteAttachment(this, it) }
            v.files.forEach { GarageStore.deleteAttachment(this, it) }
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
