package com.habitat.app.platform.widget

import com.habitat.app.di.PlatformModule
import com.habitat.app.domain.WidgetUpdater
import dagger.Binds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetUpdaterBindingTest {
    @Test
    fun `production WidgetUpdater binding targets Glance implementation`() {
        val binding = PlatformModule::class.java.getDeclaredMethod(
            "bindWidgetUpdater",
            GlanceWidgetUpdater::class.java
        )

        assertTrue("row 'production WidgetUpdater binding' is a Hilt @Binds method", binding.isAnnotationPresent(Binds::class.java))
        assertEquals("row 'production WidgetUpdater binding' returns WidgetUpdater", WidgetUpdater::class.java, binding.returnType)
        assertEquals("row 'production WidgetUpdater binding' takes GlanceWidgetUpdater", GlanceWidgetUpdater::class.java, binding.parameterTypes.single())
        assertEquals(
            "row 'receiver widget class' remains HabitGlanceWidget",
            HabitGlanceWidget::class.java,
            habitWidgetForUpdate()::class.java
        )
    }
}
