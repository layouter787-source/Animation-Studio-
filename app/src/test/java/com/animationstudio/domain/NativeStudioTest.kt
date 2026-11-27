package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prova a ponte Kotlin -> JNI -> C++ do núcleo do projeto e o registo de traços (a lógica em si é testada em C++). */
class NativeStudioTest {
    private fun stroke(x: Float) = BrushStroke(listOf(Point(x, x)), 0xFF000000.toInt(), 4f)

    @Test fun editsReturnNewProjectsAndKeepTheOldOneIntact() {
        val p = Project.initial()
        val s = stroke(1f)
        val q = p.addStroke(2L, s)
        assertEquals(0, p.strokesAt(2L, 0).size)
        assertEquals(1, q.strokesAt(2L, 0).size)
        assertSame(s, q.strokesAt(2L, 0)[0])
    }

    @Test fun noOpEditsReturnTheSameInstance() {
        val p = Project.initial()
        assertSame(p, p.addStroke(999L, stroke(1f)))
        assertSame(p, p.deleteCurrentFrame())
        assertSame(p, p.renameLayer(2L, "  "))
        assertSame(p, p.extendHold(0, 0))
        assertSame(p, p.shrinkHold(0, 3))
    }

    @Test fun unchangedDrawingsKeepTheSameStrokeListAcrossEdits() {
        // A cache de imagens por camada compara a lista de traços por identidade: tem de se manter.
        val a = Project.initial().addStroke(2L, stroke(1f))
        val listBefore = a.layers.first { it.id == 2L }.drawingAt(0L)!!.strokes
        val b = a.addLayer(LayerKind.DRAWING).setLayerOpacity(2L, 50)
        val listAfter = b.layers.first { it.id == 2L }.drawingAt(0L)!!.strokes
        assertSame(listBefore, listAfter)
        val c = b.addStroke(2L, stroke(2f))
        val changed = c.layers.first { it.id == 2L }.drawingAt(0L)!!.strokes
        assertNotSame(listBefore, changed)
        assertSame(listBefore[0], changed[0])
    }

    @Test fun releasedStrokesAreDroppedFromTheRegistry() {
        val before = StrokeRegistry.sizes()
        var p: Project? = Project.initial()
        for (i in 0 until 20) p = p!!.addStroke(2L, stroke(i.toFloat()))
        assertTrue(StrokeRegistry.sizes().first >= before.first + 20)
        p!!.layers  // materializa
        p = null
        // Sem referências, o finalizador liberta os handles; o registo esvazia na chamada seguinte.
        for (i in 0 until 20) {
            System.gc()
            System.runFinalization()
            StrokeRegistry.drain()
        }
        assertTrue("traços ainda registados: ${StrokeRegistry.sizes()}", StrokeRegistry.sizes().first <= before.first + 2)
    }

    @Test fun framesAndHoldWorkThroughTheBridge() {
        val s = stroke(1f)
        val withStroke = Project.initial().addStroke(2L, s)
        val held = withStroke.extendHold(0, 2)
        assertEquals(3, held.frameCount)
        assertEquals(2, held.holdEnd(0))
        assertTrue(held.sameExposure(0, 1))
        val next = withStroke.addFrameAfterCurrent()
        assertEquals(2, next.frameCount)
        assertEquals(1, next.currentFrame)
    }

    @Test fun playbackStepsAndRebuildFromParts() {
        assertEquals(0, NativeStudio.nNextPlaybackStep(2, 3, 1, 1)[0])
        assertEquals(1, NativeStudio.nNextPlaybackStep(2, 3, 0, 1)[2])
        val src = Project.initial().addStroke(2L, stroke(3f)).addFrameAfterCurrent()
        val copy = Project(src.frameIds, src.layers, src.currentFrame, src.nextId)
        assertEquals(src, copy)
        assertEquals(src.hashCode(), copy.hashCode())
        assertTrue(src != src.addFrameAfterCurrent())
        assertEquals(src.frameIds, copy.frameIds)
        assertEquals(src.nextId, copy.nextId)
        assertEquals(src.layers.map { it.name }, copy.layers.map { it.name })
        assertSame(src.strokesAt(2L, 0)[0], copy.strokesAt(2L, 0)[0])
    }
}
