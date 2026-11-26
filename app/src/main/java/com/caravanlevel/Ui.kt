package com.caravanlevel

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Colours used across the app (dark background, like the level screen). */
object Colours {
    val BG = Color.rgb(18, 18, 18)
    val CARD = Color.rgb(38, 44, 48)
    val TILE = Color.rgb(36, 60, 52)
    val ACCENT = Color.rgb(46, 158, 107)
    val TEXT = Color.WHITE
    val MUTED = Color.rgb(176, 184, 188)
    val RED = Color.rgb(198, 40, 40)
    val AMBER = Color.rgb(230, 140, 0)
    val GREEN = Color.rgb(46, 125, 50)
}

/** Icons drawn in code, so they look the same on every phone. */
enum class Icon { LEVEL, CHECKLIST, VEHICLE, SERVICE, REMINDER, SETTINGS }

class IconView(context: Context, var icon: Icon, var colour: Int = Color.WHITE) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        c.save()
        c.translate((width - s) / 2f, (height - s) / 2f)
        c.scale(s / 100f, s / 100f) // draw on a 100 x 100 grid
        stroke.color = colour
        fill.color = colour
        stroke.strokeWidth = 7f
        when (icon) {
            Icon.LEVEL -> {
                c.drawRoundRect(RectF(8f, 34f, 92f, 66f), 16f, 16f, stroke)
                c.drawLine(38f, 36f, 38f, 64f, stroke)
                c.drawLine(62f, 36f, 62f, 64f, stroke)
                c.drawCircle(50f, 50f, 8f, fill)
            }
            Icon.CHECKLIST -> {
                c.drawRoundRect(RectF(18f, 12f, 82f, 92f), 8f, 8f, stroke)
                for (i in 0..2) {
                    val y = 34f + i * 20f
                    val tick = Path().apply { moveTo(28f, y); lineTo(33f, y + 5f); lineTo(42f, y - 5f) }
                    c.drawPath(tick, stroke)
                    c.drawLine(50f, y, 72f, y, stroke)
                }
            }
            Icon.VEHICLE -> {
                // A caravan from the side: body, window, door, wheel and A-frame
                c.drawRoundRect(RectF(10f, 26f, 80f, 70f), 12f, 12f, stroke)
                c.drawRoundRect(RectF(20f, 36f, 42f, 50f), 3f, 3f, stroke)
                c.drawRect(RectF(56f, 38f, 68f, 70f), stroke)
                c.drawLine(80f, 66f, 94f, 72f, stroke)
                c.drawCircle(38f, 76f, 9f, stroke)
            }
            Icon.SERVICE -> {
                // A spanner: open jaw at the top right, handle down to the bottom left
                c.drawLine(24f, 76f, 58f, 42f, stroke.apply { strokeWidth = 14f })
                stroke.strokeWidth = 9f
                c.drawArc(RectF(52f, 14f, 86f, 48f), 135f, 270f, false, stroke)
            }
            Icon.REMINDER -> {
                // A bell
                val bell = Path().apply {
                    moveTo(22f, 72f)
                    quadTo(30f, 64f, 30f, 46f)
                    quadTo(30f, 20f, 50f, 20f)
                    quadTo(70f, 20f, 70f, 46f)
                    quadTo(70f, 64f, 78f, 72f)
                    close()
                }
                c.drawPath(bell, stroke)
                c.drawLine(50f, 12f, 50f, 20f, stroke)
                c.drawCircle(50f, 82f, 7f, fill)
            }
            Icon.SETTINGS -> {
                // A cog: ring with eight teeth
                stroke.strokeWidth = 10f
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    c.drawLine(
                        50f + 26f * cos(a).toFloat(), 50f + 26f * sin(a).toFloat(),
                        50f + 40f * cos(a).toFloat(), 50f + 40f * sin(a).toFloat(), stroke
                    )
                }
                c.drawCircle(50f, 50f, 24f, stroke)
                stroke.strokeWidth = 7f
                c.drawCircle(50f, 50f, 9f, stroke)
            }
        }
        c.restore()
    }
}

/** Small helpers for building screens in code. */
class Ui(val ctx: Activity) {
    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(colour: Int, radiusDp: Int = 12) = GradientDrawable().apply {
        setColor(colour)
        cornerRadius = dp(radiusDp).toFloat()
    }

    fun text(s: String, size: Float = 18f, colour: Int = Colours.TEXT, bold: Boolean = false) =
        TextView(ctx).apply {
            text = s
            textSize = size
            setTextColor(colour)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun heading(s: String) = text(s, 22f, bold = true).apply { setPadding(0, dp(20), 0, dp(6)) }

    fun label(s: String) = text(s, 16f, Colours.MUTED).apply { setPadding(0, dp(12), 0, dp(2)) }

    fun field(value: String = "", hint: String = "", type: Int = InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) = EditText(ctx).apply {
        setText(value)
        this.hint = hint
        inputType = type
        textSize = 19f
        setTextColor(Colours.TEXT)
        setHintTextColor(Colours.MUTED)
    }

    fun numberField(value: String = "", hint: String = "") =
        field(value, hint, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)

    fun wholeNumberField(value: String = "", hint: String = "") =
        field(value, hint, InputType.TYPE_CLASS_NUMBER)

    fun multiline(value: String = "", hint: String = "") = field(
        value, hint,
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    ).apply { minLines = 3; gravity = Gravity.TOP }

    /** A big full-width button. */
    fun button(label: String, colour: Int = Colours.CARD, onClick: () -> Unit) = Button(ctx).apply {
        text = label
        isAllCaps = false
        textSize = 18f
        setTextColor(Colours.TEXT)
        background = rounded(colour)
        minHeight = dp(56)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, dp(6)) }
        setOnClickListener { onClick() }
    }

    /** A button that shares a row with others. */
    fun rowButton(label: String, colour: Int = Colours.CARD, onClick: () -> Unit) =
        button(label, colour, onClick).apply {
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(3), dp(4), dp(3), dp(4)) }
        }

    fun row(vararg views: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it) }
    }

    fun column(padding: Int = 0) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    /** A rounded box to group things. */
    fun card(colour: Int = Colours.CARD) = column(14).apply {
        background = rounded(colour)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, dp(6)) }
    }

    /** A drop-down list with big text. */
    fun spinner(items: List<String>, selected: Int, onPick: (Int) -> Unit) = Spinner(ctx).apply {
        val ad = object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    textSize = 20f; setTextColor(Colours.TEXT)
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    textSize = 20f
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }
        }
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        adapter = ad
        setSelection(selected.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var first = true
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return } // ignore the initial selection
                onPick(pos)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    fun confirm(title: String, message: String, yes: String, onYes: () -> Unit) {
        AlertDialog.Builder(ctx).setTitle(title).setMessage(message)
            .setPositiveButton(yes) { _, _ -> onYes() }
            .setNegativeButton("Cancel", null).show()
    }

    fun pickDate(startMs: Long?, onPick: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = startMs ?: System.currentTimeMillis() }
        DatePickerDialog(ctx, { _, y, m, d ->
            val pick = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
            onPick(pick.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
}

/**
 * Base for every section page: the chosen vehicle at the top (pick another from the
 * drop-down), then the page's own content in a scrolling column.
 */
abstract class Page : Activity() {
    lateinit var ui: Ui
    lateinit var garage: Garage
    lateinit var vehicle: Vehicle
    lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var barHolder: LinearLayout

    abstract val section: Section

    /** Fill [content] for the current [vehicle]. Called again whenever it changes. */
    abstract fun build()

    /** Copy anything typed on screen into [vehicle] before saving or rebuilding. */
    open fun commit() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        val page = ui.column().apply { setBackgroundColor(Colours.BG) }
        page.addView(ui.text(section.label, 24f, bold = true).apply {
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), 0)
        })
        barHolder = ui.column().apply { setPadding(ui.dp(12), 0, ui.dp(12), 0) }
        page.addView(barHolder)
        content = ui.column().apply { setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(32)) }
        scroll = ScrollView(this).apply { addView(content); isFillViewport = true }
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }

    override fun onResume() {
        super.onResume()
        AppPrefs.setLastSection(this, section.name)
        reload()
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    /** Load from storage and draw everything again. */
    fun reload() {
        garage = GarageStore.load(this)
        vehicle = GarageStore.active(this, garage)
        barHolder.removeAllViews()
        barHolder.addView(VehicleBar.make(ui, garage, vehicle) { picked ->
            save()
            AppPrefs.setActiveVehicle(this, picked.id)
            reload()
        })
        rebuild()
    }

    /** Redraw the content, keeping the scroll position. */
    fun rebuild() {
        val y = scroll.scrollY
        content.removeAllViews()
        build()
        scroll.post { scroll.scrollTo(0, y) }
    }

    fun save() {
        if (!::vehicle.isInitialized) return
        commit()
        GarageStore.save(this, garage)
    }

    /** Save what's typed, then redraw (after adding or removing something). */
    fun saveAndRebuild() {
        save()
        rebuild()
    }
}

/** The vehicle picker shown at the top of every page. */
object VehicleBar {
    fun make(ui: Ui, garage: Garage, current: Vehicle, onPick: (Vehicle) -> Unit): View {
        val names = garage.vehicles.map { "${it.name}  (${it.type.label})" }
        val icon = IconView(ui.ctx, Icon.VEHICLE, Colours.ACCENT).apply {
            layoutParams = LinearLayout.LayoutParams(ui.dp(40), ui.dp(40))
        }
        val spin = ui.spinner(names, garage.vehicles.indexOf(current)) { onPick(garage.vehicles[it]) }
        spin.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        return ui.row(icon, spin).apply {
            background = ui.rounded(Colours.CARD)
            setPadding(ui.dp(10), ui.dp(2), ui.dp(4), ui.dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, ui.dp(8), 0, ui.dp(4)) }
        }
    }
}
