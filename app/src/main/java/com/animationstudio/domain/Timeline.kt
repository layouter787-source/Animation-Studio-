package com.animationstudio.domain

/** Traço de pincel/borracha em coordenadas do projeto (independente de zoom, pan e rotação). */
data class BrushStroke(
    val points: List<Point>,
    val colorArgb: Int,
    val width: Float,
    val erase: Boolean = false,
)

/** Cada frame tem identidade e conteúdo próprios. */
data class AnimFrame(val id: Long, val strokes: List<BrushStroke> = emptyList())

enum class PlayMode { ONCE, LOOP, PING_PONG }

data class PlaybackStep(val index: Int, val direction: Int, val finished: Boolean)

/** Próximo frame da reprodução. Em PING_PONG os extremos não são repetidos. */
fun nextPlaybackStep(index: Int, count: Int, mode: PlayMode, direction: Int): PlaybackStep = when (mode) {
    PlayMode.ONCE -> {
        val n = index + 1
        if (n >= count) PlaybackStep(index, 1, true) else PlaybackStep(n, 1, false)
    }
    PlayMode.LOOP -> PlaybackStep((index + 1) % count, 1, false)
    PlayMode.PING_PONG -> {
        var d = if (direction >= 0) 1 else -1
        var n = index + d
        if (n >= count) { d = -1; n = index - 1 } else if (n < 0) { d = 1; n = index + 1 }
        PlaybackStep(n.coerceIn(0, count - 1), d, false)
    }
}

/** Sequência imutável de frames. Mudar de frame nunca altera o conteúdo de outro frame. */
data class Timeline(val frames: List<AnimFrame>, val current: Int, val nextId: Long) {
    init {
        require(frames.isNotEmpty()) { "A timeline precisa de pelo menos um frame" }
        require(current in frames.indices) { "Frame atual fora dos limites" }
    }

    val count: Int get() = frames.size
    val currentFrame: AnimFrame get() = frames[current]

    fun select(index: Int): Timeline = copy(current = index.coerceIn(0, frames.lastIndex))

    fun addBlankAfterCurrent(): Timeline {
        val list = frames.toMutableList()
        list.add(current + 1, AnimFrame(nextId))
        return Timeline(list, current + 1, nextId + 1)
    }

    fun duplicateCurrent(): Timeline {
        val list = frames.toMutableList()
        list.add(current + 1, currentFrame.copy(id = nextId))
        return Timeline(list, current + 1, nextId + 1)
    }

    fun deleteCurrent(): Timeline {
        if (frames.size == 1) return this
        val list = frames.toMutableList()
        list.removeAt(current)
        return Timeline(list, current.coerceAtMost(list.lastIndex), nextId)
    }

    fun addStrokeToCurrent(stroke: BrushStroke): Timeline {
        val list = frames.toMutableList()
        list[current] = currentFrame.copy(strokes = currentFrame.strokes + stroke)
        return copy(frames = list)
    }

    companion object {
        fun initial(): Timeline = Timeline(listOf(AnimFrame(0)), 0, 1)
    }
}

/** Histórico de desfazer/refazer por instantâneos imutáveis (partilha estrutural, barato). */
data class History<T>(
    val undoStack: List<T> = emptyList(),
    val redoStack: List<T> = emptyList(),
    val limit: Int = 100,
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun record(previous: T): History<T> = copy(undoStack = (undoStack + previous).takeLast(limit), redoStack = emptyList())

    fun undo(current: T): Pair<T, History<T>>? {
        if (undoStack.isEmpty()) return null
        return undoStack.last() to copy(undoStack = undoStack.dropLast(1), redoStack = redoStack + current)
    }

    fun redo(current: T): Pair<T, History<T>>? {
        if (redoStack.isEmpty()) return null
        return redoStack.last() to copy(undoStack = undoStack + current, redoStack = redoStack.dropLast(1))
    }
}
