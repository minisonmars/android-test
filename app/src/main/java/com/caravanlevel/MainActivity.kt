package com.caravanlevel

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The home page: the chosen vehicle at the top, then a big tile for each section.
 * Settings can make the app open straight on a section, or where you left off.
 */
class MainActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var updateBanner: TextView
    private lateinit var body: LinearLayout

    companion object {
        const val EXTRA_SECTION = "section"
        const val EXTRA_VEHICLE = "vehicle"
    }

    private var wasDark = true

    override fun onCreate(savedInstanceState: Bundle?) {
        wasDark = Theme.apply(this)
        super.onCreate(savedInstanceState)
        ui = Ui(this)

        updateBanner = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            setBackgroundColor(Updater.RED)
            gravity = Gravity.CENTER
            setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14))
            visibility = View.GONE
            setOnClickListener { Updater.showDialog(this@MainActivity) { refreshUpdateBanner() } }
        }
        body = ui.column(16)
        val page = ui.column().apply {
            setBackgroundColor(Colours.BG)
            addView(updateBanner)
            addView(ScrollView(this@MainActivity).apply { addView(body) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        page.fitSystemBars()
        setContentView(page)
        Theme.barIcons(this)

        Reminders.schedule(this)
        Reminders.checkNow(this)
        Updater.checkInBackground(this) { refreshUpdateBanner() }

        if (savedInstanceState == null) openStartPage(intent, fromLauncher = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openStartPage(intent, fromLauncher = false)
    }

    /** A notification can ask for a page; otherwise follow the "Open the app on" setting. */
    private fun openStartPage(i: Intent, fromLauncher: Boolean) {
        i.getStringExtra(EXTRA_VEHICLE)?.let { AppPrefs.setActiveVehicle(this, it) }
        val asked = Section.of(i.getStringExtra(EXTRA_SECTION))
        val target = asked ?: if (fromLauncher) {
            when (val o = AppPrefs.openOn(this)) {
                AppPrefs.OPEN_LAST -> Section.of(AppPrefs.lastSection(this))
                else -> Section.of(o)
            }
        } else null
        if (target != null) startActivity(Intent(this, target.activity))
    }

    override fun onResume() {
        super.onResume()
        Theme.recreateIfChanged(this, wasDark)
        AppPrefs.setLastSection(this, AppPrefs.OPEN_HOME)
        refreshUpdateBanner()
        build()
        AutoBackup.runInBackground(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) AutoBackup.offer(this) // only once this page is really on screen
    }

    private fun build() {
        body.removeAllViews()
        val garage = GarageStore.load(this)
        val vehicle = GarageStore.active(this, garage)

        // Title with a small Settings cog at the top right
        val title = ui.text("Caravan Leveller", 26f, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val cog = IconView(this, Icon.SETTINGS, Colours.ACCENT).apply {
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            background = ui.rounded(Colours.CARD, 12)
            isClickable = true
            contentDescription = "Settings"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
            layoutParams = LinearLayout.LayoutParams(ui.dp(48), ui.dp(48))
        }
        body.addView(ui.row(title, cog))
        body.addView(VehicleBar.make(ui, garage, vehicle) {
            AppPrefs.setActiveVehicle(this, it.id)
            build()
        })

        // What needs attention for this vehicle
        summary(vehicle)?.let { (line, colour) ->
            body.addView(ui.text(line, 17f, colour).apply {
                setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(4))
                setOnClickListener { go(Section.REMINDERS) }
            })
        }

        val tiles = Section.values().map { s -> tile(s.label, s.icon) { go(s) } }
        tiles.chunked(2).forEach { pair ->
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                pair.forEach { addView(it) }
            })
        }
    }

    /** The most urgent reminder, if any is due within 30 days. */
    private fun summary(v: Vehicle): Pair<String, Int>? {
        val next = v.reminders.mapNotNull { r -> Due.days(v, r)?.let { r to it } }
            .minByOrNull { it.second } ?: return null
        val (r, days) = next
        return when {
            days < 0 -> "${r.title} is overdue (${Units.inDays(days)})" to Colours.RED
            days <= 30 -> "${r.title} is due ${Units.inDays(days)}" to Colours.AMBER
            else -> null
        }
    }

    private fun go(s: Section) = startActivity(Intent(this, s.activity))

    private fun tile(label: String, icon: Icon, onClick: () -> Unit): View {
        val col = ui.column(12).apply {
            gravity = Gravity.CENTER
            background = ui.rounded(Colours.TILE, 16)
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ui.dp(150), 1f).apply {
                setMargins(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            }
        }
        col.addView(IconView(this, icon).apply {
            layoutParams = LinearLayout.LayoutParams(ui.dp(64), ui.dp(64))
        })
        col.addView(ui.text(label, 19f, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, ui.dp(8), 0, 0)
        })
        return col
    }

    private fun refreshUpdateBanner() {
        if (Updater.available(this) != null) {
            updateBanner.text = Updater.statusLine(this) + ". Tap to see what's new."
            updateBanner.visibility = View.VISIBLE
        } else {
            updateBanner.visibility = View.GONE
        }
    }
}
