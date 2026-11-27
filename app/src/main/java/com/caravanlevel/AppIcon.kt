package com.caravanlevel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Icon as AIcon
import android.net.Uri
import android.os.Build

/**
 * The app icon: Android only lets an app pick between icons built into it, so each choice is
 * an activity alias and exactly one is switched on. A photo can't become the real app icon,
 * but it can be a home screen shortcut that opens the app.
 */
object AppIcon {
    val CHOICES = listOf(".MainActivity" to "Caravan", ".IconMotorhome" to "Motorhome", ".IconCampervan" to "Campervan")

    private fun component(ctx: Context, alias: String) = ComponentName(ctx.packageName, ctx.packageName + alias)

    fun current(ctx: Context): Int {
        val pm = ctx.packageManager
        val i = CHOICES.indexOfFirst { (alias, _) ->
            when (pm.getComponentEnabledSetting(component(ctx, alias))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> alias == ".MainActivity" // on by default
                else -> false
            }
        }
        return if (i < 0) 0 else i
    }

    fun set(ctx: Context, index: Int) {
        val pm = ctx.packageManager
        // switch the new one on first, so there's always an icon
        CHOICES.forEachIndexed { i, (alias, _) ->
            if (i == index) pm.setComponentEnabledSetting(component(ctx, alias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        CHOICES.forEachIndexed { i, (alias, _) ->
            if (i != index) pm.setComponentEnabledSetting(component(ctx, alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    fun shortcutsSupported(ctx: Context) = Build.VERSION.SDK_INT >= 26 &&
        ctx.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    /** Ask the home screen to add a shortcut with this picture. Returns false if it couldn't. */
    fun pinPhoto(ctx: Context, uri: Uri, label: String): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return false
        val bmp = squareBitmap(ctx, uri) ?: return false
        val info = ShortcutInfo.Builder(ctx, "photo-" + System.currentTimeMillis())
            .setShortLabel(label.take(10).ifBlank { "Caravan" })
            .setLongLabel(label.ifBlank { "Caravan Leveller" })
            .setIcon(AIcon.createWithAdaptiveBitmap(bmp))
            .setIntent(Intent(ctx, HomeActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
        return sm.requestPinShortcut(info, null)
    }

    /** The middle square of the picture, sized for an adaptive icon (432 px with a margin). */
    private fun squareBitmap(ctx: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 432) sample *= 2
        val src = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: null
        src?.let {
            val side = minOf(it.width, it.height)
            val out = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
            // adaptive icons show the middle two thirds, so the photo fills the whole square
            Canvas(out).drawBitmap(it, Rect((it.width - side) / 2, (it.height - side) / 2,
                (it.width + side) / 2, (it.height + side) / 2), Rect(0, 0, 432, 432), null)
            out
        }
    } catch (_: Exception) { null }
}
