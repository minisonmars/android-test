package com.caravanlevel

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Lets the camera app save a photo into the app, and lets a viewer app open a saved
 * photo or file, without giving anyone else access to the app's storage.
 */
class AttachmentProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.caravanlevel.files"

        fun uri(name: String): Uri = Uri.parse("content://$AUTHORITY/${Uri.encode(name)}")

        fun mime(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"

        /** The name to show: stored names are "<8 random characters>_<original name>". */
        fun displayName(stored: String) = if (stored.length > 9 && stored[8] == '_') stored.substring(9) else stored
    }

    private fun file(uri: Uri): File {
        val ctx: Context = context ?: throw IllegalStateException()
        val name = uri.lastPathSegment ?: throw IllegalArgumentException()
        val dir = GarageStore.attachmentsDir(ctx)
        val f = File(dir, name)
        if (f.canonicalFile.parentFile != dir.canonicalFile) throw SecurityException()
        return f
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode))

    override fun getType(uri: Uri): String = mime(uri.lastPathSegment ?: "")

    override fun query(uri: Uri, projection: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor {
        val f = file(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(displayName(f.name), f.length()))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/** Shared bits for adding and showing photos and files. */
object Attach {
    /** A unique stored name that keeps the original name readable. */
    fun newName(original: String): String {
        val clean = original.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(60).ifBlank { "file" }
        return java.util.UUID.randomUUID().toString().take(8) + "_" + clean
    }

    /** Asks the phone's camera app to save a photo straight into the app. */
    fun cameraIntent(name: String): android.content.Intent {
        val uri = AttachmentProvider.uri(name)
        return android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = android.content.ClipData.newRawUri("photo", uri) }
    }

    /** Copies a picked file into the app. Returns its stored name, or null if it failed. */
    fun copyIn(ctx: Context, uri: Uri): String? = try {
        var display = "file"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { display = it }
        }
        if (!display.contains('.')) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(ctx.contentResolver.getType(uri) ?: "")
                ?.let { display += ".$it" }
        }
        val name = newName(display)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            File(GarageStore.attachmentsDir(ctx), name).outputStream().use { input.copyTo(it) }
        }
        name
    } catch (_: Exception) {
        android.widget.Toast.makeText(ctx, "Couldn't add that file", android.widget.Toast.LENGTH_LONG).show()
        null
    }

    /** A smaller copy of a photo for showing on screen. */
    fun thumbnail(f: File, maxWidth: Int = 480): android.graphics.Bitmap? = try {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= maxWidth) sample *= 2
        android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) { null }

    /** Opens a stored photo or file in a viewer app. */
    fun view(ctx: Context, name: String) {
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(AttachmentProvider.uri(name), AttachmentProvider.mime(name))
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            ctx.startActivity(i)
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(ctx, "No app on this phone can open this file", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
