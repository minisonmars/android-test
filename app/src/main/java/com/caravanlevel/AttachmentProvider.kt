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
