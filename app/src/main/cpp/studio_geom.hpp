// Geometria do editor em C++ (etapa 1): transformação afim, transformação da vista (zoom/rotação/pan) e câmera com
// keyframes. Mesmas regras do Affine.kt, ViewTransform.kt e Camera.kt, escritas de novo. Só C++ padrão.
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <map>
#include <optional>
#include <utility>
#include <vector>

#include "studio_project.hpp"

namespace studio {

/** Transformação afim 2D: x' = a·x + c·y + tx ; y' = b·x + d·y + ty. */
struct Affine {
    float a = 1, b = 0, c = 0, d = 1, tx = 0, ty = 0;

    Pt map(Pt p) const { return {a * p.x + c * p.y + tx, b * p.x + d * p.y + ty}; }
    float scaleFactor() const { return std::sqrt(std::fabs(a * d - b * c)); }

    /** Composição: aplica primeiro [o] e depois esta transformação. */
    Affine times(const Affine& o) const {
        return {a * o.a + c * o.b, b * o.a + d * o.b, a * o.c + c * o.d, b * o.c + d * o.d,
                a * o.tx + c * o.ty + tx, b * o.tx + d * o.ty + ty};
    }

    std::optional<Affine> inverse() const {
        const float det = a * d - b * c;
        if (std::fabs(det) < 1e-9f) return std::nullopt;
        const float ia = d / det, ib = -b / det, ic = -c / det, id = a / det;
        return Affine{ia, ib, ic, id, -(ia * tx + ic * ty), -(ib * tx + id * ty)};
    }

    static Affine translate(float dx, float dy) { return {1, 0, 0, 1, dx, dy}; }
    static Affine flipH(float px) { return {-1, 0, 0, 1, 2 * px, 0}; }
    static Affine flipV(float py) { return {1, 0, 0, -1, 0, 2 * py}; }

    /** p' = R(rot)·S(scale)·(p − pivot) + pivot + (dx, dy). Rotação positiva = horária no ecrã (y para baixo). */
    static Affine around(Pt pivot, float scale, float rotationRad, float dx, float dy) {
        const float cs = std::cos(rotationRad) * scale, sn = std::sin(rotationRad) * scale;
        const float a = cs, b = sn, c = -sn, d = cs;
        return {a, b, c, d, pivot.x + dx - (a * pivot.x + c * pivot.y), pivot.y + dy - (b * pivot.x + d * pivot.y)};
    }
};

/**
 * Transformação única da vista: projeto -> ecrã.   screen = R(rotationRad) * (scale * project) + (tx, ty)
 * Fonte única de verdade para zoom, rotação visual e pan; a entrada de desenho usa screenToProject (inversa).
 */
struct ViewTransform {
    static constexpr float kMinScale = 0.02f;
    static constexpr float kMaxScale = 1000000.f;  // zoom praticamente ilimitado; o limite protege a aritmética de 32 bits

    float scale = 1, rotationRad = 0, tx = 0, ty = 0;

    float rotationDegrees() const { return static_cast<float>(rotationRad * 180.0 / 3.14159265358979323846); }

    Pt projectToScreen(Pt p) const {
        const float c = std::cos(rotationRad), s = std::sin(rotationRad);
        const float x = scale * p.x, y = scale * p.y;
        return {c * x - s * y + tx, s * x + c * y + ty};
    }
    Pt screenToProject(Pt p) const {
        const float c = std::cos(rotationRad), s = std::sin(rotationRad);
        const float dx = p.x - tx, dy = p.y - ty;
        return {(c * dx + s * dy) / scale, (-s * dx + c * dy) / scale};
    }

    ViewTransform panBy(float dx, float dy) const { return {scale, rotationRad, tx + dx, ty + dy}; }

    /** Zoom mantendo o ponto do projeto que está sob [focus] (ecrã) no mesmo sítio. */
    ViewTransform zoomAround(Pt focus, float factor) const {
        const float ns = std::min(kMaxScale, std::max(kMinScale, scale * factor));
        return anchored(screenToProject(focus), focus, ns, rotationRad);
    }
    /** Rotação visual em torno de [focus] (ecrã), preservando o foco. */
    ViewTransform rotateAround(Pt focus, float deltaRad) const {
        return anchored(screenToProject(focus), focus, scale, rotationRad + deltaRad);
    }

    /** Ajustar à folha: maior escala que mostra a folha inteira, centrada, sem rotação. */
    static ViewTransform fitSheet(float sheetW, float sheetH, float viewW, float viewH, float margin = 16.f) {
        const float s = std::min(kMaxScale, std::max(kMinScale, std::min((viewW - 2 * margin) / sheetW, (viewH - 2 * margin) / sheetH)));
        return {s, 0.f, (viewW - sheetW * s) / 2.f, (viewH - sheetH * s) / 2.f};
    }

private:
    static ViewTransform anchored(Pt pp, Pt sp, float ns, float nr) {
        const float c = std::cos(nr), s = std::sin(nr);
        const float x = ns * pp.x, y = ns * pp.y;
        return {ns, nr, sp.x - (c * x - s * y), sp.y - (s * x + c * y)};
    }
};

/** Estado da câmera num keyframe: centro (coordenadas da folha), zoom (1 = vê a folha inteira) e rotação (rad, horária). */
struct CameraKey {
    float cx = 0, cy = 0, zoom = 1, rotation = 0;

    /** Transformação folha → imagem de saída: o centro da câmera vai para o meio da saída. */
    Affine outputAffine(float sw, float sh) const {
        const Affine toOrigin = Affine::translate(-cx, -cy);
        const Affine rot = Affine::around({0, 0}, 1.f, -rotation, 0.f, 0.f);
        const Affine scale{zoom, 0, 0, zoom, 0, 0};
        const Affine toCenter = Affine::translate(sw / 2.f, sh / 2.f);
        return toCenter.times(scale).times(rot).times(toOrigin);
    }

    /** Os quatro cantos do enquadramento na folha. */
    std::vector<Pt> rect(float sw, float sh) const {
        auto inv = outputAffine(sw, sh).inverse();
        if (!inv) return {};
        std::vector<Pt> r;
        for (Pt p : {Pt{0, 0}, Pt{sw, 0}, Pt{sw, sh}, Pt{0, sh}}) r.push_back(inv->map(p));
        return r;
    }

    static CameraKey defaultFor(float sw, float sh) { return {sw / 2.f, sh / 2.f, 1.f, 0.f}; }
};

/** Keyframes da câmera por id de frame (inserir ou apagar frames não os desloca). */
struct CameraTrack {
    std::map<int64_t, CameraKey> keys;
    bool empty() const { return keys.empty(); }
    CameraTrack withKey(int64_t frameId, const CameraKey& k) const { CameraTrack t = *this; t.keys[frameId] = k; return t; }
    CameraTrack withoutKey(int64_t frameId) const { CameraTrack t = *this; t.keys.erase(frameId); return t; }
};

/**
 * Câmera na posição [position] da timeline (índice de frame, pode ser fracionário): interpola entre os dois keyframes
 * vizinhos (o zoom de forma proporcional); antes do primeiro e depois do último mantém o mais próximo.
 */
inline CameraKey cameraAt(const std::vector<int64_t>& frameIds, const CameraTrack& track, float position, float sw, float sh) {
    if (track.empty()) return CameraKey::defaultFor(sw, sh);
    std::vector<std::pair<int, CameraKey>> keys;
    for (size_t i = 0; i < frameIds.size(); ++i) {
        auto it = track.keys.find(frameIds[i]);
        if (it != track.keys.end()) keys.emplace_back(static_cast<int>(i), it->second);
    }
    if (keys.empty()) return CameraKey::defaultFor(sw, sh);
    if (position <= static_cast<float>(keys.front().first)) return keys.front().second;
    if (position >= static_cast<float>(keys.back().first)) return keys.back().second;
    auto lerp = [](float a, float b, float u) { return a + (b - a) * u; };
    for (size_t i = 0; i + 1 < keys.size(); ++i) {
        const int t0 = keys[i].first, t1 = keys[i + 1].first;
        const CameraKey &k0 = keys[i].second, &k1 = keys[i + 1].second;
        if (position >= static_cast<float>(t0) && position <= static_cast<float>(t1)) {
            const float u = t1 == t0 ? 1.f : (position - static_cast<float>(t0)) / static_cast<float>(t1 - t0);
            return {lerp(k0.cx, k1.cx, u), lerp(k0.cy, k1.cy, u), k0.zoom * std::pow(k1.zoom / k0.zoom, u),
                    lerp(k0.rotation, k1.rotation, u)};
        }
    }
    return keys.back().second;
}

}  // namespace studio
