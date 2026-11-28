// Balde de tinta em C++ (refeito): não vaza por nenhuma linha, por mais fina que seja, e não deixa vãos debaixo das linhas.
//
//  1. Parede = qualquer vestígio de tinta. Com o toque numa zona transparente, um píxel com opacidade > inkAlpha (0 por
//     defeito: basta 1/255) é parede, por isso pontas e fios mais finos do que um píxel (que o ecrã mostra como uma
//     mancha leve) continuam a fechar a região. Com o toque numa cor (preenchimento anterior), é parede o que difere dessa
//     cor mais do que colorTolerance. A inundação é de 4 vizinhos: uma parede ligada na diagonal também fecha.
//  2. Sem fecho de frestas primeiro (preenche tudo o que está ligado). Só se isso cobrir quase a folha toda (vazamento
//     por uma fresta real) tenta fechar frestas, cada vez maiores (fecho morfológico: não engrossa as linhas).
//  3. Sobras: só entram no preenchimento cantos minúsculos e tiras de 2 células de espessura (a cunha onde o fim de um
//     círculo não coincide com o início). Regiões pequenas a sério (um olho desenhado, um 'o') ficam de fora.
//  4. Debaixo das linhas: cada píxel de tinta fica com a região livre MAIS PERTO (Voronoi sobre as paredes). A região
//     chega ao meio da linha, sem sobrar vão (nem se a linha for translúcida ou esbatida) e nunca passa para o lado de lá.
//     Num empate (linha de uma célula entre duas regiões) o píxel entra em ambas, para não ficar um fio sem cor.
// Só C++ padrão; sem dependências.
#pragma once
#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <vector>

namespace studio {

struct FillParams {
    int inkAlpha = 0;          // 0..255: opacidade acima da qual um píxel é parede (toque numa zona transparente)
    int colorTolerance = 8;    // soma das diferenças de canal acima da qual um píxel é parede (toque numa cor)
    int userGapCells = 0;      // frestas que o utilizador pediu para fechar, em células
    int growCap = 24;          // quantas células o preenchimento pode entrar debaixo de uma linha
};

namespace fill_detail {

/** Dilatação quadrada de raio r em O(n) (somas de prefixos). */
inline std::vector<uint8_t> dilate(const std::vector<uint8_t>& src, int w, int h, int r) {
    if (r <= 0) return src;
    std::vector<uint8_t> tmp(static_cast<size_t>(w) * h, 0), out(static_cast<size_t>(w) * h, 0);
    std::vector<int> pre(static_cast<size_t>(std::max(w, h)) + 1);
    for (int y = 0; y < h; ++y) {
        const size_t row = static_cast<size_t>(y) * w;
        for (int x = 0; x < w; ++x) pre[x + 1] = pre[x] + (src[row + x] ? 1 : 0);
        for (int x = 0; x < w; ++x) {
            const int a = std::max(0, x - r), b = std::min(w - 1, x + r);
            tmp[row + x] = pre[b + 1] - pre[a] > 0;
        }
    }
    for (int x = 0; x < w; ++x) {
        for (int y = 0; y < h; ++y) pre[y + 1] = pre[y] + (tmp[static_cast<size_t>(y) * w + x] ? 1 : 0);
        for (int y = 0; y < h; ++y) {
            const int a = std::max(0, y - r), b = std::min(h - 1, y + r);
            out[static_cast<size_t>(y) * w + x] = pre[b + 1] - pre[a] > 0;
        }
    }
    return out;
}

/** Fecho morfológico: dilatar e depois erodir (erodir = dilatar o complemento). */
inline std::vector<uint8_t> closeGaps(const std::vector<uint8_t>& src, int w, int h, int r) {
    std::vector<uint8_t> inv = dilate(src, w, h, r);
    for (auto& v : inv) v = !v;
    const std::vector<uint8_t> eroded = dilate(inv, w, h, r);
    std::vector<uint8_t> out(src.size());
    for (size_t i = 0; i < src.size(); ++i) out[i] = src[i] || !eroded[i];
    return out;
}

/**
 * Distância (em passos de 4 vizinhos, por dentro das paredes) de cada parede até à fonte mais próxima. 255 = fora do
 * alcance. As fontes são as células de [source] que tocam numa parede.
 */
inline std::vector<uint8_t> wallDistance(const std::vector<uint8_t>& source, const std::vector<uint8_t>& wall, int w, int h, int cap) {
    const size_t n = static_cast<size_t>(w) * h;
    std::vector<uint8_t> dist(n, 255);
    std::vector<int> cur, next;
    auto touchWall = [&](int i, int x, int y) {
        return (x > 0 && wall[i - 1]) || (x < w - 1 && wall[i + 1]) || (y > 0 && wall[i - w]) || (y < h - 1 && wall[i + w]);
    };
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            const int i = y * w + x;
            if (source[i] && !wall[i] && touchWall(i, x, y)) cur.push_back(i);
        }
    }
    // Nível 0: as próprias fontes; as paredes vizinhas ficam a 1.
    for (int d = 1; d <= cap && !cur.empty(); ++d) {
        next.clear();
        for (int i : cur) {
            const int x = i % w, y = i / w;
            const int nb[4] = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
            for (int j : nb) {
                if (j < 0 || !wall[j] || dist[j] != 255) continue;
                dist[j] = static_cast<uint8_t>(d);
                next.push_back(j);
            }
        }
        cur.swap(next);
    }
    return dist;
}

}  // namespace fill_detail

/**
 * Região preenchida a partir do toque (sx, sy) sobre a imagem [px] (ARGB, w×h). Devolve a máscara (1 = preencher), ou
 * vazio se não há onde preencher.
 */
inline std::vector<uint8_t> computeFill(const uint32_t* px, int w, int h, int sx, int sy, const FillParams& P) {
    using namespace fill_detail;
    if (w <= 0 || h <= 0 || sx < 0 || sy < 0 || sx >= w || sy >= h) return {};
    const size_t n = static_cast<size_t>(w) * h;
    const uint32_t seed = px[static_cast<size_t>(sy) * w + sx];
    const int sa = static_cast<int>(seed >> 24), sr = (seed >> 16) & 0xFF, sg = (seed >> 8) & 0xFF, sb = seed & 0xFF;
    const bool transparentSeed = sa < 16;

    std::vector<uint8_t> barrier(n, 0);
    bool anyBarrier = false;
    for (size_t i = 0; i < n; ++i) {
        const uint32_t p = px[i];
        bool b;
        if (transparentSeed) {
            b = static_cast<int>(p >> 24) > P.inkAlpha;
        } else {
            b = std::abs(static_cast<int>(p >> 24) - sa) + std::abs(static_cast<int>((p >> 16) & 0xFF) - sr) +
                    std::abs(static_cast<int>((p >> 8) & 0xFF) - sg) + std::abs(static_cast<int>(p & 0xFF) - sb) >
                P.colorTolerance;
        }
        barrier[i] = b;
        if (b) anyBarrier = true;
    }

    const int sIdx = sy * w + sx;
    std::vector<int> stack(n);

    auto regionFor = [&](int gapCells, bool& ok) -> std::vector<uint8_t> {
        ok = true;
        const std::vector<uint8_t> closed = gapCells > 0 ? closeGaps(barrier, w, h, gapCells) : barrier;
        // Se o toque caiu numa zona estreita que o fecho tapou, usa a referência sem fecho.
        const std::vector<uint8_t>& blocked = closed[sIdx] ? barrier : closed;

        int start = sIdx;
        if (blocked[start]) {
            int found = -1;
            for (int r = 1; r <= 6 && found < 0; ++r) {
                for (int dy = -r; dy <= r && found < 0; ++dy) {
                    for (int dx = -r; dx <= r; ++dx) {
                        const int x = sx + dx, y = sy + dy;
                        if (x < 0 || y < 0 || x >= w || y >= h) continue;
                        if (!blocked[static_cast<size_t>(y) * w + x]) { found = y * w + x; break; }
                    }
                }
            }
            if (found < 0) { ok = false; return {}; }
            start = found;
        }

        std::vector<uint8_t> visited(n, 0);
        int sp = 0;
        stack[sp++] = start;
        visited[start] = 1;
        while (sp > 0) {
            const int i = stack[--sp];
            const int x = i % w, y = i / w;
            if (x > 0 && !visited[i - 1] && !blocked[i - 1]) { visited[i - 1] = 1; stack[sp++] = i - 1; }
            if (x < w - 1 && !visited[i + 1] && !blocked[i + 1]) { visited[i + 1] = 1; stack[sp++] = i + 1; }
            if (y > 0 && !visited[i - w] && !blocked[i - w]) { visited[i - w] = 1; stack[sp++] = i - w; }
            if (y < h - 1 && !visited[i + w] && !blocked[i + w]) { visited[i + w] = 1; stack[sp++] = i + w; }
        }

        // Sobras coladas: cantos minúsculos e tiras de ~2 células. Regiões pequenas a sério ficam de fora (não vazar).
        const int maxPocket = 12;
        const int sliverCap = std::max<int>(400, static_cast<int>(n / 4000));
        const std::vector<uint8_t> near = dilate(visited, w, h, 3);
        std::vector<uint8_t> seen(n, 0);
        std::vector<int> pocket(static_cast<size_t>(sliverCap) + 1);
        std::vector<int> absorbed;
        for (size_t s0 = 0; s0 < n; ++s0) {
            if (!near[s0] || visited[s0] || barrier[s0] || seen[s0]) continue;
            int count = 0, edgeCells = 0;
            bool overflow = false;
            sp = 0;
            stack[sp++] = static_cast<int>(s0);
            seen[s0] = 1;
            while (sp > 0) {
                const int i = stack[--sp];
                if (count < sliverCap) pocket[count] = i; else overflow = true;
                ++count;
                const int x = i % w, y = i / w;
                bool edge = false;
                auto look = [&](bool inside, int j) {
                    if (!inside) { edge = true; return; }
                    if (barrier[j] || visited[j]) { edge = true; return; }
                    if (!seen[j]) { seen[j] = 1; stack[sp++] = j; }
                };
                look(x > 0, i - 1);
                look(x < w - 1, i + 1);
                look(y > 0, i - w);
                look(y < h - 1, i + w);
                if (edge) ++edgeCells;
            }
            if (overflow) continue;
            const float thickness = count * 2.f / std::max(1, edgeCells);
            if (count <= maxPocket || thickness <= 2.5f) {
                for (int k = 0; k < count; ++k) absorbed.push_back(pocket[k]);
            }
        }
        for (int c : absorbed) visited[c] = 1;

        // Debaixo das linhas: cada parede fica com a região livre mais perto (empate: entra também aqui).
        std::vector<uint8_t> other(n, 0);
        for (size_t i = 0; i < n; ++i) other[i] = !barrier[i] && !visited[i];
        const std::vector<uint8_t> dA = wallDistance(visited, barrier, w, h, P.growCap);
        const std::vector<uint8_t> dO = wallDistance(other, barrier, w, h, P.growCap);
        std::vector<uint8_t> mask(n, 0);
        for (size_t i = 0; i < n; ++i) mask[i] = visited[i] || (barrier[i] && dA[i] != 255 && dA[i] <= dO[i]);
        return mask;
    };

    auto coverage = [&](const std::vector<uint8_t>& r) {
        size_t c = 0;
        for (uint8_t v : r) c += v;
        return static_cast<float>(c) / static_cast<float>(n);
    };

    const int userGap = std::min(24, std::max(0, P.userGapCells));
    std::vector<int> gaps = {0, userGap, std::max(userGap * 2, 12), 24};
    std::sort(gaps.begin(), gaps.end());
    gaps.erase(std::unique(gaps.begin(), gaps.end()), gaps.end());

    std::vector<uint8_t> region, firstTry;
    bool haveFirst = false;
    for (int g : gaps) {
        bool ok;
        std::vector<uint8_t> r = regionFor(g, ok);
        if (!ok) return {};
        if (!haveFirst) { firstTry = r; haveFirst = true; }
        if (!anyBarrier || coverage(r) < 0.85f) { region = std::move(r); break; }
    }
    if (region.empty()) region = std::move(firstTry);
    return region;
}

}  // namespace studio
