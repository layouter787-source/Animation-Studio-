package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridDetectionTest {

    private fun frames(total: Int, withBones: Int) =
        List(total) { i -> FrameRigInfo(hasBonePose = i < withBones) }

    private fun input(
        bones: Int = 3,
        frames: List<FrameRigInfo>,
        regions: Set<String> = emptySet(),
        whole: Boolean = true,
        override: AnimationMode? = null,
    ) = LayerRigInput(bones, frames, regions, whole, override)

    @Test fun semBonesETraditional() {
        val r = HybridDetection.detect(input(bones = 0, frames = frames(10, 10)))
        assertEquals(AnimationMode.TRADITIONAL, r.mode)
        assertFalse(r.hasBones)
    }

    @Test fun bonesSemKeyframesETraditional() {
        val r = HybridDetection.detect(input(frames = frames(10, 0)))
        assertEquals(AnimationMode.TRADITIONAL, r.mode)
        assertEquals(0.0, r.usageRatio, 0.0)
    }

    @Test fun layerSemFramesNaoRebenta() {
        val r = HybridDetection.detect(input(frames = emptyList()))
        assertEquals(AnimationMode.TRADITIONAL, r.mode)
    }

    @Test fun oitentaECincoPorCentoERigged() {
        val r = HybridDetection.detect(input(frames = frames(20, 17)))
        assertEquals(AnimationMode.RIGGED, r.mode)
    }

    @Test fun oitentaEQuatroPorCentoEHybrid() {
        val r = HybridDetection.detect(input(frames = frames(100, 84)))
        assertEquals(AnimationMode.HYBRID, r.mode)
    }

    @Test fun umPorCentoEHybrid() {
        val r = HybridDetection.detect(input(frames = frames(100, 1)))
        assertEquals(AnimationMode.HYBRID, r.mode)
    }

    @Test fun todosOsFramesComBonesERigged() {
        val r = HybridDetection.detect(input(frames = frames(200, 200)))
        assertEquals(AnimationMode.RIGGED, r.mode)
        assertEquals(1.0, r.usageRatio, 0.0)
    }

    @Test fun meshContaComoUsoDeBones() {
        val f = List(10) { FrameRigInfo(hasMeshDeformation = true) }
        assertEquals(AnimationMode.RIGGED, HybridDetection.detect(input(frames = f)).mode)
    }

    @Test fun bonesSoNumaRegiaoForcamHybrid() {
        val r = HybridDetection.detect(
            input(frames = frames(10, 10), regions = setOf("arm_l"), whole = false)
        )
        assertEquals(AnimationMode.HYBRID, r.mode)
        assertEquals(listOf("arm_l"), r.boneRegions)
    }

    @Test fun desenhoNovoDepoisDeBonesForcaHybrid() {
        val f = frames(10, 10).toMutableList()
        f[9] = f[9].copy(drawnAfterBones = true)
        assertEquals(AnimationMode.HYBRID, HybridDetection.detect(input(frames = f)).mode)
    }

    @Test fun apagarTodosOsBonesVoltaATraditional() {
        val r = HybridDetection.detect(input(bones = 0, frames = frames(10, 10)))
        assertEquals(AnimationMode.TRADITIONAL, r.mode)
    }

    @Test fun overrideManualERespeitado() {
        val r = HybridDetection.detect(
            input(bones = 0, frames = frames(10, 0), override = AnimationMode.RIGGED)
        )
        assertEquals(AnimationMode.RIGGED, r.mode)
        assertTrue(r.isManualOverride)
    }

    @Test fun voltarAAutomaticoReclassifica() {
        val forced = HybridDetection.detect(
            input(frames = frames(10, 10), override = AnimationMode.TRADITIONAL)
        )
        val auto = HybridDetection.detect(input(frames = frames(10, 10)))
        assertEquals(AnimationMode.TRADITIONAL, forced.mode)
        assertEquals(AnimationMode.RIGGED, auto.mode)
        assertFalse(auto.isManualOverride)
    }

    @Test fun cacheSoRecalculaQuandoOsDadosMudam() {
        val cache = LayerDetectionCache()
        val a = input(frames = frames(10, 10))
        val r1 = cache.get("L1", a, now = 1L)
        val r2 = cache.get("L1", a.copy(), now = 2L)
        assertSame(r1, r2)
        val r3 = cache.get("L1", input(frames = frames(10, 5)), now = 3L)
        assertNotSame(r1, r3)
        assertEquals(3L, r3.lastCalculatedAt)
        cache.invalidate("L1")
        assertNotSame(r3, cache.get("L1", input(frames = frames(10, 5)), now = 4L))
    }

    @Test fun badgesETooltip() {
        assertEquals(DetectionBadge.GREEN, AnimationMode.TRADITIONAL.badge())
        assertEquals(DetectionBadge.BLUE, AnimationMode.HYBRID.badge())
        assertEquals(DetectionBadge.PURPLE, AnimationMode.RIGGED.badge())
        val r = HybridDetection.detect(
            input(frames = frames(10, 10), regions = setOf("leg_r", "arm_l"), whole = false)
        )
        assertEquals("Hybrid – bones em: arm_l, leg_r", r.tooltip())
    }

    @Test fun vibracaoSoDeTraditionalParaHybrid() {
        assertTrue(feedbackFor(AnimationMode.TRADITIONAL, AnimationMode.HYBRID).lightHaptic)
        assertFalse(feedbackFor(AnimationMode.HYBRID, AnimationMode.RIGGED).lightHaptic)
        assertFalse(feedbackFor(AnimationMode.HYBRID, AnimationMode.HYBRID).animateBadge)
    }
}
