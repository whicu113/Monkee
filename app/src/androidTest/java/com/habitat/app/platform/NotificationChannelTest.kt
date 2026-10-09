package com.habitat.app.platform

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying that habit_reminders notification channel exists on startup.
 * SPEC Section 8.1.
 */
@RunWith(AndroidJUnit4::class)
class NotificationChannelTest {

    @Test
    fun notificationChannelExistsOnStartup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        NotificationHelper.createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
            assertNotNull("NotificationChannel habit_reminders must exist", channel)
        }
    }
}
