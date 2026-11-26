package com.caravanlevel

/**
 * Kit and paperwork needed for driving and towing in other countries, added to the packing
 * list for the countries chosen for a trip.
 *
 * General information only, as the rules were understood in 2026 (not checked against each
 * country's official sources). Rules change: check gov.uk "Driving abroad" and the AA or
 * RAC country guides before going.
 */
object TripRules {
    const val CHECKED = "2026"

    enum class Need(val word: String) { REQUIRED("required"), SOMETIMES("required in some areas or conditions"), ADVISED("recommended") }

    class Rule(val key: String, val text: String, val needs: Map<String, Need>, val motorisedOnly: Boolean = false)

    val countries = listOf(
        "France", "Spain", "Portugal", "Italy", "Germany", "Netherlands", "Belgium", "Luxembourg",
        "Switzerland", "Austria", "Ireland", "Denmark", "Norway", "Sweden", "Croatia", "Slovenia", "Czechia"
    )

    private val R = Need.REQUIRED
    private val S = Need.SOMETIMES
    private val A = Need.ADVISED
    private fun all(n: Need, except: Set<String> = emptySet()) = countries.filter { it !in except }.associateWith { n }

    val rules = listOf(
        Rule("docs", "Driving licence, V5C logbook (the caravan's CRiS document too) and insurance details", all(R)),
        Rule("passports", "Passports for everyone, in date for the whole trip (for the EU: issued in the last 10 years and valid 3 months after you leave)",
            all(R, setOf("Ireland")) + mapOf("Ireland" to A)),
        Rule("ghic", "GHIC or EHIC card for everyone, and travel insurance", all(A)),
        Rule("breakdown", "European breakdown cover details", all(A)),
        Rule("uk_sticker", "UK sticker on the car and the caravan, unless the number plates show UK with the Union flag. Spain needs the sticker even then",
            all(R, setOf("Ireland"))),
        Rule("beams", "Headlamp beam deflectors, or headlights set for driving on the right", all(R, setOf("Ireland"))),
        Rule("hivis", "Hi-vis jacket for everyone, kept inside the car (not in the boot)", mapOf(
            "France" to R, "Spain" to R, "Italy" to R, "Portugal" to A, "Germany" to R, "Belgium" to R, "Luxembourg" to R,
            "Austria" to R, "Croatia" to R, "Slovenia" to R, "Czechia" to R, "Norway" to A, "Switzerland" to A
        )),
        Rule("triangle", "Warning triangle", mapOf(
            "France" to R, "Italy" to R, "Portugal" to R, "Germany" to R, "Belgium" to R, "Luxembourg" to R, "Austria" to R,
            "Switzerland" to R, "Croatia" to R, "Slovenia" to R, "Czechia" to R, "Denmark" to R, "Norway" to A, "Netherlands" to A,
            "Sweden" to A
        )),
        Rule("triangles2", "A second warning triangle", mapOf("Spain" to A, "Croatia" to S)),
        Rule("breathalyser", "Breathalyser (the law says carry one, but there's no fine)", mapOf("France" to R)),
        Rule("bulbs", "Spare bulbs and fuses", mapOf("Croatia" to R, "Czechia" to R, "Spain" to A, "France" to A, "Slovenia" to A)),
        Rule("firstaid", "First aid kit in the car", mapOf(
            "Austria" to R, "Croatia" to R, "Czechia" to R, "Slovenia" to R, "Germany" to A, "Belgium" to A
        )),
        Rule("overhang", "Red and white overhang board for a bike rack or anything sticking out at the back", mapOf("Spain" to R, "Italy" to R)),
        Rule("glasses", "Spare pair of glasses, if you wear them for driving", mapOf("Spain" to R)),
        Rule("dipped", "Dipped headlights on all the time, day and night", mapOf(
            "Sweden" to R, "Norway" to R, "Denmark" to R, "Czechia" to R, "Slovenia" to R, "Portugal" to A
        )),
        Rule("speedcams", "Turn off speed camera warnings in the sat nav and phone apps (they're banned)", mapOf(
            "France" to R, "Germany" to R, "Switzerland" to R
        )),
        Rule("winter", "Winter tyres or snow chains in winter", mapOf(
            "France" to S, "Germany" to S, "Austria" to S, "Italy" to S, "Switzerland" to A, "Norway" to S, "Sweden" to S,
            "Slovenia" to S, "Czechia" to S, "Croatia" to S
        )),
        Rule("critair", "Crit'Air clean air sticker for city low-emission zones (buy only from the official website)", mapOf("France" to S)),
        Rule("umwelt", "Umweltplakette (green emissions sticker) for city low-emission zones", mapOf("Germany" to S)),
        Rule("lez_be", "Register the vehicle for the Brussels, Antwerp and Ghent low-emission zones before driving in", mapOf("Belgium" to S)),
        Rule("angles", "Blind spot (\"angles morts\") stickers, for vehicles over 3.5 tonnes", mapOf("France" to S), motorisedOnly = true),
        Rule("vignette_ch", "Swiss motorway vignette for the car, and another for the caravan or trailer (motorhomes over 3.5 tonnes pay a heavy vehicle charge instead)", mapOf("Switzerland" to R)),
        Rule("vignette_at", "Austrian motorway vignette (digital, buy online in advance)", mapOf("Austria" to R)),
        Rule("vignette_si", "Slovenian e-vignette for motorways", mapOf("Slovenia" to R)),
        Rule("vignette_cz", "Czech e-vignette for motorways", mapOf("Czechia" to R)),
        Rule("tolls_pt", "Portugal electronic motorway tolls: register your number plate or hire a toll tag before using them", mapOf("Portugal" to R))
    )

    /** One line per rule that applies to the chosen countries, e.g. "Warning triangle (France, Italy: required)". */
    private fun lines(chosen: List<String>, type: VehicleType): List<Pair<String, String>> = rules.mapNotNull { r ->
        if (r.motorisedOnly && !type.motorised) return@mapNotNull null
        val byNeed = chosen.mapNotNull { c -> r.needs[c]?.let { c to it } }.groupBy({ it.second }, { it.first })
        if (byNeed.isEmpty()) return@mapNotNull null
        val parts = Need.values().mapNotNull { n -> byNeed[n]?.let { "${it.joinToString(", ")}: ${n.word}" } }
        r.key to "${r.text} (${parts.joinToString("; ")})"
    }

    /** Replaces the country items in a packing list for its chosen countries, keeping ticks. */
    fun apply(list: Checklist, type: VehicleType) {
        val ticked = list.items.filter { it.rule != null }.associate { it.rule!! to it.ticked }
        list.items.removeAll { it.rule != null }
        val add = lines(list.countries, type).map { (key, text) ->
            CheckItem(text, ticked[key] ?: false).apply { rule = key }
        }
        list.items.addAll(0, add)
        if (!list.finished) list.completedAt = 0L
    }
}
