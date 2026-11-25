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

    companion object {
        fun of(s: String?) = values().firstOrNull { it.name == s } ?: CARAVAN
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
    fun json() = JSONObject().put("id", id).put("text", text).put("ticked", ticked)

    companion object {
        fun from(o: JSONObject) =
            CheckItem(o.optString("text"), o.optBoolean("ticked"), o.optString("id", newId()))
    }
}

class Checklist(var name: String, val items: MutableList<CheckItem>, val id: String = newId()) {
    fun json() = JSONObject().put("id", id).put("name", name)
        .put("items", JSONArray().apply { items.forEach { put(it.json()) } })

    fun resetTicks() = items.forEach { it.ticked = false }

    companion object {
        fun from(o: JSONObject) = Checklist(
            o.optString("name"),
            o.optJSONArray("items").objects().map { CheckItem.from(it) }.toMutableList(),
            o.optString("id", newId())
        )
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
    val id: String = newId()
) {
    fun json() = JSONObject().put("id", id).put("title", title)
        .put("due", dueMs ?: JSONObject.NULL).put("dueMiles", dueMiles ?: JSONObject.NULL)
        .put("repeatMonths", repeatMonths).put("repeatMiles", repeatMiles).put("notes", notes)

    companion object {
        fun from(o: JSONObject) = Reminder(
            o.optString("title"), o.optLongOrNull("due"), o.optIntOrNull("dueMiles"),
            o.optInt("repeatMonths"), o.optInt("repeatMiles"), o.optString("notes"),
            o.optString("id", newId())
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
    val dimensions: MutableList<Detail> = mutableListOf(),   // length, width, height (cm)
    val equipment: MutableList<Detail> = mutableListOf(),    // fridge, heater, mover...
    val checklists: MutableList<Checklist> = mutableListOf(),
    val services: MutableList<ServiceRecord> = mutableListOf(),
    val reminders: MutableList<Reminder> = mutableListOf(),
    val mileage: MutableList<MileageEntry> = mutableListOf(),
    var notes: String = "",
    val id: String = newId()
) {
    /** Latest known odometer reading, or null. */
    val latestMiles get() = mileage.maxByOrNull { it.dateMs }?.miles

    fun json(): JSONObject {
        fun list(l: List<Detail>) = JSONArray().apply { l.forEach { put(it.json()) } }
        return JSONObject().put("id", id).put("name", name).put("type", type.name)
            .put("details", list(details)).put("dimensions", list(dimensions))
            .put("equipment", list(equipment))
            .put("checklists", JSONArray().apply { checklists.forEach { put(it.json()) } })
            .put("services", JSONArray().apply { services.forEach { put(it.json()) } })
            .put("reminders", JSONArray().apply { reminders.forEach { put(it.json()) } })
            .put("mileage", JSONArray().apply { mileage.forEach { put(it.json()) } })
            .put("notes", notes)
    }

    companion object {
        fun from(o: JSONObject): Vehicle {
            fun details(k: String) =
                o.optJSONArray(k).objects().map { Detail.from(it) }.toMutableList()
            return Vehicle(
                o.optString("name", "My caravan"), VehicleType.of(o.optString("type")),
                details("details"), details("dimensions"), details("equipment"),
                o.optJSONArray("checklists").objects().map { Checklist.from(it) }.toMutableList(),
                o.optJSONArray("services").objects().map { ServiceRecord.from(it) }.toMutableList(),
                o.optJSONArray("reminders").objects().map { Reminder.from(it) }.toMutableList(),
                o.optJSONArray("mileage").objects().map { MileageEntry.from(it) }.toMutableList(),
                o.optString("notes"), o.optString("id", newId())
            )
        }

        /** A new vehicle with the default checklists for its type. */
        fun create(name: String, type: VehicleType) = Vehicle(name, type).apply {
            checklists.addAll(DefaultLists.forType(type))
            details.addAll(listOf("Make", "Model", "Year").map { Detail(it, "") })
            if (type == VehicleType.CARAVAN) details.add(Detail("CRiS number", ""))
            else details.add(Detail("Registration", ""))
        }
    }
}

class Garage(val vehicles: MutableList<Vehicle>, var milesUnit: Boolean = true) {
    fun json() = JSONObject().put("version", 1).put("miles", milesUnit)
        .put("vehicles", JSONArray().apply { vehicles.forEach { put(it.json()) } })

    fun find(id: String?) = vehicles.firstOrNull { it.id == id }

    companion object {
        fun from(o: JSONObject) = Garage(
            o.optJSONArray("vehicles").objects().map { Vehicle.from(it) }.toMutableList(),
            o.optBoolean("miles", true)
        )
    }
}

object GarageStore {
    private const val FILE = "garage.json"

    fun attachmentsDir(ctx: Context) = File(ctx.filesDir, "attachments").apply { mkdirs() }

    fun load(ctx: Context): Garage {
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) {
            try {
                return Garage.from(JSONObject(f.readText()))
            } catch (_: Exception) {
                // fall through to an empty garage; the bad file is kept as a backup
                f.copyTo(File(ctx.filesDir, "$FILE.bad"), overwrite = true)
            }
        }
        return Garage(mutableListOf())
    }

    /** Write to a temporary file first so a crash never leaves a half-written file. */
    fun save(ctx: Context, g: Garage) {
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(g.json().toString(2))
        tmp.renameTo(File(ctx.filesDir, FILE))
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
    }

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
        "Apply the handbrake and put it in gear or park",
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
        "Check tyre pressures",
        "Check oil, coolant and screen wash",
        "Habitation door locked, step in",
        "Roof lights closed, aerial down",
        "All lights and indicators working"
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
