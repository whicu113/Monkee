package com.habitat.app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.habitat.app.MainActivity
import com.habitat.app.R
import com.habitat.app.domain.model.Habit
import java.time.LocalDate

/**
 * Platform helper for posting habit reminder notifications and summary notifications.
 * Uses deterministic Data URIs with FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE.
 * SPEC Section 8.1.
 */
object NotificationHelper {

    const val CHANNEL_ID = "habit_reminders"
    const val SUMMARY_TAG = "summary"
    const val SUMMARY_ID = 90000
    const val HABIT_NOTIFICATION_ID = 1

    const val ACTION_DONE = "com.habitat.app.ACTION_DONE"
    const val ACTION_MINIMUM = "com.habitat.app.ACTION_MINIMUM"
    const val ACTION_SNOOZE = "com.habitat.app.ACTION_SNOOZE"

    const val EXTRA_HABIT_ID = "extra_habit_id"
    const val EXTRA_LOGICAL_DATE = "extra_logical_date"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Habit Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for scheduled habits"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun postHabitNotification(
        context: Context,
        habit: Habit,
        logicalDate: LocalDate,
        snoozeMinutes: Int = 30
    ) {
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("habitat://reminder/${habit.id}/open")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_HABIT_ID, habit.id)
            putExtra(EXTRA_LOGICAL_DATE, logicalDate.toString())
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Done
        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            data = Uri.parse("habitat://reminder/${habit.id}/done")
            action = ACTION_DONE
            putExtra(EXTRA_HABIT_ID, habit.id)
            putExtra(EXTRA_LOGICAL_DATE, logicalDate.toString())
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Minimum
        val minIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            data = Uri.parse("habitat://reminder/${habit.id}/min")
            action = ACTION_MINIMUM
            putExtra(EXTRA_HABIT_ID, habit.id)
            putExtra(EXTRA_LOGICAL_DATE, logicalDate.toString())
        }
        val minPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            minIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            data = Uri.parse("habitat://reminder/${habit.id}/action_snooze")
            action = ACTION_SNOOZE
            putExtra(EXTRA_HABIT_ID, habit.id)
            putExtra(EXTRA_LOGICAL_DATE, logicalDate.toString())
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyText = buildString {
            if (habit.cue.isNotBlank()) append("After I ${habit.cue}. ")
            if (habit.minVersion.isNotBlank()) append("Min: ${habit.minVersion}. ")
            if (habit.fullVersion.isNotBlank()) append("Full: ${habit.fullVersion}.")
        }.ifBlank { "Time for your habit!" }

        val snoozeActionLabel = context.getString(R.string.snooze_action, snoozeMinutes)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${habit.emoji} ${habit.name}")
            .setContentText(copyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copyText))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Done", donePendingIntent)
            .addAction(0, "Min", minPendingIntent)
            .addAction(0, snoozeActionLabel, snoozePendingIntent)
            .build()

        manager.notify(habit.id, HABIT_NOTIFICATION_ID, notification)
    }

    fun postSummaryNotification(
        context: Context,
        overflowHabits: List<Habit>,
        logicalDate: LocalDate
    ) {
        if (overflowHabits.isEmpty()) return
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("habitat://reminder/summary/open")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_LOGICAL_DATE, logicalDate.toString())
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "You have ${overflowHabits.size} other habits scheduled today"
        val body = overflowHabits.joinToString(", ") { "${it.emoji} ${it.name}" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(SUMMARY_TAG, SUMMARY_ID, notification)
    }

    fun cancelNotification(context: Context, habitId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(habitId, HABIT_NOTIFICATION_ID)
    }
}
