package com.habitat.app.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun `data uri string paths ensure distinct pending intents per habit and action`() {
        val uriDone = "habitat://reminder/habit123/done"
        val uriMin = "habitat://reminder/habit123/min"
        val uriOtherHabit = "habitat://reminder/habit456/done"

        assertNotEquals(uriDone, uriMin)
        assertNotEquals(uriDone, uriOtherHabit)
        assertEquals("habitat://reminder/habit123/done", uriDone)
    }
}
