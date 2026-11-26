package com.caravanlevel

import android.app.AlertDialog
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Setting up, packing away and any other lists for the chosen vehicle.
 * Tick items as you go; "Start again" unticks them for the next trip, and a list
 * that was finished more than 12 hours ago starts fresh by itself.
 */
class ChecklistActivity : Page() {
    override val section = Section.CHECKLISTS

    private var editing = false
    private val editFields = mutableListOf<Pair<CheckItem, EditText>>()

    companion object {
        const val FRESH_AFTER_MS = 12 * 60 * 60 * 1000L
    }

    private fun current(): Checklist? {
        val id = AppPrefs.checklist(this, vehicle.id)
        return vehicle.checklists.firstOrNull { it.id == id } ?: vehicle.checklists.firstOrNull()
    }

    override fun commit() {
        // Keep wording typed while editing
        editFields.forEach { (item, field) ->
            val t = field.text.toString().trim()
            if (t.isNotEmpty()) item.text = t
        }
    }

    override fun build() {
        editFields.clear()
        val list = current()

        // One button per list, the shown one highlighted
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        vehicle.checklists.forEach { c ->
            tabs.addView(ui.button(c.name, if (c === list) Colours.ACCENT else Colours.CARD) {
                save()
                AppPrefs.setChecklist(this, vehicle.id, c.id)
                editing = false
                rebuild()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, ui.dp(6), ui.dp(8), ui.dp(6)) }
            })
        }
        tabs.addView(ui.button("New list") { newList() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, ui.dp(6), 0, ui.dp(6)) }
        })
        content.addView(HorizontalScrollView(this).apply { addView(tabs) })

        if (list == null) {
            content.addView(ui.text("No checklists yet. Tap New list to make one.", 18f, Colours.MUTED))
            content.addView(ui.button("Add the starting lists for a ${vehicle.type.label.lowercase()}") {
                vehicle.checklists.addAll(DefaultLists.forType(vehicle.type))
                saveAndRebuild()
            })
            return
        }

        // A finished list starts fresh on the next trip
        if (!editing && list.finished && list.completedAt > 0 &&
            System.currentTimeMillis() - list.completedAt > FRESH_AFTER_MS
        ) {
            list.resetTicks()
            save()
        }

        if (editing) buildEditor(list) else buildTicks(list)
    }

    private fun buildTicks(list: Checklist) {
        val progress = ui.text("", 18f, Colours.MUTED).apply { setPadding(0, ui.dp(8), 0, ui.dp(4)) }
        fun showProgress() {
            if (list.finished) {
                progress.text = "All done: ${list.done} of ${list.items.size}"
                progress.setTextColor(Colours.ACCENT)
            } else {
                progress.text = "${list.done} of ${list.items.size} done"
                progress.setTextColor(Colours.MUTED)
            }
        }
        showProgress()
        content.addView(progress)

        list.items.forEach { item ->
            content.addView(CheckBox(this).apply {
                text = item.text
                textSize = 19f
                setTextColor(Colours.TEXT)
                isChecked = item.ticked
                setPadding(ui.dp(8), ui.dp(14), ui.dp(8), ui.dp(14))
                setOnCheckedChangeListener { _, on ->
                    item.ticked = on
                    list.completedAt = if (list.finished) System.currentTimeMillis() else 0L
                    showProgress()
                    save()
                }
            })
        }
        if (list.items.isEmpty()) {
            content.addView(ui.text("This list is empty. Tap Edit this list to add items.", 18f, Colours.MUTED))
        }

        content.addView(ui.button("Start again (untick everything)", Colours.TILE) {
            list.resetTicks()
            saveAndRebuild()
        })
        content.addView(ui.button("Edit this list") {
            editing = true
            rebuild()
        })
    }

    private fun buildEditor(list: Checklist) {
        content.addView(ui.label("List name"))
        val nameField = ui.field(list.name)
        content.addView(nameField)
        nameField.setOnFocusChangeListener { _, has ->
            if (!has) nameField.text.toString().trim().takeIf { it.isNotEmpty() }?.let { list.name = it }
        }

        content.addView(ui.label("Items (change the wording, move or remove)"))
        list.items.forEachIndexed { i, item ->
            val card = ui.card()
            val f = ui.field(item.text)
            editFields.add(item to f)
            card.addView(f)
            card.addView(ui.row(
                ui.rowButton("Move up") {
                    if (i > 0) { list.items.add(i - 1, list.items.removeAt(i)); saveAndRebuild() }
                },
                ui.rowButton("Move down") {
                    if (i < list.items.size - 1) { list.items.add(i + 1, list.items.removeAt(i)); saveAndRebuild() }
                },
                ui.rowButton("Remove", Colours.RED) {
                    save()
                    list.items.remove(item)
                    saveAndRebuild()
                }
            ))
            content.addView(card)
        }

        content.addView(ui.label("Add an item"))
        val add = ui.field(hint = "For example: Check the gas bottle is full")
        content.addView(add)
        content.addView(ui.button("Add item", Colours.TILE) {
            val t = add.text.toString().trim()
            if (t.isNotEmpty()) {
                save()
                list.items.add(CheckItem(t))
                saveAndRebuild()
            }
        })

        content.addView(ui.button("Done editing", Colours.ACCENT) {
            nameField.text.toString().trim().takeIf { it.isNotEmpty() }?.let { list.name = it }
            save()
            editing = false
            rebuild()
        })

        val starter = DefaultLists.forType(vehicle.type).firstOrNull { it.name == list.name }
        if (starter != null) {
            content.addView(ui.button("Put back the starting list") {
                ui.confirm("Put back the starting list?", "Your changes to \"${list.name}\" will be replaced.", "Put back") {
                    list.items.clear()
                    list.items.addAll(starter.items)
                    list.completedAt = 0L
                    editFields.clear()
                    saveAndRebuild()
                }
            })
        }
        content.addView(ui.button("Delete this list", Colours.RED) {
            ui.confirm("Delete \"${list.name}\"?", "This can't be undone.", "Delete") {
                editFields.clear()
                vehicle.checklists.remove(list)
                editing = false
                saveAndRebuild()
            }
        })
    }

    private fun newList() {
        val f = ui.field(hint = "For example: Winter storage")
        val box = ui.column(20).apply { addView(f) }
        AlertDialog.Builder(this).setTitle("New checklist").setView(box)
            .setPositiveButton("Make list") { _, _ ->
                val name = f.text.toString().trim().ifEmpty { "My list" }
                save()
                val c = Checklist(name, mutableListOf())
                vehicle.checklists.add(c)
                AppPrefs.setChecklist(this, vehicle.id, c.id)
                editing = true
                saveAndRebuild()
            }
            .setNegativeButton("Cancel", null).show()
    }
}
