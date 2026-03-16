package com.smokless.smokeless.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smokless.smokeless.MainActivity
import com.smokless.smokeless.R

object NotificationHelper {
    
    private const val CHANNEL_ID = "smokeless_milestones"
    private const val CHANNEL_NAME = "Milestones & Achievements"
    private const val CHANNEL_DESC = "Notifications for health milestones and achievements"
    
    private const val NOTIFICATION_ID_BASE = 1000
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showMilestoneNotification(context: Context, milestone: HealthMilestone) {
        createNotificationChannel(context)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("${milestone.icon} ${milestone.title} Achievement!")
            .setContentText(milestone.description)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${milestone.description}\n\nKeep up the great work! Your body is healing."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_BASE + milestone.hours,
                notification
            )
        } catch (e: SecurityException) {
            // Permission not granted, ignore
        }
    }
    
    fun showAchievementNotification(context: Context, achievement: Achievement) {
        createNotificationChannel(context)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("${achievement.icon} Achievement Unlocked!")
            .setContentText("${achievement.title} - ${achievement.description}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${achievement.title}\n${achievement.description}\n\nCongratulations on your progress!"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_BASE + achievement.id.hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            // Permission not granted, ignore
        }
    }
    
    fun showEncouragementNotification(context: Context, hours: Int) {
        createNotificationChannel(context)
        
        val messages = mapOf(
            24 to Pair("🌟 One Full Day!", "You've been smoke-free for 24 hours! Your body is already healing."),
            72 to Pair("💪 72 Hours Strong!", "Three days smoke-free! The hardest part is behind you."),
            168 to Pair("🎉 One Week!", "A full week without smoking! You're doing amazing!"),
            720 to Pair("🏆 One Month!", "30 days smoke-free! This is a huge achievement!"),
            2160 to Pair("🌈 Three Months!", "Quarter of a year smoke-free! Your health has significantly improved."),
            4320 to Pair("💎 Six Months!", "Half a year! You've proven you can live smoke-free."),
            8760 to Pair("🎊 One Year!", "An entire year without smoking! You're an inspiration!")
        )
        
        val message = messages[hours] ?: return
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(message.first)
            .setContentText(message.second)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.second))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_BASE + hours + 5000,
                notification
            )
        } catch (e: SecurityException) {
            // Permission not granted, ignore
        }
    }
}

