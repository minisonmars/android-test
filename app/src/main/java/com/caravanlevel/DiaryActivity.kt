package com.caravanlevel

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * The trip diary: every stay, newest first or sorted another way, with a search box.
 * Shared by all vehicles.
 */
class DiaryActivity : Page() {
    override val section = Section.DIARY

    companion object {
        private val SORTS = listOf("Most recent", "Nearest to me", "Highest rated", "Oldest first")
        private const val LOCATION = 1
    }

    private var search = ""
    private var sort = 0
    private lateinit var listBox: LinearLayout

    override fun build() {
        content.addView(ui.button("Add a trip", Colours.ACCENT) { edit(null) })
        if (garage.trips.isEmpty()) {
            content.addView(ui.text(
                "No trips yet. Add where you stayed, the pitch, what it was like, photos and the site's contact details.",
                18f, Colours.MUTED
            ).apply { setPadding(0, ui.dp(12), 0, 0) })
            return
        }
        if (garage.trips.any { it.hasPlace }) {
            content.addView(ui.button("Show all sites on a map") { showMap() })
        }

        content.addView(ui.label("Search"))
        val box = ui.field(search, "Site, place, pitch or notes")
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { search = s.toString(); fillList() }
        })
        content.addView(box)
        content.addView(ui.label("Sort"))
        content.addView(ui.spinner(SORTS, sort) {
            sort = it
            if (SORTS[it] == "Nearest to me" && !Here.allowed(this)) {
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION)
            }
            fillList()
        })
        listBox = ui.column()
        content.addView(listBox)
        fillList()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        fillList()
    }

    private fun fillList() {
        if (!::listBox.isInitialized) return
        listBox.removeAllViews()
        val words = search.lowercase().split(' ').filter { it.isNotBlank() }
        var trips = garage.trips.filter { t -> words.all { t.searchText().contains(it) } }
        val here = if (SORTS[sort] == "Nearest to me") Here.last(this) else null
        trips = when (SORTS[sort]) {
            "Nearest to me" -> if (here == null) trips.sortedByDescending { it.startMs } else
                trips.sortedBy { t -> if (t.hasPlace) Geo.km(here.latitude, here.longitude, t.lat!!, t.lon!!) else Double.MAX_VALUE }
            "Highest rated" -> trips.sortedWith(compareByDescending<Trip> { it.rating }.thenByDescending { it.startMs })
            "Oldest first" -> trips.sortedBy { it.startMs }
            else -> trips.sortedByDescending { it.startMs }
        }
        if (SORTS[sort] == "Nearest to me" && here == null) {
            listBox.addView(ui.text("Your location isn't known yet, so these are newest first.", 15f, Colours.MUTED))
        }
        if (trips.isEmpty()) listBox.addView(ui.text("Nothing matches that search.", 17f, Colours.MUTED).apply { setPadding(0, ui.dp(12), 0, 0) })
        trips.forEach { t -> listBox.addView(card(t, here)) }
    }

    private fun card(t: Trip, here: android.location.Location?) = ui.card().apply {
        t.photos.firstOrNull { AttachmentProvider.mime(it).startsWith("image/") }?.let { name ->
            Attach.thumbnail(java.io.File(GarageStore.attachmentsDir(this@DiaryActivity), name), 360)?.let { bmp ->
                addView(ImageView(this@DiaryActivity).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(140))
                })
            }
        }
        addView(ui.text(dates(t), 16f, Colours.MUTED))
        addView(ui.text(t.site.ifBlank { "Trip" }, 20f, bold = true))
        val bits = listOfNotNull(
            t.place.takeIf { it.isNotBlank() },
            t.pitch.takeIf { it.isNotBlank() }?.let { "Pitch $it" },
            if (here != null && t.hasPlace) {
                val km = Geo.km(here.latitude, here.longitude, t.lat!!, t.lon!!)
                if (AppPrefs.km(this@DiaryActivity)) "%.0f km away".format(km) else "%.0f miles away".format(km / Units.KM_PER_MILE)
            } else null
        )
        if (bits.isNotEmpty()) addView(ui.text(bits.joinToString("  ·  "), 17f))
        if (t.rating > 0) addView(Stars.row(this@DiaryActivity, ui, t.rating, 22, null))
        if (t.notes.isNotBlank()) addView(ui.text(t.notes.lines().take(2).joinToString("\n"), 16f, Colours.MUTED))
        isClickable = true
        setOnClickListener { edit(t.id) }
    }

    private fun dates(t: Trip): String {
        val end = t.endMs
        if (end == null || Units.startOfDay(end) == Units.startOfDay(t.startMs)) return Units.date(t.startMs)
        val nights = Units.daysFromToday(end) - Units.daysFromToday(t.startMs)
        return "${Units.date(t.startMs)} to ${Units.date(end)} ($nights night${if (nights == 1) "" else "s"})"
    }

    private fun showMap() {
        save()
        startActivity(Intent(this, SitesMapActivity::class.java))
    }

    private fun edit(id: String?) {
        save()
        startActivity(Intent(this, DiaryEditActivity::class.java).putExtra(DiaryEditActivity.EXTRA_TRIP, id))
    }
}

/** A row of five stars; tap one to set the rating (tap the same again to clear). */
object Stars {
    fun row(a: android.app.Activity, ui: Ui, rating: Int, sizeDp: Int, onPick: ((Int) -> Unit)?): LinearLayout =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            for (i in 1..5) {
                addView(IconView(a, if (i <= rating) Icon.STAR else Icon.STAR_EMPTY, Colours.AMBER).apply {
                    layoutParams = LinearLayout.LayoutParams(ui.dp(sizeDp), ui.dp(sizeDp)).apply { setMargins(0, ui.dp(4), ui.dp(6), ui.dp(4)) }
                    if (onPick != null) {
                        isClickable = true
                        setOnClickListener { onPick(if (i == rating) 0 else i) }
                    }
                })
            }
        }
}
