package com.caravanlevel

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar

/**
 * Checks reminders once a day (around 9 am) and after the phone restarts, and shows a
 * notification two weeks before something is due and again when it is due.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    const val SOON_DAYS = 14

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val first = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    /** Check now (quick: it only reads one small file). Errors never stop the app. */
    fun checkNow(ctx: Context) {
        try { check(ctx.applicationContext) } catch (_: Exception) {}
    }

    fun notificationsAllowed(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
    }

    @Synchronized
    fun check(ctx: Context) {
        if (!notificationsAllowed(ctx)) return
        val g = GarageStore.load(ctx)
        var changed = false
        for (v in g.vehicles) for (r in v.reminders) {
            val days = Due.days(v, r) ?: continue
            when {
                days <= 0 && !r.notifiedDue -> {
                    notify(ctx, v, r, if (days == 0) "Due today" else "Overdue: was due ${Units.inDays(days)}")
                    r.notifiedDue = true; r.notifiedSoon = true; changed = true
                }
                days in 1..SOON_DAYS && !r.notifiedSoon -> {
                    notify(ctx, v, r, "Due ${Units.inDays(days)}")
                    r.notifiedSoon = true; changed = true
                }
            }
        }
        if (changed) GarageStore.save(ctx, g)
    }

    private fun notify(ctx: Context, v: Vehicle, r: Reminder, line: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = Intent(ctx, HomeActivity::class.java)
            .putExtra(HomeActivity.EXTRA_SECTION, Section.REMINDERS.name)
            .putExtra(HomeActivity.EXTRA_VEHICLE, v.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, r.id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(ctx)
        val n = b.setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${r.title}: ${v.name}")
            .setContentText(line)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(r.id.hashCode(), n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val CHECK = "com.caravanlevel.CHECK_REMINDERS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.schedule(context)
        val pending = goAsync()
        Thread {
            try { Reminders.check(context.applicationContext) } catch (_: Exception) {}
            try { AutoBackup.runIfDue(context.applicationContext) } catch (_: Exception) {}
            pending.finish()
        }.start()
    }
}
