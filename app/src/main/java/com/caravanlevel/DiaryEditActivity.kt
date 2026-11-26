package com.caravanlevel

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.File
import java.util.Locale

/** One trip in the diary. Kept as soon as you leave the page, once something is filled in. */
class DiaryEditActivity : Activity() {
    companion object {
        const val EXTRA_TRIP = "trip"
        private const val TAKE_PHOTO = 1
        private const val PICK_FILE = 2
        private const val LOCATION = 3
    }

    private lateinit var ui: Ui
    private lateinit var garage: Garage
    private lateinit var trip: Trip
    private lateinit var col: LinearLayout
    private var pendingPhoto: String? = null
    private var deleted = false
    private val commits = mutableListOf<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        garage = GarageStore.load(this)
        val id = savedInstanceState?.getString("id") ?: intent.getStringExtra(EXTRA_TRIP)
        pendingPhoto = savedInstanceState?.getString("photo")
        trip = garage.trips.firstOrNull { it.id == id }
            ?: Trip(Units.startOfDay(System.currentTimeMillis()) + 12 * 3600_000L, id = id ?: newId()).apply {
                vehicleId = GarageStore.active(this@DiaryEditActivity, garage).id
            }
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
    }

    override fun onPause() {
        super.onPause()
        keep()
    }

    private fun keep() {
        if (deleted) return
        commits.forEach { it() }
        val t = trip
        val worth = t.site.isNotBlank() || t.place.isNotBlank() || t.notes.isNotBlank() || t.pitch.isNotBlank() ||
            t.photos.isNotEmpty() || t.hasPlace || t.rating > 0
        if (worth && garage.trips.none { it.id == t.id }) garage.trips.add(t)
        GarageStore.save(this, garage)
    }

    private fun build() {
        commits.clear()
        col.removeAllViews()
        val t = trip
        col.addView(ui.text("Trip", 24f, bold = true))

        fun field(label: String, value: String, hint: String, type: Int? = null, set: (String) -> Unit) {
            col.addView(ui.label(label))
            val f = if (type == null) ui.field(value, hint) else ui.field(value, hint, type)
            col.addView(f)
            commits.add { set(f.text.toString().trim()) }
        }
        field("Site name", t.site, "For example: Hillside Caravan Park") { t.site = it }

        col.addView(ui.label("Arrived"))
        col.addView(ui.button(Units.date(t.startMs)) { ui.pickDate(t.startMs) { keep(); t.startMs = it; build() } })
        col.addView(ui.label("Left"))
        col.addView(ui.button(t.endMs?.let { Units.date(it) } ?: "Not set (tap to set)") {
            ui.pickDate(t.endMs ?: t.startMs) { keep(); t.endMs = it; build() }
        })

        // Where
        field("Address or town", t.place, "For example: Keswick, Cumbria") { t.place = it }
        if (t.hasPlace) {
            col.addView(ui.text("On the map at %.5f, %.5f".format(Locale.UK, t.lat, t.lon), 16f, Colours.MUTED))
        } else {
            col.addView(ui.text("Not on the map yet: use where you are now, or find it from the address.", 16f, Colours.MUTED))
        }
        col.addView(ui.row(
            ui.rowButton("Use where I am now") { useHere() },
            ui.rowButton("Find from the address") { findAddress() }
        ))
        if (t.hasPlace) col.addView(ui.row(
            ui.rowButton("Open in maps") { openMaps() },
            ui.rowButton("Remove from map", Colours.BG) { keep(); t.lat = null; t.lon = null; keep(); build() }
        ))

        field("Pitch number", t.pitch, "For example: 14 or B7") { t.pitch = it }

        col.addView(ui.label("Rating"))
        col.addView(Stars.row(this, ui, t.rating, 40) { keep(); t.rating = it; keep(); build() })

        col.addView(ui.label("Cost (£, optional)"))
        val cost = ui.numberField(t.cost, "0.00")
        col.addView(cost)
        commits.add { t.cost = cost.text.toString().trim() }

        if (garage.vehicles.size > 1) {
            col.addView(ui.label("Vehicle"))
            val vs = garage.vehicles
            col.addView(ui.spinner(vs.map { it.name }, vs.indexOfFirst { it.id == t.vehicleId }.coerceAtLeast(0), vs.map { it.type.icon }) {
                t.vehicleId = vs[it].id
            })
        }

        col.addView(ui.label("Notes"))
        val notes = ui.multiline(t.notes, "What was it like? Facilities, views, things to remember...")
        col.addView(notes)
        commits.add { t.notes = notes.text.toString() }

        // Contact details
        col.addView(ui.heading("Site contact details"))
        field("Phone", t.phone, "Phone number", android.text.InputType.TYPE_CLASS_PHONE) { t.phone = it }
        field("Email", t.email, "Email address", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) { t.email = it }
        field("Website", t.website, "Website", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI) { t.website = it }
        val actions = listOfNotNull(
            t.phone.takeIf { it.isNotBlank() }?.let { p -> ui.rowButton("Call") { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(p)))) } },
            t.email.takeIf { it.isNotBlank() }?.let { e -> ui.rowButton("Email") { open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + e))) } },
            t.website.takeIf { it.isNotBlank() }?.let { w ->
                ui.rowButton("Website") { open(Intent(Intent.ACTION_VIEW, Uri.parse(if (w.startsWith("http")) w else "https://$w"))) }
            }
        )
        if (actions.isNotEmpty()) col.addView(ui.row(*actions.toTypedArray()))

        // Photos
        col.addView(ui.heading("Photos and files"))
        t.photos.toList().forEach { name -> col.addView(photoCard(name)) }
        col.addView(ui.row(
            ui.rowButton("Take a photo", Colours.TILE) { takePhoto() },
            ui.rowButton("Add photo or file", Colours.TILE) { pickFile() }
        ))

        col.addView(ui.button("Done", Colours.ACCENT) { finish() })
        col.addView(ui.button("Delete this trip", Colours.RED) {
            ui.confirm("Delete this trip?", "Its photos and files will be deleted too.", "Delete") {
                t.photos.forEach { GarageStore.deleteAttachment(this, it) }
                garage.trips.removeAll { it.id == t.id }
                GarageStore.save(this, garage)
                deleted = true
                finish()
            }
        })
    }

    private fun open(i: Intent) {
        keep()
        try { startActivity(i) } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app on this phone can do that", Toast.LENGTH_LONG).show()
        }
    }

    private fun openMaps() {
        val lat = trip.lat ?: return
        val lon = trip.lon ?: return
        val label = Uri.encode(trip.site.ifBlank { "Site" })
        open(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")))
    }

    // ---- Location ----

    private fun useHere() {
        keep()
        if (!Here.allowed(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION)
            return
        }
        Toast.makeText(this, "Finding where you are...", Toast.LENGTH_SHORT).show()
        Here.fresh(this) { l ->
            if (l == null) {
                Toast.makeText(this, "Couldn't find where you are. Is Location turned on?", Toast.LENGTH_LONG).show()
            } else {
                keep()
                trip.lat = l.latitude; trip.lon = l.longitude
                if (trip.place.isBlank()) addressFor(l.latitude, l.longitude)?.let { trip.place = it }
                keep(); build()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION && Here.allowed(this)) useHere()
    }

    /** Town or address for a position, using the phone's own lookup (needs internet). */
    @Suppress("DEPRECATION")
    private fun addressFor(lat: Double, lon: Double): String? = try {
        Geocoder(this, Locale.UK).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { a ->
            listOfNotNull(a.locality ?: a.subAdminArea, a.adminArea).distinct().joinToString(", ").ifBlank { null }
        }
    } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    private fun findAddress() {
        keep()
        val q = listOf(trip.site, trip.place).filter { it.isNotBlank() }.joinToString(", ")
        if (q.isBlank()) { Toast.makeText(this, "Type the address or town first", Toast.LENGTH_LONG).show(); return }
        Thread {
            val found = try {
                if (!Geocoder.isPresent()) null
                else Geocoder(this, Locale.UK).getFromLocationName(q, 1)?.firstOrNull()
                    ?: Geocoder(this, Locale.UK).getFromLocationName(trip.place, 1)?.firstOrNull()
            } catch (_: Exception) { null }
            runOnUiThread {
                if (found == null) Toast.makeText(this, "Couldn't find that place. Check the address, or use where you are now.", Toast.LENGTH_LONG).show()
                else {
                    keep(); trip.lat = found.latitude; trip.lon = found.longitude; keep(); build()
                    Toast.makeText(this, "Found it. Tap Open in maps to check it's right.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // ---- Photos and files ----

    private fun photoCard(name: String) = ui.card().apply {
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
        addView(ui.text(AttachmentProvider.displayName(name), 17f))
        addView(ui.row(
            ui.rowButton("Open") { Attach.view(this@DiaryEditActivity, name) },
            ui.rowButton("Remove", Colours.RED) {
                ui.confirm("Remove this file?", AttachmentProvider.displayName(name), "Remove") {
                    keep()
                    trip.photos.remove(name)
                    GarageStore.deleteAttachment(this@DiaryEditActivity, name)
                    keep()
                    build()
                }
            }
        ))
        isClickable = true
        setOnClickListener { Attach.view(this@DiaryEditActivity, name) }
    }

    private fun takePhoto() {
        keep()
        val name = Attach.newName("Photo ${Units.date(System.currentTimeMillis())}.jpg")
        File(GarageStore.attachmentsDir(this), name).createNewFile()
        try {
            pendingPhoto = name
            startActivityForResult(Attach.cameraIntent(name), TAKE_PHOTO)
        } catch (_: ActivityNotFoundException) {
            pendingPhoto = null
            GarageStore.deleteAttachment(this, name)
            Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickFile() {
        keep()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try { startActivityForResult(i, PICK_FILE) } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No file picker found", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            TAKE_PHOTO -> {
                val name = pendingPhoto ?: return
                pendingPhoto = null
                val f = File(GarageStore.attachmentsDir(this), name)
                if (resultCode == RESULT_OK && f.length() > 0) trip.photos.add(name) else f.delete()
            }
            PICK_FILE -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) Attach.copyIn(this, uri)?.let { trip.photos.add(it) }
            }
        }
        keep()
        build()
    }
}
