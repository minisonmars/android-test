package com.caravanlevel

import android.app.Activity
import android.os.Build
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
        col.addView(ui.text(installedBy() + "\nThis phone: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", 16f, Colours.MUTED).apply { setPadding(0, ui.dp(4), 0, ui.dp(4)) })
        col.addView(ui.button("App versions and updates") {
            Updater.showDialog(this) { refreshUpdateStatus() }
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Colours.BG)
            fitSystemBars()
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

    /**
     * Which app Android thinks installed this one. Updates only skip Android's confirm
     * screen (Android 12+) when it says this app itself.
     */
    private fun installedBy(): String {
        val who = try {
            if (Build.VERSION.SDK_INT >= 30) packageManager.getInstallSourceInfo(packageName).installingPackageName
            else @Suppress("DEPRECATION") packageManager.getInstallerPackageName(packageName)
        } catch (_: Exception) { null }
        return when {
            who == packageName -> "Installed by: this app, so updates can install without Android's confirm screen" +
                if (Build.VERSION.SDK_INT < 31) " (needs Android 12 or later)" else ""
            who == null -> "Installed by: unknown. The next update will ask you to confirm; after that they shouldn't"
            else -> {
                val name = try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(who, 0)).toString()
                } catch (_: Exception) { who }
                "Installed by: $name. The next update will ask you to confirm; after that they shouldn't"
            }
        }
    }

    /** Red when a newer version is ready, like the other apps' Updates line. */
    private fun refreshUpdateStatus() {
        updateStatus.text = Updater.statusLine(this)
        if (Updater.available(this) != null) updateStatus.setTextColor(Updater.RED)
        else updateStatus.setTextColor(Updater.GREEN)
    }
}
