package com.animationstudio.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class BrushEngineTest {
    private val line = List(21) { Point(it * 10f, 0f) }

    @Test fun everyStampBrushGeneratesFiniteStamps() {
        val stampBrushes = BrushLibrary.all.filter { it.stamp != StampShape.NONE }
        assertTrue(stampBrushes.isNotEmpty())
        for (p in stampBrushes) {
            val stamps = StampGeometry.generate(line, p.defaultSize, p, 11)
            assertTrue("${p.id} sem carimbos", stamps.isNotEmpty())
            assertTrue("${p.id} com valores inválidos", stamps.all { it.size > 0f && it.size.isFinite() && it.x.isFinite() && it.y.isFinite() && it.rotation.isFinite() })
            assertTrue("${p.id} excede o limite", stamps.size <= StampGeometry.MAX_STAMPS)
        }
    }

    @Test fun stampGenerationIsDeterministic() {
        val p = BrushLibrary.byId("hair_strand")
        val a = StampGeometry.generate(line, p.defaultSize, p, 5)
        val b = StampGeometry.generate(line, p.defaultSize, p, 5)
        assertTrue(a.size == b.size && a.indices.all { a[it].x == b[it].x && a[it].rotation == b[it].rotation })
    }

    @Test fun libraryHasRichCategoriesAndSeveralErasers() {
        assertTrue(BrushLibrary.ofKind(BrushKind.BRUSH).size >= 150)
        assertTrue(BrushLibrary.ofKind(BrushKind.ERASER).size >= 10)
        for (c in BrushLibrary.brushCategories) assertTrue("categoria vazia: $c", BrushLibrary.search(BrushKind.BRUSH, "", c).isNotEmpty())
        assertTrue(BrushLibrary.all.all { it.hardness in 0f..1f && it.grain in 0f..1f && it.taper in 0f..1f })
    }
}
