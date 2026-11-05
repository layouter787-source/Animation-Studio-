package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseB1Test {
    private val tol = 0.01f

    @Test fun affineCompositionAndFlips() {
        val move = Affine.translate(10f, 5f)
        val scale = Affine.around(Point(0f, 0f), 2f, 0f, 0f, 0f)
        val both = move.times(scale)
        val p = both.map(Point(3f, 4f))
        assertEquals(16f, p.x, tol)
        assertEquals(13f, p.y, tol)
        val fh = Affine.flipH(50f).map(Point(30f, 7f))
        assertEquals(70f, fh.x, tol)
        assertEquals(7f, fh.y, tol)
        val fv = Affine.flipV(20f).map(Point(3f, 5f))
        assertEquals(35f, fv.y, tol)
        val back = Affine.flipH(50f).times(Affine.flipH(50f)).map(Point(12f, 3f))
        assertEquals(12f, back.x, tol)
    }

    @Test fun imageDataBoundsFollowTransform() {
        val img = ImageData(ImageSource(byteArrayOf(1, 2, 3), 40, 20), Affine.translate(100f, 50f))
        val b = img.bounds()
        assertEquals(100f, b.minX, tol)
        assertEquals(140f, b.maxX, tol)
        assertEquals(70f, b.maxY, tol)
        val flipped = img.transformed(Affine.flipH(b.cx))
        assertEquals(b.minX, flipped.bounds().minX, tol)
        assertEquals(b.maxX, flipped.bounds().maxX, tol)
        assertTrue(img.source === flipped.source)
        val stroke = BrushStroke(emptyList(), 0, 0f, image = img)
        assertNotNull(stroke.bounds())
        assertEquals(b.cx + 10f, stroke.transformed(Affine.translate(10f, 0f))!!.bounds()!!.cx, tol)
    }

    @Test fun dabsFollowThePathAndTaper() {
        val line = List(11) { Point(it * 10f, 0f) }
        val d = StampGeometry.dabs(line, 20f, 1f, 0.1f)
        assertTrue(d.size >= 40)
        assertEquals(0f, d.first().x, tol)
        assertTrue(d.first().size < d[d.size / 2].size)
        val uniform = StampGeometry.dabs(line, 20f, 0f, 0.1f)
        assertTrue(uniform.all { kotlin.math.abs(it.size - 20f) < tol })
    }

    @Test fun parallelLinesAreSpreadAcrossTheWidth() {
        val line = List(6) { Point(it * 10f, 0f) }
        val pl = StrokeGeometry.parallel(line, 20f, 3)
        assertEquals(3, pl.lines.size)
        val ys = pl.lines.map { it[2].y }
        assertTrue(ys.max() - ys.min() > 10f)
        assertTrue(kotlin.math.abs(ys[1]) < tol)
    }

    @Test fun stabilizerDoesNotLagBehindForever() {
        val st = StrokeStabilizer(1f)
        st.start(Point(0f, 0f), 0L)
        var p = Point(0f, 0f)
        var t = 0L
        // dedo a 1000 px/s durante 0,5 s
        for (i in 1..60) {
            t += 8
            p = st.next(Point(i * 8f, 0f), t)
        }
        val raw = 60 * 8f
        assertTrue(raw - p.x < 120f)
        assertTrue(p.x > 0f)
        assertEquals(raw, st.finish(Point(raw, 0f)).last().x, tol)
    }
}
