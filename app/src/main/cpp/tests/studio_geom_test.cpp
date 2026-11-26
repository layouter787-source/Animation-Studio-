// Testes da geometria, câmera e exposição em C++. Compilar e correr:
//   g++ -std=c++17 -O1 -I.. studio_geom_test.cpp -o t && ./t
#include <cmath>
#include <cstdio>

#include "studio_geom.hpp"
#include "studio_hold.hpp"

using namespace studio;

static int g_fail = 0;
#define CHECK(c) do { if (!(c)) { std::printf("FALHOU %s:%d  %s\n", __FILE__, __LINE__, #c); ++g_fail; } } while (0)
static bool near(float a, float b, float e = 1e-3f) { return std::fabs(a - b) <= e * std::max(1.f, std::fabs(b)); }

static Stroke S() { Stroke s; s.points = {{1, 1}}; return s; }

int main() {
    {  // vista: ida e volta, com rotação
        ViewTransform v{2.5f, 0.7f, 30.f, -12.f};
        Pt p{13.f, 41.f};
        Pt q = v.screenToProject(v.projectToScreen(p));
        CHECK(near(q.x, p.x) && near(q.y, p.y));
    }
    {  // zoom e rotação preservam o ponto sob o foco
        ViewTransform v{1.f, 0.3f, 10.f, 20.f};
        Pt focus{200.f, 150.f};
        Pt before = v.screenToProject(focus);
        Pt z = v.zoomAround(focus, 3.f).screenToProject(focus);
        Pt r = v.rotateAround(focus, 0.9f).screenToProject(focus);
        CHECK(near(z.x, before.x) && near(z.y, before.y));
        CHECK(near(r.x, before.x) && near(r.y, before.y));
    }
    {  // limites do zoom
        ViewTransform v;
        CHECK(near(v.zoomAround({0, 0}, 1e-9f).scale, ViewTransform::kMinScale));
        CHECK(near(v.zoomAround({0, 0}, 1e12f).scale, ViewTransform::kMaxScale));
    }
    {  // ajustar à folha: centrada e dentro da vista
        ViewTransform v = ViewTransform::fitSheet(1000.f, 500.f, 800.f, 800.f);
        CHECK(near(v.scale, 0.768f));
        Pt tl = v.projectToScreen({0, 0}), br = v.projectToScreen({1000.f, 500.f});
        CHECK(near(tl.x, 16.f) && near(br.x, 784.f));
        CHECK(near((tl.y + br.y) / 2.f, 400.f));
    }
    {  // afim: inversa e composição
        Affine m = Affine::around({10, 10}, 2.f, 0.5f, 3.f, -4.f);
        auto inv = m.inverse();
        CHECK(inv.has_value());
        Pt p{7, 9};
        Pt q = inv->map(m.map(p));
        CHECK(near(q.x, p.x) && near(q.y, p.y));
        Affine t = Affine::translate(5, 6).times(Affine::translate(1, 2));
        CHECK(near(t.tx, 6) && near(t.ty, 8));
        CHECK((!Affine{0, 0, 0, 0, 1, 1}.inverse().has_value()));
    }
    {  // câmera: sem keyframes vê a folha inteira; interpolação do zoom é proporcional
        std::vector<int64_t> ids{10, 11, 12};
        CameraTrack t;
        CameraKey d = cameraAt(ids, t, 1.f, 1920.f, 1080.f);
        CHECK(near(d.cx, 960.f) && near(d.cy, 540.f) && near(d.zoom, 1.f));
        t = t.withKey(10, {0, 0, 1.f, 0.f}).withKey(12, {100, 50, 4.f, 1.f});
        CameraKey m = cameraAt(ids, t, 1.f, 1920.f, 1080.f);
        CHECK(near(m.cx, 50.f) && near(m.cy, 25.f) && near(m.zoom, 2.f) && near(m.rotation, 0.5f));
        CHECK(near(cameraAt(ids, t, -3.f, 1, 1).zoom, 1.f));
        CHECK(near(cameraAt(ids, t, 9.f, 1, 1).zoom, 4.f));
        CHECK(near(cameraAt(ids, t.withoutKey(12), 2.f, 1, 1).zoom, 1.f));
        // o centro da câmera cai no meio da saída
        Pt c = CameraKey{300.f, 200.f, 2.f, 0.4f}.outputAffine(1920.f, 1080.f).map({300.f, 200.f});
        CHECK(near(c.x, 960.f) && near(c.y, 540.f));
        CHECK(CameraKey::defaultFor(100, 50).rect(100, 50).size() == 4);
    }
    {  // exposição: estender partilha o desenho; encurtar tira o último e nunca o primeiro
        Project p = Project::initial();
        const int64_t dl = 2;
        p = p.addStroke(dl, S());
        p = extendHold(p, 0, 2);
        CHECK(p.frameCount() == 3);
        CHECK(holdStart(p, 2) == 0 && holdEnd(p, 0) == 2);
        CHECK(sameExposure(p, 0, 1) && sameExposure(p, 1, 2));
        CHECK(p.layer(dl)->drawings.size() == 1);  // nada foi copiado
        p = shrinkHold(p, 0, 5);
        CHECK(p.frameCount() == 1);
        CHECK(p.currentFrame == 0);
    }
    {  // frames em branco não são exposição
        Project p = Project::initial().addFrameAfterCurrent();
        CHECK(!sameExposure(p, 0, 1));
        CHECK(holdEnd(p, 0) == 0);
    }
    std::printf(g_fail ? "%d falhas\n" : "tudo certo\n", g_fail);
    return g_fail ? 1 : 0;
}
