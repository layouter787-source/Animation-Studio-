package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingTest {
    private val tol = 0.01f
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
    }

    @Test fun cameraInterpolatesBetweenKeys() {
        val t = Timing().withHold(20L, 3)
            .withKey(10L, CameraKey(100f, 100f, 1f, 0f, Ease.LINEAR))
            .withKey(30L, CameraKey(300f, 100f, 4f, 1f, Ease.LINEAR))
        val mid = TimingMath.cameraAt(ids, t, 2f, 400f, 300f)
        assertEquals(200f, mid.cx, tol)
        assertEquals(2f, mid.zoom, tol)
        assertEquals(0.5f, mid.rotation, tol)
        assertEquals(100f, TimingMath.cameraAt(ids, t, -5f, 400f, 300f).cx, tol)
        assertEquals(300f, TimingMath.cameraAt(ids, t, 40f, 400f, 300f).cx, tol)
        val none = TimingMath.cameraAt(ids, Timing(), 3f, 400f, 300f)
        assertEquals(200f, none.cx, tol)
        assertEquals(150f, none.cy, tol)
    }

    @Test fun smoothEasingStartsSlowly() {
        val t = Timing().withKey(10L, CameraKey(0f, 0f, 1f, 0f, Ease.SMOOTH)).withKey(30L, CameraKey(100f, 0f, 1f, 0f))
        val early = TimingMath.cameraAt(ids, t, 1f, 400f, 300f)
        assertTrue(early.cx < 25f)
    }

    @Test fun cameraAffineCentersAndFramesTheView() {
        val k = CameraKey(120f, 80f, 2f, 0.5f)
        val a = k.outputAffine(400f, 300f)
        val c = a.map(Point(120f, 80f))
        assertEquals(200f, c.x, tol)
        assertEquals(150f, c.y, tol)
        val rect = k.rect(400f, 300f)
        assertEquals(4, rect.size)
        val corners = listOf(Point(0f, 0f), Point(400f, 0f), Point(400f, 300f), Point(0f, 300f))
        for (i in 0..3) {
            val o = a.map(rect[i])
            assertEquals(corners[i].x, o.x, 0.05f)
            assertEquals(corners[i].y, o.y, 0.05f)
        }
        val whole = CameraKey.default(400f, 300f).outputAffine(400f, 300f).map(Point(37f, 11f))
        assertEquals(37f, whole.x, tol)
        assertEquals(11f, whole.y, tol)
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
