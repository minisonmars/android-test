package com.caravanlevel

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Automatic backup: one .zip file the user picks once (best on Google Drive or OneDrive so it
 * survives a new phone), overwritten every day or week when something has changed. Runs from
 * the daily 9am reminder check and when the app opens. Its settings are kept in their own
 * preferences so they aren't copied into the backup (the file link only works on this phone).
 */
object AutoBackup {
    private const val PREFS = "autobackup"
    const val FILE_NAME = "Caravan Leveller auto backup.zip"
    val CHOICES = listOf(1 to "Every day (when something changed)", 7 to "Every week (when something changed)", 0 to "Off")

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun uri(ctx: Context): Uri? = p(ctx).getString("uri", null)?.let { Uri.parse(it) }
    fun days(ctx: Context) = p(ctx).getInt("days", 1)
    fun setDays(ctx: Context, d: Int) = p(ctx).edit().putInt("days", d).apply()
    fun lastMs(ctx: Context) = p(ctx).getLong("last", 0L)
    fun lastError(ctx: Context): String? = p(ctx).getString("error", null)
    fun where(ctx: Context): String = p(ctx).getString("where", "") ?: ""
    private fun asked(ctx: Context) = p(ctx).getBoolean("asked", false)
    fun setAsked(ctx: Context) = p(ctx).edit().putBoolean("asked", true).apply()

    /** Keep the chosen file: permission that lasts after restarts, then back up straight away. */
    fun chosen(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) {}
        // let go of the old file's permission
        uri(ctx)?.takeIf { it != uri }?.let {
            try { ctx.contentResolver.releasePersistableUriPermission(it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
        }
        p(ctx).edit().putString("uri", uri.toString()).putString("where", describe(ctx, uri))
            .putBoolean("asked", true).putLong("last", 0L).remove("error").apply()
        if (days(ctx) == 0) setDays(ctx, 1)
    }

    /** "Google Drive", "OneDrive", "this phone"... from the app that holds the file. */
    private fun describe(ctx: Context, uri: Uri): String {
        val name = try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) { null } ?: FILE_NAME
        val place = when {
            uri.authority?.contains("google.android.apps.docs") == true -> "Google Drive"
            uri.authority?.contains("skydrive") == true || uri.authority?.contains("onedrive") == true -> "OneDrive"
            uri.authority?.contains("dropbox") == true -> "Dropbox"
            uri.authority == "com.android.externalstorage.documents" ||
                uri.authority == "com.android.providers.downloads.documents" -> "this phone"
            else -> null
        }
        return if (place != null) "$name on $place" else name
    }

    /** Saved only on the phone itself, so it would be lost with the phone. */
    fun onPhoneOnly(ctx: Context) = where(ctx).endsWith("on this phone")

    /** Newest change to the vehicles, trips or any photo/file. */
    private fun lastChange(ctx: Context): Long {
        val dir = GarageStore.attachmentsDir(ctx)
        return maxOf(File(ctx.filesDir, "garage.json").lastModified(), dir.lastModified(),
            dir.listFiles()?.maxOfOrNull { it.lastModified() } ?: 0L)
    }

    private fun due(ctx: Context): Boolean {
        val d = days(ctx)
        if (uri(ctx) == null || d <= 0) return false
        val last = lastMs(ctx)
        if (lastChange(ctx) <= last && lastError(ctx) == null) return false
        return System.currentTimeMillis() - last >= d * 24 * 3600_000L - 2 * 3600_000L
    }

    /** Back up now if it's time. Call off the main thread. */
    @Synchronized
    fun runIfDue(ctx: Context) { if (due(ctx)) run(ctx) }

    /** Back up now. Returns null if it worked, or what went wrong. Call off the main thread. */
    @Synchronized
    fun run(ctx: Context): String? {
        val u = uri(ctx) ?: return "No backup location chosen"
        return try {
            Backup.write(ctx, u)
            p(ctx).edit().putLong("last", System.currentTimeMillis()).remove("error").apply()
            null
        } catch (e: Exception) {
            val why = if (e is SecurityException) "the app no longer has permission to that file. Choose the location again."
                else e.message ?: "unknown problem"
            p(ctx).edit().putString("error", why).apply()
            why
        }
    }

    fun runInBackground(ctx: Context) {
        val app = ctx.applicationContext
        Thread { try { runIfDue(app) } catch (_: Exception) {} }.start()
    }

    private var offeredThisRun = false

    /** On first use: ask where to keep automatic backups (or restore one on a new phone). */
    fun offer(a: Activity) {
        if (offeredThisRun || asked(a) || uri(a) != null) return
        offeredThisRun = true
        AlertDialog.Builder(a)
            .setTitle("Automatic backup")
            .setMessage("Choose where the app keeps a backup of your vehicles, records, trips, photos and files. " +
                "It's updated automatically when something changes.\n\n" +
                "Google Drive, OneDrive or similar is best: the backup is kept safe if you lose or change your phone.\n\n" +
                "In the next screen, tap the menu (three lines, top left), pick Drive or OneDrive, then Save.\n\n" +
                "New phone? Pick \"I have a backup\" to restore it first.")
            .setPositiveButton("Choose where") { _, _ ->
                a.startActivity(Intent(a, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_DO, SettingsActivity.DO_CHOOSE_AUTO))
            }
            .setNegativeButton("Not now") { _, _ -> setAsked(a) }
            .setNeutralButton("I have a backup") { _, _ ->
                a.startActivity(Intent(a, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_DO, SettingsActivity.DO_RESTORE))
            }
            .setCancelable(false)
            .show()
    }
}
