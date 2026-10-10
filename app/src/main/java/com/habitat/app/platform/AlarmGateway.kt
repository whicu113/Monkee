package com.habitat.app.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Platform gateway interface for scheduling, cancelling, and querying alarm intents.
 * SPEC Section 8.1 & 8.7.
 */
interface AlarmGateway {
    fun scheduleAlarm(context: Context, habitId: String, actionPath: String, triggerAtMillis: Long, logicalDateExtra: String? = null)
    fun cancelAlarm(context: Context, habitId: String, actionPath: String = "alarm")
    fun isAlarmScheduled(context: Context, habitId: String, actionPath: String = "alarm"): Boolean

    fun scheduleDayBoundaryAlarm(context: Context, triggerAtMillis: Long)
    fun cancelDayBoundaryAlarm(context: Context)
    fun isDayBoundaryAlarmScheduled(context: Context): Boolean
}

@Singleton
class SystemAlarmGateway @Inject constructor() : AlarmGateway {

    companion object {
        const val DAY_BOUNDARY_REQUEST_CODE = 9001
    }

    override fun scheduleAlarm(
        context: Context,
        habitId: String,
        actionPath: String,
        triggerAtMillis: Long,
        logicalDateExtra: String?
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("habitat://reminder/$habitId/$actionPath")
            putExtra(NotificationHelper.EXTRA_HABIT_ID, habitId)
            if (logicalDateExtra != null) {
                putExtra(NotificationHelper.EXTRA_LOGICAL_DATE, logicalDateExtra)
            }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    override fun cancelAlarm(context: Context, habitId: String, actionPath: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("habitat://reminder/$habitId/$actionPath")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    override fun isAlarmScheduled(context: Context, habitId: String, actionPath: String): Boolean {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("habitat://reminder/$habitId/$actionPath")
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null
    }

    override fun scheduleDayBoundaryAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DayBoundaryReceiver::class.java).apply {
            action = DayBoundaryReceiver.ACTION_DAY_BOUNDARY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAY_BOUNDARY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    override fun cancelDayBoundaryAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DayBoundaryReceiver::class.java).apply {
            action = DayBoundaryReceiver.ACTION_DAY_BOUNDARY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAY_BOUNDARY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    override fun isDayBoundaryAlarmScheduled(context: Context): Boolean {
        val intent = Intent(context, DayBoundaryReceiver::class.java).apply {
            action = DayBoundaryReceiver.ACTION_DAY_BOUNDARY
        }
        return PendingIntent.getBroadcast(
            context,
            DAY_BOUNDARY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null
    }
}
