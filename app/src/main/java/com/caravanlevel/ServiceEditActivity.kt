package com.caravanlevel

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.File
import java.util.UUID

/**
 * One service record. Everything is kept as soon as you leave the page; a new record
 * is only added once something has been typed or attached.
 */
class ServiceEditActivity : Activity() {
    companion object {
        const val EXTRA_RECORD = "record"
        private const val TAKE_PHOTO = 1
        private const val PICK_FILE = 2
    }

    private lateinit var ui: Ui
    private lateinit var garage: Garage
    private lateinit var vehicle: Vehicle
    private lateinit var record: ServiceRecord
    private lateinit var col: LinearLayout
    private var pendingPhoto: String? = null
    private var deleted = false
    private val commits = mutableListOf<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        garage = GarageStore.load(this)
        vehicle = GarageStore.active(this, garage)
        val id = savedInstanceState?.getString("id") ?: intent.getStringExtra(EXTRA_RECORD)
        pendingPhoto = savedInstanceState?.getString("photo")
        record = vehicle.services.firstOrNull { it.id == id }
            ?: ServiceRecord(System.currentTimeMillis(), "", id = id ?: UUID.randomUUID().toString())

        col = ui.column(16)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Colours.BG)
            addView(col)
        })
        build()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("id", record.id)
        out.putString("photo", pendingPhoto)
    }

    override fun onPause() {
        super.onPause()
        keep()
    }

    private fun keep() {
        if (deleted) return
        commits.forEach { it() }
        val worthKeeping = record.title.isNotBlank() || record.notes.isNotBlank() ||
            record.cost.isNotBlank() || record.who.isNotBlank() || record.attachments.isNotEmpty()
        if (worthKeeping && vehicle.services.none { it.id == record.id }) vehicle.services.add(record)
        GarageStore.save(this, garage)
    }

    private fun build() {
        commits.clear()
        col.removeAllViews()
        val r = record
        col.addView(ui.text("Service record", 24f, bold = true))
        col.addView(ui.text("${vehicle.name} (${vehicle.type.label})", 17f, Colours.ACCENT))

        col.addView(ui.label("Date"))
        col.addView(ui.button(Units.date(r.dateMs)) {
            ui.pickDate(r.dateMs) { keep(); r.dateMs = it; build() }
        })

        fun field(label: String, value: String, hint: String, set: (String) -> Unit) {
            col.addView(ui.label(label))
            val f = ui.field(value, hint)
            col.addView(f)
            commits.add { set(f.text.toString().trim()) }
        }
        field("What was done", r.title, "For example: Annual habitation service") { r.title = it }
        field("Who did it", r.who, "Dealer, mobile engineer, or me") { r.who = it }

        col.addView(ui.label("Cost (£)"))
        val cost = ui.numberField(r.cost, "0.00")
        col.addView(cost)
        commits.add { r.cost = cost.text.toString().trim() }

        col.addView(ui.label("Mileage (${Units.distUnit(this)}, optional)"))
        val miles = ui.wholeNumberField(r.mileage?.let { Units.distNumber(this, it).replace(",", "") } ?: "")
        col.addView(miles)
        commits.add { r.mileage = Units.parseDist(this, miles.text.toString()) }

        col.addView(ui.label("Notes"))
        val notes = ui.multiline(r.notes, "Parts fitted, faults found, advice...")
        col.addView(notes)
        commits.add { r.notes = notes.text.toString() }

        col.addView(ui.heading("Photos and files"))
        r.attachments.toList().forEach { name -> col.addView(attachmentCard(name)) }
        col.addView(ui.row(
            ui.rowButton("Take a photo", Colours.TILE) { takePhoto() },
            ui.rowButton("Add photo or file", Colours.TILE) { pickFile() }
        ))

        col.addView(ui.button("Done", Colours.ACCENT) { finish() })
        col.addView(ui.button("Delete this record", Colours.RED) {
            ui.confirm("Delete this record?", "Its photos and files will be deleted too.", "Delete") {
                r.attachments.forEach { GarageStore.deleteAttachment(this, it) }
                vehicle.services.removeAll { it.id == r.id }
                GarageStore.save(this, garage)
                deleted = true
                finish()
            }
        })
    }

    private fun attachmentCard(name: String) = ui.card().apply {
        val f = File(GarageStore.attachmentsDir(this@ServiceEditActivity), name)
        if (AttachmentProvider.mime(name).startsWith("image/")) {
            thumbnail(f)?.let { bmp ->
                addView(ImageView(this@ServiceEditActivity).apply {
                    setImageBitmap(bmp)
                    adjustViewBounds = true
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(180))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                })
            }
        }
        addView(ui.text(AttachmentProvider.displayName(name), 17f))
        addView(ui.row(
            ui.rowButton("Open") { showFile(name) },
            ui.rowButton("Remove", Colours.RED) {
                ui.confirm("Remove this file?", AttachmentProvider.displayName(name), "Remove") {
                    keep()
                    record.attachments.remove(name)
                    GarageStore.deleteAttachment(this@ServiceEditActivity, name)
                    keep()
                    build()
                }
            }
        ))
        isClickable = true
        setOnClickListener { showFile(name) }
    }

    private fun thumbnail(f: File) = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 480) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) { null }

    private fun showFile(name: String) {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(AttachmentProvider.uri(name), AttachmentProvider.mime(name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app on this phone can open this file", Toast.LENGTH_LONG).show()
        }
    }

    private fun newName(original: String): String {
        val clean = original.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(60).ifBlank { "file" }
        return UUID.randomUUID().toString().take(8) + "_" + clean
    }

    private fun takePhoto() {
        keep()
        val name = newName("Photo ${Units.date(System.currentTimeMillis())}.jpg")
        File(GarageStore.attachmentsDir(this), name).createNewFile()
        val uri = AttachmentProvider.uri(name)
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("photo", uri)
        try {
            pendingPhoto = name
            startActivityForResult(i, TAKE_PHOTO)
        } catch (_: ActivityNotFoundException) {
            pendingPhoto = null
            GarageStore.deleteAttachment(this, name)
            Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickFile() {
        keep()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            startActivityForResult(i, PICK_FILE)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No file picker found", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            TAKE_PHOTO -> {
                val name = pendingPhoto ?: return
                pendingPhoto = null
                val f = File(GarageStore.attachmentsDir(this), name)
                if (resultCode == RESULT_OK && f.length() > 0) record.attachments.add(name)
                else f.delete()
            }
            PICK_FILE -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) copyIn(uri)
            }
        }
        keep()
        build()
    }

    private fun copyIn(uri: Uri) {
        try {
            var display = "file"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { display = it }
            }
            if (!display.contains('.')) {
                val ext = android.webkit.MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(contentResolver.getType(uri) ?: "")
                if (ext != null) display += ".$ext"
            }
            val name = newName(display)
            contentResolver.openInputStream(uri)?.use { input ->
                File(GarageStore.attachmentsDir(this), name).outputStream().use { input.copyTo(it) }
            }
            record.attachments.add(name)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't add that file", Toast.LENGTH_LONG).show()
        }
    }
}
