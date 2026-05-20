package com.smokless.smokeless.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smokless.smokeless.data.AppDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Weekly recap nudge — fires Sunday at 18:00 local time to invite the user
 * back into the home screen's "This week" card. The digest itself is always
 * computed; this is just the discovery mechanism for users who don't open
 * the app on a typical Sunday evening.
 *
 * Once-a-week cadence keeps it quiet. The notification body previews the
 * two numbers that matter most (held vs. smoked) so the user can decide
 * whether to dig in without opening the app.
 */
class WeeklyDigestReceiver : BroadcastReceiver() {

    companion object {
        private const val PREF_NAME = "SmokelessPrefs"
        private const val KEY_ENABLED = "weeklyDigestEnabled"
        private const val KEY_LAST_FIRED_WEEK = "weeklyDigestLastFiredWeek"
        private const val REQUEST_CODE = 9003

        /** Sunday at 18:00, in Calendar terms. */
        private const val FIRE_DAY = Calendar.SUNDAY
        private const val FIRE_HOUR = 18

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
            if (enabled) schedule(context) else cancel(context)
        }

        /** Schedule the next weekly fire. No-op if disabled. */
        fun schedule(context: Context) {
            if (!isEnabled(context)) return
            val triggerAt = nextSundayEvening()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context) ?: return
            // Once-weekly so even inexact scheduling can't drift the user into
            // a bad time-of-day. Using setRepeating with WEEK interval lets the
            // OS batch with other work; the slop window is wide but day-of-week
            // is preserved.
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                AlarmManager.INTERVAL_DAY * 7,
                pi,
            )
        }

        fun cancel(context: Context) {
            val pi = pendingIntent(context, createIfMissing = false) ?: return
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pi)
        }

        private fun nextSundayEvening(now: Long = System.currentTimeMillis()): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, FIRE_HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // Walk forward to Sunday 18:00; if we're already past it today, go next week.
            while (cal.get(Calendar.DAY_OF_WEEK) != FIRE_DAY || cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        private fun pendingIntent(context: Context, createIfMissing: Boolean = true): PendingIntent? {
            val intent = Intent(context, WeeklyDigestReceiver::class.java)
            val flags = if (createIfMissing) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            }
            return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        }

        private fun isoWeekKey(now: Long = System.currentTimeMillis()): String {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            // ISO week year/number — stable across year boundaries.
            val year = cal.getWeekYear()
            val week = cal.get(Calendar.WEEK_OF_YEAR)
            return String.format("%04d-W%02d", year, week)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (!isEnabled(context)) return
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
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val weekKey = isoWeekKey(now)
        if (prefs.getString(KEY_LAST_FIRED_WEEK, null) == weekKey) return

        val db = AppDatabase.getInstance(context)
        val sessions = db.smokingSessionDao().getAllSessions()
        if (sessions.isEmpty()) return

        // Don't nudge users who haven't logged in a long time — the recap
        // would mostly be stale zeros, and the unsolicited ping feels worse
        // than skipping it.
        val mostRecent = sessions.maxOf { it.timestamp }
        val day = TimeUnit.DAYS.toMillis(1)
        if (now - mostRecent > 14 * day) return

        val cravings = db.cravingDao().getAllCravings()
        val primary = SubstanceCopy.primarySubstance(sessions)
        val digest = ScoreCalculator.calculateWeeklyDigest(
            sessions = sessions,
            cravings = cravings,
            primarySubstance = primary,
            nowMs = now,
        )
        val copy = SubstanceCopy.forSubstance(primary)

        prefs.edit().putString(KEY_LAST_FIRED_WEEK, weekKey).apply()
        NotificationHelper.showWeeklyDigestNotification(context, digest, copy)
    }
}
