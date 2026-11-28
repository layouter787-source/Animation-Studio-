// Testes do traço de tinta (pontas por velocidade, contorno, corda). Compilar e correr:
//   g++ -std=c++17 -O1 -I.. studio_ink_test.cpp -o t && ./t
#include <cmath>
#include <cstdio>

#include "studio_ink.hpp"

using namespace studio::ink;

static int g_fail = 0;
#define CHECK(c) do { if (!(c)) { std::printf("FALHOU %s:%d  %s\n", __FILE__, __LINE__, #c); ++g_fail; } } while (0)

/** Reta horizontal de [len] px percorrida em [ms] ms, amostrada a 8 ms. */
static std::vector<Sample> line(float len, float ms) {
    std::vector<Sample> v;
    for (float t = 0; t <= ms; t += 8.f) v.push_back({100.f + len * t / ms, 100.f, 1.f, t});
    return v;
}

/** Meia espessura do contorno na abscissa [x] (maior extensão em y dos polígonos que cobrem x). */
static float halfWidthAt(const std::vector<float>& poly, float x) {
    float top = 1e9f, bottom = -1e9f;
    size_t k = 0;
    while (k < poly.size()) {
        const int n = static_cast<int>(poly[k]);
        float mn = 1e9f, mx = -1e9f, y0 = 1e9f, y1 = -1e9f;
        for (int j = 0; j < n; ++j) {
            const float px = poly[k + 1 + 2 * j], py = poly[k + 2 + 2 * j];
            mn = std::min(mn, px); mx = std::max(mx, px);
            y0 = std::min(y0, py); y1 = std::max(y1, py);
        }
        if (mn <= x && x <= mx) { top = std::min(top, y0); bottom = std::max(bottom, y1); }
        k += 1 + 2 * static_cast<size_t>(n);
    }
    return bottom < top ? 0.f : (bottom - top) * 0.5f;
}

static float maxX(const std::vector<float>& poly) {
    float m = -1e9f;
    size_t k = 0;
    while (k < poly.size()) {
        const int n = static_cast<int>(poly[k]);
        for (int j = 0; j < n; ++j) m = std::max(m, poly[k + 1 + 2 * j]);
        k += 1 + 2 * static_cast<size_t>(n);
    }
    return m;
}

int main() {
    Params P;
    P.width = 4.f;
    {  // vazio e ponto
        CHECK(outline({}, P).empty());
        auto d = outline({{10, 10, 1, 0}}, P);
        CHECK(!d.empty());
        CHECK(std::fabs(halfWidthAt(d, 10.f) - 2.f) < 0.15f);
    }
    {  // contorno sem NaN, polígonos bem formados
        auto o = outline(line(200.f, 400.f), P);
        size_t k = 0;
        bool ok = true;
        while (k < o.size()) {
            const int n = static_cast<int>(o[k]);
            if (n < 3) ok = false;
            for (int j = 0; j < 2 * n; ++j) if (!std::isfinite(o[k + 1 + j])) ok = false;
            k += 1 + 2 * static_cast<size_t>(n);
        }
        CHECK(ok && k == o.size());
    }
    {  // a ponta do fim: rápida = longa e fina; lenta = curta e mais grossa
        auto fast = outline(line(300.f, 150.f), P);   // 2 px/ms
        auto slow = outline(line(300.f, 3000.f), P);  // 0,1 px/ms
        // perto do fim (4 px antes) a meia espessura:
        const float hf = halfWidthAt(fast, maxX(fast) - 4.f), hs = halfWidthAt(slow, maxX(slow) - 4.f);
        CHECK(hf < hs);
        // no meio, os dois têm a espessura cheia
        CHECK(std::fabs(halfWidthAt(fast, 250.f) - 2.f) < 0.2f);
        CHECK(std::fabs(halfWidthAt(slow, 250.f) - 2.f) < 0.2f);
        // a rápida ainda está fina a 40 px do fim; a lenta já engrossou
        CHECK(halfWidthAt(fast, maxX(fast) - 40.f) < 1.6f);
        CHECK(halfWidthAt(slow, maxX(slow) - 40.f) > 1.9f);
    }
    {  // a ponta nunca desaparece: raio mínimo
        auto o = outline(line(300.f, 150.f), P);
        CHECK(halfWidthAt(o, maxX(o) - 0.5f) >= 0.1f);
    }
    {  // sem pontas: espessura cheia até ao fim
        Params Q = P;
        Q.startTaper = 0.f;
        Q.endTaper = 0.f;
        auto o = outline(line(200.f, 400.f), Q);
        CHECK(std::fabs(halfWidthAt(o, 101.f) - 2.f) < 0.2f);
        CHECK(std::fabs(halfWidthAt(o, 298.f) - 2.f) < 0.2f);
    }
    {  // pressão: sem efeito a 0; com 1, pressão fraca afina
        std::vector<Sample> v = line(200.f, 400.f);
        for (auto& s : v) s.p = 0.2f;
        Params Q = P;
        Q.startTaper = 0.f; Q.endTaper = 0.f;
        CHECK(std::fabs(halfWidthAt(outline(v, Q), 200.f) - 2.f) < 0.2f);
        Q.pressureWidth = 1.f;
        CHECK(halfWidthAt(outline(v, Q), 200.f) < 1.0f);
    }
    {  // traços sem tempo (antigos) também funcionam
        std::vector<Sample> v;
        for (int i = 0; i < 40; ++i) v.push_back({100.f + 3.f * i, 100.f, 1.f, 0.f});
        CHECK(!outline(v, P).empty());
    }
    {  // corda: a ponta não se mexe dentro do comprimento e é arrastada fora dele; o fim chega ao dedo
        float st[4];
        rope::start(st, 0, 0);
        rope::next(st, 5, 0, 20.f);
        CHECK(st[0] == 0.f && st[1] == 0.f);
        rope::next(st, 50, 0, 20.f);
        CHECK(std::fabs(st[0] - 30.f) < 1e-3f);
        auto tail = rope::finish(st, 50, 10, 20.f);
        CHECK(tail.size() >= 2);
        CHECK(tail[tail.size() - 2] == 50.f && tail[tail.size() - 1] == 10.f);
        float s2[4];
        rope::start(s2, 3, 4);
        rope::next(s2, 9, 9, 0.f);   // sem corda segue o dedo
        CHECK(s2[0] == 9.f && s2[1] == 9.f);
    }
    std::printf(g_fail ? "%d falhas\n" : "tudo certo\n", g_fail);
    return g_fail ? 1 : 0;
}
