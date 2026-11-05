package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PhaseADomainTest {
    @Test fun stampsAreDeterministicAndBoundedByScatter() {
        val preset = BrushLibrary.byId("grass")
        val line = List(11) { Point(it * 10f, 0f) }
        val a = StampGeometry.generate(line, 20f, preset, 42)
        val b = StampGeometry.generate(line, 20f, preset, 42)
        assertTrue(a.isNotEmpty())
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].x, b[i].x, 0f)
            assertEquals(a[i].y, b[i].y, 0f)
        }
        // Linha horizontal: o desvio lateral está em y e não excede scatter × raio.
        assertTrue(a.all { abs(it.y) <= preset.scatter * 10f + 0.01f })
        val c = StampGeometry.generate(line, 20f, preset, 43)
        assertTrue(a.indices.any { it >= c.size || a[it].y != c[it].y })
    }

    @Test fun continuousBrushesProduceNoStamps() {
        val line = List(5) { Point(it * 10f, 0f) }
        assertTrue(StampGeometry.generate(line, 6f, BrushLibrary.byId("ink_g"), 1).isEmpty())
        assertEquals(1, StampGeometry.generate(listOf(Point(3f, 4f)), 20f, BrushLibrary.byId("dots"), 1).size / BrushLibrary.byId("dots").density)
    }

    @Test fun libraryIsOrganizedAndSearchable() {
        assertTrue(BrushLibrary.all.map { it.id }.toSet().size == BrushLibrary.all.size)
        assertTrue(BrushLibrary.all.all { it.name.isNotBlank() && it.defaultSize in BrushLimits.MIN_SIZE..BrushLimits.MAX_SIZE })
        for (k in BrushKind.values()) assertTrue(BrushLibrary.ofKind(k).all { it.category in BrushLibrary.categoriesOf(k) })
        assertTrue(BrushLibrary.brushCategories.all { c -> BrushLibrary.ofKind(BrushKind.BRUSH).any { it.category == c } })
        // Sem acentos nem maiúsculas: "LAPIS" encontra "Lápis ...".
        assertTrue(BrushLibrary.search(BrushKind.BRUSH, "LAPIS", null).size >= 5)
        assertTrue(BrushLibrary.search(BrushKind.BRUSH, "arvore", null).any { it.id == "leaves" })
        assertTrue(BrushLibrary.search(BrushKind.BRUSH, "xyzxyz", null).isEmpty())
        assertTrue(BrushLibrary.search(BrushKind.ERASER, "", null).size == BrushLibrary.ofKind(BrushKind.ERASER).size)
    }

    @Test fun moveLayerReordersAndClamps() {
        val p = Project.initial().addLayer(LayerKind.DRAWING)
        val bottom = p.layers.first().id
        val up = p.moveLayer(bottom, 1)
        assertEquals(bottom, up.layers[1].id)
        assertEquals(bottom, p.moveLayer(bottom, -5).layers.first().id)
        assertEquals(p.layers.map { it.id }.toSet(), up.layers.map { it.id }.toSet())
        assertTrue(p.moveLayer(999L, 1) === p)
    }
}
