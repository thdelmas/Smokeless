package com.smokless.smokeless.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val PREF_NAME = "SmokelessPrefs"
        private const val KEY_REMINDERS_ENABLED = "remindersEnabled"
        private const val KEY_REMINDER_HOUR = "reminderHour"
        private const val REQUEST_CODE = 9001

        fun schedule(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_REMINDERS_ENABLED, true)) return

            val hour = prefs.getInt(KEY_REMINDER_HOUR, 20) // default 8 PM

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pendingIntent != null) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastTimestamp = prefs.getLong("lastReminderCheck", 0L)

        // Avoid duplicate notifications within 12 hours
        if (System.currentTimeMillis() - lastTimestamp < 12 * 3600_000L) return
        prefs.edit().putLong("lastReminderCheck", System.currentTimeMillis()).apply()

        NotificationHelper.showDailyCheckIn(context)
    }
}
