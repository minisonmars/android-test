package com.caravanlevel

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import java.io.File
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var state: State
    private var selected = 0

    private lateinit var spinner: Spinner
    private lateinit var nameField: EditText
    private lateinit var trackField: EditText
    private lateinit var hitchField: EditText
    private lateinit var trackLabel: TextView
    private lateinit var hitchLabel: TextView
    private lateinit var inchSwitch: Switch
    private lateinit var updateStatus: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = Store.load(this)
        selected = state.active

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }

        col.addView(TextView(this).apply {
            text = "Caravan profiles"
            textSize = 22f
        })

        spinner = Spinner(this)
        col.addView(spinner)

        val profileButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        profileButtons.addView(rowButton("New profile", true) {
            commitFields()
            state.profiles.add(Profile("Caravan ${state.profiles.size + 1}", 0f, 0f))
            selected = state.profiles.size - 1
            refreshSpinner()
            loadFields()
        })
        profileButtons.addView(rowButton("Delete profile", true) {
            if (state.profiles.size <= 1) {
                Toast.makeText(this, "You need at least one profile", Toast.LENGTH_SHORT).show()
            } else {
                state.profiles.removeAt(selected)
                selected = (selected - 1).coerceAtLeast(0)
                refreshSpinner()
                loadFields()
            }
        })
        col.addView(profileButtons)

        col.addView(label("Name"))
        nameField = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT }
        col.addView(nameField)

        trackLabel = label("")
        col.addView(trackLabel)
        trackField = numberField()
        col.addView(trackField)

        hitchLabel = label("")
        col.addView(hitchLabel)
        hitchField = numberField()
        col.addView(hitchField)

        inchSwitch = Switch(this).apply {
            text = "Use inches instead of cm"
            isChecked = state.inches
        }
        col.addView(inchSwitch)

        col.addView(rowButton("Clear calibration for this profile") {
            val p = state.profiles[selected]
            p.rollOffset = 0f
            p.pitchOffset = 0f
            Toast.makeText(this, "Calibration cleared (tap Save to keep)", Toast.LENGTH_SHORT).show()
        })
        // App updates, checked against the newest GitHub release
        col.addView(label("App updates"))
        updateStatus = TextView(this).apply {
            text = "Version ${Updater.installedVersion(this@SettingsActivity)}"
            setPadding(0, dp(4), 0, dp(4))
        }
        col.addView(updateStatus)
        col.addView(rowButton("Check for updates") { checkForUpdates() })

        col.addView(rowButton("Save") {
            commitFields()
            state.active = selected
            Store.save(this, state)
            finish()
        })

        setContentView(ScrollView(this).apply { addView(col) })

        refreshSpinner()
        loadFields()

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == selected) return
                commitFields()
                selected = position
                loadFields()
                refreshSpinner()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        inchSwitch.setOnCheckedChangeListener { _, checked ->
            commitFields() // read the boxes in the old unit first
            state.inches = checked
            loadFields()
        }
    }

    private fun checkForUpdates() {
        updateStatus.text = "Checking..."
        Thread {
            try {
                val latest = Updater.fetchLatest()
                val current = Updater.installedVersion(this)
                runOnUiThread {
                    if (latest == null) updateStatus.text = "No update found (you have $current)"
                    else if (Updater.isNewer(latest.version, current)) offerUpdate(latest, current)
                    else updateStatus.text = "You have the latest version ($current)"
                }
            } catch (e: Exception) {
                runOnUiThread { updateStatus.text = "Couldn't check: ${e.message}" }
            }
        }.start()
    }

    private fun offerUpdate(latest: Updater.Release, current: String) {
        updateStatus.text = "Version ${latest.version} is available (you have $current)"
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage("Version ${latest.version} is ready. Download and install it now?")
            .setPositiveButton("Install") { _, _ -> startUpdate(latest) }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun startUpdate(release: Updater.Release) {
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Allow updates")
                .setMessage(
                    "Android needs you to allow this app to install updates. Tap Open settings, switch on " +
                        "\"Allow from this source\", then come back and tap Check for updates again."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(
                        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val file = File(cacheDir, "update.apk")
        updateStatus.text = "Downloading..."
        Thread {
            try {
                Updater.download(release.apkUrl, file) { percent ->
                    runOnUiThread { updateStatus.text = "Downloading... $percent%" }
                }
                runOnUiThread { updateStatus.text = "Installing... confirm on the screen that appears" }
                Updater.install(this, file)
            } catch (e: Exception) {
                runOnUiThread { updateStatus.text = "Update failed: ${e.message}" }
            }
        }.start()
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        setPadding(0, dp(16), 0, 0)
    }

    private fun numberField() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun rowButton(label: String, inRow: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        layoutParams = if (inRow) {
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setOnClickListener { onClick() }
    }

    private fun refreshSpinner() {
        val names = state.profiles.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(selected)
    }

    /** Show the selected profile in the boxes, in the chosen unit. */
    private fun loadFields() {
        val p = state.profiles[selected]
        val unit = if (state.inches) "inches" else "cm"
        val per = if (state.inches) Store.CM_PER_INCH else 1f
        trackLabel.text = "Track width: wheel centre to wheel centre ($unit)"
        hitchLabel.text = "Hitch to axle: coupling to axle centre, midpoint for twin axle ($unit)"
        nameField.setText(p.name)
        trackField.setText(if (p.trackCm > 0f) Store.num(p.trackCm / per, 1) else "")
        hitchField.setText(if (p.hitchCm > 0f) Store.num(p.hitchCm / per, 1) else "")
    }

    /** Copy what is in the boxes into the selected profile (always stored in cm). */
    private fun commitFields() {
        val p = state.profiles[selected]
        val per = if (state.inches) Store.CM_PER_INCH else 1f
        val name = nameField.text.toString().trim()
        if (name.isNotEmpty()) p.name = name
        p.trackCm = parse(trackField.text.toString()) * per
        p.hitchCm = parse(hitchField.text.toString()) * per
    }

    private fun parse(s: String): Float = s.replace(',', '.').toFloatOrNull() ?: 0f
}
