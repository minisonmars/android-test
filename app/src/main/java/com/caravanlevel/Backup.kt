package com.caravanlevel

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup to one .zip file that the user saves wherever they like (Google Drive, OneDrive,
 * the phone, ...) through Android's file picker. It holds garage.json (vehicles, checklists,
 * service records, reminders), every photo and file in "attachments", and the app's settings.
 */
object Backup {
    private const val GARAGE = "garage.json"
    private const val SETTINGS = "settings.json"
    private const val ATTACH = "attachments/"
    private const val PREFS = "app"

    fun fileName() = "Caravan Leveller backup " +
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK).format(java.util.Date()) + ".zip"

    /** Writes the backup. Returns the number of photos and files included. */
    fun write(ctx: Context, to: Uri): Int {
        var files = 0
        // "wt" empties the old file first (needed when it's overwritten); some apps only offer "w"
        val out = (try { ctx.contentResolver.openOutputStream(to, "wt") } catch (_: Exception) { null })
            ?: ctx.contentResolver.openOutputStream(to, "w") ?: error("Couldn't open the file")
        ZipOutputStream(out.buffered()).use { zip ->
            val garage = File(ctx.filesDir, GARAGE)
            if (garage.exists()) {
                zip.putNextEntry(ZipEntry(GARAGE)); garage.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(SETTINGS))
            zip.write(settingsJson(ctx).toString(2).toByteArray())
            zip.closeEntry()
            GarageStore.attachmentsDir(ctx).listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry(ATTACH + f.name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                files++
            }
        }
        return files
    }

    /**
     * Replaces everything in the app with the backup. The file is checked first; if it isn't a
     * Caravan Leveller backup nothing is changed and an exception says why.
     */
    fun restore(ctx: Context, from: Uri) {
        val tmp = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val tmpAttach = File(tmp, "attachments").apply { mkdirs() }
        var garageText: String? = null
        var settingsText: String? = null
        try {
            val input = ctx.contentResolver.openInputStream(from) ?: error("Couldn't open the file")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val n = e.name
                    when {
                        e.isDirectory -> {}
                        n == GARAGE -> garageText = zip.readBytes().toString(Charsets.UTF_8)
                        n == SETTINGS -> settingsText = zip.readBytes().toString(Charsets.UTF_8)
                        n.startsWith(ATTACH) -> {
                            val name = n.removePrefix(ATTACH)
                            // Only plain file names, never paths that could reach outside the folder
                            if (name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && name != "." && name != "..") {
                                File(tmpAttach, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                }
            }
            val g = garageText ?: error("This isn't a Caravan Leveller backup")
            JSONObject(g) // must be readable before anything is replaced

            val attach = GarageStore.attachmentsDir(ctx)
            attach.deleteRecursively()
            attach.mkdirs()
            tmpAttach.listFiles()?.forEach { it.copyTo(File(attach, it.name), overwrite = true) }
            val garageTmp = File(ctx.filesDir, "$GARAGE.tmp")
            garageTmp.writeText(g)
            garageTmp.renameTo(File(ctx.filesDir, GARAGE))
            settingsText?.let { runCatching { applySettings(ctx, JSONObject(it)) } }
        } finally {
            tmp.deleteRecursively()
        }
        // Calendar entries and notifications follow the restored reminders
        CalendarSync.syncLater(ctx)
        Reminders.checkNow(ctx)
    }

    /** Settings that belong to the user's data. Phone-specific ones (calendar, updates) stay as they are. */
    private val skip = setOf("calendarId", "askedNotif")

    private fun settingsJson(ctx: Context): JSONObject {
        val o = JSONObject()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (k in skip) return@forEach
            when (v) {
                is Boolean -> o.put(k, JSONObject().put("b", v))
                is String -> o.put(k, JSONObject().put("s", v))
                is Int -> o.put(k, JSONObject().put("i", v))
                is Long -> o.put(k, JSONObject().put("l", v))
                is Float -> o.put(k, JSONObject().put("f", v.toDouble()))
            }
        }
        return o
    }

    private fun applySettings(ctx: Context, o: JSONObject) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        o.keys().forEach { k ->
            if (k in skip) return@forEach
            val v = o.optJSONObject(k) ?: return@forEach
            when {
                v.has("b") -> e.putBoolean(k, v.getBoolean("b"))
                v.has("s") -> e.putString(k, v.getString("s"))
                v.has("i") -> e.putInt(k, v.getInt("i"))
                v.has("l") -> e.putLong(k, v.getLong("l"))
                v.has("f") -> e.putFloat(k, v.getDouble("f").toFloat())
            }
        }
        e.apply()
    }
}
