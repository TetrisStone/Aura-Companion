package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.EmotionType
import com.example.data.model.TimeOfDaySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Aura Companion", appName)
    }

    @Test
    fun `time of day slot correctly maps hours`() {
        assertEquals(TimeOfDaySlot.MORNING, TimeOfDaySlot.currentSlot(8))
        assertEquals(TimeOfDaySlot.AFTERNOON, TimeOfDaySlot.currentSlot(14))
        assertEquals(TimeOfDaySlot.EVENING, TimeOfDaySlot.currentSlot(19))
        assertEquals(TimeOfDaySlot.NIGHT, TimeOfDaySlot.currentSlot(2))
    }

    @Test
    fun `emotion type parsing and idle check`() {
        assertEquals(EmotionType.HAPPY, EmotionType.fromString("HAPPY"))
        assertEquals(EmotionType.THOUGHTFUL, EmotionType.fromString("thoughtful"))
        assertEquals(EmotionType.IDLE_PHONE, EmotionType.fromString("IDLE_PHONE"))
        assertTrue(EmotionType.IDLE_PHONE.isIdle)
        assertTrue(EmotionType.IDLE_BOOK.isIdle)
        assertFalse(EmotionType.HAPPY.isIdle)
    }
}
