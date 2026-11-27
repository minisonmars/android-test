package com.caravanlevel

import android.content.Intent

/** Service and repair history for the chosen vehicle, newest first. */
class ServiceActivity : Page() {
    override val section = Section.SERVICE

    override fun build() {
        content.addView(ui.button("Add a service or repair", Colours.ACCENT) { edit(null) })

        val list = vehicle.services.sortedByDescending { it.dateMs }
        if (list.isEmpty()) {
            content.addView(ui.text(
                "No records yet. Add services, repairs, habitation checks and parts fitted, with photos of receipts if you like.",
                18f, Colours.MUTED
            ).apply { setPadding(0, ui.dp(12), 0, 0) })
            return
        }

        val total = list.sumOf { Units.moneyValue(it.cost) }
        if (total > 0) content.addView(ui.text("Total recorded: ${Units.money(total.toString())}", 17f, Colours.MUTED)
            .apply { setPadding(0, ui.dp(8), 0, ui.dp(4)) })

        list.forEach { r ->
            val card = ui.card()
            val date = ui.text(Units.date(r.dateMs), 16f, Colours.MUTED)
            if (vehicle.reminders.any { it.id == r.reminderId }) {
                // a bell shows a reminder has been set for the next one
                date.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                card.addView(ui.row(date, IconView(this, Icon.REMINDER, Colours.ACCENT).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(ui.dp(30), ui.dp(30))
                }))
            } else card.addView(date)
            card.addView(ui.text(r.title.ifBlank { "Service" }, 20f, bold = true))
            val bits = listOfNotNull(
                r.who.takeIf { it.isNotBlank() },
                r.cost.takeIf { it.isNotBlank() }?.let { Units.money(it) },
                r.mileage?.let { Units.distText(this, it) }
            )
            if (bits.isNotEmpty()) card.addView(ui.text(bits.joinToString("  ·  "), 17f))
            if (r.notes.isNotBlank()) card.addView(ui.text(r.notes.lines().take(3).joinToString("\n"), 16f, Colours.MUTED))
            if (r.attachments.isNotEmpty()) {
                val n = r.attachments.size
                card.addView(ui.text("$n photo${if (n == 1) "" else "s"} or file${if (n == 1) "" else "s"}", 16f, Colours.ACCENT))
            }
            card.isClickable = true
            card.setOnClickListener { edit(r.id) }
            content.addView(card)
        }
    }

    private fun edit(recordId: String?) {
        save()
        startActivity(Intent(this, ServiceEditActivity::class.java)
            .putExtra(ServiceEditActivity.EXTRA_RECORD, recordId))
    }
}
