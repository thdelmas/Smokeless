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

                    val hours = timeSince / 3_600_000
                    val minutes = (timeSince % 3_600_000) / 60_000

                    val timerText = when {
                        hours >= 24 -> {
                            val days = hours / 24
                            val remainingHours = hours % 24
                            "${days}d ${remainingHours}h"
                        }
                        else -> "${hours}h ${minutes}m"
                    }

                    // Calculate streak
                    val sessions = db.smokingSessionDao().getAllSessions()
                    val stats = ScoreCalculator.calculatePeriodStats(sessions, "all")
                    val streakText = when (stats.currentStreak) {
                        0 -> "Keep going!"
                        1 -> "1 day streak"
                        else -> "${stats.currentStreak} day streak"
                    }

                    views.setTextViewText(R.id.widget_timer, timerText)
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
