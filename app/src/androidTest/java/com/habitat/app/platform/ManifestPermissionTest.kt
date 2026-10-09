package com.habitat.app.platform

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying that POST_NOTIFICATIONS and RECEIVE_BOOT_COMPLETED
 * are declared in the installed app manifest.
 * SPEC Section 8.1.
 */
@RunWith(AndroidJUnit4::class)
class ManifestPermissionTest {

    @Test
    fun manifestContainsPostNotificationsAndReceiveBootCompleted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )

        val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()

        assertTrue(
            "Manifest must request android.permission.POST_NOTIFICATIONS",
            requestedPermissions.contains("android.permission.POST_NOTIFICATIONS")
        )

        assertTrue(
            "Manifest must request android.permission.RECEIVE_BOOT_COMPLETED",
            requestedPermissions.contains("android.permission.RECEIVE_BOOT_COMPLETED")
        )
    }
}
