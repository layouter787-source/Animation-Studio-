package com.animationstudio.domain

/**
 * Ponte para o núcleo C++ do projeto (studio_jni.cpp). O Kotlin só guarda números (handles) e pede cada operação ao C++.
 * Cada edição devolve um handle NOVO (o projeto é um valor imutável); o antigo continua válido até [nFree].
 * Os traços vivem no Kotlin; o C++ referencia-os por id (ver [StrokeRegistry]).
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

    /** Todos os desenhos da camada: [id do desenho, serial, n, n ids de traços]*. */
    @JvmStatic external fun nLayerDrawings(h: Long, i: Int): LongArray

    /** Exposição da camada: pares [id do frame, id do desenho]. */
    @JvmStatic external fun nLayerCels(h: Long, i: Int): LongArray
    @JvmStatic external fun nStrokeIds(h: Long, layerId: Long, frame: Int): LongArray

    /** Fichas libertadas: pares [tipo (0 traço, 1 versão de desenho), id]. */
    @JvmStatic external fun nDrainReleased(): LongArray

    @JvmStatic external fun nSelect(h: Long, i: Int): Long
    @JvmStatic external fun nAddStroke(h: Long, layerId: Long, strokeId: Long): Long
    @JvmStatic external fun nSetCurrentStrokes(h: Long, layerId: Long, strokeIds: LongArray): Long
    @JvmStatic external fun nInsertFrameAt(h: Long, position: Int, layerIds: LongArray, counts: IntArray, strokeIds: LongArray): Long

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
    @JvmStatic external fun nMoveLayer(h: Long, id: Long, delta: Int): Long

    @JvmStatic external fun nExtendHold(h: Long, index: Int, count: Int): Long
    @JvmStatic external fun nShrinkHold(h: Long, index: Int, count: Int): Long
    @JvmStatic external fun nHoldStart(h: Long, index: Int): Int
    @JvmStatic external fun nHoldEnd(h: Long, index: Int): Int
    @JvmStatic external fun nSameExposure(h: Long, a: Int, b: Int): Boolean

    /** Monta um projeto a partir de peças (ver studio_jni.cpp). */
    @JvmStatic external fun nBuild(
        frameIds: LongArray, current: Int, nextId: Long, layerIds: LongArray, layerMeta: LongArray, names: Array<String>,
        drawings: LongArray, cels: LongArray,
    ): Long

    /** [índice, direção, terminou (0/1)]. Modo: 0 uma vez, 1 loop, 2 ping-pong. */
    @JvmStatic external fun nNextPlaybackStep(index: Int, count: Int, mode: Int, direction: Int): IntArray
}
