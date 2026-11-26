package com.caravanlevel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper

/** Where the phone is, for the compass and the trip diary. */
object Here {
    fun allowed(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The most recent position the phone already knows, without waiting. */
    fun last(ctx: Context): Location? {
        if (allowed(ctx)) {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val best = (try { lm.getProviders(true) } catch (_: Exception) { emptyList<String>() })
                .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null } }
                .maxByOrNull { it.time }
            if (best != null) return best
        }
        val p = ctx.getSharedPreferences("compass", Context.MODE_PRIVATE)
        if (!p.contains("lat")) return null
        return Location("saved").apply {
            latitude = p.getFloat("lat", 0f).toDouble(); longitude = p.getFloat("lon", 0f).toDouble(); time = p.getLong("time", 0L)
        }
    }

    /** Asks for a fresh position and calls back once (null after 20 seconds without one). */
    fun fresh(ctx: Context, done: (Location?) -> Unit) {
        if (!allowed(ctx)) { done(null); return }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var finished = false
        val handler = Handler(Looper.getMainLooper())
        lateinit var listener: LocationListener
        fun finish(l: Location?) {
            if (finished) return
            finished = true
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
            done(l)
        }
        listener = LocationListener { l -> if (l.accuracy <= 200f || !l.hasAccuracy()) finish(l) }
        val providers = try { lm.getProviders(true) } catch (_: Exception) { emptyList<String>() }
        providers.forEach { p ->
            try { lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper()) } catch (_: Exception) {}
        }
        handler.postDelayed({ finish(last(ctx)) }, 20_000L)
        if (providers.isEmpty()) finish(last(ctx))
    }
}
