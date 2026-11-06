package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingTest {
    private val ids = listOf(10L, 20L, 30L)

    @Test fun holdsExpandTheTimeline() {
        val t = Timing().withHold(20L, 3)
        assertEquals(listOf(0, 1, 4), TimingMath.startTicks(ids, t).toList())
        assertEquals(5, TimingMath.totalTicks(ids, t))
        assertEquals(0, TimingMath.frameAtTick(ids, t, 0))
        assertEquals(1, TimingMath.frameAtTick(ids, t, 3))
        assertEquals(2, TimingMath.frameAtTick(ids, t, 4))
        assertEquals(2, TimingMath.frameAtTick(ids, t, 99))
        assertEquals(Timing(), t.withHold(20L, 1))
        assertEquals(Timing.MAX_HOLD, Timing().withHold(10L, 999).hold(10L))
        assertEquals(1, Timing().withHold(10L, -4).hold(10L))
    }

    @Test fun importImagesCreatesOneFrameEach() {
        val p0 = Project.initial()
        val dl = p0.layers[1].id
        val img = { n: Int -> ImageData(ImageSource(byteArrayOf(n.toByte()), 10, 10), Affine.IDENTITY) }
        val p = p0.importImages(dl, listOf(img(1), img(2), img(3)))
        assertEquals(4, p.frameCount)
        assertEquals(3, p.currentFrame)
        assertTrue(p.strokesAt(dl, 1).single().image != null)
        assertTrue(p.strokesAt(dl, 0).isEmpty())
    }
}
