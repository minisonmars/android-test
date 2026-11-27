package com.caravanlevel

/**
 * The usual UK towing guides (general advice, not law): caravan loaded weight no more than
 * 85% of the car's kerb weight (100% only for experienced towers), never over the car's
 * towing limit, and noseweight about 5-7% of the caravan's weight, within both limits.
 * Each line is (true = fine, false = a problem, null = information) to its text.
 */
object Towing {
    fun check(van: Vehicle, car: Vehicle): List<Pair<Boolean?, String>> {
        val out = mutableListOf<Pair<Boolean?, String>>()
        val vanKg = if (van.loadedKg > 0) van.loadedKg else van.mtplmKg
        val which = if (van.loadedKg > 0) "loaded weight" else "MTPLM"
        if (vanKg <= 0 || car.mroKg <= 0) {
            out.add(null to "Enter the caravan's MTPLM and the car's kerb weight to see the 85% guide.")
        } else {
            val pct = vanKg * 100.0 / car.mroKg
            val ok = pct <= 85.0
            out.add(ok to "85% guide: the caravan's $which ($vanKg kg) is %.1f%% of the car's kerb weight (${car.mroKg} kg). ".format(pct) +
                if (ok) "Within the guide." else if (pct <= 100.0)
                    "Over 85%: only for experienced towers. The guide is ${(car.mroKg * 0.85).toInt()} kg."
                else "Over 100% of the kerb weight: not recommended.")
        }
        if (vanKg > 0 && car.towLimitKg > 0) {
            out.add((vanKg <= car.towLimitKg) to if (vanKg <= car.towLimitKg)
                "Within the car's towing limit (${car.towLimitKg} kg)."
            else "Over the car's towing limit (${car.towLimitKg} kg). This isn't legal.")
        }
        val limits = listOf(van.noseLimitKg, car.noseLimitKg).filter { it > 0 }
        val limit = limits.minOrNull()
        if (vanKg > 0) {
            val low = (vanKg * 0.05).toInt(); val high = (vanKg * 0.07).toInt()
            val range = if (limit != null && limit < high) "$low to $limit kg" else "$low to $high kg"
            out.add(null to "Aim for a noseweight of about $range (5 to 7% of $vanKg kg)" +
                (if (limit != null) ", no more than $limit kg." else "."))
        }
        if (van.noseKg > 0) {
            when {
                limit != null && van.noseKg > limit ->
                    out.add(false to "Measured noseweight ${van.noseKg} kg is over the limit of $limit kg. Move heavy items back over the axle.")
                vanKg > 0 && van.noseKg < vanKg * 0.05 ->
                    out.add(false to "Measured noseweight ${van.noseKg} kg is light. Too little can make the caravan unstable (snaking).")
                else -> out.add(true to "Measured noseweight ${van.noseKg} kg is fine.")
            }
        }
        return out
    }
}
