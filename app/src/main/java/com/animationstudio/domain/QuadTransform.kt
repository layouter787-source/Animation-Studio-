package com.animationstudio.domain

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Geometria da transformação em perspetiva do laço (como a "Forma de Perspetiva" do Ibis Paint X).
 * O quadrilátero tem 4 cantos na ordem: 0 = topo-esquerda, 1 = topo-direita, 2 = baixo-direita, 3 = baixo-esquerda.
 *  - arrastar um canto deforma livremente (efeito de perspetiva);
 *  - arrastar o meio de uma aresta estica ou comprime o desenho ao longo da normal dessa aresta;
 *  - o pivô (por defeito, a interseção das diagonais em X) é o centro da rotação.
 */
object QuadTransform {
    const val NONE = 0
    const val BODY = 1
    const val CORNER0 = 10
    const val EDGE0 = 20
    const val PIVOT = 30
    const val ROTATE = 31

    /** Interseção das diagonais (X); se forem paralelas, a média dos cantos. */
    fun centroid(q: List<Point>): Point {
        val x1 = q[0].x; val y1 = q[0].y
        val x2 = q[2].x; val y2 = q[2].y
        val x3 = q[1].x; val y3 = q[1].y
        val x4 = q[3].x; val y4 = q[3].y
        val den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(den) < 1e-6f) return Point((x1 + x2 + x3 + x4) / 4f, (y1 + y2 + y3 + y4) / 4f)
        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
        return Point(x1 + t * (x2 - x1), y1 + t * (y2 - y1))
    }

    fun edgeMid(q: List<Point>, i: Int): Point {
        val a = q[i]
        val b = q[(i + 1) % 4]
        return Point((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }

    /** Normal unitária da aresta [i], a apontar para fora do quadrilátero. */
    fun outwardNormal(q: List<Point>, i: Int): Point {
        val a = q[i]
        val b = q[(i + 1) % 4]
        val ex = b.x - a.x
        val ey = b.y - a.y
        val len = hypot(ex, ey)
        if (len < 1e-6f) return Point(0f, -1f)
        var nx = ey / len
        var ny = -ex / len
        val c = centroid(q)
        val m = edgeMid(q, i)
        if ((m.x - c.x) * nx + (m.y - c.y) * ny < 0f) { nx = -nx; ny = -ny }
        return Point(nx, ny)
    }

    /** Pega de rotação: [dist] acima do meio da aresta de cima. */
    fun rotateHandle(q: List<Point>, dist: Float): Point {
        val m = edgeMid(q, 0)
        val n = outwardNormal(q, 0)
        return Point(m.x + n.x * dist, m.y + n.y * dist)
    }

    /** Move a aresta [i] só ao longo da sua normal: estica ou comprime sem incliná-la. */
    fun dragEdge(q: List<Point>, i: Int, dx: Float, dy: Float): List<Point> {
        val n = outwardNormal(q, i)
        val amt = dx * n.x + dy * n.y
        val j = (i + 1) % 4
        return q.mapIndexed { k, p -> if (k == i || k == j) Point(p.x + n.x * amt, p.y + n.y * amt) else p }
    }

    fun translate(q: List<Point>, dx: Float, dy: Float): List<Point> = q.map { Point(it.x + dx, it.y + dy) }

    fun rotate(q: List<Point>, pivot: Point, rad: Float): List<Point> {
        val a = Affine.around(pivot, 1f, rad, 0f, 0f)
        return q.map { a.map(it) }
    }

    /** Área sem sinal pela fórmula do cordão (para rejeitar quadriláteros colapsados). */
    fun area(q: List<Point>): Float {
        var s = 0f
        for (i in 0 until 4) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2f
    }

    /** O que está sob o toque (sx, sy) em coordenadas de ecrã: pega, canto, aresta, pivô ou corpo. */
    fun hit(q: List<Point>, pivot: Point, v: ViewTransform, sx: Float, sy: Float, slop: Float): Int {
        fun near(p: Point, r: Float): Boolean {
            val s = v.projectToScreen(p)
            return hypot(sx - s.x, sy - s.y) <= r
        }
        if (near(rotateHandle(q, slop / v.scale), slop)) return ROTATE
        if (near(pivot, slop * 0.7f)) return PIVOT
        for (i in 0 until 4) if (near(q[i], slop)) return CORNER0 + i
        for (i in 0 until 4) if (near(edgeMid(q, i), slop)) return EDGE0 + i
        return if (Lasso.pointInPolygon(Point(sx, sy), q.map { v.projectToScreen(it) })) BODY else NONE
    }
}
