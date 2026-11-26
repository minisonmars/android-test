package com.caravanlevel

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** App-wide settings. Every change applies straight away. */
class SettingsActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var updateStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        val col = ui.column(16)
        col.addView(ui.text("Settings", 24f, bold = true))

        // Start page
        col.addView(ui.heading("Open the app on"))
        val choices = listOf(AppPrefs.OPEN_HOME to "The home page", AppPrefs.OPEN_LAST to "Where I left off") +
            Section.values().map { it.name to it.label }
        val current = choices.indexOfFirst { it.first == AppPrefs.openOn(this) }.coerceAtLeast(0)
        col.addView(ui.spinner(choices.map { it.second }, current) { AppPrefs.setOpenOn(this, choices[it].first) })
        col.addView(ui.text("The home page is always one tap back from any page.", 16f, Colours.MUTED))

        // Units
        col.addView(ui.heading("Units"))
        col.addView(switch("Sizes in inches instead of cm", AppPrefs.inches(this)) { AppPrefs.setInches(this, it) })
        col.addView(switch("Distances in km instead of miles", AppPrefs.km(this)) { AppPrefs.setKm(this, it) })

        // App updates: same layout as in all the other apps
        col.addView(ui.heading("Updates"))
        updateStatus = ui.text("", 17f)
        col.addView(updateStatus)
        col.addView(ui.button("App versions and updates") {
            Updater.showDialog(this) { refreshUpdateStatus() }
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Colours.BG)
            addView(col)
        })

        // Opening Settings always checks for a new version straight away
        refreshUpdateStatus()
        Updater.checkInBackground(this, force = true) { refreshUpdateStatus() }
    }

    private fun switch(label: String, on: Boolean, set: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        textSize = 18f
        setTextColor(Colours.TEXT)
        isChecked = on
        setPadding(0, ui.dp(12), 0, ui.dp(12))
        setOnCheckedChangeListener { _, v -> set(v) }
    }

    /** Red when a newer version is ready, like the other apps' Updates line. */
    private fun refreshUpdateStatus() {
        updateStatus.text = Updater.statusLine(this)
        if (Updater.available(this) != null) updateStatus.setTextColor(Updater.RED)
        else updateStatus.setTextColor(Updater.GREEN)
    }
}
