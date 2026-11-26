package com.caravanlevel

/** Step-by-step help for common problems, for the chosen vehicle's type. Tap a problem to open it. */
class TroubleshootActivity : Page() {
    override val section = Section.HELP
    private val open = mutableSetOf<String>()

    override fun build() {
        content.addView(ui.text(
            "General advice for common problems. Every vehicle differs, so check its handbook. Gas and mains electric work needs a qualified engineer.",
            16f, Colours.MUTED
        ).apply { setPadding(0, ui.dp(4), 0, ui.dp(8)) })

        Troubleshooting.forType(vehicle.type).forEach { g ->
            val isOpen = g.title in open
            val card = ui.card(if (g.urgent) Colours.RED_CARD else if (isOpen) Colours.TILE else Colours.CARD)
            card.addView(ui.text(g.title, 19f, bold = true))
            if (isOpen) {
                g.steps.forEachIndexed { i, s ->
                    card.addView(ui.text("${i + 1}.  $s", 17f).apply { setPadding(0, ui.dp(6), 0, 0) })
                }
            } else {
                card.addView(ui.text("Tap to show what to do", 15f, Colours.MUTED))
            }
            card.isClickable = true
            card.setOnClickListener {
                if (isOpen) open.remove(g.title) else open.add(g.title)
                rebuild()
            }
            content.addView(card)
        }
    }
}
