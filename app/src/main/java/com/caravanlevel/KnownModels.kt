package com.caravanlevel

import android.content.Context

/**
 * Sizes and weights the makers publish for their caravans (assets/caravan_models.csv),
 * so a new vehicle can be filled in quickly. Approximate: always check the vehicle's
 * own plate and handbook.
 */
class KnownModel(
    val make: String, val year: Int, val range: String, val model: String,
    val lengthM: Float, val internalLengthM: Float, val widthM: Float, val heightM: Float,
    val mroKg: Int, val mtplmKg: Int, val mtplmUpgradeKg: Int, val axles: Int, val source: String
) {
    val name get() = "$range $model"
}

object KnownModels {
    private var cache: List<KnownModel>? = null

    fun all(ctx: Context): List<KnownModel> = cache ?: try {
        ctx.assets.open("caravan_models.csv").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val c = line.split(',')
                if (c.size < 13) return@mapNotNull null
                fun f(i: Int) = c[i].trim().toFloatOrNull() ?: 0f
                fun n(i: Int) = c[i].trim().toIntOrNull() ?: 0
                KnownModel(c[0].trim(), n(1), c[2].trim(), c[3].trim(), f(4), f(5), f(6), f(7),
                    n(8), n(9), n(10), n(11), c.drop(12).joinToString(",").trim())
            }
    } catch (_: Exception) {
        emptyList()
    }.also { cache = it }

    fun makes(ctx: Context) = all(ctx).map { it.make }.distinct().sorted()
    fun years(ctx: Context, make: String) = all(ctx).filter { it.make == make }.map { it.year }.distinct().sorted()
    fun models(ctx: Context, make: String, year: Int) =
        all(ctx).filter { it.make == make && it.year == year }.sortedWith(compareBy({ it.range }, { it.model }))
}
