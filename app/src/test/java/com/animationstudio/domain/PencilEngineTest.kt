package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PencilEngineTest {
    private val line = List(41) { Point(it * 10f, 0f) }

    @Test fun penDabsFadeAtBothEndsAndAreFullInTheMiddle() {
        val d = StampGeometry.penDabs(line, 8f, 0.07f, 3f, 0.25f, 0f, 1)
        assertTrue(d.size > 50)
        assertTrue(d.all { it.alpha in 0f..1f })
        assertTrue(d.first().alpha < 0.5f)
        assertTrue(d.last().alpha < 0.5f)
        assertEquals(1f, d[d.size / 2].alpha, 1e-3f)
    }

    @Test fun penDabsKeepThicknessAndFollowTheLine() {
        val d = StampGeometry.penDabs(line, 8f, 0.07f, 3f, 0.25f, 0f, 1)
        assertTrue(d.all { abs(it.size - 8f) < 1e-3f })
        assertTrue(d.all { abs(it.y) < 1e-3f && abs(it.rotation) < 1e-3f })
    }

    @Test fun penDabsJitterStaysSmallAndIsDeterministic() {
        val a = StampGeometry.penDabs(line, 10f, 0.07f, 3f, 0.25f, 0.05f, 7)
        val b = StampGeometry.penDabs(line, 10f, 0.07f, 3f, 0.25f, 0.05f, 7)
        assertEquals(a.size, b.size)
        assertTrue(a.indices.all { a[it].x == b[it].x && a[it].y == b[it].y && a[it].size == b[it].size })
        assertTrue(a.all { abs(it.y) <= 0.05f * 10f * 0.5f + 1e-3f && it.size in 9.4f..10.6f })
        assertTrue(a.any { it.y != 0f })
    }

    @Test fun aSingleTapMakesOneDab() {
        val d = StampGeometry.penDabs(listOf(Point(5f, 5f)), 6f, 0.07f, 3f, 0.25f, 0.05f, 1)
        assertEquals(1, d.size)
    }

    @Test fun softerPencilsDepositMoreAndHaveSofterEdges() {
        fun spec(id: String) = BrushDynamics.pencil(BrushLibrary.byId(id))!!
        assertTrue(spec("pencil_8b").flow > spec("pencil_hb").flow)
        assertTrue(spec("pencil_hb").flow > spec("pencil_4h").flow)
        assertTrue(spec("pencil_8b").hardness < spec("pencil_hb").hardness)
        assertTrue(spec("pencil_hb").hardness < spec("pencil_4h").hardness)
    }

    @Test fun everyPencilIsInTheLibraryWithValidValues() {
        val ids = listOf(
            "pencil_4h", "pencil_h", "pencil_mech", "x_pencil_hard", "pencil_sharp", "pencil_hb", "graphite", "sketch_light",
            "x_pencil_soft", "pencil_2b", "pencil_mid", "pencil_3b", "pencil_thick", "pencil_4b", "pencil_color",
            "x_pencil_textured", "pencil_crunchy", "pencil_6b", "conte", "x_pencil_wax", "pencil_8b", "x_pencil_charcoal",
        )
        for (id in ids) {
            val p = BrushLibrary.byIdOrNull(id)
            assertTrue("$id não está na biblioteca", p != null)
            val s = BrushDynamics.pencil(p!!)
            assertTrue("$id sem definições de lápis", s != null)
            assertTrue(s!!.hardness in 0f..1f && s.flow in 0.01f..1f && s.fade >= 0f && s.jitter in 0f..0.2f)
        }
    }

    @Test fun importedBrushesAreFoundAndSearchable() {
        val mine = BrushPreset("my_test", "Meu pincel", BrushLibrary.CUSTOM, BrushKind.BRUSH, 40f, 1f, 0f, 1f, keywords = "importado png")
        try {
            BrushLibrary.setCustom(listOf(mine))
            assertEquals(mine, BrushLibrary.byIdOrNull("my_test"))
            assertTrue(BrushLibrary.search(BrushKind.BRUSH, "meu", BrushLibrary.CUSTOM).contains(mine))
            assertTrue(BrushLibrary.categoriesOf(BrushKind.BRUSH).contains(BrushLibrary.CUSTOM))
            assertTrue(BrushLibrary.all.none { it.id == "my_test" })
        } finally {
            BrushLibrary.setCustom(emptyList())
        }
        assertTrue(BrushLibrary.byIdOrNull("my_test") == null)
        assertTrue(!BrushLibrary.categoriesOf(BrushKind.BRUSH).contains(BrushLibrary.CUSTOM))
    }
}
