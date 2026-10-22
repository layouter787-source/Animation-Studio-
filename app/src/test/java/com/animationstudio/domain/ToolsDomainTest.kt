package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsDomainTest {
    private val tol = 0.01f
    private val black = 0xFF000000.toInt()

    @Test fun affineAroundAndInverse() {
        val m = Affine.around(Point(10f, 20f), 2f, 0.7f, 5f, -3f)
        val p = Point(33f, 7f)
        val back = m.inverse()!!.map(m.map(p))
        assertEquals(p.x, back.x, tol)
        assertEquals(p.y, back.y, tol)
        val piv = m.map(Point(10f, 20f))
        assertEquals(15f, piv.x, tol)
        assertEquals(17f, piv.y, tol)
        assertEquals(2f, m.scaleFactor, tol)
    }

    @Test fun fillRegionEncodeDecodeRoundTrip() {
        val mask = booleanArrayOf(false, true, true, false, true, false, false, true)
        val f = FillRegion.encode(mask, 4, 2, 3, 4, 1f)
        assertTrue(mask.contentEquals(f.decode()))
        val all = FillRegion.encode(BooleanArray(6) { true }, 3, 2, 0, 0, 1f)
        assertEquals(listOf(0, 6), all.runs)
    }

    @Test fun fillRegionTranslates() {
        val f = FillRegion.encode(BooleanArray(6) { true }, 3, 2, 0, 0, 1f)
        val m = Affine.around(Point(0f, 0f), 1f, 0f, 10f, 5f)
        val t = f.transformed(m)!!
        assertEquals(10, t.x)
        assertEquals(5, t.y)
        assertEquals(3, t.w)
        assertEquals(2, t.h)
        assertTrue(t.decode().all { it })
    }

    @Test fun lassoSelectsStrokesInsidePolygon() {
        val inside = BrushStroke(listOf(Point(5f, 5f), Point(6f, 6f)), black, 2f)
        val outside = BrushStroke(listOf(Point(50f, 50f), Point(51f, 51f)), black, 2f)
        val poly = listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 10f))
        assertEquals(setOf(0), Lasso.select(listOf(inside, outside), poly))
        assertTrue(Lasso.select(listOf(inside), listOf(Point(0f, 0f), Point(1f, 1f))).isEmpty())
        val b = Lasso.boundsOf(listOf(inside, outside), setOf(0))!!
        assertEquals(4f, b.minX, tol)
        assertEquals(7f, b.maxX, tol)
    }

    @Test fun addFillGoesBelowInkAndAboveOlderFills() {
        val init = Project.initial()
        val dl = init.layers[1].id
        val ink = BrushStroke(listOf(Point(0f, 0f), Point(1f, 1f)), black, 2f)
        val f1 = BrushStroke(emptyList(), 0xFFFF0000.toInt(), 0f, fill = FillRegion.encode(BooleanArray(4) { true }, 2, 2, 0, 0, 1f))
        val f2 = BrushStroke(emptyList(), 0xFF00FF00.toInt(), 0f, fill = FillRegion.encode(BooleanArray(4) { true }, 2, 2, 5, 5, 1f))
        var p = init.addStroke(dl, ink).addFill(dl, f1).addFill(dl, f2)
        val list = p.strokesAt(dl, 0)
        assertEquals(listOf(f1, f2, ink), list)
        p = Project.initial().addFill(dl, f1)
        assertEquals(listOf(f1), p.strokesAt(dl, 0))
    }

    @Test fun transformAndRemoveElements() {
        val init = Project.initial()
        val dl = init.layers[1].id
        val a = BrushStroke(listOf(Point(0f, 0f), Point(2f, 0f)), black, 2f)
        val b = BrushStroke(listOf(Point(9f, 9f)), black, 2f)
        var p = init.addStroke(dl, a).addStroke(dl, b)
        p = p.transformElements(dl, setOf(0), Affine.around(Point(0f, 0f), 1f, 0f, 10f, 0f))
        val moved = p.strokesAt(dl, 0)
        assertEquals(10f, moved[0].points[0].x, tol)
        assertEquals(9f, moved[1].points[0].x, tol)
        p = p.removeElements(dl, setOf(1))
        assertEquals(1, p.strokesAt(dl, 0).size)
    }

    @Test fun stabilizerLagsThenCatchesUp() {
        val st = StrokeStabilizer(0.8f)
        st.start(Point(0f, 0f))
        val p = st.next(Point(100f, 0f))
        assertTrue(p.x in 0.1f..60f)
        val tail = st.finish(Point(100f, 0f))
        assertEquals(100f, tail.last().x, tol)
        val none = StrokeStabilizer(0f)
        none.start(Point(0f, 0f))
        assertEquals(100f, none.next(Point(100f, 0f)).x, tol)
    }

    @Test fun strokeShapeSamplesAndTapers() {
        val line = List(11) { Point(it * 10f, 0f) }
        val s = StrokeGeometry.sample(line, 1f)
        assertEquals(0f, s.first().x, tol)
        assertEquals(100f, s.last().x, tol)
        assertTrue(s.size >= 100)
        val shape = StrokeGeometry.build(line, 8f, 1f)
        assertNotNull(shape.start)
        assertNotNull(shape.end)
        assertTrue(shape.start!!.tipRadius < shape.radius)
        assertEquals(4f, shape.radius, tol)
        assertNull(StrokeGeometry.build(line, 8f, 0f).start)
        assertNull(StrokeGeometry.build(listOf(Point(0f, 0f), Point(2f, 0f)), 8f, 1f).start)
    }

    @Test fun brushLibraryIsOrganizedAndWithinLimits() {
        assertTrue(BrushLibrary.all.map { it.id }.toSet().size == BrushLibrary.all.size)
        assertTrue(BrushLibrary.all.all { it.defaultSize in BrushLimits.MIN_SIZE..BrushLimits.MAX_SIZE })
        assertTrue(BrushLibrary.all.all { it.defaultOpacity in 0f..1f })
        for (k in BrushKind.values()) assertTrue(BrushLibrary.ofKind(k).all { it.category in BrushLibrary.categoriesOf(k) })
        assertEquals(2000f, BrushLimits.MAX_SIZE, 0f)
    }
}
