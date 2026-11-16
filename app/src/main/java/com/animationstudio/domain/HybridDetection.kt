package com.animationstudio.domain

/**
 * Hybrid Animation Engine — detecção automática do tipo de animação de uma layer
 * (secção 3 da especificação do Modo Studio).
 *
 * Este ficheiro é domínio puro (sem Android, sem dependência do modelo Project):
 * a UI/ProjectLayers converte a layer real para [LayerRigInput] e recebe um [LayerDetection].
 */
enum class AnimationMode { TRADITIONAL, RIGGED, HYBRID }

/** Informação mínima de um frame para a detecção. */
data class FrameRigInfo(
    val hasBonePose: Boolean = false,
    val hasMeshDeformation: Boolean = false,
    /** Frame desenhado frame-a-frame depois de a layer já ter bones. */
    val drawnAfterBones: Boolean = false,
) {
    val usesBones: Boolean get() = hasBonePose || hasMeshDeformation
}

data class LayerRigInput(
    val boneCount: Int,
    val frames: List<FrameRigInfo>,
    /** Regiões onde existem bones (ex.: "arm_l", "leg_r"). */
    val boneRegions: Set<String> = emptySet(),
    /** false quando os bones cobrem só parte do desenho (ex.: só o braço). */
    val coversWholeDrawing: Boolean = true,
    /** Modo forçado pelo utilizador; null = detecção automática. */
    val manualOverride: AnimationMode? = null,
)

data class LayerDetection(
    val mode: AnimationMode,
    val isManualOverride: Boolean,
    /** 0.0 a 1.0: frames com bonePose/meshDeformation a dividir pelo total. */
    val usageRatio: Double,
    val hasBones: Boolean,
    val lastCalculatedAt: Long,
    val boneRegions: List<String>,
)

object HybridDetection {
    /** Limiar oficial para Rigged: >= 85% dos frames. */
    const val RIGGED_THRESHOLD_PERCENT = 85

    fun detect(input: LayerRigInput, now: Long = 0L): LayerDetection {
        val total = input.frames.size
        val withBones = input.frames.count { it.usesBones }
        val ratio = if (total == 0) 0.0 else withBones.toDouble() / total
        val hasBones = input.boneCount > 0
        val auto = classify(input, hasBones, total, withBones)
        val forced = input.manualOverride
        return LayerDetection(
            mode = forced ?: auto,
            isManualOverride = forced != null,
            usageRatio = ratio,
            hasBones = hasBones,
            lastCalculatedAt = now,
            boneRegions = input.boneRegions.sorted(),
        )
    }

    private fun classify(
        input: LayerRigInput,
        hasBones: Boolean,
        total: Int,
        withBones: Int,
    ): AnimationMode {
        // Sem bones, sem frames, ou bones que nunca foram usados (sem keyframes): Traditional.
        if (!hasBones || total == 0 || withBones == 0) return AnimationMode.TRADITIONAL
        // Refinamentos que forçam Hybrid, mesmo com uso alto de bones.
        if (!input.coversWholeDrawing) return AnimationMode.HYBRID
        if (input.frames.any { it.drawnAfterBones }) return AnimationMode.HYBRID
        // Comparação com inteiros: sem erro de arredondamento no limiar de 85%.
        return if (withBones * 100 >= total * RIGGED_THRESHOLD_PERCENT) {
            AnimationMode.RIGGED
        } else {
            AnimationMode.HYBRID
        }
    }
}

/**
 * Cache por layer: só recalcula quando os dados relevantes mudam
 * (a comparação é por igualdade de [LayerRigInput], que deve ser imutável).
 */
class LayerDetectionCache {
    private class Entry(val input: LayerRigInput, val result: LayerDetection)

    private val entries = HashMap<String, Entry>()

    fun get(layerId: String, input: LayerRigInput, now: Long): LayerDetection {
        val cached = entries[layerId]
        if (cached != null && cached.input == input) return cached.result
        val result = HybridDetection.detect(input, now)
        entries[layerId] = Entry(input, result)
        return result
    }

    fun invalidate(layerId: String) {
        entries.remove(layerId)
    }

    fun clear() = entries.clear()
}

/** Cores dos badges: verde = Traditional, azul = Hybrid, roxo = Rigged (ARGB). */
enum class DetectionBadge(val argb: Long) {
    GREEN(0xFF2E9E5B),
    BLUE(0xFF2F7DE1),
    PURPLE(0xFF8A4FD6),
}

fun AnimationMode.badge(): DetectionBadge = when (this) {
    AnimationMode.TRADITIONAL -> DetectionBadge.GREEN
    AnimationMode.HYBRID -> DetectionBadge.BLUE
    AnimationMode.RIGGED -> DetectionBadge.PURPLE
}

/** Texto curto do tooltip ao tocar no badge. */
fun LayerDetection.tooltip(): String {
    val base = when (mode) {
        AnimationMode.TRADITIONAL -> "Traditional"
        AnimationMode.RIGGED -> "Rigged – bones em todo o desenho"
        AnimationMode.HYBRID ->
            if (boneRegions.isEmpty()) "Hybrid – frame a frame misturado com bones"
            else "Hybrid – bones em: " + boneRegions.joinToString(", ")
    }
    return if (isManualOverride) "$base (manual)" else base
}

/** Feedback ao mudar de modo (a UI decide como o apresenta). */
data class ModeChangeFeedback(val animateBadge: Boolean, val lightHaptic: Boolean)

fun feedbackFor(old: AnimationMode, new: AnimationMode): ModeChangeFeedback =
    ModeChangeFeedback(
        animateBadge = old != new,
        lightHaptic = old == AnimationMode.TRADITIONAL && new == AnimationMode.HYBRID,
    )
