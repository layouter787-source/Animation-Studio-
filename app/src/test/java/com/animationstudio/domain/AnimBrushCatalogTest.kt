package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimBrushCatalogTest {
    private val json = """
        {
          "brushes": [
            {"id":"tinta.caneta-g","name":"Caneta G","category":"tinta","eraser":false,"animated":false,"additive":false,
             "defaultSize":4,"minSize":1,"maxSize":60,"stamp":"","description":"Caneta de ponta metálica","tags":["manga","linha"],
             "params":[{"key":"taper","label":"Afinar pontas","def":0.5,"min":0,"max":1}]},
            {"id":"raios.relampago","name":"Relâmpago","category":"luz","eraser":false,"animated":true,"additive":true,
             "defaultSize":3,"minSize":1,"maxSize":30,"stamp":"","description":"Descarga elétrica","tags":[],"params":[]},
            {"id":"borracha.dura","name":"Borracha dura","category":"borrachas","eraser":true,"animated":false,"additive":false,
             "defaultSize":10,"minSize":1,"maxSize":100,"stamp":"","description":"","tags":[],"params":[]}
          ]
        }
    """.trimIndent()

    @Test fun leOsPincéisEOsParametros() {
        val list = AnimBrushCatalog.parse(json)
        assertEquals(3, list.size)
        val g = list[0]
        assertEquals("tinta.caneta-g", g.id)
        assertEquals("Caneta G", g.name)
        assertEquals(4f, g.defaultRadius, 0f)
        assertEquals(listOf("manga", "linha"), g.tags)
        assertEquals(1, g.params.size)
        assertEquals("taper", g.params[0].key)
        assertEquals(0.5f, g.params[0].def, 0f)
        assertTrue(list[1].animated)
        assertTrue(list[1].additive)
        assertTrue(list[2].eraser)
    }

    @Test fun converteEmPredefinicaoComPrefixoEDiametro() {
        val p = AnimBrushCatalog.toPreset(AnimBrushCatalog.parse(json)[0])
        assertEquals("ab:tinta.caneta-g", p.id)
        assertEquals("AnimBrush · Tinta", p.category)
        assertEquals(BrushKind.BRUSH, p.kind)
        // raio 4 px = diâmetro 8 px
        assertEquals(8f, p.defaultSize, 0f)
        assertTrue(p.keywords.contains("manga"))
    }

    @Test fun borrachaViraPredefinicaoDeBorracha() {
        val p = AnimBrushCatalog.toPreset(AnimBrushCatalog.parse(json)[2])
        assertEquals(BrushKind.ERASER, p.kind)
    }

    @Test fun idsComPrefixo() {
        assertTrue(AnimBrushCatalog.isAnimBrushId("ab:luz.neon"))
        assertFalse(AnimBrushCatalog.isAnimBrushId("ink_g"))
        assertEquals("luz.neon", AnimBrushCatalog.nativeId("ab:luz.neon"))
    }

    @Test fun jsonSemPincéisDevolveListaVazia() {
        assertEquals(0, AnimBrushCatalog.parse("{}").size)
        assertEquals(0, AnimBrushCatalog.parse("""{"brushes":[{"name":"sem id"}]}""").size)
    }
}
