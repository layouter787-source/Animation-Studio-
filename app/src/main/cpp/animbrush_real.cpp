// Pincéis de natureza realistas (C++): ramo com folhas, folhas soltas e grama.
//
// Cada folha e cada lâmina é calculada por píxel com cobertura analítica (bordas suaves, sem serrilhado), em vez de ser um
// carimbo de mancha verde: contorno com ponta e base próprias, dentes na margem, nervura central, nervuras laterais,
// dobra ao longo da nervura (um lado mais claro), curvatura, mosqueado e variação de cor de folha para folha.
// A grama são lâminas curvas (Bézier) que afinam até à ponta, com base escura, ponta clara e vinco ao centro.
// A dinâmica (pressão, velocidade, direção do traço) comanda tamanho, abertura e inclinação.
#include "animbrush_core.h"

#ifdef ANIMBRUSH_ENABLED
#include <algorithm>
#include <cmath>
#include <memory>

#include "animbrush/animbrush.hpp"

namespace abridge {
namespace {
using namespace ab;

// ---------------------------------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------------------------------
inline Color shadeC(Color c, float k) { return {clampf(c.r * k), clampf(c.g * k), clampf(c.b * k), c.a}; }
inline Color lightenC(Color c, float k) { return mix(c, Color{1, 1, 1, c.a}, k); }
inline float smooth01(float t) { t = clampf(t); return t * t * (3.f - 2.f * t); }

// Escreve um píxel (cor direita, cobertura) num Patch pré-multiplicado, por cima do que lá estiver (source-over).
inline void putPx(Patch& p, int x, int y, Color c, float cov) {
    if (!p.contains(x, y) || cov <= 0.002f) return;
    float* d = p.at(x, y);
    const float a = clampf(cov);
    const float inv = 1.f - a;
    d[0] = c.r * a + d[0] * inv;
    d[1] = c.g * a + d[1] * inv;
    d[2] = c.b * a + d[2] * inv;
    d[3] = a + d[3] * inv;
}

inline IRect clipRect(ISurface& s, float x0, float y0, float x1, float y1) {
    IRect r;
    r.x0 = std::max(0, static_cast<int>(std::floor(x0)));
    r.y0 = std::max(0, static_cast<int>(std::floor(y0)));
    r.x1 = std::min(s.width(), static_cast<int>(std::ceil(x1)) + 1);
    r.y1 = std::min(s.height(), static_cast<int>(std::ceil(y1)) + 1);
    return r;
}

// Cápsula com largura variável (ramo, pecíolo): cobertura pela distância ao segmento.
void capsule(ISurface& s, Vec2 a, Vec2 b, float ra, float rb, Color c, float lightSide) {
    const float m = std::max(ra, rb) + 1.5f;
    IRect r = clipRect(s, std::min(a.x, b.x) - m, std::min(a.y, b.y) - m, std::max(a.x, b.x) + m, std::max(a.y, b.y) + m);
    if (r.empty()) return;
    Patch pt(r.x0, r.y0, r.w(), r.h());
    const Vec2 ab = b - a;
    const float l2 = std::max(1e-6f, ab.len2());
    const Vec2 nrm = ab.norm().perp();
    for (int y = r.y0; y < r.y1; ++y) {
        for (int x = r.x0; x < r.x1; ++x) {
            const Vec2 p{x + 0.5f, y + 0.5f};
            const float t = clampf(dot(p - a, ab) / l2);
            const float rad = lerpf(ra, rb, t);
            const float d = (p - (a + ab * t)).len();
            const float cov = clampf(0.5f + (rad - d));
            if (cov <= 0.f) continue;
            // Cilindro: o lado de cima apanha luz
            const float side = clampf(dot(p - (a + ab * t), nrm) / std::max(0.3f, rad), -1.f, 1.f);
            Color col = c;
            col = side < 0 ? lightenC(col, lightSide * -side) : shadeC(col, 1.f - 0.35f * side * lightSide * 2.f);
            putPx(pt, x, y, col, cov);
        }
    }
    s.compositePatch(pt, Blend::Normal, 1.f);
}

// ---------------------------------------------------------------------------------------------
// Folha
// ---------------------------------------------------------------------------------------------
struct LeafSpec {
    float len = 40, wid = 20;   // comprimento e largura totais (px)
    float angle = 0;            // direção base -> ponta (rad)
    float bend = 0;             // curvatura (fração do comprimento, com sinal)
    float widest = 0.4f;        // onde é mais larga (0..1 ao longo)
    float pointy = 1.f;         // agudeza da ponta (>1 = mais afilada)
    float baseRound = 0.8f;     // arredondamento da base (<1 = mais afilada)
    float tooth = 0;            // amplitude dos dentes (fração da meia-largura)
    float teeth = 12;           // nº de dentes
    float veins = 7;            // pares de nervuras laterais
    float fold = 0.6f;          // contraste entre as duas metades
    float notch = 0;            // reentrância na base (cordiforme)
    Color base, light, dark;
    uint32_t seed = 1;
};

// Meia-largura relativa (0..1) em u (0..1).
inline float profile(const LeafSpec& L, float u) {
    if (u <= 0.f || u >= 1.f) return 0.f;
    float w;
    if (u < L.widest) {
        const float k = u / L.widest;
        w = std::pow(std::sin(kPi * 0.5f * k), L.baseRound);
    } else {
        const float k = (u - L.widest) / (1.f - L.widest);
        w = std::pow(std::cos(kPi * 0.5f * k), L.pointy);
    }
    if (L.tooth > 0.f) {
        const float ph = u * L.teeth;
        const float saw = 1.f - 2.f * std::fabs(ph - std::floor(ph) - 0.5f);   // 0..1..0
        w *= 1.f + L.tooth * (saw - 0.5f) * smooth01(u * 8.f) * smooth01((1.f - u) * 6.f);
    }
    return w;
}

void drawLeaf(ISurface& s, Vec2 base, const LeafSpec& L, float opacity) {
    const Vec2 dir = fromAngle(L.angle);
    const Vec2 nrm = dir.perp();
    const float half = L.wid * 0.5f;
    const float reach = L.len + std::fabs(L.bend) * L.len + half * 1.4f + 3.f;
    IRect r = clipRect(s, base.x - reach, base.y - reach, base.x + reach, base.y + reach);
    // caixa mais justa: os 4 cantos do retângulo orientado
    {
        const float bd = std::fabs(L.bend) * L.len;
        const Vec2 c[4] = {base - nrm * (half + bd + 2.f), base + nrm * (half + bd + 2.f),
                           base + dir * (L.len + 2.f) - nrm * (half + bd + 2.f), base + dir * (L.len + 2.f) + nrm * (half + bd + 2.f)};
        float x0 = 1e9f, y0 = 1e9f, x1 = -1e9f, y1 = -1e9f;
        for (const Vec2& p : c) { x0 = std::min(x0, p.x); y0 = std::min(y0, p.y); x1 = std::max(x1, p.x); y1 = std::max(y1, p.y); }
        r = clipRect(s, x0 - 2.f, y0 - 2.f, x1 + 2.f, y1 + 2.f);
    }
    if (r.empty()) return;
    Patch pt(r.x0, r.y0, r.w(), r.h());

    const float invLen = 1.f / std::max(1.f, L.len);
    const float ribW = std::max(0.55f, L.wid * 0.028f);
    const float veinGap = L.len / std::max(2.f, L.veins);
    const float lightUp = 0.5f;   // luz vinda de cima
    const float eps = 0.01f;
    for (int y = r.y0; y < r.y1; ++y) {
        for (int x = r.x0; x < r.x1; ++x) {
            const Vec2 p{x + 0.5f, y + 0.5f};
            const Vec2 q = p - base;
            const float a = dot(q, dir);                       // ao longo
            float b = dot(q, nrm);                             // através
            const float u = a * invLen;
            b -= L.bend * L.len * u * u;                       // endireita a curvatura
            if (u < -0.02f || u > 1.02f) continue;
            const float w = profile(L, u);
            const float wh = w * half;
            // cobertura analítica: distância à margem convertida em píxeis (gradiente do perfil)
            const float dw = (profile(L, u + eps) - profile(L, u - eps)) / (2.f * eps) * half * invLen;
            const float grad = std::sqrt(1.f + dw * dw);
            float d = (wh - std::fabs(b)) / grad;
            // reentrância da base (folha em coração)
            if (L.notch > 0.f) d = std::min(d, (a - L.notch * half * std::fabs(b) / std::max(1.f, half)) + 0.5f + 0.f);
            const float dEnds = std::min(a, L.len - a);
            float cov = clampf(0.5f + std::min(d, dEnds));
            if (cov <= 0.f) continue;

            const float v = wh > 1e-3f ? clampf(b / wh, -1.f, 1.f) : 0.f;   // -1..1 através da folha
            const float av = std::fabs(v);

            // base: mais escura junto à base e à nervura, mais clara para a ponta e a margem
            float t = clampf(0.25f + 0.55f * u + 0.18f * av);
            Color col = mix(L.dark, L.base, smooth01(t * 1.4f));
            col = mix(col, L.light, smooth01((t - 0.55f) * 2.f) * 0.55f);

            // dobra ao longo da nervura: a metade virada para a luz é mais clara
            const float facing = (-nrm.y) * (v >= 0 ? 1.f : -1.f);    // >0 se esse lado olha para cima
            col = facing > 0 ? lightenC(col, L.fold * 0.20f * facing * lightUp * 2.f * smooth01(av * 2.f))
                             : shadeC(col, 1.f + L.fold * 0.30f * facing * smooth01(av * 2.f));

            // nervuras laterais: linhas que saem da nervura para a ponta
            const float qv = (a - (std::fabs(b) * 0.85f + b * b * 0.9f / std::max(6.f, half))) / veinGap;   // curvam para a ponta
            const float dq = std::fabs(qv - std::round(qv)) * veinGap * 0.72f;   // distância aproximada em px
            const float veinW = std::max(0.45f, L.wid * 0.016f);
            const float vein = (1.f - smooth01((dq - veinW * 0.5f) / (veinW * 1.2f + 0.3f))) * smooth01((u - 0.07f) * 10.f) *
                               (1.f - smooth01((av - 0.62f) * 3.f)) * (1.f - smooth01((u - 0.9f) * 10.f));
            // entre nervuras, a lâmina fica ligeiramente "inchada"
            const float puff = 0.5f + 0.5f * std::cos(kTau * qv);
            col = shadeC(col, 1.f - 0.07f * puff * (1.f - av));
            col = mix(col, lightenC(L.light, 0.12f), vein * 0.38f);

            // nervura central: clara, mais fina para a ponta
            const float ribHere = ribW * (1.15f - 0.85f * u);
            const float rib = std::exp(-(b * b) / (ribHere * ribHere));
            col = mix(col, lightenC(L.light, 0.28f), rib * smooth01(u * 12.f) * (1.f - smooth01((u - 0.96f) * 25.f)) * 0.85f);

            // mosqueado e margem
            const float n = fbm(p.x * 0.11f, p.y * 0.11f, 3, 2.f, 0.5f, L.seed);
            col = shadeC(col, 0.93f + 0.14f * n);
            col = mix(col, lightenC(L.light, 0.1f), smooth01((av - 0.9f) * 8.f) * 0.25f);
            col.a = 1.f;
            putPx(pt, x, y, col, cov * opacity);
        }
    }
    s.compositePatch(pt, Blend::Normal, 1.f);
}

// Cor da folha derivada da cor escolhida, com variação natural de folha para folha.
void leafColors(Color base, Rng& rng, float varHue, float varLight, Color& baseOut, Color& lightOut, Color& darkOut) {
    Color c = shiftHSV(base, rng.sym() * varHue, rng.sym() * 0.08f, rng.sym() * varLight);
    baseOut = c;
    lightOut = shiftHSV(lightenC(c, 0.28f), 0.025f, -0.05f, 0.04f);   // para amarelo-claro
    darkOut = shiftHSV(shadeC(c, 0.52f), -0.015f, 0.04f, 0.f);
}

// Cinco silhuetas de folha.
LeafSpec leafKind(int kind, float len, Rng& rng) {
    LeafSpec L;
    L.len = len;
    switch (kind) {
        default:
        case 0:  // oval com ponta (carvalho/louro)
            L.wid = len * rng.range(0.42f, 0.52f); L.widest = rng.range(0.34f, 0.46f); L.pointy = 1.25f; L.baseRound = 0.75f; L.veins = 8; break;
        case 1:  // lanceolada (salgueiro/oliveira)
            L.wid = len * rng.range(0.20f, 0.27f); L.widest = rng.range(0.38f, 0.5f); L.pointy = 1.05f; L.baseRound = 0.9f; L.veins = 9; break;
        case 2:  // arredondada (choupo)
            L.wid = len * rng.range(0.7f, 0.85f); L.widest = rng.range(0.3f, 0.4f); L.pointy = 0.7f; L.baseRound = 0.55f; L.veins = 6; L.notch = 0.1f; break;
        case 3:  // serrilhada (olmo/bétula)
            L.wid = len * rng.range(0.46f, 0.56f); L.widest = rng.range(0.3f, 0.42f); L.pointy = 1.4f; L.baseRound = 0.7f; L.veins = 9; L.tooth = 0.1f; L.teeth = 15; break;
        case 4:  // ovada larga (hera/figueira)
            L.wid = len * rng.range(0.6f, 0.72f); L.widest = rng.range(0.28f, 0.36f); L.pointy = 1.7f; L.baseRound = 0.6f; L.veins = 7; L.notch = 0.06f; break;
    }
    L.bend = rng.sym() * 0.16f;
    L.fold = rng.range(0.45f, 0.8f);
    return L;
}

// ---------------------------------------------------------------------------------------------
// Pincéis de folha: ramo com folhas e folhas soltas
// ---------------------------------------------------------------------------------------------
class LeafBrush : public Brush {
public:
    LeafBrush(BrushInfo info, bool twig) : twig_(twig) { info_ = std::move(info); }

    void begin(ISurface& s, const StrokeParams& sp, const InputSample& in) override {
        sp_ = sp;
        rng_.reseed(sp.seed);
        path_.reset();
        path_.add(in, 0.5f);
        idx_ = 0;
        next_ = 0.f;
        lastTwig_ = path_.back().p;
        emitLeaf(s, path_.back());
        next_ = step();
    }

    void move(ISurface& s, const InputSample& in) override {
        const PathPoint& pp = path_.add(in, 0.5f);
        if (twig_) {
            const float w = std::max(0.6f, sp_.size * 0.16f * (0.55f + 0.45f * pp.pressure));
            capsule(s, lastTwig_, pp.p, w, w, twigColor(), 0.18f);
            lastTwig_ = pp.p;
        }
        while (next_ <= path_.length()) {
            emitLeaf(s, path_.at(next_));
            next_ += step();
        }
    }

    void end(ISurface& s) override {
        if (path_.size() < 2) return;
        // folha terminal na ponta do ramo, no sentido do traço
        const PathPoint& e = path_.back();
        if (twig_) leafAt(s, e, e.dir.angle(), 1.f, 0);
    }

private:
    float step() const { return std::max(2.f, sp_.size * (twig_ ? 1.15f : 0.85f)); }
    Color twigColor() const { return mix(Color{0.36f, 0.25f, 0.15f, 1}, shadeC(sp_.color, 0.45f), 0.25f); }

    void emitLeaf(ISurface& s, const PathPoint& pp) {
        const int side = (idx_++ & 1) ? 1 : -1;   // alternadas, como numa haste
        float ang;
        if (twig_) {
            // abre-se da haste para a frente: mais fechada se a mão vai depressa (a folha acompanha o gesto)
            const float speedN = clampf(pp.speed / 900.f);
            const float open = lerpf(0.95f, 0.55f, speedN) + rng_.sym() * 0.22f;
            ang = pp.dir.angle() + side * open;
        } else {
            ang = pp.dir.angle() + rng_.sym() * kPi;   // soltas: qualquer direção
        }
        leafAt(s, pp, ang, twig_ ? 1.f : rng_.range(0.7f, 1.15f), side);
    }

    void leafAt(ISurface& s, const PathPoint& pp, float ang, float scale, int side) {
        const float press = 0.55f + 0.45f * pp.pressure;
        const float len = sp_.size * rng_.range(1.9f, 2.7f) * scale * press;
        LeafSpec L = leafKind(static_cast<int>(rng_.uniform() * 5.f) % 5, len, rng_);
        L.angle = ang;
        L.bend *= (side == 0 ? 1.f : static_cast<float>(side));
        L.seed = rng_.next();
        leafColors(sp_.color, rng_, 0.035f, 0.14f, L.base, L.light, L.dark);
        Vec2 base = pp.p;
        if (!twig_) base += rng_.inDisc() * (sp_.size * 0.9f);
        // pecíolo
        const float pet = std::max(1.f, len * 0.14f);
        const Vec2 tipPet = base + fromAngle(ang) * pet;
        capsule(s, base, tipPet, std::max(0.5f, len * 0.012f), std::max(0.4f, len * 0.008f), shadeC(L.base, 0.7f), 0.15f);
        L.len = len - pet;
        L.wid *= (len - pet) / std::max(1.f, len);
        drawLeaf(s, tipPet, L, 1.f);
    }

    bool twig_;
    StrokeParams sp_;
    Rng rng_;
    PathTracker path_;
    int idx_ = 0;
    float next_ = 0;
    Vec2 lastTwig_{};
};

// ---------------------------------------------------------------------------------------------
// Grama
// ---------------------------------------------------------------------------------------------
struct BladeSpec {
    Vec2 base;
    float height, width;
    float lean;      // rad: inclinação da base
    float curl;      // fração da altura em que a ponta tomba
    Color body, tip, root;
    float depth;     // 0 = atrás, 1 = à frente
};

void drawBlade(ISurface& s, const BladeSpec& B) {
    const float H = B.height;
    const Vec2 tipDir{std::sin(B.lean), -std::cos(B.lean)};
    const Vec2 tip = B.base + tipDir * H + Vec2{B.curl * H * 0.45f, H * 0.03f};
    const Vec2 ctrl = B.base + Vec2{std::sin(B.lean * 0.35f) * H * 0.55f, -H * 0.62f};
    constexpr int N = 22;
    Vec2 left[N + 1], right[N + 1], mid[N + 1];
    float ts[N + 1];
    for (int i = 0; i <= N; ++i) {
        const float t = static_cast<float>(i) / N, u = 1.f - t;
        const Vec2 c = B.base * (u * u) + ctrl * (2.f * u * t) + tip * (t * t);
        const Vec2 d = (ctrl - B.base) * (2.f * u) + (tip - ctrl) * (2.f * t);
        const Vec2 n = d.norm().perp();
        const float w = B.width * 0.5f * std::pow(std::max(0.f, 1.f - t), 0.82f) * (1.f + 0.22f * std::sin(kPi * std::min(1.f, t * 1.6f)));
        left[i] = c - n * w;
        right[i] = c + n * w;
        mid[i] = c;
        ts[i] = t;
    }
    float x0 = 1e9f, y0 = 1e9f, x1 = -1e9f, y1 = -1e9f;
    for (int i = 0; i <= N; ++i) {
        for (const Vec2& p : {left[i], right[i]}) { x0 = std::min(x0, p.x); y0 = std::min(y0, p.y); x1 = std::max(x1, p.x); y1 = std::max(y1, p.y); }
    }
    IRect r = clipRect(s, x0 - 1.f, y0 - 1.f, x1 + 1.f, y1 + 1.f);
    if (r.empty()) return;
    Patch pt(r.x0, r.y0, r.w(), r.h());

    // contorno fechado: esquerda para cima, direita para baixo
    std::vector<Vec2> poly;
    poly.reserve(2 * (N + 1));
    for (int i = 0; i <= N; ++i) poly.push_back(left[i]);
    for (int i = N; i >= 0; --i) poly.push_back(right[i]);

    constexpr int SS = 4;
    std::vector<float> xs;
    for (int y = r.y0; y < r.y1; ++y) {
        // espaços por sub-linha
        std::vector<float> cov(static_cast<size_t>(r.w()), 0.f);
        float uSum = 0.f, uCnt = 0.f, tSum = 0.f;
        for (int k = 0; k < SS; ++k) {
            const float sy = y + (k + 0.5f) / SS;
            xs.clear();
            for (size_t i = 0; i < poly.size(); ++i) {
                const Vec2 a = poly[i], b = poly[(i + 1) % poly.size()];
                if ((a.y <= sy && b.y > sy) || (b.y <= sy && a.y > sy)) xs.push_back(a.x + (sy - a.y) / (b.y - a.y) * (b.x - a.x));
            }
            std::sort(xs.begin(), xs.end());
            for (size_t i = 0; i + 1 < xs.size(); i += 2) {
                const float xa = std::max(xs[i], static_cast<float>(r.x0)), xb = std::min(xs[i + 1], static_cast<float>(r.x1));
                if (xb <= xa) continue;
                for (int px = static_cast<int>(std::floor(xa)); px <= static_cast<int>(std::floor(xb)) && px < r.x1; ++px) {
                    const float ov = std::min(xb, px + 1.f) - std::max(xa, static_cast<float>(px));
                    if (ov > 0) cov[static_cast<size_t>(px - r.x0)] += ov / SS;
                }
                uSum += xs[i]; uCnt += 1.f; tSum += xs[i + 1];
            }
        }
        for (int x = r.x0; x < r.x1; ++x) {
            const float cv = clampf(cov[static_cast<size_t>(x - r.x0)]);
            if (cv <= 0.002f) continue;
            // posição ao longo da lâmina: pela altura entre a base e a ponta
            const float t = clampf((B.base.y - (y + 0.5f)) / std::max(1.f, B.base.y - std::min(tip.y, B.base.y - 1.f)));
            // posição através da lâmina (0 esquerda .. 1 direita) pela largura da linha
            const float xa = uCnt > 0 ? uSum / uCnt : x, xb = uCnt > 0 ? tSum / uCnt : x + 1.f;
            const float across = xb - xa > 0.5f ? clampf((x + 0.5f - xa) / (xb - xa)) : 0.5f;
            Color col = mix(B.root, B.body, smooth01(t * 2.2f));
            col = mix(col, B.tip, smooth01((t - 0.45f) * 1.8f));
            // vinco central claro; lado esquerdo apanha luz, o direito fica na sombra
            const float crease = std::exp(-std::pow((across - 0.5f) * 5.5f, 2.f));
            col = lightenC(col, 0.16f * crease * (1.f - t * 0.5f));
            col = across < 0.5f ? lightenC(col, 0.10f * (0.5f - across) * 2.f) : shadeC(col, 1.f - 0.20f * (across - 0.5f) * 2.f);
            col = shadeC(col, 0.55f + 0.45f * B.depth);
            col.a = 1.f;
            putPx(pt, x, y, col, cv);
        }
    }
    s.compositePatch(pt, Blend::Normal, 1.f);
}

class GrassBrush : public Brush {
public:
    explicit GrassBrush(BrushInfo info) { info_ = std::move(info); }

    void begin(ISurface& s, const StrokeParams& sp, const InputSample& in) override {
        sp_ = sp;
        rng_.reseed(sp.seed);
        path_.reset();
        path_.add(in, 0.5f);
        next_ = 0.f;
        tuft(s, path_.back(), 0.4f);
        next_ = step();
    }

    void move(ISurface& s, const InputSample& in) override {
        path_.add(in, 0.5f);
        while (next_ <= path_.length()) {
            const PathPoint pp = path_.at(next_);
            // entrada suave nos primeiros pixels do traço
            tuft(s, pp, 0.4f + 0.6f * smooth01(next_ / std::max(4.f, sp_.size * 3.f)));
            next_ += step();
        }
    }

    void end(ISurface&) override {}

private:
    float step() const { return std::max(1.5f, sp_.size * 0.46f); }

    void tuft(ISurface& s, const PathPoint& pp, float grow) {
        const int n = 3 + static_cast<int>(rng_.uniform() * 4.f);
        BladeSpec bl[8];
        const Color body = sp_.color;
        const float speedN = clampf(pp.speed / 900.f);
        // o gesto "puxa" as lâminas para o lado em que a mão vai
        const float drag = clampf(pp.dir.x) * speedN * 0.3f;
        for (int i = 0; i < n; ++i) {
            BladeSpec& b = bl[i];
            b.depth = rng_.uniform();
            const float d = 0.65f + 0.35f * b.depth;
            b.base = pp.p + Vec2{rng_.sym() * sp_.size * 0.7f, rng_.sym() * sp_.size * 0.12f};
            b.height = sp_.size * rng_.range(2.3f, 6.2f) * (0.55f + 0.45f * pp.pressure) * grow * d;
            b.width = sp_.size * rng_.range(0.16f, 0.28f) * (0.7f + 0.3f * d);
            b.lean = rng_.sym() * 0.38f + sp_.wind * 0.35f + drag;
            b.curl = b.lean * 0.9f + rng_.sym() * 0.5f;
            Color c = shiftHSV(body, rng_.sym() * 0.03f, rng_.sym() * 0.07f, rng_.sym() * 0.12f);
            b.body = c;
            b.tip = shiftHSV(lightenC(c, 0.3f), 0.03f, -0.1f, 0.05f);
            b.root = shiftHSV(shadeC(c, 0.4f), -0.02f, 0.05f, 0.f);
        }
        // de trás para a frente
        std::sort(bl, bl + n, [](const BladeSpec& a, const BladeSpec& b) { return a.depth < b.depth; });
        for (int i = 0; i < n; ++i) drawBlade(s, bl[i]);
    }

    StrokeParams sp_;
    Rng rng_;
    PathTracker path_;
    float next_ = 0;
};

BrushInfo makeInfo(const char* id, const char* name, const char* desc, float size, Color c1, const char* demo) {
    BrushInfo b;
    b.id = id;
    b.name = name;
    b.category = "plantas";
    b.description = desc;
    b.tags = {"natureza", "realista"};
    b.defaultSize = size;
    b.minSize = std::max(2.f, size * 0.3f);
    b.maxSize = size * 4.f;
    b.suggestedColor = c1;
    b.suggestedColor2 = Color{0.55f, 0.75f, 0.25f, 1};
    b.demo = demo;
    b.needsPressure = true;
    return b;
}
}  // namespace

void registerRealBrushes() {
    static bool done = false;
    if (done) return;
    done = true;
    BrushLibrary& lib = BrushLibrary::instance();
    {
        BrushInfo i = makeInfo("real.ramo-folhas", "Ramo com folhas (realista)",
                               "Um ramo com folhas alternadas, cada uma com a sua forma (oval, lanceolada, redonda, serrilhada, larga), nervura central e "
                               "nervuras laterais, dobra e cor própria. A pressão comanda o tamanho; a mão mais rápida fecha o ângulo das folhas.",
                               10.f, Color{0.22f, 0.5f, 0.16f, 1}, "wave");
        lib.add(i, [i]() -> std::unique_ptr<Brush> { return std::make_unique<LeafBrush>(i, true); });
    }
    {
        BrushInfo i = makeInfo("real.folhas-soltas", "Folhas soltas (realistas)",
                               "Folhas isoladas espalhadas ao longo do traço, em todas as direções, cada uma desenhada com nervuras, margem e sombreado.",
                               10.f, Color{0.3f, 0.55f, 0.15f, 1}, "wave");
        lib.add(i, [i]() -> std::unique_ptr<Brush> { return std::make_unique<LeafBrush>(i, false); });
    }
    {
        BrushInfo i = makeInfo("real.grama", "Grama (realista)",
                               "Tufos de lâminas curvas que afinam até à ponta, com base escura, ponta clara e vinco ao centro; crescem para cima a partir do "
                               "traço. A pressão comanda a altura e a velocidade da mão inclina as lâminas.",
                               8.f, Color{0.25f, 0.55f, 0.12f, 1}, "wave");
        lib.add(i, [i]() -> std::unique_ptr<Brush> { return std::make_unique<GrassBrush>(i); });
    }
}

}  // namespace abridge

#else
namespace abridge {
void registerRealBrushes() {}
}  // namespace abridge
#endif
