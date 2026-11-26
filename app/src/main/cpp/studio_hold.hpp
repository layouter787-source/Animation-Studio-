// Exposição de frames em C++ (etapa 1): vários frames seguidos que apontam para o mesmo desenho em todas as camadas.
// Estender acrescenta frames que partilham o desenho (não copia nada); encurtar tira o último. Mesmas regras do
// FrameHold.kt. Frames em branco não contam: só há exposição onde há desenho numa camada de desenho.
#pragma once
#include <algorithm>
#include <map>
#include <set>

#include "studio_project.hpp"

namespace studio {

/** Dois frames mostram exatamente o mesmo desenho (e há desenho numa camada de desenho). */
inline bool sameExposure(const Project& p, int a, int b) {
    if (a == b || a < 0 || b < 0 || a >= p.frameCount() || b >= p.frameCount()) return false;
    const int64_t fa = p.frameIds[static_cast<size_t>(a)], fb = p.frameIds[static_cast<size_t>(b)];
    bool any = false;
    for (const Layer& l : p.layers) {
        auto ca = l.cels.find(fa), cb = l.cels.find(fb);
        const bool ha = ca != l.cels.end(), hb = cb != l.cels.end();
        if (ha != hb) return false;
        if (ha && ca->second != cb->second) return false;
        if (ha && l.kind == LayerKind::Drawing) any = true;
    }
    return any;
}

inline int holdStart(const Project& p, int index) {
    int j = std::min(std::max(index, 0), p.frameCount() - 1);
    while (j > 0 && sameExposure(p, j - 1, j)) --j;
    return j;
}

inline int holdEnd(const Project& p, int index) {
    int j = std::min(std::max(index, 0), p.frameCount() - 1);
    while (j + 1 < p.frameCount() && sameExposure(p, j, j + 1)) ++j;
    return j;
}

/** Estende a exposição do frame [index] em [count] frames, logo a seguir ao fim dela. O frame atual não muda. */
inline Project extendHold(const Project& start, int index, int count = 1) {
    Project p = start;
    const int n = std::min(std::max(count, 0), 500);
    for (int k = 0; k < n; ++k) {
        const int e = holdEnd(p, std::min(index, p.frameCount() - 1));
        const int64_t src = p.frameIds[static_cast<size_t>(e)];
        const int64_t newId = p.nextId;
        for (Layer& l : p.layers) {
            auto c = l.cels.find(src);
            if (c != l.cels.end()) l.cels[newId] = c->second;
        }
        p.frameIds.insert(p.frameIds.begin() + e + 1, newId);
        if (p.currentFrame > e) p.currentFrame += 1;
        p.nextId += 1;
    }
    return p;
}

/** Encurta a exposição do frame [index] em [count] frames (tira o último de cada vez; nunca o primeiro). */
inline Project shrinkHold(const Project& start, int index, int count = 1) {
    Project p = start;
    int idx = index;
    const int n = std::min(std::max(count, 0), 500);
    for (int k = 0; k < n; ++k) {
        idx = std::min(std::max(idx, 0), p.frameCount() - 1);
        const int s = holdStart(p, idx), e = holdEnd(p, idx);
        if (e <= s) continue;
        const int64_t fid = p.frameIds[static_cast<size_t>(e)];
        p.frameIds.erase(p.frameIds.begin() + e);
        for (Layer& l : p.layers) {
            l.cels.erase(fid);
            std::set<int64_t> used;
            for (const auto& kv : l.cels) used.insert(kv.second);
            std::map<int64_t, DrawingPtr> kept;
            for (const auto& kv : l.drawings) if (used.count(kv.first)) kept[kv.first] = kv.second;
            l.drawings = std::move(kept);
        }
        if (p.currentFrame == e) p.currentFrame = e - 1;
        else if (p.currentFrame > e) p.currentFrame -= 1;
        if (idx == e) idx = e - 1;
    }
    return p;
}

}  // namespace studio
