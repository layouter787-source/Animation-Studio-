// Testes do balde em C++. Compilar e correr:
//   g++ -std=c++17 -O1 -I.. studio_fill_test.cpp -o t && ./t
#include <cmath>
#include <cstdio>

#include "studio_fill.hpp"

using namespace studio;

static int g_fail = 0;
#define CHECK(c) do { if (!(c)) { std::printf("FALHOU %s:%d  %s\n", __FILE__, __LINE__, #c); ++g_fail; } } while (0)

struct Img {
    int w, h;
    std::vector<uint32_t> px;
    Img(int w_, int h_) : w(w_), h(h_), px(static_cast<size_t>(w_) * h_, 0u) {}
    uint32_t& at(int x, int y) { return px[static_cast<size_t>(y) * w + x]; }
    /** Preto com cobertura [cov] (0..1) por cima do que existe. */
    void ink(int x, int y, float cov) {
        if (x < 0 || y < 0 || x >= w || y >= h || cov <= 0.f) return;
        const uint32_t a0 = at(x, y) >> 24;
        const float a = 1.f - (1.f - a0 / 255.f) * (1.f - std::min(1.f, cov));
        at(x, y) = (static_cast<uint32_t>(std::lround(a * 255.f)) << 24);
    }
    /** Circunferência de raio R e espessura t (cobertura aproximada por distância, como um desenho com anti-aliasing). */
    void ring(float cx, float cy, float R, float t) {
        for (int y = 0; y < h; ++y)
            for (int x = 0; x < w; ++x) {
                const float d = std::hypot(x + 0.5f - cx, y + 0.5f - cy);
                ink(x, y, std::min(1.f, std::max(0.f, t * 0.5f + 0.5f - std::fabs(d - R))));
            }
    }
    /** Segmento de reta de espessura t. */
    void seg(float x0, float y0, float x1, float y1, float t) {
        const float dx = x1 - x0, dy = y1 - y0, L2 = dx * dx + dy * dy;
        for (int y = 0; y < h; ++y)
            for (int x = 0; x < w; ++x) {
                float u = L2 > 0 ? ((x + 0.5f - x0) * dx + (y + 0.5f - y0) * dy) / L2 : 0.f;
                u = std::min(1.f, std::max(0.f, u));
                const float d = std::hypot(x + 0.5f - (x0 + dx * u), y + 0.5f - (y0 + dy * u));
                ink(x, y, std::min(1.f, std::max(0.f, t * 0.5f + 0.5f - d)));
            }
    }
};

static int countIn(const std::vector<uint8_t>& m, int w, int x0, int y0, int x1, int y1) {
    int c = 0;
    for (int y = y0; y < y1; ++y) for (int x = x0; x < x1; ++x) c += m[static_cast<size_t>(y) * w + x];
    return c;
}

int main() {
    FillParams P;
    {  // linha fina (0,15 px): uma circunferência que a opacidade de 8% já não via. Não pode vazar.
        Img im(120, 120);
        im.ring(60, 60, 30, 0.15f);
        auto m = computeFill(im.px.data(), im.w, im.h, 60, 60, P);
        CHECK(!m.empty());
        CHECK(countIn(m, 120, 0, 0, 120, 10) == 0);          // nada fora (topo da folha)
        CHECK(m[static_cast<size_t>(60) * 120 + 60] == 1);
        CHECK(m[static_cast<size_t>(60) * 120 + 5] == 0);
        // dentro tudo cheio: nenhum vão
        int holes = 0;
        for (int y = 0; y < 120; ++y) for (int x = 0; x < 120; ++x)
            if (std::hypot(x + 0.5f - 60, y + 0.5f - 60) < 29.f && !m[static_cast<size_t>(y) * 120 + x]) ++holes;
        CHECK(holes == 0);
    }
    {  // duas regiões separadas por uma linha de 1 px: a cor de A não passa para B, e não sobra vão no fio
        Img im(80, 40);
        im.seg(40.f, 0.f, 40.f, 40.f, 1.0f);
        auto m = computeFill(im.px.data(), im.w, im.h, 10, 20, P);
        CHECK(countIn(m, 80, 0, 0, 39, 40) == 39 * 40);        // A inteiro
        CHECK(countIn(m, 80, 42, 0, 80, 40) == 0);            // B nada
        int line = 0;
        for (int y = 0; y < 40; ++y) for (int x = 39; x <= 41; ++x) if (im.at(x, y) >> 24) line += m[static_cast<size_t>(y) * 80 + x];
        CHECK(line > 0);                                       // debaixo do fio há cor
    }
    {  // linha larga e esbatida (8 px, meia opacidade) entre A e B: cada lado fica até ao meio, sem vão e sem passar
        Img im(100, 30);
        for (int y = 0; y < 30; ++y) for (int x = 46; x < 54; ++x) im.at(x, y) = 0x80000000u;
        auto a = computeFill(im.px.data(), 100, 30, 10, 15, P);
        auto b = computeFill(im.px.data(), 100, 30, 90, 15, P);
        for (int y = 0; y < 30; ++y) {
            CHECK(a[static_cast<size_t>(y) * 100 + 49] == 1 && a[static_cast<size_t>(y) * 100 + 50] == 0);
            CHECK(b[static_cast<size_t>(y) * 100 + 50] == 1 && b[static_cast<size_t>(y) * 100 + 49] == 0);
        }
    }
    {  // um 'olho' pequeno desenhado com linha fina dentro da região: não é engolido
        Img im(200, 200);
        im.ring(100, 100, 80, 3.f);
        im.ring(100, 100, 6.f, 0.4f);
        auto m = computeFill(im.px.data(), 200, 200, 40, 100, P);
        CHECK(m[static_cast<size_t>(100) * 200 + 40] == 1);
        CHECK(m[static_cast<size_t>(100) * 200 + 100] == 0);   // dentro do olho: fora
    }
    {  // fundo de uma folha com um desenho no meio: preenche tudo o que não é tinta
        Img im(100, 100);
        im.ring(50, 50, 20, 2.f);
        auto m = computeFill(im.px.data(), 100, 100, 3, 3, P);
        int free = 0, got = 0;
        for (int y = 0; y < 100; ++y) for (int x = 0; x < 100; ++x) {
            if (std::hypot(x + 0.5f - 50, y + 0.5f - 50) > 23.f) { ++free; got += m[static_cast<size_t>(y) * 100 + x]; }
        }
        CHECK(got == free);
        CHECK(m[static_cast<size_t>(50) * 100 + 50] == 0);     // o miolo do círculo não
    }
    {  // toque em cima de uma linha fraca: procura a célula livre mais próxima
        Img im(60, 60);
        im.ring(30, 30, 20, 1.f);
        auto m = computeFill(im.px.data(), 60, 60, 30 + 20, 30, P);
        CHECK(!m.empty());
    }
    {  // círculo com uma fresta pequena: tenta fechar e não enche a folha
        Img im(120, 120);
        im.ring(60, 60, 30, 2.f);
        for (int y = 26; y <= 34; ++y) for (int x = 56; x <= 64; ++x) im.at(x, y) = 0u;   // fresta de ~9 px no topo
        auto m = computeFill(im.px.data(), 120, 120, 60, 60, P);
        CHECK(!m.empty());
        CHECK(m[static_cast<size_t>(5) * 120 + 5] == 0);
        CHECK(m[static_cast<size_t>(60) * 120 + 60] == 1);
    }
    std::printf(g_fail ? "%d falhas\n" : "tudo certo\n", g_fail);
    return g_fail ? 1 : 0;
}
