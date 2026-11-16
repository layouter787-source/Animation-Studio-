package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoRasterizeTest {
    private fun cell(m: BooleanArray, w: Int, k: Int, x: Float, y: Float): Boolean =
        m[(y * k).toInt() * (w * k) + (x * k).toInt()]

    @Test fun retanguloSelecionaExatamenteAsCelulasDentro() {
        val poly = Lasso.rectangle(Point(2f, 2f), Point(6f, 5f))
        val m = Lasso.rasterize(poly, 0, 0, 8, 8, 1)
        assertEquals(12, m.count { it })
        assertTrue(cell(m, 8, 1, 2f, 2f))
        assertTrue(cell(m, 8, 1, 5f, 4f))
        assertFalse(cell(m, 8, 1, 6f, 4f))
        assertFalse(cell(m, 8, 1, 5f, 5f))
    }

    @Test fun superAmostragemMultiplicaAResolucaoSemMudarAForma() {
        val poly = Lasso.rectangle(Point(2f, 2f), Point(6f, 5f))
        val m = Lasso.rasterize(poly, 0, 0, 8, 8, 2)
        assertEquals(8 * 8 * 4, m.size)
        assertEquals(4 * 2 * 3 * 2, m.count { it })
    }

    @Test fun origemDaCaixaDesloca() {
        val poly = Lasso.rectangle(Point(12f, 22f), Point(16f, 25f))
        val m = Lasso.rasterize(poly, 10, 20, 8, 8, 1)
        assertEquals(12, m.count { it })
        assertTrue(cell(m, 8, 1, 2f, 2f))
    }

    @Test fun trianguloTemAAreaCerta() {
        val poly = listOf(Point(0f, 0f), Point(10f, 0f), Point(0f, 10f))
        val m = Lasso.rasterize(poly, 0, 0, 10, 10, 4)
        val area = m.count { it } / 16f
        assertEquals(50f, area, 2f)
    }

    @Test fun lacoQueSeCruzaPreencheOsDoisLobos() {
        val poly = listOf(Point(0f, 0f), Point(10f, 10f), Point(10f, 0f), Point(0f, 10f))
        val m = Lasso.rasterize(poly, 0, 0, 10, 10, 1)
        assertTrue(cell(m, 10, 1, 1.5f, 5.5f))
        assertTrue(cell(m, 10, 1, 8.5f, 5.5f))
        assertFalse(cell(m, 10, 1, 5.5f, 1.5f))
        assertFalse(cell(m, 10, 1, 5.5f, 8.5f))
    }

    @Test fun poligonoMaiorQueACaixaPreencheTudo() {
        val poly = Lasso.rectangle(Point(-5f, -5f), Point(50f, 50f))
        val m = Lasso.rasterize(poly, 0, 0, 6, 6, 3)
        assertTrue(m.all { it })
    }

    @Test fun poligonoDegeneradoNaoSeleccionaNada() {
        val m = Lasso.rasterize(listOf(Point(1f, 1f), Point(5f, 5f)), 0, 0, 6, 6, 2)
        assertEquals(0, m.count { it })
    }

    @Test fun superAmostragemSobeComOZoomEDesceNasSelecoesGrandes() {
        assertEquals(2, Lasso.supersample(100, 100, 0.3f))
        assertEquals(4, Lasso.supersample(100, 100, 3f))
        assertEquals(2, Lasso.supersample(1920, 1080, 3f))
        assertTrue(Lasso.supersample(1920, 1080, 3f).toLong().let { 1920L * 1080L * it * it <= Lasso.MAX_LIFT_PIXELS })
        assertEquals(1, Lasso.supersample(4000, 3000, 3f))
    }
}
