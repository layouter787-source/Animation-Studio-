#include "animbrush_core.h"

#ifdef ANIMBRUSH_ENABLED
#include <algorithm>
#include <cmath>
#include <mutex>

#include "animbrush/animbrush.hpp"

namespace abridge {
namespace {
std::mutex g_mutex;  // os pincéis usam registos globais (biblioteca e texturas): um traço de cada vez

constexpr double kMaxPixels = 4500000.0;  // limite de memória da superfície (float RGBA = 16 bytes por píxel)
constexpr float kDt = 0.008f;             // o traço guardado não tem tempos: 8 ms por amostra
}  // namespace

bool available() { return true; }

bool init(const uint8_t* data, size_t size) {
    std::lock_guard<std::mutex> lock(g_mutex);
    ab::BrushLibrary::instance();
    if (data == nullptr || size == 0) return false;
    return ab::StampBank::instance().loadPackFromMemory(data, size);
}

std::string catalog() {
    std::lock_guard<std::mutex> lock(g_mutex);
    return ab::BrushLibrary::instance().catalogJson();
}

bool render(const char* id, const float* pts, int count, float radius, float cr, float cg, float cb, uint32_t seed,
            bool eraser, Image& out) {
    if (id == nullptr || pts == nullptr || count < 1) return false;
    std::lock_guard<std::mutex> lock(g_mutex);
    std::unique_ptr<ab::Brush> brush = ab::BrushLibrary::instance().create(id);
    if (!brush) return false;

    float minx = pts[0], maxx = pts[0], miny = pts[1], maxy = pts[1];
    for (int i = 1; i < count; ++i) {
        minx = std::min(minx, pts[3 * i]);
        maxx = std::max(maxx, pts[3 * i]);
        miny = std::min(miny, pts[3 * i + 1]);
        maxy = std::max(maxy, pts[3 * i + 1]);
    }
    radius = std::max(0.3f, radius);
    // Margem à volta do traço: ramos, brilho, raios e copas passam do caminho.
    const float margin = std::max(48.f, radius * 4.f);
    const float bw = (maxx - minx) + 2.f * margin;
    const float bh = (maxy - miny) + 2.f * margin;
    // Dobra a resolução em traços pequenos (nitidez ao ampliar); reduz em traços enormes (memória).
    float scale = (static_cast<double>(bw) * bh * 4.0 <= 1.2e6) ? 2.f : 1.f;
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
        for (int i = 1; i < count; ++i) brush->move(surface, sample(i));
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
            const float* p = surface.pixel(x, y);  // pré-multiplicado
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
            out.argb[static_cast<size_t>(y - y0) * out.w + (x - x0)] = (A << 24) | (R << 16) | (G << 8) | B;
        }
    }
    return true;
}

}  // namespace abridge

#else  // sem a AnimBrush: tudo desligado

namespace abridge {
bool available() { return false; }
bool init(const uint8_t*, size_t) { return false; }
std::string catalog() { return "{\"brushes\":[]}"; }
bool render(const char*, const float*, int, float, float, float, float, uint32_t, bool, Image&) { return false; }
}  // namespace abridge

#endif
