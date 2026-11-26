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
import android.widget.AbsListView
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

/**
 * From target API 35 Android draws apps edge to edge, under the status bar, navigation
 * bar and keyboard. Pad the screen's root view so nothing hides behind them.
 */
fun View.fitSystemBars() {
    setOnApplyWindowInsetsListener { v, insets ->
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
            v.setPadding(i.left, i.top, i.right, i.bottom)
        } else {
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        insets
    }
    requestApplyInsets()
}

/**
 * Colours used across the app. Light or dark depending on Settings > Theme (light, dark,
 * or follow the phone). Call [Theme.apply] at the start of each screen's onCreate.
 */
object Colours {
    var dark = true
        private set
    var BG = 0; var CARD = 0; var TILE = 0; var TEXT = 0; var MUTED = 0
    /** Buttons, icons and highlights: the colour chosen in Settings. */
    var ACCENT = Color.rgb(46, 158, 107)
        private set
    val RED = Color.rgb(198, 40, 40)
    val AMBER = Color.rgb(230, 140, 0)
    val GREEN = Color.rgb(46, 125, 50)
    var RED_CARD = 0; var AMBER_CARD = 0   // backgrounds for overdue / due-soon cards
    var LEVEL_GREEN = 0; var LEVEL_ORANGE = 0

    /** The colours offered in Settings: name and accent colour. */
    val choices = listOf(
        "Green" to Color.rgb(46, 158, 107),
        "Blue" to Color.rgb(30, 110, 200),
        "Teal" to Color.rgb(0, 137, 140),
        "Purple" to Color.rgb(123, 70, 180),
        "Pink" to Color.rgb(200, 60, 130),
        "Red" to Color.rgb(196, 48, 48),
        "Orange" to Color.rgb(222, 110, 20),
        "Brown" to Color.rgb(130, 90, 60),
        "Grey" to Color.rgb(100, 110, 120)
    )

    /** a mixed toward b: amount 0 = all a, 1 = all b. */
    private fun mix(a: Int, b: Int, amount: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * amount).toInt().coerceIn(0, 255)
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    init { set(true) }

    fun set(dark: Boolean, accent: Int = ACCENT) {
        this.dark = dark
        ACCENT = accent
        if (dark) {
            BG = mix(Color.rgb(18, 18, 18), accent, 0.04f); CARD = mix(Color.rgb(38, 44, 48), accent, 0.06f)
            TILE = mix(Color.rgb(30, 30, 30), accent, 0.28f)
            TEXT = Color.WHITE; MUTED = Color.rgb(176, 184, 188)
            RED_CARD = RED; AMBER_CARD = AMBER
            LEVEL_GREEN = Color.rgb(60, 220, 90); LEVEL_ORANGE = Color.rgb(255, 170, 0)
        } else {
            BG = mix(Color.rgb(246, 247, 248), accent, 0.04f); CARD = mix(Color.rgb(226, 231, 234), accent, 0.06f)
            TILE = mix(Color.WHITE, accent, 0.24f)
            TEXT = Color.rgb(25, 28, 30); MUTED = Color.rgb(84, 94, 100)
            RED_CARD = Color.rgb(255, 205, 210); AMBER_CARD = Color.rgb(255, 224, 178)
            LEVEL_GREEN = Color.rgb(30, 150, 60); LEVEL_ORANGE = Color.rgb(214, 120, 0)
        }
    }

    /** Text colour that reads well on a strongly coloured button. */
    fun on(background: Int) = if (background == ACCENT || background == RED || background == GREEN || background == AMBER) Color.WHITE else TEXT
}

/** Settings > Theme: light, dark, or follow the phone. */
object Theme {
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
    const val SYSTEM = "SYSTEM"

    fun choice(ctx: Context): String =
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getString("theme", SYSTEM) ?: SYSTEM

    fun setChoice(ctx: Context, v: String) =
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putString("theme", v).apply()

    /** The chosen colour's name (see Colours.choices). */
    fun colour(ctx: Context): String =
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getString("colour", "Green") ?: "Green"

    fun setColour(ctx: Context, v: String) =
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putString("colour", v).apply()

    private fun accent(ctx: Context) =
        Colours.choices.firstOrNull { it.first == colour(ctx) }?.second ?: Colours.choices[0].second

    /** What each open screen was drawn with, so it can redraw after a change in Settings. */
    private val drawnWith = java.util.WeakHashMap<Activity, String>()
    private fun signature(ctx: Context) = "${isDark(ctx)}/${colour(ctx)}"

    fun isDark(ctx: Context): Boolean = when (choice(ctx)) {
        LIGHT -> false
        DARK -> true
        else -> (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** Call before super.onCreate. Returns whether dark was used, to spot a change later. */
    fun apply(a: Activity): Boolean {
        val dark = isDark(a)
        Colours.set(dark, accent(a))
        drawnWith[a] = signature(a)
        a.setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        return dark
    }

    /** Status and navigation bar icons dark on a light page. Call after setContentView. */
    @Suppress("DEPRECATION")
    fun barIcons(a: Activity) {
        val dark = Colours.dark
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val flags = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            a.window.insetsController?.setSystemBarsAppearance(if (dark) 0 else flags, flags)
        } else if (android.os.Build.VERSION.SDK_INT >= 23) {
            var v = a.window.decorView.systemUiVisibility
            v = if (dark) v and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() else v or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            a.window.decorView.systemUiVisibility = v
        }
    }

    /** In onResume: if the theme setting changed while this screen was open, redraw it. */
    fun recreateIfChanged(a: Activity, wasDark: Boolean) {
        if (isDark(a) != wasDark || drawnWith[a] != signature(a)) a.recreate()
    }
}

/** Icons drawn in code, so they look the same on every phone. */
enum class Icon { LEVEL, CHECKLIST, VEHICLE, SERVICE, REMINDER, SETTINGS, CARAVAN, CAMPERVAN, MOTORHOME, CAR, COMPASS, CALIBRATE, BUBBLE, VIALS, VOICE, SIZES, DIARY, STAR, STAR_EMPTY, LOCATE, EDIT, PHONE, EMAIL, WEB, NAVIGATE }

class IconView(context: Context, var icon: Icon, var colour: Int = Colours.TEXT) : View(context) {
    private val painter = IconPainter()

    override fun onDraw(c: Canvas) {
        painter.draw(c, icon, colour, width.toFloat(), height.toFloat())
    }
}

/** An icon as a Drawable, for drop-down rows. */
class IconDrawable(private val icon: Icon, private val colour: Int, private val sizePx: Int) : android.graphics.drawable.Drawable() {
    private val painter = IconPainter()
    init { setBounds(0, 0, sizePx, sizePx) }
    override fun draw(c: Canvas) {
        c.save()
        c.translate(bounds.left.toFloat(), bounds.top.toFloat())
        painter.draw(c, icon, colour, bounds.width().toFloat(), bounds.height().toFloat())
        c.restore()
    }
    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** Draws the app's icons on a 100 x 100 grid. */
class IconPainter {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun draw(c: Canvas, icon: Icon, colour: Int, width: Float, height: Float) {
        val s = min(width, height)
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
            Icon.EDIT -> {
                // A pencil
                c.drawPath(Path().apply {
                    moveTo(22f, 78f); lineTo(26f, 60f); lineTo(66f, 20f); lineTo(80f, 34f); lineTo(40f, 74f); close()
                }, stroke)
                c.drawLine(58f, 28f, 72f, 42f, stroke)
                c.drawLine(14f, 88f, 86f, 88f, stroke)
            }
            Icon.PHONE -> {
                // An old-style handset
                c.drawPath(Path().apply {
                    moveTo(24f, 14f); lineTo(38f, 14f); lineTo(44f, 32f); lineTo(34f, 40f)
                    quadTo(44f, 60f, 60f, 66f); lineTo(68f, 56f); lineTo(86f, 62f); lineTo(86f, 76f)
                    quadTo(84f, 88f, 70f, 86f); quadTo(18f, 76f, 14f, 30f); quadTo(14f, 16f, 24f, 14f); close()
                }, fill)
            }
            Icon.EMAIL -> {
                // An envelope
                c.drawRoundRect(RectF(10f, 24f, 90f, 76f), 6f, 6f, stroke)
                c.drawPath(Path().apply { moveTo(12f, 28f); lineTo(50f, 54f); lineTo(88f, 28f) }, stroke)
            }
            Icon.WEB -> {
                // A globe
                c.drawCircle(50f, 50f, 38f, stroke)
                c.drawOval(RectF(32f, 12f, 68f, 88f), stroke)
                c.drawLine(12f, 50f, 88f, 50f, stroke)
                c.drawLine(18f, 30f, 82f, 30f, stroke.apply { strokeWidth = 5f })
                c.drawLine(18f, 70f, 82f, 70f, stroke)
                stroke.strokeWidth = 7f
            }
            Icon.NAVIGATE -> {
                // A navigation arrow
                c.drawPath(Path().apply {
                    moveTo(86f, 14f); lineTo(14f, 46f); lineTo(46f, 54f); lineTo(54f, 86f); close()
                }, fill)
            }
            Icon.LOCATE -> {
                // "Where am I": a ring with a dot and four short lines
                c.drawCircle(50f, 50f, 28f, stroke)
                c.drawCircle(50f, 50f, 11f, fill)
                c.drawLine(50f, 6f, 50f, 22f, stroke); c.drawLine(50f, 78f, 50f, 94f, stroke)
                c.drawLine(6f, 50f, 22f, 50f, stroke); c.drawLine(78f, 50f, 94f, 50f, stroke)
            }
            Icon.DIARY -> {
                // An open book with a map pin on the right page
                c.drawPath(Path().apply {
                    moveTo(50f, 26f); quadTo(30f, 16f, 8f, 20f); lineTo(8f, 80f); quadTo(30f, 76f, 50f, 86f)
                    quadTo(70f, 76f, 92f, 80f); lineTo(92f, 20f); quadTo(70f, 16f, 50f, 26f); close()
                }, stroke)
                c.drawLine(50f, 26f, 50f, 86f, stroke)
                c.drawLine(18f, 40f, 40f, 42f, stroke.apply { strokeWidth = 5f })
                c.drawLine(18f, 54f, 40f, 56f, stroke)
                stroke.strokeWidth = 7f
                c.drawCircle(71f, 44f, 9f, fill)
                c.drawPath(Path().apply { moveTo(63f, 48f); lineTo(79f, 48f); lineTo(71f, 66f); close() }, fill)
            }
            Icon.STAR, Icon.STAR_EMPTY -> {
                val star = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 44f else 19f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = 50f + r * cos(a).toFloat(); val y = 54f + r * sin(a).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                stroke.strokeWidth = 6f
                if (icon == Icon.STAR) c.drawPath(star, fill)
                c.drawPath(star, stroke)
                stroke.strokeWidth = 7f
            }
            Icon.CALIBRATE -> {
                // A target: ring, cross hairs and a centre dot
                c.drawCircle(50f, 50f, 30f, stroke)
                c.drawLine(50f, 8f, 50f, 30f, stroke); c.drawLine(50f, 70f, 50f, 92f, stroke)
                c.drawLine(8f, 50f, 30f, 50f, stroke); c.drawLine(70f, 50f, 92f, 50f, stroke)
                c.drawCircle(50f, 50f, 7f, fill)
            }
            Icon.BUBBLE -> {
                // A round bubble level
                c.drawCircle(50f, 50f, 38f, stroke)
                stroke.strokeWidth = 4f
                c.drawCircle(50f, 50f, 16f, stroke)
                stroke.strokeWidth = 7f
                c.drawCircle(58f, 44f, 10f, fill)
            }
            Icon.VIALS -> {
                // Two vials: one across, one upright
                c.drawRoundRect(RectF(8f, 10f, 92f, 36f), 13f, 13f, stroke)
                c.drawCircle(56f, 23f, 6f, fill)
                c.drawRoundRect(RectF(37f, 46f, 63f, 94f), 13f, 13f, stroke)
                c.drawCircle(50f, 66f, 6f, fill)
            }
            Icon.VOICE -> {
                // A loudspeaker with sound waves
                c.drawPath(Path().apply {
                    moveTo(10f, 38f); lineTo(26f, 38f); lineTo(46f, 20f); lineTo(46f, 80f); lineTo(26f, 62f); lineTo(10f, 62f); close()
                }, stroke)
                c.drawArc(RectF(42f, 32f, 70f, 68f), -50f, 100f, false, stroke)
                c.drawArc(RectF(46f, 18f, 90f, 82f), -50f, 100f, false, stroke)
            }
            Icon.SIZES -> {
                // A ruler with marks
                c.drawRoundRect(RectF(6f, 32f, 94f, 68f), 6f, 6f, stroke)
                stroke.strokeWidth = 5f
                for (i in 0..6) {
                    val x = 18f + i * 11f
                    c.drawLine(x, 32f, x, if (i % 2 == 0) 52f else 44f, stroke)
                }
                stroke.strokeWidth = 7f
            }
            Icon.COMPASS -> {
                // A ring with a needle: the north half filled
                c.drawCircle(50f, 50f, 40f, stroke)
                c.drawPath(Path().apply { moveTo(50f, 18f); lineTo(60f, 50f); lineTo(40f, 50f); close() }, fill)
                c.drawPath(Path().apply { moveTo(50f, 82f); lineTo(60f, 50f); lineTo(40f, 50f); close() }, stroke)
            }
            Icon.CAMPERVAN -> {
                // A van from the side, facing right: sloping bonnet, two windows, two wheels
                val body = Path().apply {
                    moveTo(8f, 70f); lineTo(8f, 30f); quadTo(8f, 24f, 14f, 24f)
                    lineTo(64f, 24f); lineTo(78f, 42f); lineTo(88f, 46f)
                    quadTo(94f, 48f, 94f, 56f); lineTo(94f, 70f); close()
                }
                c.drawPath(body, stroke)
                c.drawRoundRect(RectF(16f, 32f, 36f, 46f), 3f, 3f, stroke)
                c.drawLine(44f, 32f, 62f, 32f, stroke)
                c.drawLine(62f, 32f, 70f, 44f, stroke)
                c.drawCircle(26f, 74f, 9f, stroke)
                c.drawCircle(76f, 74f, 9f, stroke)
            }
            Icon.MOTORHOME -> {
                // A coachbuilt motorhome: tall box, bed over the cab, lower cab at the front
                c.drawRoundRect(RectF(6f, 14f, 70f, 70f), 8f, 8f, stroke)
                c.drawRoundRect(RectF(66f, 14f, 90f, 30f), 6f, 6f, stroke)
                val cab = Path().apply {
                    moveTo(70f, 36f); lineTo(82f, 36f); lineTo(94f, 52f); lineTo(94f, 70f); lineTo(70f, 70f)
                }
                c.drawPath(cab, stroke)
                c.drawRoundRect(RectF(14f, 24f, 32f, 38f), 3f, 3f, stroke)
                c.drawRect(RectF(44f, 36f, 56f, 70f), stroke)
                c.drawCircle(22f, 76f, 9f, stroke)
                c.drawCircle(80f, 76f, 9f, stroke)
            }
            Icon.CAR -> {
                // A car facing right with a tow ball at the back
                val body = Path().apply {
                    moveTo(10f, 66f); lineTo(10f, 54f); quadTo(10f, 48f, 18f, 47f)
                    lineTo(28f, 45f); lineTo(40f, 30f); quadTo(42f, 28f, 48f, 28f)
                    lineTo(66f, 28f); quadTo(70f, 28f, 72f, 32f); lineTo(80f, 45f)
                    lineTo(88f, 47f); quadTo(95f, 49f, 94f, 57f); lineTo(94f, 66f); close()
                }
                c.drawPath(body, stroke)
                c.drawLine(10f, 62f, 3f, 62f, stroke)
                c.drawCircle(3f, 56f, 4f, fill)
                c.drawCircle(30f, 70f, 9f, stroke)
                c.drawCircle(74f, 70f, 9f, stroke)
            }
            Icon.CARAVAN, Icon.VEHICLE -> {
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
        setTextColor(Colours.on(colour))
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
    fun spinner(items: List<String>, selected: Int, icons: List<Icon>? = null, onPick: (Int) -> Unit) = Spinner(ctx).apply {
        // Our own rows, so big text is never cut off (the built-in rows have a fixed height)
        val ad = object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items) {
            private fun row(position: Int, convertView: View?, padV: Int, colour: Int, oneLine: Boolean): View =
                ((convertView as? TextView) ?: TextView(ctx)).apply {
                    text = getItem(position)
                    // The chosen item stays on one line; the open list can wrap long items
                    isSingleLine = oneLine
                    ellipsize = if (oneLine) android.text.TextUtils.TruncateAt.END else null
                    val ic = icons?.getOrNull(position)
                    setCompoundDrawablesRelative(ic?.let { IconDrawable(it, Colours.ACCENT, dp(32)) }, null, null, null)
                    compoundDrawablePadding = dp(12)
                    textSize = 20f
                    setTextColor(colour)
                    minHeight = dp(52)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(padV), dp(16), dp(padV))
                    layoutParams = AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                row(position, convertView, 10, Colours.TEXT, true)

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                row(position, convertView, 14, Colours.TEXT, false)
        }
        adapter = ad
        setSelection(selected.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var current = selected.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos == current) return // the initial selection, or no change
                current = pos
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
    /** Content that stays put above the scrolling part (empty on most pages). */
    lateinit var fixed: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var barHolder: LinearLayout

    abstract val section: Section

    /** Fill [content] for the current [vehicle]. Called again whenever it changes. */
    abstract fun build()

    /** Copy anything typed on screen into [vehicle] before saving or rebuilding. */
    open fun commit() {}

    private var wasDark = true

    override fun onCreate(savedInstanceState: Bundle?) {
        wasDark = Theme.apply(this)
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        val page = ui.column().apply { setBackgroundColor(Colours.BG) }
        page.addView(ui.text(section.label, 24f, bold = true).apply {
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), 0)
        })
        barHolder = ui.column().apply { setPadding(ui.dp(12), 0, ui.dp(12), 0) }
        page.addView(barHolder)
        fixed = ui.column().apply { setPadding(ui.dp(16), 0, ui.dp(16), 0) }
        page.addView(fixed)
        content = ui.column().apply { setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(32)) }
        scroll = ScrollView(this).apply { addView(content); isFillViewport = true }
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.fitSystemBars()
        setContentView(page)
        Theme.barIcons(this)
    }

    override fun onResume() {
        super.onResume()
        Theme.recreateIfChanged(this, wasDark)
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
        fixed.removeAllViews()
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
        val spin = ui.spinner(names, garage.vehicles.indexOf(current), garage.vehicles.map { it.type.icon }) {
            onPick(garage.vehicles[it])
        }
        spin.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        return ui.row(spin).apply {
            background = ui.rounded(Colours.CARD)
            setPadding(ui.dp(10), ui.dp(2), ui.dp(4), ui.dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, ui.dp(8), 0, ui.dp(4)) }
        }
    }
}
