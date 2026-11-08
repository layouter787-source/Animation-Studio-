package com.animationstudio.format

import com.animationstudio.domain.BrushStroke
import com.animationstudio.domain.LayerKind
import com.animationstudio.domain.Point
import com.animationstudio.domain.Project
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AspkTest {

    private fun manifest(version: Int = 1, kind: String = "DRAWING", blend: String = "NORMAL") = """
        {"format":"animation-studio-interchange","version":$version,"producer":"teste","name":"p",
         "width":1280,"height":720,"fps":24,"paperColorArgb":-1,"currentFrame":0,"nextId":10,
         "frameIds":[0,1],
         "layers":[{"id":2,"name":"L","kind":"$kind","opacityPercent":100,"visible":true,"blend":"$blend",
                    "locked":false,"cels":{"0":5,"1":5},"drawings":[5]}]}
    """.trimIndent()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
        }
        return ByteArrayInputStream(bos.toByteArray())
    }

    private fun m(s: String) = "manifest.json" to s.toByteArray()

    @Test fun roundTripKeepsRasterExtrasAndIds() {
        val png = byteArrayOf(1, 2, 3)
        val p = AspkProject(
            name = "p", width = 1280, height = 720, fps = 24, paperArgb = -1,
            frameIds = listOf(0, 1), currentFrame = 1, nextId = 10,
            layers = listOf(
                AspkLayer(
                    AspkLayerMeta(2, "L", "DRAWING", 80, true, "MULTIPLY", true),
                    mapOf(5L to AspkDrawing(5, rasterPng = png)),
                    mapOf(0L to 5L, 1L to 5L),
                ),
            ),
            extras = mapOf("camera.json" to byteArrayOf(9, 8)),
        )
        val bos = ByteArrayOutputStream()
        AspkIO.write(p, bos)
        val r = AspkIO.read(ByteArrayInputStream(bos.toByteArray()))
        assertEquals(listOf(0L, 1L), r.frameIds)
        assertEquals(1, r.currentFrame)
        assertEquals("MULTIPLY", r.layers[0].meta.blend)
        assertEquals(mapOf(0L to 5L, 1L to 5L), r.layers[0].cels)
        assertArrayEquals(png, r.layers[0].drawings.getValue(5L).rasterPng)
        assertArrayEquals(byteArrayOf(9, 8), r.extras.getValue("camera.json"))
    }

    @Test fun rejectsPathTraversal() {
        val s = zip(m(manifest()), "../evil.txt" to byteArrayOf(1))
        assertThrows(AspkException::class.java) { AspkIO.read(s) }
    }

    @Test fun rejectsNewerVersion() {
        val s = zip(m(manifest(version = 2)))
        assertThrows(AspkException::class.java) { AspkIO.read(s) }
    }

    @Test fun rejectsMissingManifest() {
        val s = zip("x.txt" to byteArrayOf(1))
        assertThrows(AspkException::class.java) { AspkIO.read(s) }
    }

    @Test fun rejectsCorruptedManifestFields() {
        val s = zip(m("""{"format":"animation-studio-interchange","version":1}"""))
        assertThrows(AspkException::class.java) { AspkIO.read(s) }
    }

    @Test fun bridgeRasterPrevailsOverStrokesAndWarns() {
        val strokes = """[{"c":-16777216,"w":3.0,"e":false,"p":[1.0,2.0,0.5,3.0,4.0,0.5]}]"""
        val s = zip(
            m(manifest()),
            "layers/2/strokes/5.json" to strokes.toByteArray(),
            "layers/2/raster/5.png" to byteArrayOf(7, 7),
        )
        val imp = AspkBridge.import(AspkIO.read(s))
        val d = imp.document.project.layers[0].drawings.getValue(5L)
        assertTrue(d.strokes.isEmpty())
        assertArrayEquals(byteArrayOf(7, 7), imp.document.rasters.getValue(RasterKey(2, 5)))
        assertTrue(imp.warnings.isNotEmpty())
    }

    @Test fun bridgeWarnsOnUnknownKindAndBlend() {
        val imp = AspkBridge.import(AspkIO.read(zip(m(manifest(kind = "WEIRD", blend = "OVERLAY")))))
        assertEquals(LayerKind.DRAWING, imp.document.project.layers[0].kind)
        assertEquals(2, imp.warnings.size)
        assertEquals("OVERLAY", imp.document.layerExtras.getValue(2L).blend)
    }

    @Test fun bridgeDropsDanglingCelsAndKeepsNextIdAhead() {
        val bad = manifest().replace("\"cels\":{\"0\":5,\"1\":5}", "\"cels\":{\"0\":5,\"9\":5,\"1\":77}")
        val imp = AspkBridge.import(AspkIO.read(zip(m(bad))))
        assertEquals(mapOf(0L to 5L), imp.document.project.layers[0].cels)
        assertTrue(imp.document.project.nextId > 5)
    }

    @Test fun strokesSurviveDomainExportImportRoundTrip() {
        val stroke = BrushStroke(listOf(Point(1f, 2f), Point(3f, 4f)), 0xFF112233.toInt(), 5f, erase = false)
        val project = Project.initial().addStroke(2, stroke)
        val doc = AspkDocument(AspkSheet("t", 1280, 720, 24, -1), project)
        val exp = AspkBridge.export(doc)
        val bos = ByteArrayOutputStream()
        AspkIO.write(exp.project, bos)
        val back = AspkBridge.import(AspkIO.read(ByteArrayInputStream(bos.toByteArray())))
        assertEquals(listOf(stroke), back.document.project.strokesAt(2, 0))
        assertEquals(project.frameIds, back.document.project.frameIds)
        assertEquals(LayerKind.SCENERY, back.document.project.layers[0].kind)
    }
}
