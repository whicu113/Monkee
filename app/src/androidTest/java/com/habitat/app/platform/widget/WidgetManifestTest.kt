package com.habitat.app.platform.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetManifestTest {

    @Test
    fun appWidgetUpdateResolvesToAnExportedReceiverInTheAppPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .setPackage(context.packageName)
        val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)

        assertTrue(
            "APPWIDGET_UPDATE must resolve to a receiver in ${context.packageName}",
            receivers.any { it.activityInfo?.packageName == context.packageName }
        )
        assertTrue(
            "The APPWIDGET_UPDATE receiver must be exported",
            receivers.any {
                it.activityInfo?.packageName == context.packageName && it.activityInfo.exported
            }
        )
    }

    @Test
    fun appWidgetManagerListsAProviderWithPositiveMinimumDimensions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val providers = AppWidgetManager.getInstance(context)
            .getInstalledProvidersForPackage(context.packageName, Process.myUserHandle())

        assertTrue(
            "AppWidgetManager must list a provider for ${context.packageName}",
            providers.isNotEmpty()
        )

        val provider = providers.first()
        assertTrue("Provider must belong to ${context.packageName}", provider.provider.packageName == context.packageName)
        assertTrue("Provider minWidth must be greater than zero", provider.minWidth > 0)
        assertTrue("Provider minHeight must be greater than zero", provider.minHeight > 0)
        assertEquals("Provider fallback refresh period must be 30 minutes", 1_800_000, provider.updatePeriodMillis)
    }
}
