package com.caravanlevel

import android.content.Context
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private fun rad(d: Double) = d * PI / 180.0
private fun deg(r: Double) = r * 180.0 / PI
private fun norm(d: Double) = ((d % 360.0) + 360.0) % 360.0

/** Sunrise and sunset for a day and place (standard sunrise equation, good to a minute or two). */
object Sun {
    /** Times in ms and directions in degrees from true north; null times = no sunrise/sunset that day. */
    class Day(val riseMs: Long?, val setMs: Long?, val riseAz: Double?, val setAz: Double?,
              val noonMs: Long, val maxHeight: Double)

    fun day(lat: Double, lon: Double, dayMs: Long): Day {
        val local = Calendar.getInstance().apply { timeInMillis = dayMs }
        val noonUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 12, 0)
        }.timeInMillis
        val jd = noonUtc / 86_400_000.0 + 2440587.5
        val n = Math.round(jd - 2451545.0 + 0.0008).toDouble()
        val jStar = n - lon / 360.0
        val m = norm(357.5291 + 0.98560028 * jStar)
        val c = 1.9148 * sin(rad(m)) + 0.02 * sin(rad(2 * m)) + 0.0003 * sin(rad(3 * m))
        val lambda = norm(m + c + 180.0 + 102.9372)
        val transit = 2451545.0 + jStar + 0.0053 * sin(rad(m)) - 0.0069 * sin(rad(2 * lambda))
        val sinDec = sin(rad(lambda)) * sin(rad(23.4397))
        val cosDec = cos(asin(sinDec))
        val cosW = (sin(rad(-0.833)) - sin(rad(lat)) * sinDec) / (cos(rad(lat)) * cosDec)
        fun ms(j: Double) = ((j - 2440587.5) * 86_400_000.0).toLong()
        // Highest the sun gets, at solar noon, in degrees above the horizon
        val maxHeight = 90.0 - abs(lat - deg(asin(sinDec)))
        if (cosW < -1 || cosW > 1) return Day(null, null, null, null, ms(transit), maxHeight)
        val w = deg(acos(cosW))
        val cosAz = (sinDec / cos(rad(lat))).coerceIn(-1.0, 1.0)
        val riseAz = deg(acos(cosAz))
        return Day(ms(transit - w / 360.0), ms(transit + w / 360.0), riseAz, 360.0 - riseAz, ms(transit), maxHeight)
    }
}

/** Pointing a dish at a TV satellite in geostationary orbit. */
object Satellite {
    const val ASTRA2_LON = 28.2 // Astra 2 / Eutelsat at 28.2 East: Freesat and Sky

    class Aim(val azimuth: Double, val elevation: Double, val skew: Double)

    fun aim(lat: Double, lon: Double, satLon: Double = ASTRA2_LON): Aim {
        val d = rad(lon - satLon)
        val la = rad(lat)
        val az = norm(180.0 + deg(atan(tan(d) / sin(la))))
        val cosG = cos(d) * cos(la)
        val el = deg(atan((cosG - 0.1513) / sqrt(1 - cosG * cosG)))
        val skew = deg(atan(sin(d) / tan(la)))
        return Aim(az, el, skew)
    }
}

/** Great-circle distance and direction. */
object Geo {
    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = rad(lat2 - lat1); val dLon = rad(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 6371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val y = sin(rad(lon2 - lon1)) * cos(rad(lat2))
        val x = cos(rad(lat1)) * sin(rad(lat2)) - sin(rad(lat1)) * cos(rad(lat2)) * cos(rad(lon2 - lon1))
        return norm(deg(atan2(y, x)))
    }

    /** 0 -> "N", 45 -> "NE" and so on (16 points). */
    fun point(deg: Double): String {
        val names = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return names[((norm(deg) + 11.25) / 22.5).toInt() % 16]
    }

    fun dirText(deg: Double) = "%.0f° (%s)".format(norm(deg), point(deg))
}

class Transmitter(val name: String, val lat: Double, val lon: Double)

object Transmitters {
    private var cache: List<Transmitter>? = null

    fun all(ctx: Context): List<Transmitter> = cache ?: try {
        ctx.assets.open("tv_transmitters.csv").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { l ->
                val c = l.split(',')
                if (c.size < 3) null else Transmitter(c[0].trim(), c[1].trim().toDoubleOrNull() ?: return@mapNotNull null,
                    c[2].trim().toDoubleOrNull() ?: return@mapNotNull null)
            }
    } catch (_: Exception) { emptyList() }.also { cache = it }

    fun nearest(ctx: Context, lat: Double, lon: Double, count: Int = 3) =
        all(ctx).sortedBy { Geo.km(lat, lon, it.lat, it.lon) }.take(count)

}
