package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTest {
    private val s1 = BrushStroke(listOf(Point(0f, 0f), Point(1f, 1f)), 0xFF000000.toInt(), 4f)
    private val s2 = BrushStroke(listOf(Point(5f, 5f)), 0xFFFF0000.toInt(), 6f)

    private fun scenery(p: Project) = p.layers.first { it.kind == LayerKind.SCENERY }.id
    private fun drawing(p: Project) = p.layers.first { it.kind == LayerKind.DRAWING }.id
    private fun threeFrames() = Project.initial().addFrameAfterCurrent().addFrameAfterCurrent()

    @Test fun sharedSceneryEditAffectsAllFrames() {
        var p = threeFrames()
        val sc = scenery(p)
        p = p.select(0).addStroke(sc, s1)
        for (i in 0..2) assertEquals(listOf(s1), p.strokesAt(sc, i))
        p = p.select(2).addStroke(sc, s2)
        for (i in 0..2) assertEquals(listOf(s1, s2), p.strokesAt(sc, i))
    }

    @Test fun separateSceneryFromCurrentKeepsEarlierFramesUntouched() {
        var p = threeFrames()
        val sc = scenery(p)
        p = p.select(0).addStroke(sc, s1)
        p = p.select(1).separateSceneryFromCurrent(sc).addStroke(sc, s2)
        assertEquals(listOf(s1), p.strokesAt(sc, 0))
        assertEquals(listOf(s1, s2), p.strokesAt(sc, 1))
        assertEquals(listOf(s1, s2), p.strokesAt(sc, 2))
    }

    @Test fun newFrameInheritsSceneryButDrawingLayerStartsBlank() {
        var p = Project.initial()
        val sc = scenery(p)
        val dl = drawing(p)
        p = p.addStroke(sc, s1).addStroke(dl, s2).addFrameAfterCurrent()
        assertEquals(listOf(s1), p.strokesAt(sc, 1))
        assertTrue(p.strokesAt(dl, 1).isEmpty())
        assertEquals(listOf(s2), p.strokesAt(dl, 0))
    }

    @Test fun drawingLayerStrokesStayInTheirFrame() {
        var p = Project.initial()
        val dl = drawing(p)
        p = p.addStroke(dl, s1).addFrameAfterCurrent().addStroke(dl, s2)
        assertEquals(listOf(s1), p.strokesAt(dl, 0))
        assertEquals(listOf(s2), p.strokesAt(dl, 1))
    }

    @Test fun duplicateFrameCopiesDrawingsIndependentlyAndSharesScenery() {
        var p = Project.initial()
        val sc = scenery(p)
        val dl = drawing(p)
        p = p.addStroke(sc, s1).addStroke(dl, s1).duplicateCurrentFrame()
        assertEquals(1, p.currentFrame)
        p = p.addStroke(dl, s2)
        assertEquals(listOf(s1, s2), p.strokesAt(dl, 1))
        assertEquals(listOf(s1), p.strokesAt(dl, 0))
        p = p.addStroke(sc, s2)
        assertEquals(p.strokesAt(sc, 0), p.strokesAt(sc, 1))
    }

    @Test fun duplicateLayerIsIndependentAndPlacedAboveOriginal() {
        var p = Project.initial()
        val dl = drawing(p)
        p = p.addStroke(dl, s1).duplicateLayer(dl)
        assertEquals(3, p.layers.size)
        val copy = p.layers[p.layers.indexOfFirst { it.id == dl } + 1]
        assertEquals("Camada 1 cópia", copy.name)
        assertEquals(listOf(s1), p.strokesAt(copy.id, 0))
        p = p.addStroke(copy.id, s2)
        assertEquals(listOf(s1), p.strokesAt(dl, 0))
        assertEquals(listOf(s1, s2), p.strokesAt(copy.id, 0))
    }

    @Test fun newLayersAreOrderedSceneryBelowDrawing() {
        val p = Project.initial().addLayer(LayerKind.DRAWING).addLayer(LayerKind.SCENERY)
        assertEquals(
            listOf(LayerKind.SCENERY, LayerKind.SCENERY, LayerKind.DRAWING, LayerKind.DRAWING),
            p.layers.map { it.kind }
        )
        assertEquals("Cenário 2", p.layers[1].name)
        assertEquals("Camada 2", p.layers[3].name)
    }

    @Test fun opacityIsClampedAndRenameIgnoresBlank() {
        var p = Project.initial()
        val dl = drawing(p)
        assertEquals(100, p.setLayerOpacity(dl, 150).layers.first { it.id == dl }.opacityPercent)
        assertEquals(0, p.setLayerOpacity(dl, -5).layers.first { it.id == dl }.opacityPercent)
        assertEquals(40, p.setLayerOpacity(dl, 40).layers.first { it.id == dl }.opacityPercent)
        p = p.renameLayer(dl, "   ")
        assertEquals("Camada 1", p.layers.first { it.id == dl }.name)
        p = p.renameLayer(dl, " Corpo ")
        assertEquals("Corpo", p.layers.first { it.id == dl }.name)
    }

    @Test fun lastLayerCannotBeDeleted() {
        var p = Project.initial()
        p = p.deleteLayer(scenery(p))
        assertEquals(1, p.layers.size)
        assertEquals(1, p.deleteLayer(p.layers[0].id).layers.size)
    }

    @Test fun deleteFrameDropsOrphanDrawingsButKeepsSharedScenery() {
        var p = Project.initial()
        val sc = scenery(p)
        val dl = drawing(p)
        p = p.addStroke(sc, s1).addStroke(dl, s2).addFrameAfterCurrent().select(0).deleteCurrentFrame()
        assertEquals(1, p.frameCount)
        assertTrue(p.layers.first { it.id == dl }.drawings.isEmpty())
        assertEquals(listOf(s1), p.strokesAt(sc, 0))
    }
}
