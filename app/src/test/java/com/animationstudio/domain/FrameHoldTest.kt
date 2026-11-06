package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class FrameHoldTest {
    private fun drawnProject(): Project =
        Project.initial().addStroke(2L, BrushStroke(listOf(Point(0f, 0f), Point(5f, 5f)), 0xFF000000.toInt(), 4f))

    @Test fun blankFramesAreNotHolds() {
        val p = Project.initial().addFrameAfterCurrent()
        assertFalse(p.sameExposure(0, 1))
        assertEquals(0, p.holdEnd(0))
    }

    @Test fun extendingSharesTheDrawingAndKeepsTheCurrentFrame() {
        val p = drawnProject().extendHold(0, 2)
        assertEquals(3, p.frameCount)
        assertEquals(0, p.currentFrame)
        val layer = p.layers.first { it.id == 2L }
        assertEquals(1, layer.drawings.size)
        assertTrue(layer.cels[p.frameIds[0]] == layer.cels[p.frameIds[1]] && layer.cels[p.frameIds[1]] == layer.cels[p.frameIds[2]])
        assertEquals(2, p.holdEnd(0))
        assertEquals(0, p.holdStart(2))
    }

    @Test fun shrinkingUndoesExtendingAndNeverRemovesTheFirstFrame() {
        val extended = drawnProject().extendHold(0, 3)
        val back = extended.shrinkHold(0, 3)
        assertEquals(1, back.frameCount)
        assertEquals(drawnProject().layers.first { it.id == 2L }.drawings.keys, back.layers.first { it.id == 2L }.drawings.keys)
        assertEquals(1, back.shrinkHold(0, 5).frameCount)
    }

    @Test fun differentDrawingsAreNotTheSameExposure() {
        val p = drawnProject().addFrameAfterCurrent().addStroke(2L, BrushStroke(listOf(Point(1f, 1f)), 0xFF000000.toInt(), 4f))
        assertFalse(p.sameExposure(0, 1))
        assertEquals(0, p.holdEnd(0))
    }

    @Test fun extendingInsertsRightAfterTheHoldEnd() {
        val p = drawnProject().addFrameAfterCurrent().extendHold(0, 1)
        // frames: [desenho, novo, vazio] -> o frame 0 estende-se para o frame 1 e empurra o que estava a seguir.
        assertEquals(3, p.frameCount)
        assertTrue(p.sameExposure(0, 1))
        assertFalse(p.sameExposure(1, 2))
    }

    @Test fun ropeStabilizerIgnoresJitterInsideTheRopeAndEndsOnTheFinger() {
        val s = StrokeStabilizer(0.5f)
        var pen = s.start(Point(0f, 0f), 0L)
        // tremor de 3 px dentro da corda (20 px): a ponta não sai do sítio
        for (i in 1..10) pen = s.next(Point(if (i % 2 == 0) 3f else -3f, if (i % 2 == 0) -3f else 3f), i * 8L)
        assertTrue(hypot(pen.x, pen.y) < 0.01f)
        // movimento reto: a ponta segue com a corda esticada
        for (i in 1..50) pen = s.next(Point(i * 10f, 0f), 100L + i * 8L)
        assertEquals(500f - 20f, pen.x, 0.5f)
        val end = s.finish(Point(500f, 0f))
        assertEquals(500f, end.last().x, 0f)
        assertTrue(end.size >= 2)
    }

    @Test fun ropeStabilizerWithZeroStrengthIsTransparent() {
        val s = StrokeStabilizer(0f)
        assertEquals(Point(1f, 2f), s.start(Point(1f, 2f), 0L))
        assertEquals(Point(7f, 9f), s.next(Point(7f, 9f), 5L))
        assertEquals(listOf(Point(3f, 3f)), s.finish(Point(3f, 3f)))
    }
}
