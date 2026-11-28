package com.caravanlevel

import android.content.Context

/**
 * Sizes and weights the makers publish (assets/caravan_models.csv and
 * assets/motorhome_models.csv), so a vehicle can be filled in quickly. Approximate: always
 * check the vehicle's own plate and handbook.
 */
class KnownModel(
    val type: VehicleType, val make: String, val year: Int, val range: String, val model: String,
    val lengthM: Float, val internalLengthM: Float, val widthM: Float, val heightM: Float,
    val mroKg: Int, val mtplmKg: Int, val mtplmUpgradeKg: Int, val axles: Int, val source: String,
    val wheelbaseM: Float = 0f, val baseVehicle: String = ""
) {
    val name get() = "$range $model".trim()
}

object KnownModels {
    private var cache: List<KnownModel>? = null

    private fun lines(ctx: Context, file: String) = try {
        ctx.assets.open(file).bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }
    } catch (_: Exception) { emptyList() }

    fun all(ctx: Context): List<KnownModel> = cache ?: run {
        val caravans = lines(ctx, "caravan_models.csv").mapNotNull { line ->
            val c = line.split(',')
            if (c.size < 13) return@mapNotNull null
            fun f(i: Int) = c[i].trim().toFloatOrNull() ?: 0f
            fun n(i: Int) = c[i].trim().toIntOrNull() ?: 0
            KnownModel(VehicleType.CARAVAN, c[0].trim(), n(1), c[2].trim(), c[3].trim(), f(4), f(5), f(6), f(7),
                n(8), n(9), n(10), n(11), c.drop(12).joinToString(",").trim())
        }
        // make,year,type,range,model,length_m,width_m,height_m,wheelbase_m,mro_kg,mtplm_kg,base_vehicle,source
        val vans = lines(ctx, "motorhome_models.csv").mapNotNull { line ->
            val c = line.split(',')
            if (c.size < 13) return@mapNotNull null
            fun f(i: Int) = c[i].trim().toFloatOrNull() ?: 0f
            fun n(i: Int) = c[i].trim().toIntOrNull() ?: 0
            val type = if (c[2].trim().equals("Campervan", true)) VehicleType.CAMPERVAN else VehicleType.MOTORHOME
            KnownModel(type, c[0].trim(), n(1), c[3].trim(), c[4].trim(), f(5), 0f, f(6), f(7),
                n(9), n(10), n(10), 2, c.drop(12).joinToString(",").trim(), f(8), c[11].trim())
        }
        (caravans + vans).also { cache = it }
    }

    private var links: List<Pair<String, String>>? = null

    /** Link to the document a model's figures were copied from (assets/model_sources.csv), or null. */
    fun sourceUrl(ctx: Context, m: KnownModel): String? {
        val all = links ?: lines(ctx, "model_sources.csv").mapNotNull { line ->
            val c = line.split(',')
            if (c.size < 2 || !c[1].trim().startsWith("http")) null else c[0].trim() to c[1].trim()
        }.also { links = it }
        return all.filter { m.source.startsWith(it.first) }.maxByOrNull { it.first.length }?.second
    }

    fun of(ctx: Context, t: VehicleType) = all(ctx).filter { it.type == t }
    fun makes(ctx: Context, t: VehicleType) = of(ctx, t).map { it.make }.distinct().sorted()
    fun years(ctx: Context, t: VehicleType, make: String) = of(ctx, t).filter { it.make == make }.map { it.year }.distinct().sorted()
    fun models(ctx: Context, t: VehicleType, make: String, year: Int) =
        of(ctx, t).filter { it.make == make && it.year == year }.sortedWith(compareBy({ it.range }, { it.model }))
}
