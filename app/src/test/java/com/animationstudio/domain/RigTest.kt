package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class RigTest {
    private val eps = 1e-6

    private fun bone(id: String, parent: String? = null, len: Double = 10.0, rest: Double = 0.0, region: String? = null) =
        Bone(id = id, name = id, parentId = parent, length = len, restAngle = rest, region = region)

    // ---------- hierarquia ----------

    @Test fun rejeitaIdRepetido() {
        assertThrows(IllegalArgumentException::class.java) {
            BoneHierarchy.of(listOf(bone("a"), bone("a")))
        }
    }

    @Test fun rejeitaPaiInexistente() {
        assertThrows(IllegalArgumentException::class.java) {
            BoneHierarchy.of(listOf(bone("a", parent = "x")))
        }
    }

    @Test fun rejeitaCiclos() {
        assertThrows(IllegalArgumentException::class.java) {
            BoneHierarchy.of(listOf(bone("a", parent = "b"), bone("b", parent = "a")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BoneHierarchy.of(listOf(bone("a", parent = "a")))
        }
    }

    @Test fun rejeitaComprimentoNegativo() {
        assertThrows(IllegalArgumentException::class.java) {
            BoneHierarchy.of(listOf(bone("a", len = -1.0)))
        }
    }

    @Test fun removerApagaDescendentes() {
        val h = BoneHierarchy.of(listOf(bone("a"), bone("b", "a"), bone("c", "b"), bone("d")))
        val r = h.remove("b")
        assertEquals(listOf("a", "d"), r.bones.map { it.id })
        assertSame(h, h.remove("inexistente"))
    }

    @Test fun ordenadoComPaisPrimeiroMesmoComEntradaDesordenada() {
        val h = BoneHierarchy.of(listOf(bone("c", "b"), bone("b", "a"), bone("a")))
        assertEquals(listOf("a", "b", "c"), h.ordered.map { it.id })
    }

    // ---------- FK ----------

    @Test fun fkBoneUnico() {
        val h = BoneHierarchy.of(listOf(bone("a", len = 10.0)))
        val w = Skeleton.solve(h, FramePose())["a"]!!
        assertEquals(10.0, w.x1, eps)
        assertEquals(0.0, w.y1, eps)
    }

    @Test fun fkFilhoComRotacaoDeRepouso() {
        val h = BoneHierarchy.of(listOf(bone("a"), bone("b", "a", rest = PI / 2)))
        val b = Skeleton.solve(h, FramePose())["b"]!!
        assertEquals(10.0, b.x0, eps)
        assertEquals(10.0, b.x1, eps)
        assertEquals(10.0, b.y1, eps)
    }

    @Test fun fkRotacaoDaPoseRodaACadeia() {
        val h = BoneHierarchy.of(listOf(bone("a"), bone("b", "a")))
        val w = Skeleton.solve(h, FramePose(rotations = mapOf("a" to PI / 2)))
        assertEquals(0.0, w["b"]!!.x1, eps)
        assertEquals(20.0, w["b"]!!.y1, eps)
    }

    @Test fun fkDeslocamentoMoveARaiz() {
        val h = BoneHierarchy.of(listOf(bone("a")))
        val w = Skeleton.solve(h, FramePose(rootOffsetX = 5.0, rootOffsetY = 3.0))["a"]!!
        assertEquals(5.0, w.x0, eps)
        assertEquals(3.0, w.y0, eps)
    }

    // ---------- IK ----------

    private fun endOf(h: BoneHierarchy, pose: FramePose, id: String): WorldBone =
        Skeleton.solve(h, pose)[id]!!

    @Test fun ikAlcancaOAlvoNosDoisSentidosDeDobra() {
        val h = BoneHierarchy.of(listOf(bone("u"), bone("l", "u")))
        for (bend in listOf(true, false)) {
            val pose = TwoBoneIk.solveChain(h, "u", "l", 10.0, 10.0, bendPositive = bend)
            assertNotNull(pose)
            val e = endOf(h, pose!!, "l")
            assertEquals(10.0, e.x1, 1e-4)
            assertEquals(10.0, e.y1, 1e-4)
        }
    }

    @Test fun ikForaDeAlcanceEsticaNaDirecaoDoAlvo() {
        val r = TwoBoneIk.solve(0.0, 0.0, 10.0, 10.0, 100.0, 0.0)
        assertFalse(r.reached)
        val h = BoneHierarchy.of(listOf(bone("u"), bone("l", "u")))
        val pose = TwoBoneIk.solveChain(h, "u", "l", 100.0, 0.0)!!
        val e = endOf(h, pose, "l")
        assertEquals(20.0, e.x1, 1e-4)
        assertEquals(0.0, e.y1, 1e-4)
    }

    @Test fun ikComPaiRodadoEComRepouso() {
        // pai aponta para cima (ângulo +90°); o braço começa na ponta do pai, em (0, 10)
        val h = BoneHierarchy.of(
            listOf(
                bone("p", len = 10.0, rest = PI / 2),
                bone("u", "p", len = 10.0, rest = -PI / 2),
                bone("l", "u", len = 10.0),
            )
        )
        val pose = TwoBoneIk.solveChain(h, "u", "l", 10.0, 20.0)!!
        val e = endOf(h, pose, "l")
        assertEquals(10.0, e.x1, 1e-4)
        assertEquals(20.0, e.y1, 1e-4)
    }

    @Test fun ikCadeiaInvalidaDevolveNull() {
        val h = BoneHierarchy.of(listOf(bone("a"), bone("b"), bone("c", "a")))
        assertNull(TwoBoneIk.solveChain(h, "a", "b", 1.0, 1.0))
        assertNull(TwoBoneIk.solveChain(h, "a", "zzz", 1.0, 1.0))
    }

    // ---------- RigState + detecção ----------

    private fun detect(s: RigState, frames: Int) =
        HybridDetection.detect(s.toDetectionInput(frames)).mode

    private fun withTwoBones(): RigState =
        RigState().withBone(bone("a", region = "arm_l")).withBone(bone("b", "a", region = "arm_l"))

    private val pose = FramePose(rotations = mapOf("a" to 0.5))

    @Test fun semBonesETraditional() {
        assertEquals(AnimationMode.TRADITIONAL, detect(RigState(), 10))
    }

    @Test fun bonesSemPosesETraditional() {
        assertEquals(AnimationMode.TRADITIONAL, detect(withTwoBones(), 10))
    }

    @Test fun posesEmTodosOsFramesERigged() {
        var s = withTwoBones().copy(coversWholeDrawing = true)
        for (i in 0 until 10) s = s.withPose(i, pose)
        assertEquals(AnimationMode.RIGGED, detect(s, 10))
    }

    @Test fun posesEmMetadeDosFramesEHybrid() {
        var s = withTwoBones()
        for (i in 0 until 5) s = s.withPose(i, pose)
        assertEquals(AnimationMode.HYBRID, detect(s, 10))
    }

    @Test fun meshDeformadaContaComoUso() {
        var s = withTwoBones()
        for (i in 0 until 10) s = s.withPose(i, FramePose(meshDeformed = true))
        assertEquals(AnimationMode.RIGGED, detect(s, 10))
    }

    @Test fun desenhoNovoDepoisDeBonesVoltaHybrid() {
        var s = withTwoBones()
        for (i in 0 until 10) s = s.withPose(i, pose)
        s = s.markDrawn(9)
        assertEquals(AnimationMode.HYBRID, detect(s, 10))
    }

    @Test fun markDrawnSemBonesNaoFazNada() {
        assertEquals(RigState(), RigState().markDrawn(3))
    }

    @Test fun bonesSoNumaRegiaoSaoHybrid() {
        var s = withTwoBones().copy(coversWholeDrawing = false)
        for (i in 0 until 10) s = s.withPose(i, pose)
        val input = s.toDetectionInput(10)
        assertEquals(setOf("arm_l"), input.boneRegions)
        assertEquals(AnimationMode.HYBRID, HybridDetection.detect(input).mode)
    }

    @Test fun apagarTodosOsBonesLimpaTudoEVoltaATraditional() {
        var s = withTwoBones()
        for (i in 0 until 10) s = s.withPose(i, pose)
        s = s.markDrawn(2).withoutBone("a") // apaga a e o filho b
        assertEquals(0, s.hierarchy.count)
        assertTrue(s.poses.isEmpty())
        assertTrue(s.drawnAfterBones.isEmpty())
        assertEquals(AnimationMode.TRADITIONAL, detect(s, 10))
    }

    @Test fun apagarUmBoneLimpaAsSuasRotacoesNasPoses() {
        var s = RigState().withBone(bone("a")).withBone(bone("b")).withBone(bone("c", "b"))
        s = s.withPose(0, FramePose(rotations = mapOf("a" to 1.0, "b" to 2.0, "c" to 3.0)))
        s = s.withoutBone("b")
        assertEquals(setOf("a"), s.poses[0]!!.rotations.keys)
    }

    @Test fun overrideManualPassaParaADetecao() {
        val s = withTwoBones().withOverride(AnimationMode.RIGGED)
        val r = HybridDetection.detect(s.toDetectionInput(10))
        assertEquals(AnimationMode.RIGGED, r.mode)
        assertTrue(r.isManualOverride)
    }

    @Test fun projetoGrandeDuzentosFramesEClassificadoRapido() {
        var s = withTwoBones().copy(coversWholeDrawing = true)
        for (i in 0 until 200) s = s.withPose(i, pose)
        val t0 = System.nanoTime()
        val mode = detect(s, 200)
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        assertEquals(AnimationMode.RIGGED, mode)
        // margem larga para não ser frágil no CI; o alvo real (< 8 ms) mede-se em dispositivo
        assertTrue("demorou $ms ms", ms < 500.0)
    }
}
