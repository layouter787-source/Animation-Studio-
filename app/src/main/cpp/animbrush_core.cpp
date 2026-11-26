#include "animbrush_core.h"

#ifdef ANIMBRUSH_ENABLED
#include <algorithm>
#include <cmath>
#include <memory>
#include <mutex>
#include <vector>

#include "animbrush/animbrush.hpp"
#include "animbrush_path.hpp"

namespace abridge {
namespace {
std::mutex g_mutex;  // os pincéis usam registos globais (biblioteca e texturas): um traço de cada vez

constexpr double kMaxPixels = 4500000.0;  // limite de memória da superfície (float RGBA = 16 bytes por píxel)
constexpr float kDt = 0.008f;             // o traço guardado não tem tempos: 8 ms por amostra

/** Superfície que regista a área tocada por cada pincelada (caixa exata, em píxeis da superfície). */
class TrackingSurface : public ab::RasterSurface {
public:
    TrackingSurface(int w, int h) : ab::RasterSurface(w, h) {}

    void drawDab(const ab::Dab& d) override {
        ab::RasterSurface::drawDab(d);
        const float r = std::max(0.05f, d.radius) * std::max(1.f, std::max(0.05f, d.aspect)) + 2.5f;
        add(static_cast<int>(std::floor(d.x - r)), static_cast<int>(std::floor(d.y - r)),
            static_cast<int>(std::ceil(d.x + r)), static_cast<int>(std::ceil(d.y + r)));
    }

    void compositePatch(const ab::Patch& p, ab::Blend mode, float opacity) override {
        ab::RasterSurface::compositePatch(p, mode, opacity);
        add(p.x0, p.y0, p.x0 + p.w, p.y0 + p.h);
    }

    bool takeDirty(ab::IRect& out) {
        if (dirty_.empty()) return false;
        out = dirty_;
        dirty_ = ab::IRect{};
        return true;
    }

private:
    void add(int x0, int y0, int x1, int y1) {
        x0 = std::max(0, x0);
        y0 = std::max(0, y0);
        x1 = std::min(width(), x1);
        y1 = std::min(height(), y1);
        if (x1 <= x0 || y1 <= y0) return;
        if (dirty_.empty()) {
            dirty_ = ab::IRect{x0, y0, x1, y1};
        } else {
            dirty_.x0 = std::min(dirty_.x0, x0);
            dirty_.y0 = std::min(dirty_.y0, y0);
            dirty_.x1 = std::max(dirty_.x1, x1);
            dirty_.y1 = std::max(dirty_.y1, y1);
        }
    }
    ab::IRect dirty_;
};

struct LiveSession {
    std::unique_ptr<ab::Brush> brush;
    std::unique_ptr<TrackingSurface> surface;
    float scale = 1.f;
    bool eraser = false;
    bool started = false;
    bool ended = false;
    ab::StrokeParams sp;
    PathFilter filter;               // dinâmica do traço: suavização, pressão, curva
    std::vector<PathSample> pending; // amostras já suavizadas à espera de irem para o pincel
};
std::unique_ptr<LiveSession> g_live;

uint32_t packPixel(const float* p, bool eraser) {  // p: pré-multiplicado
    float a, r, g, b;
    if (eraser) {
        a = 1.f - p[3];
        r = g = b = 0.f;
    } else {
        a = p[3];
        if (a > 1e-5f) {
            r = std::min(1.f, p[0] / a);
            g = std::min(1.f, p[1] / a);
            b = std::min(1.f, p[2] / a);
        } else {
            r = g = b = 0.f;
        }
    }
    a = std::min(1.f, std::max(0.f, a));
    const uint32_t A = static_cast<uint32_t>(a * 255.f + 0.5f);
    const uint32_t R = static_cast<uint32_t>(r * 255.f + 0.5f);
    const uint32_t G = static_cast<uint32_t>(g * 255.f + 0.5f);
    const uint32_t B = static_cast<uint32_t>(b * 255.f + 0.5f);
    return (A << 24) | (R << 16) | (G << 8) | B;
}

/** Copia a área tocada desde a última chamada. Devolve false se nada mudou. */
bool takeDirtyImage(LiveSession& s, Image& out) {
    ab::IRect r;
    if (!s.surface->takeDirty(r)) return false;
    out.w = r.w();
    out.h = r.h();
    out.scale = s.scale;
    out.x = static_cast<float>(r.x0) / s.scale;
    out.y = static_cast<float>(r.y0) / s.scale;
    out.argb.assign(static_cast<size_t>(out.w) * out.h, 0u);
    for (int y = r.y0; y < r.y1; ++y) {
        for (int x = r.x0; x < r.x1; ++x) {
            out.argb[static_cast<size_t>(y - r.y0) * out.w + (x - r.x0)] = packPixel(s.surface->pixel(x, y), s.eraser);
        }
    }
    return true;
}

ab::InputSample liveSample(const LiveSession& s, float x, float y, float pressure, float dt) {
    ab::InputSample in;
    in.x = x * s.scale;
    in.y = y * s.scale;
    in.pressure = std::min(1.f, std::max(0.05f, pressure));
    in.dt = std::min(0.1f, std::max(0.001f, dt));  // tempo real entre amostras, limitado a valores sãos
    return in;
}

/** Dinâmica por tipo de pincel: os de tinta/pressão alisam mais o caminho e a pressão; os de carimbo/efeitos menos. */
PathConfig pathConfigFor(const ab::Brush& b) {
    PathConfig c;
    const bool pressure = b.info().needsPressure;
    c.streamline = pressure ? 0.22f : 0.12f;
    c.pressureSmoothing = pressure ? 0.3f : 0.f;
    c.chord = 3.f;
    return c;
}

/** A biblioteca com os pincéis realistas já registados (idempotente: vale qualquer ordem de arranque). */
ab::BrushLibrary& library() {
    registerRealBrushes();
    return ab::BrushLibrary::instance();
}

ab::InputSample sampleOf(const PathSample& p) {
    ab::InputSample in;
    in.x = p.x;
    in.y = p.y;
    in.pressure = std::min(1.f, std::max(0.05f, p.pressure));
    in.dt = std::min(0.1f, std::max(0.0005f, p.dt));
    return in;
}
}  // namespace

bool available() { return true; }

bool init(const uint8_t* data, size_t size) {
    std::lock_guard<std::mutex> lock(g_mutex);
    library();
    if (data == nullptr || size == 0) return false;
    return ab::StampBank::instance().loadPackFromMemory(data, size);
}

std::string catalog() {
    std::lock_guard<std::mutex> lock(g_mutex);
    return library().catalogJson();
}

bool render(const char* id, const float* pts, int count, float radius, float cr, float cg, float cb, uint32_t seed,
            bool eraser, Image& out) {
    if (id == nullptr || pts == nullptr || count < 1) return false;
    std::lock_guard<std::mutex> lock(g_mutex);
    std::unique_ptr<ab::Brush> brush = library().create(id);
    if (!brush) return false;

    float minx = pts[0], maxx = pts[0], miny = pts[1], maxy = pts[1];
    for (int i = 1; i < count; ++i) {
        minx = std::min(minx, pts[3 * i]);
        maxx = std::max(maxx, pts[3 * i]);
        miny = std::min(miny, pts[3 * i + 1]);
        maxy = std::max(maxy, pts[3 * i + 1]);
    }
    radius = std::max(0.3f, radius);
    // Margem à volta do traço: ramos, copas das árvores, brilho e raios passam muito do caminho. Uma margem curta
    // cortava a textura (testado com todo o catálogo: com 400 só ficam por baixo da margem os pincéis de linhas de foco,
    // que por desenho se estendem sem limite).
    const float margin = std::max(400.f, radius * 24.f);
    const float bw = (maxx - minx) + 2.f * margin;
    const float bh = (maxy - miny) + 2.f * margin;
    // Resolução 1:1; reduz só em traços enormes (memória).
    float scale = 1.f;
    if (static_cast<double>(bw) * bh * scale * scale > kMaxPixels) {
        scale = static_cast<float>(std::sqrt(kMaxPixels / (static_cast<double>(bw) * bh)));
    }
    const int W = std::max(2, static_cast<int>(std::ceil(bw * scale)));
    const int H = std::max(2, static_cast<int>(std::ceil(bh * scale)));
    const float ox = minx - margin;
    const float oy = miny - margin;

    ab::RasterSurface surface(W, H);
    // Borrachas: partem de preto opaco; o que o pincel retira é a quantidade apagada.
    if (eraser) surface.clear(ab::Color{0, 0, 0, 1});

    ab::StrokeParams sp;
    sp.color = eraser ? ab::Color{0, 0, 0, 1} : ab::Color{cr, cg, cb, 1};
    sp.size = radius * scale;
    sp.opacity = 1.f;
    sp.seed = seed;

    auto sample = [&](int i) {
        ab::InputSample s;
        s.x = (pts[3 * i] - ox) * scale;
        s.y = (pts[3 * i + 1] - oy) * scale;
        s.pressure = std::min(1.f, std::max(0.05f, pts[3 * i + 2]));
        s.dt = kDt;
        return s;
    };
    brush->begin(surface, sp, sample(0));
    if (count == 1) {
        brush->move(surface, sample(0));
    } else {
        PathFilter filter;
        std::vector<PathSample> out;
        const ab::InputSample f = sample(0);
        filter.start(pathConfigFor(*brush), f.x, f.y, f.pressure);
        for (int i = 1; i < count; ++i) {
            const ab::InputSample in = sample(i);
            filter.add(in.x, in.y, in.pressure, in.dt, out);
            for (const PathSample& p : out) brush->move(surface, sampleOf(p));
            out.clear();
        }
        filter.finish(out);
        for (const PathSample& p : out) brush->move(surface, sampleOf(p));
    }
    brush->end(surface);

    // Caixa justa em volta do que foi pintado (poupa memória: o resto é transparente).
    int x0 = W, y0 = H, x1 = -1, y1 = -1;
    auto alphaAt = [&](int x, int y) {
        const float a = surface.pixel(x, y)[3];
        return eraser ? 1.f - a : a;
    };
    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            if (alphaAt(x, y) > 0.002f) {
                x0 = std::min(x0, x);
                x1 = std::max(x1, x);
                y0 = std::min(y0, y);
                y1 = std::max(y1, y);
            }
        }
    }
    if (x1 < x0 || y1 < y0) return false;

    out.w = x1 - x0 + 1;
    out.h = y1 - y0 + 1;
    out.scale = scale;
    out.x = ox + static_cast<float>(x0) / scale;
    out.y = oy + static_cast<float>(y0) / scale;
    out.argb.assign(static_cast<size_t>(out.w) * out.h, 0u);
    for (int y = y0; y <= y1; ++y) {
        for (int x = x0; x <= x1; ++x) {
            out.argb[static_cast<size_t>(y - y0) * out.w + (x - x0)] = packPixel(surface.pixel(x, y), eraser);
        }
    }
    return true;
}

// ---- traço ao vivo ----

bool liveBegin(const char* id, float sheetW, float sheetH, float radius, float cr, float cg, float cb, uint32_t seed,
               bool eraser, float x, float y, float pressure, Image& dirty) {
    if (id == nullptr || sheetW < 1.f || sheetH < 1.f) return false;
    std::lock_guard<std::mutex> lock(g_mutex);
    g_live.reset();
    std::unique_ptr<ab::Brush> brush = library().create(id);
    if (!brush) return false;

    auto s = std::make_unique<LiveSession>();
    float scale = 1.f;
    if (static_cast<double>(sheetW) * sheetH > kMaxPixels) scale = static_cast<float>(std::sqrt(kMaxPixels / (static_cast<double>(sheetW) * sheetH)));
    const int W = std::max(2, static_cast<int>(std::ceil(sheetW * scale)));
    const int H = std::max(2, static_cast<int>(std::ceil(sheetH * scale)));
    s->scale = scale;
    s->eraser = eraser;
    s->surface = std::make_unique<TrackingSurface>(W, H);
    if (eraser) s->surface->clear(ab::Color{0, 0, 0, 1});
    s->sp.color = eraser ? ab::Color{0, 0, 0, 1} : ab::Color{cr, cg, cb, 1};
    s->sp.size = std::max(0.3f, radius) * scale;
    s->sp.opacity = 1.f;
    s->sp.seed = seed;
    s->brush = std::move(brush);
    const ab::InputSample first = liveSample(*s, x, y, pressure, kDt);
    s->filter.start(pathConfigFor(*s->brush), first.x, first.y, first.pressure);
    s->brush->begin(*s->surface, s->sp, first);
    s->started = true;
    const bool any = takeDirtyImage(*s, dirty);
    g_live = std::move(s);
    return any;
}

bool liveMove(float x, float y, float pressure, float dtSeconds, Image& dirty) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_live || !g_live->started || g_live->ended) return false;
    const ab::InputSample in = liveSample(*g_live, x, y, pressure, dtSeconds);
    g_live->pending.clear();
    g_live->filter.add(in.x, in.y, in.pressure, in.dt, g_live->pending);
    for (const PathSample& p : g_live->pending) g_live->brush->move(*g_live->surface, sampleOf(p));
    return takeDirtyImage(*g_live, dirty);
}

bool liveEnd(Image& dirty) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_live) return false;
    bool any = false;
    if (g_live->started && !g_live->ended) {
        g_live->pending.clear();
        g_live->filter.finish(g_live->pending);  // o caminho chega ao último ponto real
        for (const PathSample& p : g_live->pending) g_live->brush->move(*g_live->surface, sampleOf(p));
        g_live->brush->end(*g_live->surface);
        g_live->ended = true;
        any = takeDirtyImage(*g_live, dirty);
    }
    g_live.reset();
    return any;
}

void liveCancel() {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_live.reset();
}

}  // namespace abridge

#else  // sem a AnimBrush: tudo desligado

namespace abridge {
bool available() { return false; }
bool init(const uint8_t*, size_t) { return false; }
std::string catalog() { return "{\"brushes\":[]}"; }
bool render(const char*, const float*, int, float, float, float, float, uint32_t, bool, Image&) { return false; }
bool liveBegin(const char*, float, float, float, float, float, float, uint32_t, bool, float, float, float, Image&) { return false; }
bool liveMove(float, float, float, float, Image&) { return false; }
bool liveEnd(Image&) { return false; }
void liveCancel() {}
}  // namespace abridge

#endif
