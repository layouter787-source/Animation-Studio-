package com.animationstudio.data

import com.animationstudio.domain.CameraKey
import com.animationstudio.domain.Ease
import com.animationstudio.domain.Timing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimingJsonTest {
    @Test fun roundTrip() {
        val t = Timing(mapOf(2L to 3, 5L to 12), mapOf(2L to CameraKey(10.5f, 20f, 1.5f, 0.25f, Ease.LINEAR), 5L to CameraKey(0f, 1f, 2f, -1f)))
        assertEquals(t, TimingJson.decode(TimingJson.encode(t)))
        assertEquals(Timing(), TimingJson.decode(TimingJson.encode(Timing())))
        assertNull(TimingJson.decode("{não é json"))
    }
}
