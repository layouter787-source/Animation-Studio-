package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Prova a ponte do traço de tinta (a lógica em si é testada em C++, studio_ink_test.cpp). */
class InkOutlineTest {
    private fun line(len: Float, ms: Float): List<Point> {
        val out = ArrayList<Point>()
        var t = 0f
        while (t <= ms) {
            out.add(Point(100f + len * t / ms, 100f, 1f, t))
            t += 8f
        }
        return out
    }

    /** Meia espessura vertical do contorno na abscissa [x]. */
    private fun halfWidthAt(poly: FloatArray, x: Float): Float {
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        var k = 0
        while (k < poly.size) {
            val n = poly[k].toInt()
            var mn = Float.MAX_VALUE
            var mx = -Float.MAX_VALUE
            var y0 = Float.MAX_VALUE
            var y1 = -Float.MAX_VALUE
            for (j in 0 until n) {
                val px = poly[k + 1 + 2 * j]
                val py = poly[k + 2 + 2 * j]
                mn = minOf(mn, px); mx = maxOf(mx, px); y0 = minOf(y0, py); y1 = maxOf(y1, py)
            }
            if (x in mn..mx) { top = minOf(top, y0); bottom = maxOf(bottom, y1) }
            k += 1 + 2 * n
        }
        return if (bottom < top) 0f else (bottom - top) / 2f
    }

    @Test fun fastStrokesEndInALongFineTipAndSlowOnesInAShortThickerOne() {
        val fast = InkOutline.polygons(line(300f, 150f), 4f, 1f)
        val slow = InkOutline.polygons(line(300f, 3000f), 4f, 1f)
        assertTrue(fast.isNotEmpty() && slow.isNotEmpty())
        assertTrue(halfWidthAt(fast, 360f) < halfWidthAt(slow, 360f))
        assertTrue(halfWidthAt(fast, 360f) < 1.6f)
        assertTrue(halfWidthAt(slow, 360f) > 1.9f)
        assertEquals(2f, halfWidthAt(fast, 250f), 0.25f)
    }

    @Test fun withoutTaperTheStrokeKeepsFullWidthToTheEnd() {
        val p = InkOutline.polygons(line(200f, 400f), 4f, 0f)
        assertEquals(2f, halfWidthAt(p, 298f), 0.25f)
    }

    @Test fun ropeLagsInsideItsLengthAndCatchesUpAtTheEnd() {
        val s = StrokeStabilizer(0.5f)   // corda de 20 px
        val first = s.start(Point(0f, 0f), 0)
        assertEquals(0f, first.x, 1e-6f)
        val inside = s.next(Point(5f, 0f), 8)
        assertEquals(0f, inside.x, 1e-6f)
        val pulled = s.next(Point(50f, 0f), 16)
        assertEquals(30f, pulled.x, 1e-3f)
        assertEquals(16f, pulled.t, 1e-6f)   // o ponto leva o tempo desde o início
        val tail = s.finish(Point(50f, 10f))
        assertEquals(50f, tail.last().x, 1e-6f)
        assertEquals(10f, tail.last().y, 1e-6f)
        assertTrue(tail.all { abs(it.y) < 30f })
        val off = StrokeStabilizer(0f)
        off.start(Point(1f, 1f), 0)
        assertEquals(7f, off.next(Point(7f, 9f), 8).x, 1e-6f)
    }

    @Test fun pointsKeepTheirTimeThroughSmoothingAndTransforms() {
        val pts = line(120f, 240f)
        val sm = StrokeSmoothing.smooth(pts, 0.5f)
        assertTrue(sm.last().t > 200f)
        val moved = Affine.translate(5f, 5f).map(pts[3])
        assertEquals(pts[3].t, moved.t, 1e-6f)
    }
}
