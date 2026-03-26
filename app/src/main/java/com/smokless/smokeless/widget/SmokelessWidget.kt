package com.smokless.smokeless.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.smokless.smokeless.MainActivity
import com.smokless.smokeless.R
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.util.ScoreCalculator

class SmokelessWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_smokeless)

            // Open app on tap
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_timer, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_label, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_streak, pendingIntent)

            // Fetch data on background thread
            AppDatabase.databaseExecutor.execute {
                try {
                    val db = AppDatabase.getInstance(context)
                    val lastTimestamp = db.smokingSessionDao().getLastTimestamp() ?: 0L
                    val timeSince = ScoreCalculator.calculateTimeSinceLastSmoke(lastTimestamp)

                    // Calculate target interval for countdown
                    val sessions = db.smokingSessionDao().getAllSessions()
                    val prefs = context.getSharedPreferences("SmokelessPrefs", Context.MODE_PRIVATE)
                    val difficulty = prefs.getInt("difficultyLevel", 0)
                    val targetInterval = ScoreCalculator.calculateTargetInterval(sessions, difficulty)

                    val remaining = targetInterval - timeSince

                    val timerText: String
                    val labelText: String

                    if (targetInterval <= 0L) {
                        // No data yet — show elapsed time
                        val hours = timeSince / 3_600_000
                        val minutes = (timeSince % 3_600_000) / 60_000
                        timerText = "${hours}h ${minutes}m"
                        labelText = "SMOKE-FREE FOR"
                    } else if (remaining > 0) {
                        // Counting down
                        val hours = remaining / 3_600_000
                        val minutes = (remaining % 3_600_000) / 60_000
                        timerText = when {
                            hours >= 24 -> {
                                val days = hours / 24
                                val remainingHours = hours % 24
                                "${days}d ${remainingHours}h"
                            }
                            else -> "${hours}h ${minutes}m"
                        }
                        labelText = "WAIT BEFORE NEXT"
                    } else {
                        // Bonus time
                        val bonus = -remaining
                        val hours = bonus / 3_600_000
                        val minutes = (bonus % 3_600_000) / 60_000
                        timerText = "+${hours}h ${minutes}m"
                        labelText = "BONUS TIME"
                    }

                    val streakText = when {
                        targetInterval <= 0L -> "Log smokes to start"
                        remaining > 0 -> "Hold on, you've got this!"
                        else -> "Target reached!"
                    }

                    views.setTextViewText(R.id.widget_timer, timerText)
                    views.setTextViewText(R.id.widget_label, labelText)
                    views.setTextViewText(R.id.widget_streak, streakText)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (_: Exception) {
                    views.setTextViewText(R.id.widget_timer, "--")
                    views.setTextViewText(R.id.widget_streak, "Tap to open")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }

            // Update immediately with pending intent (before DB query finishes)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
