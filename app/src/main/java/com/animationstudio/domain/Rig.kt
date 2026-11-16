package com.animationstudio.domain

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Modelo de bones, pose por frame, cinemática direta (FK) e IK de dois bones.
 * Domínio puro (sem Android). Ângulos em radianos, eixo Y para baixo como no ecrã
 * (o sentido positivo do ângulo é o mesmo de atan2(y, x)).
 */
data class Bone(
    val id: String,
    val name: String,
    /** null = bone raiz. */
    val parentId: String?,
    val length: Double,
    /** Posição da base; só usada em bones raiz. Os filhos começam na ponta do pai. */
    val restX: Double = 0.0,
    val restY: Double = 0.0,
    /** Ângulo de repouso relativo ao pai (ou ao eixo X nos bones raiz). */
    val restAngle: Double = 0.0,
    /** Região do desenho que este bone controla (ex.: "arm_l"). */
    val region: String? = null,
)

/** Hierarquia imutável e validada: ids únicos, pais existentes, sem ciclos. */
class BoneHierarchy private constructor(val bones: List<Bone>) {
    private val byParent: Map<String?, List<Bone>> = bones.groupBy { it.parentId }
    private val byId: Map<String, Bone> = bones.associateBy { it.id }

    val count: Int get() = bones.size
    val roots: List<Bone> get() = byParent[null] ?: emptyList()

    fun get(id: String): Bone? = byId[id]
    fun children(id: String): List<Bone> = byParent[id] ?: emptyList()

    /** Bones por ordem em que o pai vem sempre antes dos filhos. */
    val ordered: List<Bone> by lazy {
        val out = ArrayList<Bone>(bones.size)
        fun visit(b: Bone) {
            out.add(b)
            for (c in children(b.id)) visit(c)
        }
        for (r in roots) visit(r)
        out
    }

    fun add(bone: Bone): BoneHierarchy = of(bones + bone)

    /** Remove o bone e todos os seus descendentes. Id inexistente: devolve a mesma hierarquia. */
    fun remove(id: String): BoneHierarchy {
        if (!byId.containsKey(id)) return this
        val gone = HashSet<String>()
        fun collect(i: String) {
            gone.add(i)
            for (c in children(i)) collect(c.id)
        }
        collect(id)
        return of(bones.filter { it.id !in gone })
    }

    companion object {
        val EMPTY = BoneHierarchy(emptyList())

        fun of(bones: List<Bone>): BoneHierarchy {
            val ids = HashSet<String>()
            for (b in bones) {
                require(b.length >= 0.0) { "Comprimento negativo: ${b.id}" }
                require(ids.add(b.id)) { "Id repetido: ${b.id}" }
            }
            val index = bones.associateBy { it.id }
            for (b in bones) {
                val p = b.parentId
                if (p != null) require(index.containsKey(p)) { "Pai inexistente: $p" }
                var cur: String? = b.id
                var steps = 0
                while (cur != null) {
                    require(steps <= bones.size) { "Ciclo na hierarquia: ${b.id}" }
                    cur = index[cur]?.parentId
                    steps++
                }
            }
            return BoneHierarchy(bones)
        }
    }
}

/**
 * Pose de um frame: rotação adicional (delta sobre o repouso) por bone,
 * deslocamento do esqueleto (aplicado aos bones raiz) e se há deformação de mesh.
 */
data class FramePose(
    val rotations: Map<String, Double> = emptyMap(),
    val rootOffsetX: Double = 0.0,
    val rootOffsetY: Double = 0.0,
    val meshDeformed: Boolean = false,
) {
    val hasBonePose: Boolean
        get() = rotations.isNotEmpty() || rootOffsetX != 0.0 || rootOffsetY != 0.0
}

data class WorldBone(
    val id: String,
    val x0: Double,
    val y0: Double,
    val x1: Double,
    val y1: Double,
    /** Ângulo absoluto (mundo). */
    val angle: Double,
)

object Skeleton {
    /** Cinemática direta: posição e ângulo de cada bone no espaço do desenho. */
    fun solve(hierarchy: BoneHierarchy, pose: FramePose): Map<String, WorldBone> {
        val out = LinkedHashMap<String, WorldBone>()
        for (b in hierarchy.ordered) {
            val parent = b.parentId?.let { out[it] }
            val angle = (parent?.angle ?: 0.0) + b.restAngle + (pose.rotations[b.id] ?: 0.0)
            val sx = parent?.x1 ?: (b.restX + pose.rootOffsetX)
            val sy = parent?.y1 ?: (b.restY + pose.rootOffsetY)
            out[b.id] = WorldBone(
                id = b.id,
                x0 = sx,
                y0 = sy,
                x1 = sx + cos(angle) * b.length,
                y1 = sy + sin(angle) * b.length,
                angle = angle,
            )
        }
        return out
    }
}

/**
 * @param upperAngle ângulo ABSOLUTO (mundo) do bone de cima.
 * @param lowerAngle ângulo do bone de baixo RELATIVO ao de cima (cotovelo/joelho).
 * @param reached false quando o alvo estava fora de alcance (o braço estica na direção dele).
 */
data class TwoBoneIkResult(val upperAngle: Double, val lowerAngle: Double, val reached: Boolean)

object TwoBoneIk {
    fun solve(
        baseX: Double,
        baseY: Double,
        upperLength: Double,
        lowerLength: Double,
        targetX: Double,
        targetY: Double,
        bendPositive: Boolean = true,
    ): TwoBoneIkResult {
        val dx = targetX - baseX
        val dy = targetY - baseY
        val toTarget = atan2(dy, dx)
        if (upperLength <= 0.0 || lowerLength <= 0.0) {
            return TwoBoneIkResult(toTarget, 0.0, false)
        }
        val rawDist = sqrt(dx * dx + dy * dy)
        val maxReach = upperLength + lowerLength
        val minReach = kotlin.math.abs(upperLength - lowerLength)
        val reached = rawDist in minReach..maxReach
        val d = rawDist.coerceIn(minReach, maxReach)

        val cosInterior = ((upperLength * upperLength + lowerLength * lowerLength - d * d) /
            (2.0 * upperLength * lowerLength)).coerceIn(-1.0, 1.0)
        val bend = Math.PI - acos(cosInterior)
        val lower = if (bendPositive) bend else -bend
        val upper = toTarget - atan2(
            lowerLength * sin(lower),
            upperLength + lowerLength * cos(lower),
        )
        return TwoBoneIkResult(upper, lower, reached)
    }

    /**
     * Resolve IK para a cadeia [upperId] -> [lowerId] (lowerId tem de ser filho de upperId)
     * e devolve a pose com as rotações já convertidas em deltas sobre o repouso.
     * Devolve null se a cadeia for inválida.
     */
    fun solveChain(
        hierarchy: BoneHierarchy,
        upperId: String,
        lowerId: String,
        targetX: Double,
        targetY: Double,
        bendPositive: Boolean = true,
        base: FramePose = FramePose(),
    ): FramePose? {
        val upper = hierarchy.get(upperId) ?: return null
        val lower = hierarchy.get(lowerId) ?: return null
        if (lower.parentId != upperId) return null
        val world = Skeleton.solve(hierarchy, base)
        val u = world[upperId] ?: return null
        val parentAngle = u.angle - upper.restAngle - (base.rotations[upperId] ?: 0.0)
        val r = solve(u.x0, u.y0, upper.length, lower.length, targetX, targetY, bendPositive)
        val rotations = base.rotations + mapOf(
            upperId to (r.upperAngle - parentAngle - upper.restAngle),
            lowerId to (r.lowerAngle - lower.restAngle),
        )
        return base.copy(rotations = rotations)
    }
}

/**
 * Estado de rig de uma layer: bones, pose por índice de frame e frames desenhados
 * depois de existirem bones. Imutável; cada operação devolve um novo estado.
 */
data class RigState(
    val hierarchy: BoneHierarchy = BoneHierarchy.EMPTY,
    val poses: Map<Int, FramePose> = emptyMap(),
    val drawnAfterBones: Set<Int> = emptySet(),
    val coversWholeDrawing: Boolean = true,
    val manualOverride: AnimationMode? = null,
) {
    fun withBone(bone: Bone): RigState = copy(hierarchy = hierarchy.add(bone))

    /** Apagar o último bone limpa poses e marcas: a layer volta a Traditional. */
    fun withoutBone(id: String): RigState {
        val next = hierarchy.remove(id)
        if (next.count == 0) {
            return copy(hierarchy = next, poses = emptyMap(), drawnAfterBones = emptySet())
        }
        val removed = hierarchy.bones.map { it.id }.toSet() - next.bones.map { it.id }.toSet()
        val cleaned = poses.mapValues { (_, p) ->
            p.copy(rotations = p.rotations.filterKeys { it !in removed })
        }
        return copy(hierarchy = next, poses = cleaned)
    }

    fun withPose(frame: Int, pose: FramePose): RigState = copy(poses = poses + (frame to pose))

    fun withoutPose(frame: Int): RigState = copy(poses = poses - frame)

    /** Chamar quando o utilizador desenha frame-a-frame nesta layer. Sem bones, não faz nada. */
    fun markDrawn(frame: Int): RigState =
        if (hierarchy.count == 0) this else copy(drawnAfterBones = drawnAfterBones + frame)

    fun withOverride(mode: AnimationMode?): RigState = copy(manualOverride = mode)

    fun toDetectionInput(frameCount: Int): LayerRigInput = LayerRigInput(
        boneCount = hierarchy.count,
        frames = List(frameCount) { i ->
            val p = poses[i]
            FrameRigInfo(
                hasBonePose = p?.hasBonePose == true,
                hasMeshDeformation = p?.meshDeformed == true,
                drawnAfterBones = i in drawnAfterBones,
            )
        },
        boneRegions = hierarchy.bones.mapNotNull { it.region }.toSet(),
        coversWholeDrawing = coversWholeDrawing,
        manualOverride = manualOverride,
    )
}
