package com.animationstudio.data

import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.FillRegion
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import com.animationstudio.domain.addFill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectJsonTest {
    @Test fun roundTripKeepsEverything() {
        var p = Project.initial()
        val sc = p.layers[0].id
        val dl = p.layers[1].id
        val fill = BrushStroke(emptyList(), 0x80336699.toInt(), 0f, fill = FillRegion.encode(booleanArrayOf(true, false, true, true), 2, 2, 3, 4, 1f))
        p = p.addStroke(sc, BrushStroke(listOf(Point(0.1f, 2.5f), Point(100f, 200.25f)), 0xFF112233.toInt(), 3.5f, taper = 0.25f, hardness = 0.5f))
            .addStroke(dl, BrushStroke(listOf(Point(1f, 1f)), 0x80FF0000.toInt(), 9f, erase = true))
            .addFill(dl, fill)
            .addFrameAfterCurrent()
            .setLayerOpacity(dl, 40)
            .renameLayer(sc, "Fundo")
        val meta = ProjectMeta("id-1", "Teste", 15, 1280, 720, 1000L, 2000L)
        val decoded = ProjectJson.decode(ProjectJson.encode(meta, p))
        assertNotNull(decoded)
        assertEquals(meta, decoded!!.first)
        assertEquals(p, decoded.second)
    }

    @Test fun corruptOrUnknownVersionReturnsNull() {
        assertNull(ProjectJson.decode("{not json"))
        assertNull(ProjectJson.decode("{\"v\":2}"))
        assertNull(ProjectJson.decodeMeta(""))
    }
}
