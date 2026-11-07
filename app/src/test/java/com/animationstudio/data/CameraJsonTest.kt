package com.animationstudio.data

import com.animationstudio.domain.CameraKey
import com.animationstudio.domain.CameraTrack
import com.animationstudio.domain.Ease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraJsonTest {
    @Test fun roundTrip() {
        val t = CameraTrack(mapOf(2L to CameraKey(10.5f, 20f, 1.5f, 0.25f, Ease.LINEAR), 5L to CameraKey(0f, 1f, 2f, -1f)))
        assertEquals(t, CameraJson.decode(CameraJson.encode(t)))
        assertEquals(CameraTrack(), CameraJson.decode(CameraJson.encode(CameraTrack())))
        assertNull(CameraJson.decode("{não é json"))
    }
}
