package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewTransformTest {
    private val tol = 0.01f
    private fun assertPoint(e: Point, a: Point) {
        assertEquals(e.x, a.x, tol)
        assertEquals(e.y, a.y, tol)
    }

    @Test fun roundTripWithScaleRotationTranslation() {
        val v = ViewTransform(1.7f, 0.63f, 120f, -45f)
        val p = Point(123.4f, 56.7f)
        assertPoint(p, v.screenToProject(v.projectToScreen(p)))
    }

    @Test fun zoomKeepsFocusStableWithRotation() {
        val v = ViewTransform(1.3f, 0.4f, 50f, -20f)
        val focus = Point(300f, 200f)
        val before = v.screenToProject(focus)
        val z = v.zoomAround(focus, 2f)
        assertEquals(2.6f, z.scale, 1e-4f)
        assertPoint(before, z.screenToProject(focus))
    }

    @Test fun rotateKeepsFocusStable() {
        val v = ViewTransform(2f, 0.1f, 10f, 10f)
        val focus = Point(400f, 250f)
        val before = v.screenToProject(focus)
        assertPoint(before, v.rotateAround(focus, 0.9f).screenToProject(focus))
    }

    @Test fun zoomIsClampedToLimits() {
        val v = ViewTransform()
        assertEquals(ViewTransform.MAX_SCALE, v.zoomAround(Point(0f, 0f), 1000f).scale, 1e-4f)
        assertEquals(ViewTransform.MIN_SCALE, v.zoomAround(Point(0f, 0f), 0.0001f).scale, 1e-4f)
    }

    @Test fun panOnlyChangesTranslation() {
        val v = ViewTransform(2f, 0.5f, 1f, 2f).panBy(10f, -5f)
        assertEquals(2f, v.scale, 0f)
        assertEquals(0.5f, v.rotationRad, 0f)
        assertEquals(11f, v.tx, 0f)
        assertEquals(-3f, v.ty, 0f)
    }

    @Test fun fitSheetShowsWholeSheetCentered() {
        val v = ViewTransform.fitSheet(1280f, 720f, 800f, 400f, 16f)
        val tl = v.projectToScreen(Point(0f, 0f))
        val br = v.projectToScreen(Point(1280f, 720f))
        assertTrue(tl.x >= 0f && tl.y >= 0f && br.x <= 800f && br.y <= 400f)
        assertEquals(tl.x, 800f - br.x, tol)
        assertEquals(tl.y, 400f - br.y, tol)
    }

    @Test fun repeatedZoomDoesNotDrift() {
        var v = ViewTransform(1f, 0.3f, 40f, 40f)
        val focus = Point(250f, 180f)
        val anchor = v.screenToProject(focus)
        repeat(100) { v = v.zoomAround(focus, 1.05f).zoomAround(focus, 1f / 1.05f) }
        assertEquals(1f, v.scale, 1e-3f)
        assertPoint(anchor, v.screenToProject(focus))
    }
}
