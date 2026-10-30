package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeSmoothingTest {
    private val zigzag = List(20) { Point(it * 10f, if (it % 2 == 0) 0f else 6f) }

    @Test fun zeroPercentKeepsPoints() {
        assertTrue(StrokeSmoothing.smooth(zigzag, 0f) === zigzag)
    }

    @Test fun endpointsArePreserved() {
        val s = StrokeSmoothing.smooth(zigzag, 1f)
        assertEquals(zigzag.first().x, s.first().x, 0.0001f)
        assertEquals(zigzag.first().y, s.first().y, 0.0001f)
        assertEquals(zigzag.last().x, s.last().x, 0.0001f)
        assertEquals(zigzag.last().y, s.last().y, 0.0001f)
    }

    @Test fun moreSmoothingReducesZigzag() {
        fun roughness(p: List<Point>): Float {
            var t = 0f
            for (i in 1 until p.size) t += kotlin.math.abs(p[i].y - p[i - 1].y)
            return t / p.size
        }
        val low = StrokeSmoothing.smooth(zigzag, 0.2f)
        val high = StrokeSmoothing.smooth(zigzag, 1f)
        assertTrue(roughness(high) < roughness(low))
        assertTrue(roughness(low) < roughness(zigzag))
    }

    @Test fun pointCountIsBounded() {
        val long = List(5000) { Point(it.toFloat(), (it % 7).toFloat()) }
        assertTrue(StrokeSmoothing.smooth(long, 1f).size <= StrokeSmoothing.MAX_POINTS * 2)
    }
}
