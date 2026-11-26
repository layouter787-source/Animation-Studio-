package com.animationstudio.domain

/**
 * Ponte para o núcleo C++ do projeto (studio_jni.cpp). O Kotlin só guarda números (handles) e pede cada operação ao C++.
 * Cada edição devolve um handle NOVO (o projeto é um valor imutável); o antigo continua válido até [nFree].
 * Nos testes JVM a biblioteca vem de -Dstudio.native.path (compilada pela tarefa buildHostStudio); na app, de
 * libanimation_engine.so.
 */
object NativeStudio {
    init {
        val path = System.getProperty("studio.native.path")
        if (path != null) System.load(path) else System.loadLibrary("animation_engine")
    }

    @JvmStatic external fun nInitial(): Long
    @JvmStatic external fun nFree(h: Long)
    @JvmStatic external fun nLiveCount(): Int

    @JvmStatic external fun nFrameCount(h: Long): Int
    @JvmStatic external fun nCurrentFrame(h: Long): Int
    @JvmStatic external fun nNextId(h: Long): Long
    @JvmStatic external fun nLayerCount(h: Long): Int
    @JvmStatic external fun nFrameIds(h: Long): LongArray

    /** [id, tipo (0 desenho, 1 cenário), opacidade %, visível (0/1)]. */
    @JvmStatic external fun nLayerInfo(h: Long, i: Int): LongArray
    @JvmStatic external fun nLayerName(h: Long, i: Int): String
    @JvmStatic external fun nStrokeCount(h: Long, layerId: Long, frame: Int): Int
    @JvmStatic external fun nStrokePoints(h: Long, layerId: Long, frame: Int, index: Int): FloatArray

    /** [cor ARGB, largura, apagar, afunilamento, dureza, modo, id do preenchimento, id da imagem]. */
    @JvmStatic external fun nStrokeMeta(h: Long, layerId: Long, frame: Int, index: Int): DoubleArray
    @JvmStatic external fun nStrokeBrush(h: Long, layerId: Long, frame: Int, index: Int): String

    @JvmStatic external fun nSelect(h: Long, i: Int): Long
    @JvmStatic external fun nAddStroke(
        h: Long, layerId: Long, pts: FloatArray, color: Int, width: Float, erase: Boolean, taper: Float, hardness: Float,
        brush: String, mode: Int, fillBlob: Long, imageBlob: Long,
    ): Long

    @JvmStatic external fun nAddFrame(h: Long): Long
    @JvmStatic external fun nDuplicateFrame(h: Long): Long
    @JvmStatic external fun nDeleteFrame(h: Long): Long
    @JvmStatic external fun nSeparateScenery(h: Long, layerId: Long): Long
    @JvmStatic external fun nAddLayer(h: Long, kind: Int): Long
    @JvmStatic external fun nDuplicateLayer(h: Long, id: Long): Long
    @JvmStatic external fun nRenameLayer(h: Long, id: Long, name: String): Long
    @JvmStatic external fun nSetLayerOpacity(h: Long, id: Long, pct: Int): Long
    @JvmStatic external fun nToggleLayerVisible(h: Long, id: Long): Long
    @JvmStatic external fun nDeleteLayer(h: Long, id: Long): Long

    @JvmStatic external fun nExtendHold(h: Long, index: Int, count: Int): Long
    @JvmStatic external fun nShrinkHold(h: Long, index: Int, count: Int): Long
    @JvmStatic external fun nHoldStart(h: Long, index: Int): Int
    @JvmStatic external fun nHoldEnd(h: Long, index: Int): Int
    @JvmStatic external fun nSameExposure(h: Long, a: Int, b: Int): Boolean

    /** [índice, direção, terminou (0/1)]. Modo: 0 uma vez, 1 loop, 2 ping-pong. */
    @JvmStatic external fun nNextPlaybackStep(index: Int, count: Int, mode: Int, direction: Int): IntArray
}
