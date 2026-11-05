package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProjectFramesTest {
    private val s1 = BrushStroke(listOf(Point(0f, 0f), Point(1f, 1f)), 0xFF000000.toInt(), 4f)
    private val s2 = BrushStroke(listOf(Point(5f, 5f)), 0xFFFF0000.toInt(), 6f)

    private fun threeFrames() = Project.initial().addFrameAfterCurrent().addFrameAfterCurrent()

    @Test fun insertBlankLeftAndRightOfAFrame() {
        val p = threeFrames()
        val before = p.frameIds
        val left = p.insertFrameAt(1)
        assertEquals(4, left.frameCount)
        assertEquals(1, left.currentFrame)
        assertEquals(before[0], left.frameIds[0])
        assertEquals(before[1], left.frameIds[2])
        val right = p.insertFrameAt(3)
        assertEquals(3, right.currentFrame)
        assertEquals(before, right.frameIds.take(3))
    }

    @Test fun insertAtStartAndOutOfRangeIsClamped() {
        val p = threeFrames()
        assertEquals(0, p.insertFrameAt(0).currentFrame)
        assertEquals(3, p.insertFrameAt(99).currentFrame)
        assertEquals(0, p.insertFrameAt(-5).currentFrame)
    }

    @Test fun pasteCopiesDrawingLayerIndependently() {
        val dl = Project.initial().layers[1].id
        var p = Project.initial().addStroke(dl, s1)
        val clip = p.frameClip(0)
        p = p.insertFrameAt(1, clip)
        assertEquals(listOf(s1), p.strokesAt(dl, 1))
        p = p.addStroke(dl, s2)
        assertEquals(listOf(s1, s2), p.strokesAt(dl, 1))
        assertEquals(listOf(s1), p.strokesAt(dl, 0))
    }

    @Test fun clipIgnoresSceneryAndInsertedFramesInheritIt() {
        val init = Project.initial()
        val sc = init.layers[0].id
        val p = init.addStroke(sc, s1)
        assertFalse(sc in p.frameClip(0))
        assertEquals(listOf(s1), p.insertFrameAt(1).strokesAt(sc, 1))
        assertEquals(listOf(s1), p.insertFrameAt(0).strokesAt(sc, 0))
    }
}
