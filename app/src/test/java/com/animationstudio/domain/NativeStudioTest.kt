package com.animationstudio.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prova a ponte Kotlin -> JNI -> C++ do núcleo do projeto (a lógica em si é testada em C++ e espelha ProjectTest). */
class NativeStudioTest {
    private val n = NativeStudio

    private fun drawingLayer(h: Long): Long = n.nLayerInfo(h, 1)[0]

    @Test fun initialProjectHasSceneryAndDrawingLayer() {
        val p = n.nInitial()
        assertEquals(1, n.nFrameCount(p))
        assertEquals(2, n.nLayerCount(p))
        assertEquals(1L, n.nLayerInfo(p, 0)[1])
        assertEquals("Camada 1", n.nLayerName(p, 1))
        n.nFree(p)
    }

    @Test fun editsReturnNewHandlesAndKeepTheOldOneIntact() {
        val p = n.nInitial()
        val dl = drawingLayer(p)
        val q = n.nAddStroke(p, dl, floatArrayOf(1f, 2f, 3f, 4f), 0xFFFF0000.toInt(), 6f, false, 0.6f, 1f, "ink_g", 0, -1L, -1L)
        assertEquals(0, n.nStrokeCount(p, dl, 0))
        assertEquals(1, n.nStrokeCount(q, dl, 0))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f), n.nStrokePoints(q, dl, 0, 0), 0f)
        assertEquals("ink_g", n.nStrokeBrush(q, dl, 0, 0))
        assertEquals(6.0, n.nStrokeMeta(q, dl, 0, 0)[1], 0.0)
        n.nFree(p)
        n.nFree(q)
    }

    @Test fun framesAndHoldWorkThroughTheBridge() {
        val p = n.nInitial()
        val dl = drawingLayer(p)
        val withStroke = n.nAddStroke(p, dl, floatArrayOf(0f, 0f), 0xFF000000.toInt(), 4f, false, 0.6f, 1f, "", 0, -1L, -1L)
        val held = n.nExtendHold(withStroke, 0, 2)
        assertEquals(3, n.nFrameCount(held))
        assertEquals(2, n.nHoldEnd(held, 0))
        assertTrue(n.nSameExposure(held, 0, 1))
        val next = n.nAddFrame(withStroke)
        assertEquals(2, n.nFrameCount(next))
        assertEquals(1, n.nCurrentFrame(next))
        listOf(p, withStroke, held, next).forEach { n.nFree(it) }
    }

    @Test fun playbackStepsAndInvalidHandles() {
        assertEquals(0, n.nNextPlaybackStep(2, 3, 1, 1)[0])
        assertEquals(1, n.nNextPlaybackStep(2, 3, 0, 1)[2])
        assertEquals(0, n.nFrameCount(987654321L))
        assertEquals(0L, n.nAddFrame(987654321L))
    }
}
