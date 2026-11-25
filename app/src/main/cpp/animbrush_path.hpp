// Dinâmica do traço (C++): o que acontece entre a caneta e o pincel.
//
// Estudado o funcionamento de motores de pintura profissionais (suavização do caminho com tempo real,
// suavização da pressão, curva suave entre amostras em vez de linhas retas). Reescrito do zero; nenhum código copiado.
//
//  1. Suavização do caminho ("streamline"): o caminho segue a caneta com um atraso que depende do TEMPO real entre
//     amostras (filtro de um pólo, constante de tempo tau). Duas canetas com taxas de amostragem diferentes sentem o mesmo.
//  2. Suavização da pressão: o mesmo filtro, com a sua própria constante de tempo, para a pressão não dar degraus.
//  3. Curva centrípeta (Catmull-Rom) entre os pontos já suavizados, repartida em cordas curtas: um traço rápido deixa de
//     ser uma poligonal. O tempo de cada amostra reparte-se pelas cordas, por isso a velocidade que o pincel mede mantém-se.
//  4. Ao levantar a caneta o caminho vai até ao último ponto real (nada fica por desenhar).
//
// Só matemática; sem dependências.
#pragma once
#include <algorithm>
#include <cmath>
#include <vector>

namespace abridge {

struct PathSample {
    float x = 0, y = 0, pressure = 1, dt = 0.008f;
};

struct PathConfig {
    float streamline = 0.2f;         // 0..1: quanto atrasa/alisa o caminho (0 = segue a caneta ao pixel)
    float pressureSmoothing = 0.3f;  // 0..1: quanto alisa a pressão
    float chord = 3.f;               // corda máxima da curva, em píxeis da superfície
};

class PathFilter {
public:
    void start(const PathConfig& c, float x, float y, float pressure) {
        cfg_ = c;
        pts_.clear();
        segs_ = 0;
        Pt p{x, y, pressure, 0.f};
        pts_.push_back(p);
        sx_ = x; sy_ = y; sp_ = pressure;
        rx_ = x; ry_ = y; rp_ = pressure;
    }

    /** Uma amostra real da caneta. Acrescenta a [out] as amostras já suavizadas e curvas (pode ficar vazio). */
    void add(float x, float y, float pressure, float dt, std::vector<PathSample>& out) {
        dt = std::min(0.1f, std::max(0.001f, dt));
        rx_ = x; ry_ = y; rp_ = pressure;
        const float ka = follow(dt, tau(cfg_.streamline, 0.09f));
        const float kp = follow(dt, tau(cfg_.pressureSmoothing, 0.12f));
        sx_ += (x - sx_) * ka;
        sy_ += (y - sy_) * ka;
        sp_ += (pressure - sp_) * kp;
        pts_.push_back(Pt{sx_, sy_, sp_, dt});
        drain(out, false);
    }

    /** Fim do traço: o caminho chega ao último ponto real e esvazia o que ficou à espera. */
    void finish(std::vector<PathSample>& out) {
        const Pt& last = pts_.back();
        if (std::hypot(last.x - rx_, last.y - ry_) > 0.05f || std::fabs(last.p - rp_) > 0.01f) {
            pts_.push_back(Pt{rx_, ry_, rp_, 0.012f});
        }
        drain(out, true);
    }

private:
    struct Pt {
        float x, y, p, dt;  // dt: tempo desde o ponto anterior
    };
    struct V {
        float x, y;
    };

    static float tau(float amount, float longest) {
        const float a = std::min(1.f, std::max(0.f, amount));
        return a * a * longest;
    }
    static float follow(float dt, float tau) { return tau <= 1e-5f ? 1.f : 1.f - std::exp(-dt / tau); }

    // Ponto de t (0 em p1, 1 em p2) da curva Catmull-Rom centrípeta por p0..p3 (Barry-Goldman). p0/p3 ausentes: reflexão.
    static V curve(V p0, V p1, V p2, V p3, float t) {
        auto knot = [](V a, V b) { return std::max(1e-3f, std::sqrt(std::hypot(b.x - a.x, b.y - a.y))); };
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

    void drain(std::vector<PathSample>& out, bool all) {
        // O segmento i (pts_[i] -> pts_[i+1]) precisa do ponto i+2 (ou, no fim, usa o reflexo).
        while (static_cast<size_t>(segs_) + 1 < pts_.size()) {
            const size_t i = static_cast<size_t>(segs_);
            const bool hasNext = i + 2 < pts_.size();
            if (!hasNext && !all) break;
            emit(i, out);
            ++segs_;
        }
        // Esquece o que já não serve (ficam 3 pontos para trás).
        if (segs_ > 6) {
            const int drop = segs_ - 3;
            pts_.erase(pts_.begin(), pts_.begin() + drop);
            segs_ -= drop;
        }
    }

    void emit(size_t i, std::vector<PathSample>& out) const {
        const Pt& p1 = pts_[i];
        const Pt& p2 = pts_[i + 1];
        const V a{p1.x, p1.y}, b{p2.x, p2.y};
        const V c0 = i > 0 ? V{pts_[i - 1].x, pts_[i - 1].y} : V{2 * a.x - b.x, 2 * a.y - b.y};
        const V c3 = i + 2 < pts_.size() ? V{pts_[i + 2].x, pts_[i + 2].y} : V{2 * b.x - a.x, 2 * b.y - a.y};
        const float len = std::hypot(b.x - a.x, b.y - a.y);
        const int n = std::max(1, std::min(64, static_cast<int>(std::ceil(len / std::max(0.5f, cfg_.chord)))));
        for (int k = 1; k <= n; ++k) {
            const float t = static_cast<float>(k) / n;
            const V v = k == n ? b : curve(c0, a, b, c3, t);
            out.push_back(PathSample{v.x, v.y, p1.p + (p2.p - p1.p) * t, p2.dt / n});
        }
    }

    PathConfig cfg_;
    std::vector<Pt> pts_;
    int segs_ = 0;
    float sx_ = 0, sy_ = 0, sp_ = 1;  // ponto suavizado
    float rx_ = 0, ry_ = 0, rp_ = 1;  // último ponto real
};

}  // namespace abridge
