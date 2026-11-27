package com.caravanlevel

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.CheckBox
import android.widget.ScrollView

/** Android Auto on or off: the car screens are only offered while the service is enabled. */
object CarSwitch {
    private fun c(ctx: Context) = ComponentName(ctx, CarService::class.java)
    fun on(ctx: Context) = ctx.packageManager.getComponentEnabledSetting(c(ctx)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    fun set(ctx: Context, on: Boolean) = ctx.packageManager.setComponentEnabledSetting(c(ctx),
        if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP)
}

/**
 * Ask AI: writes the vehicle's details as plain text and hands it to an AI app (Claude,
 * Gemini, ChatGPT...) through Android's share menu. Nothing is sent by this app itself.
 */
object AskAi {
    fun show(a: Activity) {
        val ui = Ui(a)
        val v = GarageStore.active(a, GarageStore.load(a))
        val box = ui.column(20)
        box.addView(ui.text("Your ${v.type.label.lowercase()}'s details are written out as text and shared to an AI app of your choice, so it can help you work out a problem. You choose what's included.", 16f, Colours.MUTED))
        box.addView(ui.label("What's the problem? (optional)"))
        val question = ui.multiline("", "For example: The fridge won't work on gas")
        box.addView(question)
        fun tick(label: String, on: Boolean) = CheckBox(a).apply {
            text = label; textSize = 17f; setTextColor(Colours.TEXT); isChecked = on
        }.also { box.addView(it) }
        box.addView(ui.label("Include"))
        val details = tick("Make, model, sizes, weights and tyres", true)
        val equipment = tick("Equipment", true)
        val services = tick("Service history", true)
        val reminders = tick("Reminders", false)
        val notes = tick("Notes", true)
        val ids = tick("Registration, CRiS and VIN numbers", false)

        AlertDialog.Builder(a)
            .setTitle("Ask AI about ${v.name}")
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton("Share to an AI app") { _, _ ->
                val text = build(a, v, question.text.toString().trim(), details.isChecked, equipment.isChecked,
                    services.isChecked, reminders.isChecked, notes.isChecked, ids.isChecked)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "${v.name} (${v.type.label})")
                    .putExtra(Intent.EXTRA_TEXT, text)
                a.startActivity(Intent.createChooser(send, "Choose your AI app"))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun build(ctx: Context, v: Vehicle, question: String, details: Boolean, equipment: Boolean,
                      services: Boolean, reminders: Boolean, notes: Boolean, ids: Boolean): String = buildString {
        val hidden = setOf("Registration", "CRiS number", Vehicle.VIN)
        appendLine(if (question.isNotBlank()) "Please help me with a problem on my ${v.type.label.lowercase()}: $question"
            else "Here are the details of my ${v.type.label.lowercase()}. Please use them to help me with questions about it.")
        appendLine("I'm in the UK. Please keep advice practical and say when something needs a qualified engineer (especially gas and electrics).")
        appendLine()
        appendLine("VEHICLE: ${v.name} (${v.type.label})")
        if (details) {
            v.details.filter { it.value.isNotBlank() && (ids || it.label !in hidden) }.forEach { appendLine("${it.label}: ${it.value}") }
            fun size(label: String, cm: Float) { if (cm > 0) appendLine("$label: %.2f m".format(cm / 100f)) }
            size("Length", v.lengthCm); size("Body length", v.bodyLengthCm); size("Width", v.widthCm)
            size("Width with mirrors", v.mirrorWidthCm); size("Height", v.heightCm)
            if (v.mroKg > 0) appendLine((if (v.type == VehicleType.CAR) "Kerb weight" else "MRO") + ": ${v.mroKg} kg")
            if (v.mtplmKg > 0) appendLine((if (v.type == VehicleType.CAR) "Gross weight" else "MTPLM") + ": ${v.mtplmKg} kg")
            if (v.loadedKg > 0) appendLine("Loaded weight: ${v.loadedKg} kg")
            if (v.noseLimitKg > 0) appendLine("Maximum noseweight: ${v.noseLimitKg} kg")
            if (v.towLimitKg > 0) appendLine("Maximum towing weight: ${v.towLimitKg} kg")
            if (v.tyreSize.isNotBlank()) appendLine("Tyre size: ${v.tyreSize}")
            if (v.tyrePsiFront > 0) appendLine("Tyre pressure${if (v.type != VehicleType.CARAVAN) " front" else ""}: %.1f psi".format(v.tyrePsiFront))
            if (v.tyrePsiRear > 0) appendLine("Tyre pressure rear: %.1f psi".format(v.tyrePsiRear))
            Tyres.ageYears(v.tyreDot)?.let { appendLine("Tyre age: about %.1f years (DOT ${v.tyreDot})".format(it)) }
            v.latestReading?.let { appendLine("Mileage: ${Units.distText(ctx, it.miles)} on ${Units.date(it.dateMs)}") }
        }
        if (equipment && v.equipment.any { it.value.isNotBlank() || it.aftermarket }) {
            appendLine(); appendLine("EQUIPMENT")
            v.equipment.filter { it.label.isNotBlank() }.forEach { d ->
                val extra = if (d.aftermarket) " (aftermarket" + (d.installedMs?.let { ", fitted ${Units.date(it)}" } ?: "") + ")" else ""
                appendLine("${d.label}: ${d.value.ifBlank { "not recorded" }}$extra")
            }
        }
        if (services && v.services.isNotEmpty()) {
            appendLine(); appendLine("SERVICE HISTORY (newest first)")
            v.services.sortedByDescending { it.dateMs }.forEach { r ->
                append("${Units.date(r.dateMs)}: ${r.title.ifBlank { "Service" }}")
                if (r.who.isNotBlank()) append(" by ${r.who}")
                r.mileage?.let { append(", at ${Units.distText(ctx, it)}") }
                appendLine()
                if (r.notes.isNotBlank()) appendLine("  Notes: ${r.notes.replace("\n", " ")}")
            }
        }
        if (reminders && v.reminders.isNotEmpty()) {
            appendLine(); appendLine("REMINDERS")
            v.reminders.forEach { appendLine("${it.title}: ${Due.describe(ctx, v, it)}") }
        }
        if (notes && v.notes.isNotBlank()) {
            appendLine(); appendLine("NOTES"); appendLine(v.notes)
        }
    }
}
