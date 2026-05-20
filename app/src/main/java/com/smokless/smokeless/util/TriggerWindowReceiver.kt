package com.smokless.smokeless.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.data.entity.Substance
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Hourly check that fires a heads-up notification when wall-clock enters one
 * of the user's trigger windows. Closes the "only fires when app is open" gap
 * in the in-app trigger-window banner — the anticipatory loop should reach
 * the user *before* they reach for the phone.
 *
 * Scheduling uses AlarmManager.setInexactRepeating; the system batches these
 * to save battery, so slop of 15–30 min is expected and fine for hourly
 * granularity. Dedupe is per-day-per-slot to avoid double-firing on the same
 * peak hour after a reschedule.
 */
class TriggerWindowReceiver : BroadcastReceiver() {

    companion object {
        private const val PREF_NAME = "SmokelessPrefs"
        private const val KEY_ENABLED = "triggerHeadsUpEnabled"
        private const val KEY_LAST_NOTIFIED_SLOT = "triggerLastNotifiedSlot"
        private const val REQUEST_CODE = 9002

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
            if (enabled) schedule(context) else cancel(context)
        }

        /** Schedules the hourly check starting at the next top-of-hour. No-op if disabled. */
        fun schedule(context: Context) {
            if (!isEnabled(context)) return
            val triggerAt = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context) ?: return
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                AlarmManager.INTERVAL_HOUR,
                pi,
            )
        }

        fun cancel(context: Context) {
            val pi = pendingIntent(context, createIfMissing = false) ?: return
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pi)
        }

        private fun pendingIntent(context: Context, createIfMissing: Boolean = true): PendingIntent? {
            val intent = Intent(context, TriggerWindowReceiver::class.java)
            val flags = if (createIfMissing) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            }
            return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (!isEnabled(context)) return

        // DB access on background thread — receiver runs on main.
        val pendingResult = goAsync()
        AppDatabase.databaseExecutor.execute {
            try {
                evaluate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun evaluate(context: Context) {
        val db = AppDatabase.getInstance(context)
        val sessions = db.smokingSessionDao().getAllSessions()
        if (sessions.size < 5) return

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val nowHour = cal.get(Calendar.HOUR_OF_DAY)

        val windows = ScoreCalculator.calculateTriggerWindows(sessions, now)
        val firing = windows.filter { tw ->
            // Notify when the *current* hour is a peak. The alarm fires near
            // the top of each hour, so this reads as "your window just opened."
            nowHour in tw.peakHours
        }
        if (firing.isEmpty()) return

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val slotKey = String.format(
            "%04d-%02d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            nowHour,
        )
        if (prefs.getString(KEY_LAST_NOTIFIED_SLOT, null) == slotKey) return
        prefs.edit().putString(KEY_LAST_NOTIFIED_SLOT, slotKey).apply()

        // Quiet hours: don't wake the user 23:00–07:00 unless they explicitly
        // smoke during those hours and the peak survived the threshold. Even
        // then, an alarm fire is fine if it slopped past midnight by a few
        // minutes; just don't push notifications between 0 and 6 inclusive.
        if (nowHour in 0..6) return

        val substanceLabel = firing.joinToString(" + ") {
            when (it.substance) {
                Substance.TOBACCO -> "tobacco"
                Substance.CANNABIS -> "cannabis"
            }
        }
        val tactic = CravingTactics.random()
        NotificationHelper.showTriggerWindowNotification(
            context = context,
            slotHour = nowHour,
            substanceLabel = substanceLabel,
            tactic = tactic,
        )

        // Stale-data hygiene: if a user hasn't logged for 14 days, don't keep
        // firing on their historical pattern. The 5-session minimum above
        // protects new users; this protects long-absent ones.
        val day = TimeUnit.DAYS.toMillis(1)
        val mostRecent = sessions.maxOf { it.timestamp }
        if (now - mostRecent > 14 * day) {
            cancel(context)
        }
    }
}
