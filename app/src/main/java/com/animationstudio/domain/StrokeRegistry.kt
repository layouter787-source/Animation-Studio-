package com.animationstudio.domain

import java.util.IdentityHashMap

/**
 * Onde vivem os traços (objetos Kotlin: pontos, preenchimentos, imagens, caches de desenho). O modelo C++ do projeto
 * referencia-os por id; o registo devolve sempre o MESMO objeto para o mesmo id, por isso a identidade dos traços e das
 * listas de traços de um desenho (de que a cache de imagens por camada depende) mantém-se enquanto o conteúdo não muda.
 * Quando o C++ deixa de guardar um traço ou uma versão de desenho (nenhum projeto o referencia), a ficha é libertada
 * ([NativeStudio.nDrainReleased]) e o registo larga o objeto.
 */
internal object StrokeRegistry {
    private val byId = HashMap<Long, BrushStroke>()
    private val ids = IdentityHashMap<BrushStroke, Long>()
    private val drawings = HashMap<Long, Drawing>()  // serial (versão do desenho) -> desenho
    private var next = 0L

    /** Id do traço (regista-o se ainda não está registado). */
    @Synchronized
    fun idOf(s: BrushStroke): Long {
        drain()
        ids[s]?.let { return it }
        next += 1
        byId[next] = s
        ids[s] = next
        return next
    }

    @Synchronized
    fun stroke(id: Long): BrushStroke? = byId[id]

    /** Desenho da versão [serial]; a mesma instância (e a mesma lista) enquanto a versão existir. */
    @Synchronized
    fun drawing(serial: Long, drawingId: Long, flat: LongArray, from: Int, count: Int): Drawing =
        drawings.getOrPut(serial) {
            val list = ArrayList<BrushStroke>(count)
            for (j in 0 until count) byId[flat[from + j]]?.let { list.add(it) }
            Drawing(drawingId, list)
        }

    /** Larga o que o C++ já não referencia. */
    @Synchronized
    fun drain() {
        val r = NativeStudio.nDrainReleased()
        var k = 0
        while (k + 1 < r.size) {
            val id = r[k + 1]
            if (r[k] == 0L) byId.remove(id)?.let { ids.remove(it) } else drawings.remove(id)
            k += 2
        }
    }

    /** Para os testes: quantos traços e versões de desenho estão registados. */
    @Synchronized
    fun sizes(): Pair<Int, Int> = byId.size to drawings.size
}
