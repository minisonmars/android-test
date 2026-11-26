package com.caravanlevel

/**
 * Beginner's troubleshooting guides. General UK advice only: every caravan and van differs,
 * so the vehicle's own handbook comes first, and gas and mains electric work needs a
 * qualified engineer.
 */
object Troubleshooting {
    /** forTowing: only for vehicles that tow or are towed. vanOnly: only for campervans and motorhomes. */
    class Guide(val title: String, val steps: List<String>, val forTowing: Boolean = false, val vanOnly: Boolean = false, val urgent: Boolean = false)

    fun forType(t: VehicleType) = guides.filter {
        (!it.forTowing || t == VehicleType.CARAVAN || t == VehicleType.CAR) &&
            (!it.vanOnly || t == VehicleType.CAMPERVAN || t == VehicleType.MOTORHOME) &&
            (t != VehicleType.CAR || it.forTowing || it.urgent)
    }

    val guides = listOf(
        Guide("Carbon monoxide or smoke alarm going off", listOf(
            "If the carbon monoxide (CO) alarm sounds: get everyone (and pets) outside straight away.",
            "Turn off the gas at the bottle, and leave doors and windows open.",
            "Don't go back in until the alarm has stopped and you've had fresh air. If anyone feels dizzy, sick or has a headache, call 999 or NHS 111.",
            "Don't use the gas appliances again until a qualified gas engineer has checked them.",
            "Smoke alarm: check for smoke or fire first. If it's only cooking, ventilate and press the test/hush button.",
            "Short chirps every minute usually mean the alarm's battery is low: replace it."
        ), urgent = true),
        Guide("Smell of gas", listOf(
            "Turn off the gas at the bottle straight away.",
            "Don't use switches, lighters or anything that could spark. Open doors and windows.",
            "Get everyone outside and away until the smell has gone.",
            "Don't use the gas again until it's been checked by a qualified gas engineer. On site, tell reception."
        ), urgent = true),
        Guide("No 12 V power (lights, pump and control panel dead)", listOf(
            "Check the main 12 V switch on the control panel is on.",
            "Check the leisure battery isolator switch (often near the battery box) is on.",
            "Check the leisure battery is charged: many control panels show the voltage. About 12.6 V is full; under 12 V is low.",
            "On hook-up, check the battery charger is switched on (often a switch on the power unit or control panel).",
            "Check the fuses. The 12 V fuses are usually in the power unit, a fuse box near it, or by the battery. The handbook shows where.",
            "A blown blade fuse has a broken wire inside. Replace it with one of the SAME rating (the number on top). If it blows again, something's wrong: get it checked.",
            "While towing, the car usually charges the battery and runs the fridge: check the 13-pin plug is fully in and twisted to lock."
        )),
        Guide("No mains power (sockets and hook-up items dead)", listOf(
            "Check the hook-up cable is fully pushed in at both ends and twisted to lock.",
            "Check the switches in the caravan's consumer unit (the RCD and the small circuit breakers). If one has dropped, push it back up.",
            "If it trips again straight away, unplug everything, reset, then plug things back in one at a time to find the faulty one.",
            "Check the site bollard: it may have tripped. Ask reception to reset it.",
            "Site supplies are often only 6 to 16 amps. A kettle (about 2 to 3 kW) plus a heater can be too much: use one big item at a time.",
            "A plug-in polarity tester in a socket shows whether the supply is wired correctly. Reversed polarity is a site problem: tell reception."
        )),
        Guide("No water from the taps", listOf(
            "Check there's water in the container (Aquaroll or similar) and the pump is fully under water.",
            "Check the pump's plug is pushed fully into the inlet socket on the side.",
            "Check the pump switch on the control panel is on, and there's 12 V power.",
            "Many taps switch the pump on with a small switch inside the tap: open the tap fully.",
            "If the pump runs but no water comes, there may be an air lock: open all the taps, including the shower, until water flows.",
            "Check the water filter on the inlet isn't blocked, and in cold weather that nothing has frozen.",
            "Check the fuse for the pump."
        )),
        Guide("Fridge not cooling", listOf(
            "Check which power the fridge is set to: mains (on hook-up), gas, or 12 V. 12 V usually only works while towing or driving with the engine running.",
            "Most caravan fridges need to be fairly level to work: use the Level page.",
            "On gas: check the gas is on, the bottle isn't empty, and the fridge lights (you may hear clicking).",
            "Take the winter vent covers off in warm weather: the fridge needs air flow outside.",
            "A fridge can take several hours to get cold. Start it the day before if you can, and fill it with cold food.",
            "In very hot weather, keep the outside vents in the shade if possible."
        )),
        Guide("Gas not working (hob, heater, fridge)", listOf(
            "Check the bottle isn't empty: lift or weigh it.",
            "Check the regulator is connected properly and the bottle valve is open.",
            "Check the gas isolation taps inside (often in a kitchen cupboard) are on for that appliance.",
            "Butane doesn't work well near freezing: use propane in cold weather.",
            "Hob: hold the knob in for 10 to 15 seconds after lighting so the safety valve stays open.",
            "Ignition not sparking: check the ignition battery (often an AA by the hob or heater).",
            "Don't take apart any gas fittings yourself: if the checks above don't help, use a qualified gas engineer."
        )),
        Guide("No hot water or heating", listOf(
            "Check the heater is switched on at its control panel, and on the right mode (gas, electric or both).",
            "For electric, check you're on hook-up and the mains switches are on.",
            "For gas, check the gas is on (see the gas guide).",
            "Truma water heaters have a frost valve that empties the water if it gets cold. Close it (usually a yellow knob you push or turn) before filling.",
            "Fill the water heater with water before switching it on, or it can be damaged.",
            "Check the fuses for the heater."
        )),
        Guide("Road lights not working when towing", listOf(
            "Check the plug is fully pushed in and twisted to lock in the car's socket.",
            "Check whether the car has a 13-pin socket and the caravan a 13-pin plug. Older ones use 7-pin (12N/12S) and need an adaptor.",
            "Look for dirty or bent pins in the plug and socket: clean them gently.",
            "Check the bulbs in the caravan's lights.",
            "Check the car's towing fuse or tow module (see the car's handbook).",
            "Ask someone to stand behind and check each light: sidelights, brake lights, indicators and fog light."
        ), forTowing = true),
        Guide("Snaking (caravan swaying) while towing", listOf(
            "Ease off the accelerator gently and let the car slow down by itself.",
            "Hold the steering wheel steady: don't try to steer out of it.",
            "Don't brake hard unless you have to. If you must, brake gently in a straight line.",
            "Once it's stable, pull over safely and check: loading (heavy items low and over the axle), noseweight (see the car and caravan handbooks), and tyre pressures.",
            "Keep below 60 mph on motorways and dual carriageways (50 mph on single carriageways) when towing in the UK."
        ), forTowing = true),
        Guide("Motor mover not working", listOf(
            "Check the leisure battery is charged: movers need a lot of power.",
            "Check the mover's own isolator switch is on.",
            "Check the remote's batteries, and that the remote is paired (see the mover's instructions).",
            "Check the rollers are fully engaged against the tyres.",
            "Disengage the rollers before towing, or the caravan won't move freely."
        ), forTowing = true),
        Guide("Leisure battery won't charge or goes flat quickly", listOf(
            "On hook-up, check the battery charger is switched on.",
            "Check the battery fuse and the isolator switch.",
            "With solar: check the solar controller's lights and that the panel isn't shaded.",
            "Batteries wear out: if it's more than about 5 years old, it may need replacing.",
            "In storage, charge it every month or so (there's a preset reminder for this)."
        )),
        Guide("Cassette toilet problems", listOf(
            "Blade hard to open: the seal may need lubricating with seal lubricant (not oil or washing-up liquid).",
            "Flush not working: check the flush tank has water and the 12 V power is on.",
            "Cassette full light on: empty it at the site's chemical disposal point.",
            "Bad smells: check the toilet chemical is added, the cassette seal is clean, and the vent (if fitted) isn't blocked."
        )),
        Guide("Damp or condensation", listOf(
            "Keep the vents clear: they're needed for safety (gas) and to stop condensation.",
            "Open windows a little when cooking or showering, and use the extractor or rooflight.",
            "Keep some heating on in cold weather.",
            "Have a damp check done each year as part of the habitation service.",
            "Soft spots in walls or floor, or a musty smell, can mean water getting in: get it checked soon."
        )),
        Guide("Flat tyre or puncture", listOf(
            "Pull over somewhere safe and away from traffic. Put the hazard lights on and everyone out on the safe side.",
            "Put on hi-vis and set up the warning triangle if it's safe to do so (not on a motorway).",
            "If you're not sure how to change it safely, call your breakdown service.",
            "Caravans: only jack at the points shown in the handbook, never on the axle tube or a corner steady.",
            "Check the spare's pressure before you need it, and replace tyres that are old: caravan tyres are usually advised to be replaced every 5 to 7 years whatever the tread."
        )),
        Guide("Engine warning light or won't start", listOf(
            "A red warning light means stop as soon as it's safe. Amber usually means get it checked soon: see the vehicle's handbook.",
            "Won't start: the starter battery may be flat (separate from the leisure battery). Some vans have a switch to start from the leisure battery.",
            "Check you're not low on fuel or AdBlue (diesels).",
            "Call your breakdown service if you're not sure."
        ), vanOnly = true)
    )
}
