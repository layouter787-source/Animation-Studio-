package com.animationstudio.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class BrushEngineTest {
    private val line = List(21) { Point(it * 10f, 0f) }

    /** O motor de carimbos continua testado com pincel construído à mão, mesmo sem carimbos na biblioteca. */
    @Test fun stampEngineGeneratesFiniteStampsForEveryShape() {
        for (shape in StampShape.values()) {
            if (shape == StampShape.NONE) continue
            val p = BrushPreset("t_${shape.name}", shape.name, "t", BrushKind.BRUSH, 30f, 1f, 0f, 1f, shape, 0.5f, 0.5f, 0.3f, 2)
            val stamps = StampGeometry.generate(line, p.defaultSize, p, 11)
            assertTrue("${shape.name} sem carimbos", stamps.isNotEmpty())
            assertTrue("${shape.name} com valores inválidos", stamps.all { it.size > 0f && it.size.isFinite() && it.x.isFinite() && it.y.isFinite() && it.rotation.isFinite() })
            assertTrue("${shape.name} excede o limite", stamps.size <= StampGeometry.MAX_STAMPS)
        }
    }

    @Test fun stampGenerationIsDeterministic() {
        val p = BrushPreset("t", "t", "t", BrushKind.BRUSH, 60f, 1f, 0f, 1f, StampShape.HAIR, 0.05f, 0.6f, 0.4f, 4)
        val a = StampGeometry.generate(line, p.defaultSize, p, 5)
        val b = StampGeometry.generate(line, p.defaultSize, p, 5)
        assertTrue(a.isNotEmpty() && a.size == b.size && a.indices.all { a[it].x == b[it].x && a[it].rotation == b[it].rotation })
    }

    @Test fun dabsCarryStrokeDirection() {
        val d = StampGeometry.dabs(line, 20f, 0f, 0.1f)
        assertTrue(d.isNotEmpty() && d.all { kotlin.math.abs(it.rotation) < 1e-3f })
    }

    @Test fun libraryHasOnlyProceduralBrushesAndSeveralErasers() {
        assertTrue(BrushLibrary.all.none { it.stamp != StampShape.NONE })
        assertTrue(BrushLibrary.all.none { it.kind == BrushKind.BRUSH && it.lines > 1 && BrushDynamics.bristle(it) == 0f })
        assertTrue(BrushLibrary.ofKind(BrushKind.BRUSH).size >= 100)
        assertTrue(BrushLibrary.ofKind(BrushKind.ERASER).size >= 10)
        for (c in BrushLibrary.brushCategories) assertTrue("categoria vazia: $c", BrushLibrary.search(BrushKind.BRUSH, "", c).isNotEmpty())
        assertTrue(BrushLibrary.all.all { it.hardness in 0f..1f && it.grain in 0f..1f && it.taper in 0f..1f })
    }
}
