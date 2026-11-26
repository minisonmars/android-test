package com.caravanlevel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/*
 * Everything about the user's vehicles, kept in one JSON file (garage.json) in the
 * app's private storage, so it can be backed up and restored as a single file.
 * Attachments (photos, receipts) are copied into the app's "attachments" folder and
 * referenced by file name.
 */

enum class VehicleType(val label: String) {
    CARAVAN("Caravan"),
    CAMPERVAN("Campervan"),
    MOTORHOME("Motorhome"),
    CAR("Tow car");

    /** Has its own engine and odometer (and MOT, tax and so on). */
    val motorised get() = this != CARAVAN

    val icon get() = when (this) {
        CARAVAN -> Icon.CARAVAN
        CAMPERVAN -> Icon.CAMPERVAN
        MOTORHOME -> Icon.MOTORHOME
        CAR -> Icon.CAR
    }

    companion object {
        fun of(s: String?) = values().firstOrNull { it.name == s } ?: CARAVAN
    }
}

/** How the vehicle is used through the year, to spread the annual mileage over the months. */
enum class Usage(val label: String) {
    ALL_YEAR("All year round"),
    MOSTLY_SUMMER("Mostly spring and summer"),
    SUMMER_ONLY("Only spring and summer (April to September)");

    companion object {
        fun of(s: String?) = values().firstOrNull { it.name == s } ?: ALL_YEAR
    }
}

fun newId(): String = UUID.randomUUID().toString()

/** A named detail, e.g. "Fridge" = "Thetford N4112E, serial 1234". */
class Detail(var label: String, var value: String) {
    fun json() = JSONObject().put("label", label).put("value", value)

    companion object {
        fun from(o: JSONObject) = Detail(o.optString("label"), o.optString("value"))
    }
}

class CheckItem(var text: String, var ticked: Boolean = false, val id: String = newId()) {
    /** Set on items added for trip countries (the TripRules key), so they can be updated. */
    var rule: String? = null

    fun json() = JSONObject().put("id", id).put("text", text).put("ticked", ticked)
        .put("rule", rule ?: JSONObject.NULL)

    companion object {
        fun from(o: JSONObject) =
            CheckItem(o.optString("text"), o.optBoolean("ticked"), o.optString("id", newId())).apply {
                rule = if (o.isNull("rule")) null else o.optString("rule").ifBlank { null }
            }
    }
}

class Checklist(
    var name: String,
    val items: MutableList<CheckItem>,
    val id: String = newId(),
    var completedAt: Long = 0L   // when the last item was ticked, 0 if not finished
) {
    /** "packing" for the packing list (it can add kit for trip countries), else "". */
    var kind = ""
    /** Countries chosen for the trip (packing list only). */
    val countries = mutableListOf<String>()

    fun json() = JSONObject().put("id", id).put("name", name).put("completedAt", completedAt)
        .put("items", JSONArray().apply { items.forEach { put(it.json()) } })
        .put("kind", kind).put("countries", JSONArray(countries))

    val done get() = items.count { it.ticked }
    val finished get() = items.isNotEmpty() && done == items.size

    fun resetTicks() {
        items.forEach { it.ticked = false }
        completedAt = 0L
    }

    companion object {
        fun from(o: JSONObject) = Checklist(
            o.optString("name"),
            o.optJSONArray("items").objects().map { CheckItem.from(it) }.toMutableList(),
            o.optString("id", newId()),
            o.optLong("completedAt", 0L)
        ).apply {
            kind = o.optString("kind", "")
            countries.addAll(o.optJSONArray("countries").strings())
        }
    }
}

class ServiceRecord(
    var dateMs: Long,
    var title: String,          // e.g. "Annual habitation service"
    var who: String = "",
    var cost: String = "",      // kept as typed, e.g. "245.00"
    var mileage: Int? = null,
    var notes: String = "",
    val attachments: MutableList<String> = mutableListOf(),
    val id: String = newId()
) {
    fun json() = JSONObject().put("id", id).put("date", dateMs).put("title", title)
        .put("who", who).put("cost", cost).put("mileage", mileage ?: JSONObject.NULL)
        .put("notes", notes)
        .put("attachments", JSONArray().apply { attachments.forEach { put(it) } })

    companion object {
        fun from(o: JSONObject) = ServiceRecord(
            o.optLong("date"), o.optString("title"), o.optString("who"), o.optString("cost"),
            o.optIntOrNull("mileage"), o.optString("notes"),
            o.optJSONArray("attachments").strings().toMutableList(),
            o.optString("id", newId())
        )
    }
}

/** Due by date, by mileage, or both (whichever comes first). Repeats if set. */
class Reminder(
    var title: String,
    var dueMs: Long? = null,
    var dueMiles: Int? = null,
    var repeatMonths: Int = 0,
    var repeatMiles: Int = 0,
    var notes: String = "",
    val id: String = newId(),
    var notifiedSoon: Boolean = false,   // "due in 2 weeks" notification already shown
    var notifiedDue: Boolean = false     // "due today / overdue" notification already shown
) {
    fun json() = JSONObject().put("id", id).put("title", title)
        .put("due", dueMs ?: JSONObject.NULL).put("dueMiles", dueMiles ?: JSONObject.NULL)
        .put("repeatMonths", repeatMonths).put("repeatMiles", repeatMiles).put("notes", notes)
        .put("notifiedSoon", notifiedSoon).put("notifiedDue", notifiedDue)

    companion object {
        fun from(o: JSONObject) = Reminder(
            o.optString("title"), o.optLongOrNull("due"), o.optIntOrNull("dueMiles"),
            o.optInt("repeatMonths"), o.optInt("repeatMiles"), o.optString("notes"),
            o.optString("id", newId()), o.optBoolean("notifiedSoon"), o.optBoolean("notifiedDue")
        )
    }
}

/** An odometer reading, typed in or added from a tracked trip. */
class MileageEntry(val dateMs: Long, val miles: Int, val source: String = "manual") {
    fun json() = JSONObject().put("date", dateMs).put("miles", miles).put("source", source)

    companion object {
        fun from(o: JSONObject) =
            MileageEntry(o.optLong("date"), o.optInt("miles"), o.optString("source", "manual"))
    }
}

class Vehicle(
    var name: String,
    var type: VehicleType,
    val details: MutableList<Detail> = mutableListOf(),      // make, model, year, CRiS...
    val equipment: MutableList<Detail> = mutableListOf(),    // fridge, heater, mover...
    val checklists: MutableList<Checklist> = mutableListOf(),
    val services: MutableList<ServiceRecord> = mutableListOf(),
    val reminders: MutableList<Reminder> = mutableListOf(),
    val mileage: MutableList<MileageEntry> = mutableListOf(),
    var notes: String = "",
    val id: String = newId()
) {
    // Sizes, always in cm; 0 = not set
    var lengthCm = 0f       // overall length
    var bodyLengthCm = 0f   // caravan: body without the A-frame
    var widthCm = 0f
    var mirrorWidthCm = 0f  // vans: across the wing mirrors
    var heightCm = 0f
    var trackCm = 0f        // wheel centre to wheel centre (levelling side to side)
    var hitchCm = 0f        // caravan: hitch coupling to axle centre (levelling front to back)
    var wheelbaseCm = 0f    // motorised: front axle to rear axle (levelling front to back)

    /** The unit each size was typed in (key -> SizeUnit name), so it's shown the same way. */
    val sizeUnits = mutableMapOf<String, String>()

    // Weights in kg; 0 = not set
    var mroKg = 0      // mass in running order (MIRO / MRO), empty weight as built
    var mtplmKg = 0    // maximum technically permissible laden mass (the plated maximum)

    /** The packing list has been added once (so deleting it is respected). */
    var packingAdded = false

    /** Photo of the vehicle: a file name in the attachments folder. */
    var photo: String? = null

    // Level calibration, degrees
    var rollOffset = 0f
    var pitchOffset = 0f

    // Mileage estimate (miles)
    var annualMiles = 0
    var usage = Usage.ALL_YEAR

    /** Latest odometer reading, or null. */
    val latestReading get() = mileage.maxByOrNull { it.dateMs }

    /** The size used for front-to-back levelling. */
    val lengthForLevel get() = if (type == VehicleType.CARAVAN) hitchCm else wheelbaseCm

    fun json(): JSONObject {
        fun list(l: List<Detail>) = JSONArray().apply { l.forEach { put(it.json()) } }
        return JSONObject().put("id", id).put("name", name).put("type", type.name)
            .put("details", list(details)).put("equipment", list(equipment))
            .put("checklists", JSONArray().apply { checklists.forEach { put(it.json()) } })
            .put("services", JSONArray().apply { services.forEach { put(it.json()) } })
            .put("reminders", JSONArray().apply { reminders.forEach { put(it.json()) } })
            .put("mileage", JSONArray().apply { mileage.forEach { put(it.json()) } })
            .put("notes", notes)
            .put("length", lengthCm.toDouble()).put("bodyLength", bodyLengthCm.toDouble())
            .put("width", widthCm.toDouble()).put("mirrorWidth", mirrorWidthCm.toDouble()).put("height", heightCm.toDouble())
            .put("track", trackCm.toDouble()).put("hitch", hitchCm.toDouble())
            .put("wheelbase", wheelbaseCm.toDouble())
            .put("roll", rollOffset.toDouble()).put("pitch", pitchOffset.toDouble())
            .put("annualMiles", annualMiles).put("usage", usage.name)
            .put("sizeUnits", JSONObject(sizeUnits.toMap()))
            .put("photo", photo ?: JSONObject.NULL)
            .put("packingAdded", packingAdded)
            .put("mro", mroKg).put("mtplm", mtplmKg)
    }

    /**
     * Make sure the standard lines for this type are there (e.g. Registration for a van,
     * CRiS number for a caravan), and drop empty standard lines that don't apply.
     */
    fun applyStandardDetails() {
        val wanted = standardDetails(type)
        details.removeAll { it.label in FIXED_DETAILS && it.label !in wanted && it.value.isBlank() }
        var insertAt = 0
        for (name in wanted) {
            val at = details.indexOfFirst { it.label == name }
            if (at >= 0) insertAt = at + 1
            else details.add(insertAt++, Detail(name, ""))
        }
    }

    companion object {
        fun from(o: JSONObject): Vehicle {
            fun details(k: String) =
                o.optJSONArray(k).objects().map { Detail.from(it) }.toMutableList()
            fun f(k: String) = o.optDouble(k, 0.0).toFloat()
            return Vehicle(
                o.optString("name", "My caravan"), VehicleType.of(o.optString("type")),
                details("details"), details("equipment"),
                o.optJSONArray("checklists").objects().map { Checklist.from(it) }.toMutableList(),
                o.optJSONArray("services").objects().map { ServiceRecord.from(it) }.toMutableList(),
                o.optJSONArray("reminders").objects().map { Reminder.from(it) }.toMutableList(),
                o.optJSONArray("mileage").objects().map { MileageEntry.from(it) }.toMutableList(),
                o.optString("notes"), o.optString("id", newId())
            ).apply {
                lengthCm = f("length"); bodyLengthCm = f("bodyLength")
                widthCm = f("width"); mirrorWidthCm = f("mirrorWidth"); heightCm = f("height")
                trackCm = f("track"); hitchCm = f("hitch"); wheelbaseCm = f("wheelbase")
                rollOffset = f("roll"); pitchOffset = f("pitch")
                annualMiles = o.optInt("annualMiles", 0)
                usage = Usage.of(o.optString("usage"))
                o.optJSONObject("sizeUnits")?.let { u -> u.keys().forEach { k -> sizeUnits[k] = u.optString(k) } }
                mroKg = o.optInt("mro", 0); mtplmKg = o.optInt("mtplm", 0)
                photo = if (o.isNull("photo")) null else o.optString("photo").ifBlank { null }
                packingAdded = o.optBoolean("packingAdded", false)
            }
        }

        const val VIN = "VIN (chassis number)"

        /** Standard detail lines: their names can't be changed and they can't be removed. */
        val FIXED_DETAILS = setOf("Make", "Model", "Year", "CRiS number", "Registration", VIN)

        /** Caravans have a CRiS number but no registration; vans and cars are registered. */
        fun standardDetails(t: VehicleType) =
            if (t == VehicleType.CARAVAN) listOf("Make", "Model", "Year", "CRiS number", VIN)
            else listOf("Make", "Model", "Year", "Registration", VIN)

        /** A new vehicle with the default checklists and detail lines for its type. */
        fun create(name: String, type: VehicleType) = Vehicle(name, type).apply {
            checklists.addAll(DefaultLists.forType(type))
            packingAdded = true
            applyStandardDetails()
        }
    }
}

class Garage(val vehicles: MutableList<Vehicle>) {
    fun json() = JSONObject().put("version", 1)
        .put("vehicles", JSONArray().apply { vehicles.forEach { put(it.json()) } })

    fun find(id: String?) = vehicles.firstOrNull { it.id == id }

    companion object {
        fun from(o: JSONObject) = Garage(
            o.optJSONArray("vehicles").objects().map { Vehicle.from(it) }.toMutableList()
        )
    }
}

object GarageStore {
    private const val FILE = "garage.json"

    fun attachmentsDir(ctx: Context) = File(ctx.filesDir, "attachments").apply { mkdirs() }

    /** Always returns at least one vehicle. */
    fun load(ctx: Context): Garage {
        val f = File(ctx.filesDir, FILE)
        var g: Garage? = null
        if (f.exists()) {
            try {
                g = Garage.from(JSONObject(f.readText()))
            } catch (_: Exception) {
                // keep the unreadable file so nothing is lost for good
                f.copyTo(File(ctx.filesDir, "$FILE.bad"), overwrite = true)
            }
        }
        if (g == null) {
            // First run of this version: turn the old level profiles into caravans
            g = Garage(Store.load(ctx).profiles.map { p ->
                Vehicle.create(p.name, VehicleType.CARAVAN).apply {
                    trackCm = p.trackCm; hitchCm = p.hitchCm
                    rollOffset = p.rollOffset; pitchOffset = p.pitchOffset
                }
            }.toMutableList())
            save(ctx, g)
        }
        var changed = fixTypedIntoNames(g)
        for (v in g.vehicles) {
            val before = v.details.map { it.label }
            v.applyStandardDetails()
            if (v.details.map { it.label } != before) changed = true
            if (!v.packingAdded) {
                // Packing lists came in v0.3.35: give every vehicle one, once
                if (v.checklists.none { it.kind == "packing" }) v.checklists.add(DefaultLists.packing(v.type))
                v.packingAdded = true
                changed = true
            }
        }
        if (changed) save(ctx, g)
        if (g.vehicles.isEmpty()) {
            g.vehicles.add(Vehicle.create("My caravan", VehicleType.CARAVAN))
            save(ctx, g)
        }
        return g
    }

    /**
     * Version 0.3.14 let you type over the line names, so "Make" could become "Swift"
     * with nothing in the value. If the first three starting lines look like that, put
     * the names back (Make, Model, Year) and move what was typed into the values.
     */
    private fun fixTypedIntoNames(g: Garage): Boolean {
        var changed = false
        val names = listOf("Make", "Model", "Year")
        for (v in g.vehicles) {
            if (v.details.size < 3) continue
            val first = v.details.take(3)
            if (first.all { it.value.isBlank() } && first.none { it.label in names } &&
                first.all { it.label.isNotBlank() }
            ) {
                first.forEachIndexed { i, d ->
                    // "Makeswift" -> "swift": drop the old name if it was typed after it
                    val typed = d.label.removePrefix(names[i]).removePrefix(names[i].lowercase()).trim()
                    d.value = typed.ifEmpty { d.label }
                    d.label = names[i]
                }
                changed = true
            }
        }
        return changed
    }

    /** The vehicle picked in the bar at the top of each page. */
    fun active(ctx: Context, g: Garage): Vehicle =
        g.find(AppPrefs.activeVehicle(ctx)) ?: g.vehicles[0]

    fun deleteAttachment(ctx: Context, name: String) {
        File(attachmentsDir(ctx), name).delete()
    }

    /** Write to a temporary file first so a crash never leaves a half-written file. */
    fun save(ctx: Context, g: Garage) {
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(g.json().toString(2))
        tmp.renameTo(File(ctx.filesDir, FILE))
        CalendarSync.syncLater(ctx) // keep the phone calendar matching the reminders
    }
}

/** Starting checklists. Users can edit, add, remove and reorder items afterwards. */
object DefaultLists {
    fun forType(t: VehicleType): List<Checklist> = when (t) {
        VehicleType.CARAVAN -> listOf(
            list("Setting up", caravanSetup),
            list("Packing away", caravanPackAway),
            list("Before towing", caravanBeforeTowing)
        )
        VehicleType.CAMPERVAN, VehicleType.MOTORHOME -> listOf(
            list("Setting up", vanSetup),
            list("Packing away", vanPackAway),
            list("Before driving", vanBeforeDriving)
        )
        VehicleType.CAR -> listOf(list("Before towing", carBeforeTowing))
    } + packing(t)

    /** Things to pack or take. Kit needed abroad is added when trip countries are chosen. */
    fun packing(t: VehicleType) = list("Packing", when (t) {
        VehicleType.CAR -> carPacking
        VehicleType.CARAVAN -> livingPacking + caravanPacking
        else -> livingPacking
    }).apply { kind = "packing" }

    private val livingPacking = listOf(
        "Bedding and pillows",
        "Towels",
        "Clothes, coats and wellies",
        "Toiletries",
        "Medicines and prescriptions",
        "First aid kit",
        "Phone chargers and cables",
        "Torch and spare batteries",
        "Food and drinks",
        "Kettle, pans and cooking utensils",
        "Plates, cups and cutlery",
        "Tea towels and washing-up liquid",
        "Bin bags",
        "Toilet chemicals and toilet roll",
        "Fresh water container and hose",
        "Waste water container",
        "Electric hook-up cable and adaptors",
        "Spare gas bottle",
        "Levelling ramps or blocks",
        "Outdoor chairs and table",
        "Awning or windbreak, pegs and mallet",
        "Site booking and breakdown cover details",
        "Games, books and things to do",
        "Pet bed, food and lead (if bringing pets)"
    )

    private val caravanPacking = listOf(
        "Towing mirrors",
        "Hitch lock and wheel clamp keys",
        "Motor mover remote (if fitted)"
    )

    private val carPacking = listOf(
        "Towing mirrors",
        "Tow ball cover and spare electrics adaptor",
        "Breakdown cover details",
        "Phone charger for the car"
    )

    private fun list(name: String, items: List<String>) =
        Checklist(name, items.map { CheckItem(it) }.toMutableList())

    private val caravanSetup = listOf(
        "Check the pitch for slopes, soft ground and overhanging branches",
        "Level side to side with ramps or blocks before unhitching",
        "Apply the caravan handbrake and chock the wheels",
        "Unplug the road lights cable and remove the breakaway cable",
        "Unhitch and level front to back with the jockey wheel",
        "Wind down the corner steadies (on pads if the ground is soft)",
        "Connect the mains hook-up: caravan end first, then the bollard",
        "Check the RCD and switch on the mains",
        "Turn on the gas at the bottle and check for smells",
        "Connect fresh water and waste containers",
        "Switch the fridge from 12 V to mains or gas",
        "Open roof vents and check the smoke and CO alarms",
        "Put up the awning, if using"
    )

    private val caravanPackAway = listOf(
        "Empty the waste water and toilet cassette",
        "Drain the fresh water system if frost is likely",
        "Switch the fridge to 12 V (or off) and wedge the door",
        "Turn off the gas at the bottle",
        "Close and lock windows, roof lights and vents",
        "Stow loose items low and over the axle; secure cupboards",
        "Lower and stow the TV aerial",
        "Unplug the mains hook-up: bollard end first, then the caravan",
        "Wind up the corner steadies",
        "Hitch up and check the hitch stability indicator",
        "Attach the breakaway cable to a proper point on the car",
        "Plug in the road lights cable and check all lights",
        "Raise and stow the jockey wheel",
        "Remove chocks, release the handbrake, check the pitch for left items"
    )

    private val caravanBeforeTowing = listOf(
        "Check tyre pressures (caravan and car)",
        "Check noseweight is within limits",
        "Towing mirrors fitted and adjusted",
        "Door locked, step stowed",
        "Corner steadies up and jockey wheel stowed",
        "All lights and indicators working"
    )

    private val vanSetup = listOf(
        "Check the pitch for slopes, soft ground and overhanging branches",
        "Drive onto levelling ramps if needed",
        "Apply the handbrake and put it in gear or park; engine off",
        "Turn the cab seats round, if they swivel",
        "Fit the cab blinds or windscreen cover",
        "Connect the mains hook-up: van end first, then the bollard",
        "Check the RCD and switch on the mains",
        "Turn on the gas and check for smells",
        "Fill fresh water / connect waste container",
        "Switch the fridge from 12 V to mains or gas",
        "Open roof vents and check the smoke and CO alarms",
        "Put out the awning, if using"
    )

    private val vanPackAway = listOf(
        "Empty the waste water and toilet cassette",
        "Drain the fresh water system if frost is likely",
        "Switch the fridge to 12 V (or off) and wedge the door",
        "Turn off the gas",
        "Close and lock windows, roof lights and vents",
        "Stow loose items and secure cupboards",
        "Lower and stow the TV aerial and wind in the awning",
        "Unplug the mains hook-up: bollard end first, then the van",
        "Retract the step",
        "Drive off the ramps and stow them",
        "Walk round: check for left items and anything still connected"
    )

    private val vanBeforeDriving = listOf(
        "Check tyre pressures (including the spare)",
        "Check oil, coolant, brake fluid and screen wash",
        "Enough fuel (and AdBlue, if your van uses it)",
        "Gas turned off at the bottle (or set for travel if you have a crash-sensor regulator)",
        "Fridge on 12 V or off, door catch locked",
        "Cupboards, drawers and the fridge closed; loose items and the TV stowed",
        "Hook-up cable unplugged and stowed",
        "Habitation door locked, step in",
        "Roof lights, windows and vents closed; aerial and satellite dish down",
        "Awning wound in and locked",
        "Cab seats facing forward, cab blinds and screen cover off",
        "Bikes and rack secure, number plate and lights not hidden",
        "Levelling ramps and chocks picked up and stowed",
        "Mirrors adjusted; all lights and indicators working"
    )

    private val carBeforeTowing = listOf(
        "Tyre pressures set to the towing (laden) figure",
        "Towing mirrors fitted and adjusted",
        "Tow ball clean and greased (unless using a stabiliser hitch)",
        "Electrics plugged in and lights checked"
    )
}

// Small JSON helpers
fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

fun JSONObject.optIntOrNull(k: String): Int? = if (isNull(k) || !has(k)) null else optInt(k)
fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else optLong(k)
