package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraTest {
    private val tol = 0.01f
    private val ids = listOf(10L, 20L, 30L, 40L)

    @Test fun interpolatesBetweenKeysByFrame() {
        val t = CameraTrack()
            .withKey(10L, CameraKey(100f, 100f, 1f, 0f, Ease.LINEAR))
            .withKey(40L, CameraKey(400f, 100f, 4f, 1.5f, Ease.LINEAR))
        val mid = CameraMath.at(ids, t, 1.5f, 800f, 600f)
        assertEquals(250f, mid.cx, tol)
        assertEquals(2f, mid.zoom, tol)
        assertEquals(0.75f, mid.rotation, tol)
        assertEquals(100f, CameraMath.at(ids, t, -5f, 800f, 600f).cx, tol)
        assertEquals(400f, CameraMath.at(ids, t, 9f, 800f, 600f).cx, tol)
        val none = CameraMath.at(ids, CameraTrack(), 2f, 800f, 600f)
        assertEquals(400f, none.cx, tol)
        assertEquals(300f, none.cy, tol)
    }

    @Test fun smoothEasingStartsSlowly() {
        val t = CameraTrack().withKey(10L, CameraKey(0f, 0f, 1f, 0f, Ease.SMOOTH)).withKey(40L, CameraKey(300f, 0f, 1f, 0f))
        assertTrue(CameraMath.at(ids, t, 0.5f, 800f, 600f).cx < 40f)
        assertEquals(150f, CameraMath.at(ids, t, 1.5f, 800f, 600f).cx, tol)
    }

    @Test fun keyIsAnchoredToTheFrameNotTheIndex() {
        val t = CameraTrack().withKey(30L, CameraKey(50f, 50f, 2f, 0f))
        val afterInsert = listOf(10L, 99L, 20L, 30L, 40L)
        assertEquals(50f, CameraMath.at(afterInsert, t, 3f, 800f, 600f).cx, tol)
        assertEquals(50f, CameraMath.at(afterInsert, t, 0f, 800f, 600f).cx, tol)
        assertEquals(CameraTrack(), t.withoutKey(30L))
    }

    @Test fun affineCentersAndFramesTheView() {
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
