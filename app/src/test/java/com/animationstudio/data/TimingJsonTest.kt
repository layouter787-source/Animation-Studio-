package com.animationstudio.data

import com.animationstudio.domain.Timing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimingJsonTest {
    @Test fun roundTrip() {
        val t = Timing(mapOf(2L to 3, 5L to 12))
        assertEquals(t, TimingJson.decode(TimingJson.encode(t)))
        assertEquals(Timing(), TimingJson.decode(TimingJson.encode(Timing())))
        assertNull(TimingJson.decode("{não é json"))
    }
}
