package com.habitat.app.platform

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying Intent filterEquals behavior for PendingIntents.
 * SPEC Section 8.1.
 */
@RunWith(AndroidJUnit4::class)
class NotificationHelperTest {

    @Test
    fun intentsForDifferentHabitsAndActionsAreNotFilterEquals() {
        val intentDoneA = Intent(NotificationHelper.ACTION_DONE).apply {
            data = Uri.parse("habitat://reminder/habitA/done")
        }
        val intentMinA = Intent(NotificationHelper.ACTION_MINIMUM).apply {
            data = Uri.parse("habitat://reminder/habitA/min")
        }
        val intentDoneB = Intent(NotificationHelper.ACTION_DONE).apply {
            data = Uri.parse("habitat://reminder/habitB/done")
        }

        assertFalse(intentDoneA.filterEquals(intentMinA))
        assertFalse(intentDoneA.filterEquals(intentDoneB))
    }
}
