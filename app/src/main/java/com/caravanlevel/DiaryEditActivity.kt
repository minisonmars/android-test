package com.caravanlevel

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.File
import java.util.Locale

/**
 * One trip in the diary, made of one or more stops. Kept as soon as you leave the page, once
 * something is filled in. A trip whose last leaving date has passed opens read-only, with an
 * Edit button.
 */
class DiaryEditActivity : Activity() {
    companion object {
        const val EXTRA_TRIP = "trip"
        private const val TAKE_PHOTO = 1
        private const val PICK_FILE = 2
        private const val MAP = 4
    }

    private lateinit var ui: Ui
    private lateinit var garage: Garage
    private lateinit var trip: Trip
    private lateinit var col: LinearLayout
    private var pendingPhoto: String? = null
    private var pendingStop: String? = null
    private var deleted = false
    private var editing = false
    private val open = mutableSetOf<String>()   // stops shown in full
    private val commits = mutableListOf<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        garage = GarageStore.load(this)
        val id = savedInstanceState?.getString("id") ?: intent.getStringExtra(EXTRA_TRIP)
        pendingPhoto = savedInstanceState?.getString("photo")
        pendingStop = savedInstanceState?.getString("stop")
        trip = garage.trips.firstOrNull { it.id == id }
            ?: Trip(vehicleId = GarageStore.active(this, garage).id, id = id ?: newId()).apply {
                stops.add(Stop(Units.startOfDay(System.currentTimeMillis()) + 12 * 3600_000L))
            }
        editing = savedInstanceState?.getBoolean("editing") ?: !trip.over
        savedInstanceState?.getStringArrayList("open")?.let { open.addAll(it) }
            ?: trip.stops.lastOrNull()?.let { open.add(it.id) } // the latest stop starts open
        col = ui.column(16)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Colours.BG)
            fitSystemBars()
            addView(col)
        })
        Theme.barIcons(this)
        build()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("id", trip.id)
        out.putString("photo", pendingPhoto)
        out.putString("stop", pendingStop)
        out.putBoolean("editing", editing)
        out.putStringArrayList("open", ArrayList(open))
    }

    override fun onPause() {
        super.onPause()
        keep()
    }

    private fun keep() {
        if (deleted) return
        commits.forEach { it() }
        val worth = trip.title.isNotBlank() || trip.stops.any { !it.isEmpty }
        if (worth && garage.trips.none { it.id == trip.id }) garage.trips.add(trip)
        GarageStore.save(this, garage)
    }

    private fun stop(id: String?) = trip.stops.firstOrNull { it.id == id }

    private fun build() {
        commits.clear()
        col.removeAllViews()
        val t = trip
        val title = ui.text(t.name.takeIf { !editing } ?: "Trip", 24f, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(if (editing) title else ui.row(title, iconButton(Icon.EDIT) { editing = true; build() }))
        if (!editing) col.addView(ui.text(DiaryDates.of(t.startMs, t.endMs), 17f, Colours.MUTED))

        if (editing) {
            col.addView(ui.label("Trip name (optional)"))
            val title = ui.field(t.title, "For example: Lake District, summer 2026")
            col.addView(title)
            commits.add { t.title = title.text.toString().trim() }
            if (garage.vehicles.size > 1) {
                col.addView(ui.label("Vehicle"))
                val vs = garage.vehicles
                col.addView(ui.spinner(vs.map { it.name }, vs.indexOfFirst { it.id == t.vehicleId }.coerceAtLeast(0), vs.map { it.type.icon }) {
                    t.vehicleId = vs[it].id
                })
            }
        } else {
            garage.vehicles.firstOrNull { it.id == t.vehicleId }?.let { col.addView(ui.text("With ${it.name}", 16f, Colours.MUTED)) }
        }

        col.addView(ui.heading(if (t.stops.size == 1) "Stop" else "Stops"))
        t.stops.sortedBy { it.startMs }.forEachIndexed { i, s -> stopSection(s, i + 1) }

        if (editing) {
            col.addView(ui.button("Add another stop", Colours.TILE) {
                keep()
                val last = t.stops.maxByOrNull { it.startMs }
                val s = Stop(last?.endMs ?: last?.startMs ?: (Units.startOfDay(System.currentTimeMillis()) + 12 * 3600_000L))
                t.stops.add(s)
                open.clear(); open.add(s.id)
                keep(); build()
            })
            col.addView(ui.button("Done", Colours.ACCENT) { finish() })
            col.addView(ui.iconRow(ui.binButton {
                ui.confirm("Delete this trip?", "All its stops, photos and files will be deleted too.", "Delete") {
                    t.stops.flatMap { it.photos }.forEach { GarageStore.deleteAttachment(this, it) }
                    garage.trips.removeAll { it.id == t.id }
                    GarageStore.save(this, garage)
                    deleted = true
                    finish()
                }
            }, end = true))
        } else {
            col.addView(ui.button("Done", Colours.ACCENT) { finish() })
        }
    }

    /** A stop: a header to tap (site name and dates), and the details when it's open. */
    private fun stopSection(s: Stop, number: Int) {
        val isOpen = s.id in open
        val header = ui.card(if (isOpen) Colours.TILE else Colours.CARD).apply {
            addView(ui.text(s.site.ifBlank { "Stop $number" }, 19f, bold = true))
            addView(ui.text(DiaryDates.of(s.startMs, s.endMs) + if (isOpen) "" else "   (tap to show)", 15f, Colours.MUTED))
            isClickable = true
            setOnClickListener {
                keep()
                if (isOpen) open.remove(s.id) else open.add(s.id)
                build()
            }
        }
        col.addView(header)
        if (!isOpen) return
        if (editing) stopEditor(s) else stopView(s)
    }

    // ---- Read-only ----

    private fun stopView(s: Stop) {
        val box = ui.column().apply { setPadding(ui.dp(8), 0, ui.dp(8), ui.dp(8)) }
        if (s.place.isNotBlank()) box.addView(ui.text(s.place, 17f))
        if (s.pitch.isNotBlank()) box.addView(ui.text("Pitch ${s.pitch}", 17f))
        if (s.rating > 0) box.addView(Stars.row(this, ui, s.rating, 28, null))
        if (s.cost.isNotBlank()) box.addView(ui.text("Cost ${Units.money(s.cost)}", 17f))
        if (s.notes.isNotBlank()) box.addView(ui.text(s.notes, 17f).apply { setPadding(0, ui.dp(6), 0, ui.dp(6)) })
        val contact = listOfNotNull(
            s.phone.takeIf { it.isNotBlank() }?.let { "Phone: $it" },
            s.email.takeIf { it.isNotBlank() }?.let { "Email: $it" },
            s.website.takeIf { it.isNotBlank() }?.let { "Website: $it" }
        )
        if (contact.isNotEmpty()) box.addView(ui.text(contact.joinToString("\n"), 16f, Colours.MUTED))
        col.addView(box)
        actions(s)
        s.photos.forEach { name -> col.addView(photoCard(s, name, removable = false)) }
    }

    /** A small round-cornered icon button. */
    private fun iconButton(icon: Icon, onClick: () -> Unit) = ui.iconButton(icon, onClick = onClick)

    /** Call, email, website and navigate icons for whatever the stop has. */
    private fun actions(s: Stop) {
        val buttons = listOfNotNull(
            s.phone.takeIf { it.isNotBlank() }?.let { p -> iconButton(Icon.PHONE) { go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(p)))) } },
            s.email.takeIf { it.isNotBlank() }?.let { e -> iconButton(Icon.EMAIL) { go(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$e"))) } },
            s.website.takeIf { it.isNotBlank() }?.let { w ->
                iconButton(Icon.WEB) { go(Intent(Intent.ACTION_VIEW, Uri.parse(if (w.startsWith("http")) w else "https://$w"))) }
            },
            if (s.hasPlace) iconButton(Icon.NAVIGATE) { openMaps(s) } else null
        )
        if (buttons.isNotEmpty()) col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEach { addView(it) }
        })
    }

    // ---- Editing ----

    private fun stopEditor(s: Stop) {
        fun field(label: String, value: String, hint: String, type: Int? = null, set: (String) -> Unit) {
            col.addView(ui.label(label))
            val f = if (type == null) ui.field(value, hint) else ui.field(value, hint, type)
            col.addView(f)
            commits.add { set(f.text.toString().trim()) }
        }
        field("Site name", s.site, "For example: Hillside Caravan Park") { s.site = it }

        col.addView(ui.label("Arrived"))
        col.addView(ui.button(Units.date(s.startMs)) { ui.pickDate(s.startMs) { keep(); s.startMs = it; build() } })
        col.addView(ui.label("Left"))
        col.addView(ui.button(s.endMs?.let { Units.date(it) } ?: "Not set (tap to set)") {
            ui.pickDate(s.endMs ?: s.startMs) { keep(); s.endMs = it; build() }
        })

        // Where
        field("Address or town", s.place, "For example: Keswick, Cumbria") { s.place = it }
        if (s.hasPlace) {
            col.addView(ui.text("Pinned on the map", 16f, Colours.MUTED))
            col.addView(ui.button("Move the pin") { pickOnMap(s) })
            col.addView(ui.button("Remove pin", Colours.BG) { keep(); s.lat = null; s.lon = null; keep(); build() })
        } else {
            col.addView(ui.text("Not on the map yet: choose the spot on the map, or find it from the address.", 16f, Colours.MUTED))
            col.addView(ui.row(
                ui.rowButton("Choose on the map") { pickOnMap(s) },
                ui.rowButton("Find from the address") { findAddress(s) }
            ))
        }

        field("Pitch number", s.pitch, "For example: 14 or B7") { s.pitch = it }

        col.addView(ui.label("Rating"))
        col.addView(Stars.row(this, ui, s.rating, 40) { keep(); s.rating = it; keep(); build() })

        col.addView(ui.label("Cost (£, optional)"))
        val cost = ui.numberField(s.cost, "0.00")
        col.addView(cost)
        commits.add { s.cost = cost.text.toString().trim() }

        col.addView(ui.label("Notes"))
        val notes = ui.multiline(s.notes, "What was it like? Facilities, views, things to remember...")
        col.addView(notes)
        commits.add { s.notes = notes.text.toString() }

        col.addView(ui.label("Site contact details"))
        field("Phone", s.phone, "Phone number", android.text.InputType.TYPE_CLASS_PHONE) { s.phone = it }
        field("Email", s.email, "Email address", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) { s.email = it }
        field("Website", s.website, "Website", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI) { s.website = it }
        actions(s)

        col.addView(ui.label("Photos and files"))
        s.photos.toList().forEach { name -> col.addView(photoCard(s, name, removable = true)) }
        col.addView(ui.iconRow(
            ui.iconButton(Icon.CAMERA) { takePhoto(s) },
            ui.iconButton(Icon.ATTACH) { pickFile(s) }
        ))
        if (trip.stops.size > 1) {
            col.addView(ui.iconRow(ui.binButton {
                ui.confirm("Remove this stop?", "Its photos and files will be deleted too.", "Remove") {
                    keep()
                    s.photos.forEach { GarageStore.deleteAttachment(this, it) }
                    trip.stops.remove(s)
                    keep(); build()
                }
            }, end = true))
        }
        col.addView(android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(1, ui.dp(12)) })
    }

    private fun go(i: Intent) {
        keep()
        try { startActivity(i) } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app on this phone can do that", Toast.LENGTH_LONG).show()
        }
    }

    private fun openMaps(s: Stop) {
        val lat = s.lat ?: return
        val lon = s.lon ?: return
        go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(s.site.ifBlank { "Site" })})")))
    }

    // ---- Map and addresses ----

    private fun pickOnMap(s: Stop) {
        keep()
        startActivityForResult(Intent(this, SitesMapActivity::class.java)
            .putExtra(SitesMapActivity.EXTRA_PICK, true)
            .putExtra("lat", s.lat ?: Double.NaN).putExtra("lon", s.lon ?: Double.NaN)
            .putExtra("stop", s.id), MAP)
    }

    @Suppress("DEPRECATION")
    private fun findAddress(s: Stop) {
        keep()
        val q = listOf(s.site, s.place).filter { it.isNotBlank() }.joinToString(", ")
        if (q.isBlank()) { Toast.makeText(this, "Type the address or town first", Toast.LENGTH_LONG).show(); return }
        Thread {
            val found = try {
                if (!Geocoder.isPresent()) null
                else Geocoder(this, Locale.UK).getFromLocationName(q, 1)?.firstOrNull()
                    ?: Geocoder(this, Locale.UK).getFromLocationName(s.place, 1)?.firstOrNull()
            } catch (_: Exception) { null }
            runOnUiThread {
                if (found == null) Toast.makeText(this, "Couldn't find that place. Check the address, or choose it on the map.", Toast.LENGTH_LONG).show()
                else {
                    s.lat = found.latitude; s.lon = found.longitude; keep(); build()
                    Toast.makeText(this, "Found it. Tap Move the pin to check it's right.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // ---- Photos and files ----

    private fun photoCard(s: Stop, name: String, removable: Boolean) = ui.card().apply {
        if (AttachmentProvider.mime(name).startsWith("image/")) {
            Attach.thumbnail(File(GarageStore.attachmentsDir(this@DiaryEditActivity), name))?.let { bmp ->
                addView(ImageView(this@DiaryEditActivity).apply {
                    setImageBitmap(bmp)
                    adjustViewBounds = true
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(180))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                })
            }
        }
        addView(ui.text(AttachmentProvider.displayName(name), 16f).apply { gravity = Gravity.START })
        if (removable) addView(ui.iconRow(
            ui.binButton {
                ui.confirm("Remove this file?", AttachmentProvider.displayName(name), "Remove") {
                    keep()
                    s.photos.remove(name)
                    GarageStore.deleteAttachment(this@DiaryEditActivity, name)
                    keep(); build()
                }
            }, end = true
        ))
        isClickable = true
        setOnClickListener { Attach.view(this@DiaryEditActivity, name) }
    }

    private fun takePhoto(s: Stop) {
        keep()
        val name = Attach.newName("Photo ${Units.date(System.currentTimeMillis())}.jpg")
        File(GarageStore.attachmentsDir(this), name).createNewFile()
        try {
            pendingPhoto = name; pendingStop = s.id
            startActivityForResult(Attach.cameraIntent(name), TAKE_PHOTO)
        } catch (_: ActivityNotFoundException) {
            pendingPhoto = null
            GarageStore.deleteAttachment(this, name)
            Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickFile(s: Stop) {
        keep()
        pendingStop = s.id
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try { startActivityForResult(i, PICK_FILE) } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No file picker found", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            MAP -> if (resultCode == RESULT_OK && data != null) {
                stop(data.getStringExtra("stop"))?.let { s ->
                    s.lat = data.getDoubleExtra("lat", 0.0); s.lon = data.getDoubleExtra("lon", 0.0)
                }
            }
            TAKE_PHOTO -> {
                val name = pendingPhoto ?: return
                pendingPhoto = null
                val f = File(GarageStore.attachmentsDir(this), name)
                val s = stop(pendingStop) ?: trip.stops.lastOrNull()
                if (resultCode == RESULT_OK && f.length() > 0 && s != null) s.photos.add(name) else f.delete()
            }
            PICK_FILE -> {
                val uri = data?.data
                val s = stop(pendingStop) ?: trip.stops.lastOrNull()
                if (resultCode == RESULT_OK && uri != null && s != null) Attach.copyIn(this, uri)?.let { s.photos.add(it) }
            }
        }
        keep()
        build()
    }
}
