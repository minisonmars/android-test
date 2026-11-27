package com.caravanlevel

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan

/** What to raise or lower, as text and as speech. Shared by the Level page and Android Auto. */
object LevelText {
    /** Round to a sensible step for the chosen unit: 0.5 cm or 0.25 in. */
    private fun snap(valueInUnit: Float, inches: Boolean): Float {
        val step = if (inches) 0.25f else 0.5f
        return (valueInUnit / step).roundToInt() * step
    }

    class Result(val side: String, val front: String, val hint: String, val spoken: String)

    fun work(v: Vehicle, roll: Float, pitch: Float, inches: Boolean): Result {
        val levelSide = abs(roll) <= BubbleView.LEVEL_DEG
        val levelFront = abs(pitch) <= BubbleView.LEVEL_DEG
        val caravan = v.type == VehicleType.CARAVAN
        val unitWord = if (inches) "inches" else "cm"

        if (v.trackCm <= 0f || v.lengthForLevel <= 0f) {
            // No sizes yet: say which way and how many degrees instead of an amount
            val spoken = mutableListOf<String>()
            var sideText = ""
            var frontText = ""
            if (!levelSide) {
                val which = if (roll > 0) "left" else "right" // raise the low side
                sideText = "Raise ${which.uppercase()} ${String.format(Locale.UK, "%.1f", abs(roll))}°"
                spoken.add("Raise $which, ${Store.num(abs(roll), 1)} degrees")
            }
            if (!levelFront) {
                // always said from the front: rear low = front high
                val how = if (pitch > 0) "high" else "low"
                frontText = "Front ${how.uppercase()} ${String.format(Locale.UK, "%.1f", abs(pitch))}°"
                spoken.add("Front $how, ${Store.num(abs(pitch), 1)} degrees")
            }
            if (levelSide && levelFront) spoken.add("Level")
            return Result(sideText, frontText, "Tap Sizes and add the levelling sizes to get amounts in $unitWord", spoken.joinToString(". "))
        }

        val unitShort = if (inches) "in" else "cm"
        val unitSpoken = if (inches) "inches" else "centimetres"
        val perUnit = if (inches) Store.CM_PER_INCH else 1f

        var sideText = ""
        var frontText = ""
        val spoken = mutableListOf<String>()

        if (!levelSide) {
            val cm = v.trackCm * tan(Math.toRadians(abs(roll).toDouble())).toFloat()
            val amount = snap(cm / perUnit, inches)
            val which = if (roll > 0) "left" else "right" // raise the low side
            val what = if (caravan) "side" else "wheels"
            sideText = "Raise ${which.uppercase()} $what ${Store.fixed(amount)} $unitShort"
            spoken.add("Raise $which $what ${Store.num(amount)} $unitSpoken")
        }
        if (!levelFront) {
            val cm = v.lengthForLevel * tan(Math.toRadians(abs(pitch).toDouble())).toFloat()
            val amount = snap(cm / perUnit, inches)
            if (caravan) {
                // Jockey wheel: front high -> lower the front
                val verb = if (pitch > 0) "Lower" else "Raise"
                frontText = "$verb FRONT ${Store.fixed(amount)} $unitShort"
                spoken.add("$verb front ${Store.num(amount)} $unitSpoken")
            } else {
                // Ramps: you can only raise, so raise the low end's wheels
                val end = if (pitch > 0) "rear" else "front"
                frontText = "Raise ${end.uppercase()} wheels ${Store.fixed(amount)} $unitShort"
                spoken.add("Raise $end wheels ${Store.num(amount)} $unitSpoken")
            }
        }
        if (levelSide && levelFront) spoken.add("Level")

        return Result(sideText, frontText, "", spoken.joinToString(". "))
    }

}
