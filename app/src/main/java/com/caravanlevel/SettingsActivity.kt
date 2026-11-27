package com.caravanlevel

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** App-wide settings. Every change applies straight away. */
class SettingsActivity : Activity() {
    companion object {
        private const val BACKUP = 1
        private const val RESTORE = 2
        private const val CHOOSE_AUTO = 3
        const val EXTRA_DO = "do"
        const val DO_CHOOSE_AUTO = "chooseAuto"
        const val DO_RESTORE = "restore"
    }

    private var autoStatus: TextView? = null

    private lateinit var ui: Ui
    private lateinit var updateStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        val col = ui.column(16)
        col.addView(ui.text("Settings", 24f, bold = true))

        // Theme
        col.addView(ui.heading("Theme"))
        val themes = listOf(Theme.SYSTEM to "Follow the phone", Theme.LIGHT to "Light (for daytime)", Theme.DARK to "Dark (for night-time)")
        val shownTheme = themes.indexOfFirst { it.first == Theme.choice(this) }.coerceAtLeast(0)
        col.addView(ui.spinner(themes.map { it.second }, shownTheme) {
            Theme.setChoice(this, themes[it].first)
            recreate() // redraw this page straight away; the others redraw when you go back
        })
        col.addView(ui.text("Follow the phone switches between light and dark when your phone does.", 16f, Colours.MUTED))

        // Colour
        col.addView(ui.heading("Colour"))
        val names = Colours.choices.map { it.first }
        col.addView(ui.spinner(names, names.indexOf(Theme.colour(this)).coerceAtLeast(0)) {
            Theme.setColour(this, names[it])
            recreate()
        })
        col.addView(ui.text("Used for buttons, icons and the vehicle pictures, with a light tint in the background.", 16f, Colours.MUTED))

        // Start page
        col.addView(ui.heading("Open the app on"))
        val choices = listOf(AppPrefs.OPEN_HOME to "The home page", AppPrefs.OPEN_LAST to "Where I left off") +
            Section.values().map { it.name to it.label }
        val current = choices.indexOfFirst { it.first == AppPrefs.openOn(this) }.coerceAtLeast(0)
        col.addView(ui.spinner(choices.map { it.second }, current) { AppPrefs.setOpenOn(this, choices[it].first) })
        col.addView(ui.text("The home page is always one tap back from any page.", 16f, Colours.MUTED))

        // Units
        col.addView(ui.heading("Units"))
        col.addView(switch("Inches instead of cm: for the Level page's raise/lower amounts, and the starting unit for sizes not entered yet", AppPrefs.inches(this)) { AppPrefs.setInches(this, it) })
        col.addView(switch("Km instead of miles: for mileage, reminders, and distances on the Compass and Trip diary", AppPrefs.km(this)) { AppPrefs.setKm(this, it) })

        // Automatic backup
        col.addView(ui.heading("Automatic backup"))
        autoStatus = ui.text("", 17f)
        col.addView(autoStatus)
        refreshAuto()
        col.addView(ui.button(if (AutoBackup.uri(this) == null) "Choose where to keep it" else "Change where it's kept", Colours.ACCENT) { chooseAuto() })
        val shown = AutoBackup.CHOICES.indexOfFirst { it.first == AutoBackup.days(this) }.coerceAtLeast(0)
        col.addView(ui.label("How often"))
        col.addView(ui.spinner(AutoBackup.CHOICES.map { it.second }, shown) {
            AutoBackup.setDays(this, AutoBackup.CHOICES[it].first); refreshAuto()
        })
        col.addView(ui.button("Back up now") {
            if (AutoBackup.uri(this) == null) { chooseAuto(); return@button }
            val busy = AlertDialog.Builder(this).setMessage("Backing up...").setCancelable(false).show()
            Thread {
                val err = AutoBackup.run(this)
                runOnUiThread { busy.dismiss(); toast(if (err == null) "Backup saved" else "Couldn't back up: $err"); refreshAuto() }
            }.start()
        })

        // Backup
        col.addView(ui.heading("Backup to a file"))
        col.addView(ui.text("Saves everything (vehicles, checklists, service records with their photos and files, reminders and settings) in one file. You choose where: Google Drive, OneDrive, the phone or anywhere else the phone offers.", 16f, Colours.MUTED))
        col.addView(ui.button("Back up to a file", Colours.ACCENT) {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip").putExtra(Intent.EXTRA_TITLE, Backup.fileName())
            try { startActivityForResult(i, BACKUP) } catch (_: Exception) { toast("No file picker found") }
        })
        col.addView(ui.button("Restore from a backup") {
            ui.confirm("Restore from a backup?", "Everything in the app now will be replaced by what's in the backup file.", "Choose file") {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                try { startActivityForResult(i, RESTORE) } catch (_: Exception) { toast("No file picker found") }
            }
        })

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
        Theme.barIcons(this)

        // Sent here from the first-use question
        if (savedInstanceState == null) when (intent.getStringExtra(EXTRA_DO)) {
            DO_CHOOSE_AUTO -> chooseAuto()
            DO_RESTORE -> {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                try { startActivityForResult(i, RESTORE) } catch (_: Exception) { toast("No file picker found") }
            }
        }

        // Opening Settings always checks for a new version straight away
        refreshUpdateStatus()
        Updater.checkInBackground(this, force = true) { refreshUpdateStatus() }
    }

    private fun chooseAuto() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/zip").putExtra(Intent.EXTRA_TITLE, AutoBackup.FILE_NAME)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try { startActivityForResult(i, CHOOSE_AUTO) } catch (_: Exception) { toast("No file picker found") }
    }

    private fun refreshAuto() {
        val t = autoStatus ?: return
        val where = AutoBackup.where(this)
        val err = AutoBackup.lastError(this)
        val last = AutoBackup.lastMs(this)
        t.text = when {
            AutoBackup.uri(this) == null -> "Not set up. Choose a place (Google Drive or OneDrive is best, so it survives a new phone) and the app keeps a backup up to date."
            else -> buildString {
                append(if (AutoBackup.days(this@SettingsActivity) == 0) "Off. " else "On. ")
                append("Kept in: $where")
                append("\nLast backup: " + if (last > 0) Units.date(last) + " " +
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(last)) else "not yet")
                if (err != null) append("\nLast try failed: $err")
                if (AutoBackup.onPhoneOnly(this@SettingsActivity)) append("\nThis is only on this phone, so it would be lost with the phone. Google Drive or OneDrive is safer.")
            }
        }
        t.setTextColor(if (err != null) Colours.RED else Colours.TEXT)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == CHOOSE_AUTO) {
            if (resultCode != RESULT_OK || uri == null) { if (intent.hasExtra(EXTRA_DO)) finish(); return }
            AutoBackup.chosen(this, uri)
            val busy = AlertDialog.Builder(this).setMessage("Making the first backup...").setCancelable(false).show()
            Thread {
                val err = AutoBackup.run(this)
                runOnUiThread {
                    busy.dismiss(); refreshAuto()
                    AlertDialog.Builder(this).setMessage(if (err == null) "Automatic backup is on. Kept in: ${AutoBackup.where(this)}" else "Couldn't back up: $err")
                        .setPositiveButton("OK") { _, _ -> if (intent.hasExtra(EXTRA_DO)) finish() }.show()
                }
            }.start()
            return
        }
        if (resultCode != RESULT_OK || uri == null) return
        val busy = AlertDialog.Builder(this).setMessage(if (requestCode == BACKUP) "Backing up..." else "Restoring...")
            .setCancelable(false).show()
        Thread {
            val result = try {
                if (requestCode == BACKUP) {
                    val n = Backup.write(this, uri)
                    "Backup saved, with $n photo${if (n == 1) "" else "s"} and file${if (n == 1) "" else "s"}."
                } else {
                    Backup.restore(this, uri)
                    null
                }
            } catch (e: Exception) {
                (if (requestCode == BACKUP) "Couldn't save the backup: " else "Couldn't restore: ") + (e.message ?: "unknown problem")
            }
            runOnUiThread {
                busy.dismiss()
                if (requestCode == RESTORE && result == null) {
                    // Start again from the home page with the restored data and settings
                    startActivity(Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    toast("Backup restored")
                } else {
                    AlertDialog.Builder(this).setMessage(result).setPositiveButton("OK", null).show()
                }
            }
        }.start()
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
