package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class QuadTransformTest {
    private val sq = listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 10f))

    @Test fun centroidIsDiagonalIntersection() {
        val c = QuadTransform.centroid(sq)
        assertEquals(5f, c.x, 1e-4f)
        assertEquals(5f, c.y, 1e-4f)
    }

    @Test fun draggingTopEdgeStretchesAlongNormalOnly() {
        val r = QuadTransform.dragEdge(sq, 0, 4f, -3f)
        assertEquals(-3f, r[0].y, 1e-4f)
        assertEquals(-3f, r[1].y, 1e-4f)
        assertEquals(0f, r[0].x, 1e-4f)
        assertEquals(10f, r[1].x, 1e-4f)
        assertEquals(10f, r[2].y, 1e-4f)
        assertEquals(10f, r[3].y, 1e-4f)
    }

    @Test fun draggingEdgeInwardCompresses() {
        val r = QuadTransform.dragEdge(sq, 1, -4f, 0f)
        assertEquals(6f, r[1].x, 1e-4f)
        assertEquals(6f, r[2].x, 1e-4f)
    }

    @Test fun rotationKeepsDistanceToPivot() {
        val pv = Point(2f, 3f)
        val r = QuadTransform.rotate(sq, pv, 0.7f)
        for (i in 0 until 4) {
            val d0 = hypot(sq[i].x - pv.x, sq[i].y - pv.y)
            val d1 = hypot(r[i].x - pv.x, r[i].y - pv.y)
            assertEquals(d0, d1, 1e-3f)
        }
    }

    @Test fun outwardNormalPointsAway() {
        val n = QuadTransform.outwardNormal(sq, 2)
        assertEquals(0f, n.x, 1e-4f)
        assertEquals(1f, n.y, 1e-4f)
    }

    @Test fun hitFindsCornerPivotAndBody() {
        val v = ViewTransform(1f, 0f, 0f, 0f)
        val pv = QuadTransform.centroid(sq)
        assertEquals(QuadTransform.CORNER0 + 2, QuadTransform.hit(sq, pv, v, 10f, 10f, 3f))
        assertEquals(QuadTransform.PIVOT, QuadTransform.hit(sq, pv, v, 5f, 5f, 3f))
        assertEquals(QuadTransform.EDGE0 + 1, QuadTransform.hit(sq, pv, v, 10f, 5f, 3f))
        assertEquals(QuadTransform.BODY, QuadTransform.hit(sq, pv, v, 8f, 2f, 1f))
        assertEquals(QuadTransform.NONE, QuadTransform.hit(sq, pv, v, 50f, 50f, 1f))
    }

    @Test fun areaOfSquare() {
        assertTrue(kotlin.math.abs(QuadTransform.area(sq) - 100f) < 1e-3f)
    }
}
