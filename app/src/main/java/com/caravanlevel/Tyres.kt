package com.caravanlevel

import java.util.Calendar

/** Tyre helpers: pressure units and age from the DOT date code (week + year, e.g. "2321"). */
object Tyres {
    const val PSI_PER_BAR = 14.5038f
    const val REPLACE_YEARS = 5f

    /** Age in years from a 4-digit DOT code, or null if it doesn't look like one. */
    fun ageYears(code: String): Float? {
        val c = code.filter { it.isDigit() }
        if (c.length != 4) return null
        val week = c.substring(0, 2).toInt()
        val year = 2000 + c.substring(2).toInt()
        if (week !in 1..53) return null
        val made = Calendar.getInstance().apply {
            clear(); set(Calendar.YEAR, year); set(Calendar.WEEK_OF_YEAR, week); set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }.timeInMillis
        val years = (System.currentTimeMillis() - made) / (365.25f * 24 * 3600 * 1000)
        return if (years < -0.1f) null else maxOf(0f, years)
    }

    fun oldAdvice(t: VehicleType) = if (t == VehicleType.CAR)
        "Over 5 years old: have them checked for cracking every year."
    else
        "Over 5 years old: caravan and motorhome tyre advice is to replace them at 5 years, even with good tread."
}
