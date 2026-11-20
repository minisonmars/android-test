package com.caravanlevel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One caravan. Sizes are always stored in centimetres. */
class Profile(
    var name: String,
    var trackCm: Float,   // wheel centre to wheel centre
    var hitchCm: Float,   // hitch coupling to axle centre (midpoint for twin axle)
    var rollOffset: Float = 0f,
    var pitchOffset: Float = 0f
)

class State(val profiles: MutableList<Profile>, var active: Int, var inches: Boolean)

object Store {
    private const val PREFS = "level"
    const val CM_PER_INCH = 2.54f

    fun load(ctx: Context): State {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = mutableListOf<Profile>()
        val json = p.getString("profiles", null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Profile(
                            o.optString("name", "Caravan"),
                            o.optDouble("track", 0.0).toFloat(),
                            o.optDouble("hitch", 0.0).toFloat(),
                            o.optDouble("roll", 0.0).toFloat(),
                            o.optDouble("pitch", 0.0).toFloat()
                        )
                    )
                }
            } catch (e: Exception) {
                list.clear()
            }
        }
        if (list.isEmpty()) {
            // Version 0.1 stored a single calibration; carry it over
            list.add(
                Profile(
                    "My caravan", 0f, 0f,
                    p.getFloat("rollOffset", 0f), p.getFloat("pitchOffset", 0f)
                )
            )
        }
        val active = p.getInt("active", 0).coerceIn(0, list.size - 1)
        return State(list, active, p.getBoolean("inches", false))
    }

    fun save(ctx: Context, s: State) {
        val arr = JSONArray()
        for (pr in s.profiles) {
            arr.put(
                JSONObject()
                    .put("name", pr.name)
                    .put("track", pr.trackCm.toDouble())
                    .put("hitch", pr.hitchCm.toDouble())
                    .put("roll", pr.rollOffset.toDouble())
                    .put("pitch", pr.pitchOffset.toDouble())
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("profiles", arr.toString())
            .putInt("active", s.active)
            .putBoolean("inches", s.inches)
            .apply()
    }

    /** Always two decimals: 4 -> "4.00", 4.5 -> "4.50". */
    fun fixed(v: Float): String = String.format(Locale.UK, "%.2f", v)

    /** Format a number, trimming trailing zeros: 4.50 -> "4.5", 4.00 -> "4". */
    fun num(v: Float, decimals: Int = 2): String {
        val s = String.format(Locale.UK, "%.${decimals}f", v)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }
}
