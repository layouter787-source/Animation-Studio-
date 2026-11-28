// Traço de tinta em C++: contorno natural de uma linha desenhada com o dedo ou a caneta, com pontas que dependem da
// VELOCIDADE, e a corda (smoothing por corda) que alisa a mão.
//
// Como as pontas nascem (estudado em motores de pintura, escrito de novo):
//  - O comprimento da ponta não é fixo: é o caminho que o traço andou nos últimos [taperMs] milissegundos antes de levantar
//    (e nos primeiros ao começar). Um traço rápido percorre muito chão nesse tempo, por isso acaba numa ponta longa e fina,
//    quase um fio; um traço lento percorre pouco, por isso acaba numa ponta curta e mais grossa. É exatamente o que acontece
//    na tinta de verdade: a velocidade decide a ponta.
//  - A espessura desce até um mínimo (0,3 px na caneta de tinta) com uma curva suave que sai do corpo sem cantos.
//  - Opcionalmente a espessura também segue a pressão e a velocidade ao longo do traço (0 = desligado, como na caneta
//    de tinta dura).
//
// Contorno: a linha central é uma curva suave (Catmull-Rom centrípeta) reamostrada por comprimento de arco, com um alisamento
// leve. O contorno sai como quadriláteros por segmento (todos com o mesmo sentido, por isso o preenchimento "não-zero" faz a
// união sem buracos nem camadas duplas) e círculos nas curvas fechadas e nas pontas (juntas redondas).
// Só C++ padrão; sem dependências.
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace studio {
namespace ink {

struct Sample {
    float x = 0, y = 0, p = 1, t = 0;  // t: milissegundos desde o início do traço
};

struct Params {
    float width = 4.f;             // espessura máxima (diâmetro), em unidades do projeto
    float minWidthFraction = 0.01f;  // espessura mínima como fração da máxima (0,3 px / 30 px)
    float minRadius = 0.12f;       // raio mínimo absoluto da ponta (px): nunca desaparece
    float pressureWidth = 0.f;     // 0..1: quanto a pressão manda na espessura
    float speedWidth = 0.f;        // 0..1: quanto a velocidade afina o traço (rápido = mais fino)
    float startTaper = 1.f;        // 0..1: tamanho da ponta do início (0 = sem ponta)
    float endTaper = 1.f;          // 0..1: tamanho da ponta do fim (0 = sem ponta)
    float taperSize = 1.f;         // 0..1: quanto as pontas afinam (1 = até ao mínimo)
    float taperMs = 100.f;         // tempo que a ponta mede quando o parâmetro de ponta vale 1
    float smooth = 0.5f;           // 0..1: alisamento da linha central (0,5 = filtro gaussiano de ~2*(1,2+0,3*largura) px de largura)
    float scale = 1.f;             // píxeis de ecrã por unidade do projeto (só para o passo da amostragem)
};

namespace detail {

constexpr float kPi = 3.14159265358979323846f;

struct V {
    float x, y;
};

inline float len(V a, V b) { return std::hypot(b.x - a.x, b.y - a.y); }

/** Ponto de t (0 em p1, 1 em p2) da Catmull-Rom centrípeta por p0..p3 (Barry-Goldman). */
inline V catmull(V p0, V p1, V p2, V p3, float t) {
    auto knot = [](V a, V b) { return std::max(1e-3f, std::sqrt(len(a, b))); };
    const float t0 = 0.f, t1 = t0 + knot(p0, p1), t2 = t1 + knot(p1, p2), t3 = t2 + knot(p2, p3);
    const float u = t1 + (t2 - t1) * t;
    auto mix = [](V a, V b, float from, float to, float at) {
        const float w = (at - from) / (to - from);
        return V{a.x + (b.x - a.x) * w, a.y + (b.y - a.y) * w};
    };
    const V a1 = mix(p0, p1, t0, t1, u), a2 = mix(p1, p2, t1, t2, u), a3 = mix(p2, p3, t2, t3, u);
    const V b1 = mix(a1, a2, t0, t2, u), b2 = mix(a2, a3, t1, t3, u);
    return mix(b1, b2, t1, t2, u);
}

inline float smoothstep(float x) {
    x = std::min(1.f, std::max(0.f, x));
    return x * x * (3.f - 2.f * x);
}

/**
 * Perfil da ponta: 0 na extremidade, 1 no corpo. Seno de quarto de onda: sai da ponta como um cone fino e entra no corpo com
 * inclinação zero (sem canto), como a ponta de um pincel de tinta; o smoothstep antigo deixava a ponta um fio muito comprido.
 */
inline float tipProfile(float u) {
    u = std::min(1.f, std::max(0.f, u));
    return std::sin(u * kPi * 0.5f);
}

/** Valor de uma função tabelada (xs crescente) em [at], por interpolação linear. */
inline float lerpAt(const std::vector<float>& xs, const std::vector<float>& ys, float at) {
    if (xs.empty()) return 0.f;
    if (at <= xs.front()) return ys.front();
    if (at >= xs.back()) return ys.back();
    const size_t hi = static_cast<size_t>(std::upper_bound(xs.begin(), xs.end(), at) - xs.begin());
    const size_t lo = hi - 1;
    const float span = xs[hi] - xs[lo];
    const float u = span > 0.f ? (at - xs[lo]) / span : 0.f;
    return ys[lo] + (ys[hi] - ys[lo]) * u;
}

/** Acrescenta um círculo (mesmo sentido dos quadriláteros) à lista de polígonos. */
inline void circle(std::vector<float>& out, float cx, float cy, float r) {
    const int n = std::max(8, std::min(40, static_cast<int>(std::ceil(std::sqrt(std::max(r, 0.05f)) * 7.f))));
    out.push_back(static_cast<float>(n));
    for (int i = 0; i < n; ++i) {
        const float a = -2.f * kPi * static_cast<float>(i) / static_cast<float>(n);
        out.push_back(cx + r * std::cos(a));
        out.push_back(cy + r * std::sin(a));
    }
}

}  // namespace detail

/**
 * Polígonos do traço: lista de [n, x0, y0, ..., x(n-1), y(n-1)] seguidos. Preencher com a regra "não-zero".
 * Vazio se não há amostras.
 */
inline std::vector<float> outline(const std::vector<Sample>& input, const Params& P) {
    using namespace detail;
    std::vector<float> out;
    if (input.empty()) return out;
    const float R = std::max(0.05f, P.width * 0.5f);

    // 1. Limpa repetidos e garante tempo estritamente crescente.
    std::vector<Sample> s;
    s.reserve(input.size());
    for (const Sample& a : input) {
        if (!s.empty() && len({s.back().x, s.back().y}, {a.x, a.y}) < 1e-4f) continue;
        Sample b = a;
        if (!s.empty()) b.t = std::max(b.t, s.back().t + 0.001f);
        s.push_back(b);
    }
    if (s.size() == 1) {
        circle(out, s[0].x, s[0].y, R);
        return out;
    }
    // Sem tempos (traços antigos): 8 ms por amostra.
    bool hasTime = false;
    for (size_t i = 1; i < s.size(); ++i) if (s[i].t - s[i - 1].t > 0.01f) { hasTime = true; break; }
    if (!hasTime) for (size_t i = 0; i < s.size(); ++i) s[i].t = 8.f * static_cast<float>(i);

    const size_t m = s.size();
    // Comprimento acumulado e tempo ao longo do traço bruto.
    std::vector<float> rawS(m, 0.f), rawT(m), rawP(m);
    for (size_t i = 0; i < m; ++i) {
        if (i) rawS[i] = rawS[i - 1] + len({s[i - 1].x, s[i - 1].y}, {s[i].x, s[i].y});
        rawT[i] = s[i].t;
        rawP[i] = s[i].p;
    }
    const float rawTotal = rawS.back();

    // 2. Linha central: curva suave reamostrada. Passo ~ um quarto da espessura em ecrã, entre 0,35 e 2 px.
    const float scale = std::max(1e-3f, P.scale);
    float step = std::min(2.f, std::max(0.35f, P.width * scale * 0.25f)) / scale;
    const float maxSeg = 6000.f;
    step = std::max(step, rawTotal / maxSeg);
    std::vector<V> c;      // posições
    std::vector<float> cs; // arco bruto correspondente (para tempo e pressão)
    c.push_back({s[0].x, s[0].y});
    cs.push_back(0.f);
    for (size_t i = 0; i + 1 < m; ++i) {
        const V p1{s[i].x, s[i].y}, p2{s[i + 1].x, s[i + 1].y};
        const V p0 = i > 0 ? V{s[i - 1].x, s[i - 1].y} : V{2 * p1.x - p2.x, 2 * p1.y - p2.y};
        const V p3 = i + 2 < m ? V{s[i + 2].x, s[i + 2].y} : V{2 * p2.x - p1.x, 2 * p2.y - p1.y};
        const float seg = len(p1, p2);
        const int n = std::max(1, static_cast<int>(std::ceil(seg / step)));
        for (int k = 1; k <= n; ++k) {
            const float u = static_cast<float>(k) / static_cast<float>(n);
            c.push_back(k == n ? p2 : catmull(p0, p1, p2, p3, u));
            cs.push_back(rawS[i] + (rawS[i + 1] - rawS[i]) * u);
        }
    }
    const size_t N = c.size();

    // 3. Alisamento gaussiano por comprimento de arco: tira o tremor da mão (o ruído do ecrã tátil) e deixa a linha com
    //    curvatura contínua. A largura do filtro cresce com a espessura (um traço grosso perdoa mais) e as pontas ficam
    //    fixas (reflexão ímpar), por isso a curva não encolhe nos extremos.
    {
        float arcRaw = 0.f;
        for (size_t i = 1; i < N; ++i) arcRaw += len(c[i - 1], c[i]);
        const float ds = N > 1 ? arcRaw / static_cast<float>(N - 1) : 1.f;
        float sigma = std::min(1.f, std::max(0.f, P.smooth)) * 2.f * (1.2f + 0.3f * P.width);
        sigma = std::min(sigma, std::min(10.f, arcRaw / 8.f));
        const int rad = static_cast<int>(std::ceil(3.f * sigma / std::max(ds, 1e-3f)));
        if (sigma > 0.05f && rad >= 1 && N > 2) {
            std::vector<float> w(static_cast<size_t>(rad) + 1);
            float sum = 0.f;
            for (int k = 0; k <= rad; ++k) {
                const float d = static_cast<float>(k) * ds / sigma;
                w[static_cast<size_t>(k)] = std::exp(-0.5f * d * d);
                sum += (k == 0 ? 1.f : 2.f) * w[static_cast<size_t>(k)];
            }
            auto at = [&](long j) -> V {
                const long last = static_cast<long>(N) - 1;
                if (j < 0) { const long m = std::min(-j, last); return {2 * c[0].x - c[static_cast<size_t>(m)].x, 2 * c[0].y - c[static_cast<size_t>(m)].y}; }
                if (j > last) { const long m = std::max(0L, 2 * last - j); return {2 * c[static_cast<size_t>(last)].x - c[static_cast<size_t>(m)].x, 2 * c[static_cast<size_t>(last)].y - c[static_cast<size_t>(m)].y}; }
                return c[static_cast<size_t>(j)];
            };
            std::vector<V> d = c;
            for (size_t i = 1; i + 1 < N; ++i) {
                float ax = 0.f, ay = 0.f;
                for (int k = -rad; k <= rad; ++k) {
                    const V q = at(static_cast<long>(i) + k);
                    const float wk = w[static_cast<size_t>(std::abs(k))];
                    ax += q.x * wk;
                    ay += q.y * wk;
                }
                d[i] = {ax / sum, ay / sum};
            }
            c.swap(d);
        }
    }

    // 4. Arco real, tempo e pressão em cada ponto.
    std::vector<float> arc(N, 0.f), time(N), press(N);
    for (size_t i = 0; i < N; ++i) {
        if (i) arc[i] = arc[i - 1] + len(c[i - 1], c[i]);
        time[i] = lerpAt(rawS, rawT, cs[i]);
        press[i] = lerpAt(rawS, rawP, cs[i]);
    }
    const float total = arc.back();

    // 5. Velocidade (px/ms) numa janela de ~40 ms.
    std::vector<float> speed(N, 0.f);
    {
        size_t lo = 0, hi = 0;
        for (size_t i = 0; i < N; ++i) {
            while (lo < i && time[i] - time[lo] > 20.f) ++lo;
            if (hi < i) hi = i;
            while (hi + 1 < N && time[hi + 1] - time[i] <= 20.f) ++hi;
            const float dt = time[hi] - time[lo];
            speed[i] = dt > 1e-3f ? (arc[hi] - arc[lo]) / dt : 0.f;
        }
    }

    // 6. Comprimento das pontas = caminho andado nos primeiros/últimos [taperMs] ms.
    auto arcAtTime = [&](float at) { return lerpAt(time, arc, at); };
    const float t0 = time.front(), t1 = time.back();
    const float minLen = std::max(R * 0.9f, 1.f);
    float startLen = 0.f, endLen = 0.f;
    if (P.startTaper > 0.f) startLen = std::max(minLen, arcAtTime(t0 + P.taperMs * P.startTaper) - 0.f);
    if (P.endTaper > 0.f) endLen = std::max(minLen, total - arcAtTime(t1 - P.taperMs * P.endTaper));
    // Uma ponta nunca é mais comprida do que ~8 espessuras (um traço muito rápido dava um "fio" que ocupava o traço todo,
    // com ar de folha) nem do que 40% do traço.
    const float lenCap = std::max(8.f * P.width, 24.f);
    const float cap = std::min(lenCap, total * 0.4f);
    startLen = std::min(startLen, cap);
    endLen = std::min(endLen, cap);

    // 7. Raio em cada ponto.
    const float minR = std::max(P.minRadius, R * P.minWidthFraction);
    const float size = std::min(1.f, std::max(0.f, P.taperSize));
    std::vector<float> r(N);
    for (size_t i = 0; i < N; ++i) {
        float f = 1.f;
        if (P.pressureWidth > 0.f) {
            const float pr = std::min(1.f, std::max(0.f, press[i]));
            f *= 1.f - P.pressureWidth * (1.f - (minR / R + (1.f - minR / R) * pr));
        }
        if (P.speedWidth > 0.f) {
            // 0 até ~0,4 px/ms (lento), 1 a partir de ~2 px/ms (rápido); raiz quadrada, como o desenho a tinta.
            const float v = std::min(1.f, std::max(0.f, (speed[i] - 0.4f) / 1.6f));
            f *= 1.f - P.speedWidth * std::sqrt(v) * (1.f - minR / R);
        }
        float tipK = 1.f;
        if (startLen > 0.f && arc[i] < startLen) tipK = std::min(tipK, 1.f - size * (1.f - tipProfile(arc[i] / startLen)));
        const float dEnd = total - arc[i];
        if (endLen > 0.f && dEnd < endLen) tipK = std::min(tipK, 1.f - size * (1.f - tipProfile(dEnd / endLen)));
        r[i] = std::max(minR, R * f * tipK);
    }
    // Um alisamento do raio tira degraus (pressão e velocidade).
    if (N > 2) {
        std::vector<float> q = r;
        for (size_t i = 1; i + 1 < N; ++i) q[i] = std::max(minR, (r[i - 1] + 2 * r[i] + r[i + 1]) * 0.25f);
        // As pontas ficam como foram calculadas.
        q[0] = r[0];
        q[N - 1] = r[N - 1];
        r.swap(q);
    }

    // 8. Tangentes e normais.
    std::vector<V> nrm(N);
    for (size_t i = 0; i < N; ++i) {
        const V a = c[i > 0 ? i - 1 : 0], b = c[i + 1 < N ? i + 1 : N - 1];
        float tx = b.x - a.x, ty = b.y - a.y;
        const float l = std::hypot(tx, ty);
        if (l > 1e-6f) { tx /= l; ty /= l; } else if (i > 0) { tx = -nrm[i - 1].y; ty = nrm[i - 1].x; } else { tx = 1.f; ty = 0.f; }
        nrm[i] = {-ty, tx};
    }

    // 9. Quadriláteros por segmento + juntas redondas onde a curva é fechada + pontas redondas.
    out.reserve(N * 9 + 64);
    for (size_t i = 0; i + 1 < N; ++i) {
        const V L0{c[i].x + nrm[i].x * r[i], c[i].y + nrm[i].y * r[i]};
        const V R0{c[i].x - nrm[i].x * r[i], c[i].y - nrm[i].y * r[i]};
        const V L1{c[i + 1].x + nrm[i + 1].x * r[i + 1], c[i + 1].y + nrm[i + 1].y * r[i + 1]};
        const V R1{c[i + 1].x - nrm[i + 1].x * r[i + 1], c[i + 1].y - nrm[i + 1].y * r[i + 1]};
        out.push_back(4.f);
        out.push_back(L0.x); out.push_back(L0.y);
        out.push_back(L1.x); out.push_back(L1.y);
        out.push_back(R1.x); out.push_back(R1.y);
        out.push_back(R0.x); out.push_back(R0.y);
        if (i + 2 < N) {
            const float dot = nrm[i].x * nrm[i + 1].x + nrm[i].y * nrm[i + 1].y;
            const float turn = std::acos(std::min(1.f, std::max(-1.f, dot)));
            if (r[i + 1] * turn > 0.1f) circle(out, c[i + 1].x, c[i + 1].y, r[i + 1]);
        }
    }
    if (r[0] > 0.1f) circle(out, c[0].x, c[0].y, r[0]);
    if (r[N - 1] > 0.1f) circle(out, c[N - 1].x, c[N - 1].y, r[N - 1]);
    return out;
}

// ---------------------------------------------------------------------------------------------------------------------
// Corda (smoothing por corda): a ponta do pincel é puxada pelo dedo por uma corda de comprimento fixo. Enquanto o dedo
// está dentro do comprimento da corda a ponta não se mexe; quando se afasta, a corda estica e arrasta a ponta pelo
// caminho mais curto. O tremor da mão (menor do que a corda) desaparece. Estado: [x, y, direção x, direção y].
// No fim a ponta junta-se ao dedo por uma CURVA que sai na direção em que a corda andava (em vez de um segmento reto).
// ---------------------------------------------------------------------------------------------------------------------
namespace rope {

constexpr float kMinRope = 0.5f;

inline void start(float* st, float x, float y) {
    st[0] = x; st[1] = y; st[2] = 0.f; st[3] = 0.f;
}

/** Nova posição da ponta para o dedo em (x, y). Devolve a posição em st[0], st[1]. */
inline void next(float* st, float x, float y, float ropePx) {
    if (ropePx < kMinRope) { start(st, x, y); return; }
    const float dx = x - st[0], dy = y - st[1];
    const float d = std::hypot(dx, dy);
    if (d <= ropePx) return;
    const float k = (d - ropePx) / d;
    const float mx = dx * k, my = dy * k;
    const float ml = std::hypot(mx, my);
    st[0] += mx;
    st[1] += my;
    if (ml > 1e-4f) {
        // A direção da corda varia devagar: serve para a curva final.
        const float ux = mx / ml, uy = my / ml;
        const float hx = st[2] * 0.6f + ux * 0.4f, hy = st[3] * 0.6f + uy * 0.4f;
        const float hl = std::hypot(hx, hy);
        if (hl > 1e-4f) { st[2] = hx / hl; st[3] = hy / hl; }
    }
}

/** Pontos que levam a ponta até (x, y) numa curva; o último é exatamente (x, y). */
inline std::vector<float> finish(float* st, float x, float y, float ropePx) {
    std::vector<float> out;
    if (ropePx < kMinRope) { out = {x, y}; start(st, x, y); return out; }
    const float ax = st[0], ay = st[1];
    const float dx = x - ax, dy = y - ay;
    const float d = std::hypot(dx, dy);
    if (d < 0.05f) { out = {x, y}; start(st, x, y); return out; }
    // Quadrática: parte na direção da corda (se se conhece) e chega ao dedo.
    float hx = st[2], hy = st[3];
    const float along = hx * dx + hy * dy;
    if (std::hypot(hx, hy) < 0.5f || along <= 0.f) { hx = dx / d; hy = dy / d; }  // sem direção, ou o dedo recuou: reta
    const float cx = ax + hx * d * 0.5f, cy = ay + hy * d * 0.5f;
    const int steps = std::max(1, std::min(60, static_cast<int>(std::ceil(d / 2.f))));
    for (int i = 1; i < steps; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(steps), u = 1.f - t;
        out.push_back(u * u * ax + 2.f * u * t * cx + t * t * x);
        out.push_back(u * u * ay + 2.f * u * t * cy + t * t * y);
    }
    out.push_back(x);
    out.push_back(y);
    start(st, x, y);
    return out;
}

}  // namespace rope
}  // namespace ink
}  // namespace studio
