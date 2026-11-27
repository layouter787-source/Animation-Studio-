// Testes do modelo C++ (espelham ProjectTest.kt e TimelineTest.kt). Compilar e correr:
//   g++ -std=c++17 -O1 studio_project_test.cpp -o t && ./t
#include <cstdio>
#include <cstdlib>

#include "studio_project.hpp"

using namespace studio;

static int g_fail = 0;
#define CHECK(c) do { if (!(c)) { std::printf("FALHOU %s:%d  %s\n", __FILE__, __LINE__, #c); ++g_fail; } } while (0)

static Stroke S(float x, float y, uint32_t c, float w) { Stroke s; s.points = {{x, y}}; s.colorArgb = c; s.width = w; return s; }
static int64_t scenery(const Project& p) { for (auto& l : p.layers) if (l.kind == LayerKind::Scenery) return l.id; return -1; }
static int64_t drawing(const Project& p) { for (auto& l : p.layers) if (l.kind == LayerKind::Drawing) return l.id; return -1; }
static Project threeFrames() { return Project::initial().addFrameAfterCurrent().addFrameAfterCurrent(); }
using V = std::vector<Stroke>;

int main() {
    const Stroke s1 = S(0, 0, 0xFF000000u, 4), s2 = S(5, 5, 0xFFFF0000u, 6);

    {  // sharedSceneryEditAffectsAllFrames
        Project p = threeFrames();
        const auto sc = scenery(p);
        p = p.select(0).addStroke(sc, s1);
        for (int i = 0; i < 3; ++i) CHECK(p.strokesAt(sc, i) == V{s1});
        p = p.select(2).addStroke(sc, s2);
        for (int i = 0; i < 3; ++i) CHECK((p.strokesAt(sc, i) == V{s1, s2}));
    }
    {  // separateSceneryFromCurrentKeepsEarlierFramesUntouched
        Project p = threeFrames();
        const auto sc = scenery(p);
        p = p.select(0).addStroke(sc, s1);
        p = p.select(1).separateSceneryFromCurrent(sc).addStroke(sc, s2);
        CHECK(p.strokesAt(sc, 0) == V{s1});
        CHECK((p.strokesAt(sc, 1) == V{s1, s2}));
        CHECK((p.strokesAt(sc, 2) == V{s1, s2}));
    }
    {  // newFrameInheritsSceneryButDrawingLayerStartsBlank
        Project p = Project::initial();
        const auto sc = scenery(p), dl = drawing(p);
        p = p.addStroke(sc, s1).addStroke(dl, s2).addFrameAfterCurrent();
        CHECK(p.strokesAt(sc, 1) == V{s1});
        CHECK(p.strokesAt(dl, 1).empty());
        CHECK(p.strokesAt(dl, 0) == V{s2});
    }
    {  // drawingLayerStrokesStayInTheirFrame
        Project p = Project::initial();
        const auto dl = drawing(p);
        p = p.addStroke(dl, s1).addFrameAfterCurrent().addStroke(dl, s2);
        CHECK(p.strokesAt(dl, 0) == V{s1});
        CHECK(p.strokesAt(dl, 1) == V{s2});
    }
    {  // duplicateFrameCopiesDrawingsIndependentlyAndSharesScenery
        Project p = Project::initial();
        const auto sc = scenery(p), dl = drawing(p);
        p = p.addStroke(sc, s1).addStroke(dl, s1).duplicateCurrentFrame();
        CHECK(p.currentFrame == 1);
        p = p.addStroke(dl, s2);
        CHECK((p.strokesAt(dl, 1) == V{s1, s2}));
        CHECK(p.strokesAt(dl, 0) == V{s1});
        p = p.addStroke(sc, s2);
        CHECK(p.strokesAt(sc, 0) == p.strokesAt(sc, 1));
    }
    {  // duplicateLayerIsIndependentAndPlacedAboveOriginal
        Project p = Project::initial();
        const auto dl = drawing(p);
        p = p.addStroke(dl, s1).duplicateLayer(dl);
        CHECK(p.layers.size() == 3);
        size_t i = 0;
        while (p.layers[i].id != dl) ++i;
        const Layer copy = p.layers[i + 1];
        CHECK(copy.name == "Camada 1 cópia");
        CHECK(p.strokesAt(copy.id, 0) == V{s1});
        p = p.addStroke(copy.id, s2);
        CHECK(p.strokesAt(dl, 0) == V{s1});
        CHECK((p.strokesAt(copy.id, 0) == V{s1, s2}));
    }
    {  // newLayersAreOrderedSceneryBelowDrawing
        Project p = Project::initial().addLayer(LayerKind::Drawing).addLayer(LayerKind::Scenery);
        const LayerKind e[] = {LayerKind::Scenery, LayerKind::Scenery, LayerKind::Drawing, LayerKind::Drawing};
        CHECK(p.layers.size() == 4);
        for (size_t i = 0; i < 4; ++i) CHECK(p.layers[i].kind == e[i]);
        CHECK(p.layers[1].name == "Cenário 2");
        CHECK(p.layers[3].name == "Camada 2");
    }
    {  // opacityIsClampedAndRenameIgnoresBlank
        Project p = Project::initial();
        const auto dl = drawing(p);
        auto op = [&](const Project& q) { return q.layer(dl)->opacityPercent; };
        CHECK(op(p.setLayerOpacity(dl, 150)) == 100);
        CHECK(op(p.setLayerOpacity(dl, -5)) == 0);
        CHECK(op(p.setLayerOpacity(dl, 40)) == 40);
        p = p.renameLayer(dl, "   ");
        CHECK(p.layer(dl)->name == "Camada 1");
        p = p.renameLayer(dl, " Corpo ");
        CHECK(p.layer(dl)->name == "Corpo");
    }
    {  // lastLayerCannotBeDeleted
        Project p = Project::initial();
        p = p.deleteLayer(scenery(p));
        CHECK(p.layers.size() == 1);
        CHECK(p.deleteLayer(p.layers[0].id).layers.size() == 1);
    }
    {  // deleteFrameDropsOrphanDrawingsButKeepsSharedScenery
        Project p = Project::initial();
        const auto sc = scenery(p), dl = drawing(p);
        p = p.addStroke(sc, s1).addStroke(dl, s2).addFrameAfterCurrent().select(0).deleteCurrentFrame();
        CHECK(p.frameCount() == 1);
        CHECK(p.layer(dl)->drawings.empty());
        CHECK(p.strokesAt(sc, 0) == V{s1});
    }
    {  // histórico: desfazer/refazer
        Project a = Project::initial();
        Project b = a.addStroke(drawing(a), s1);
        History<Project> h;
        h.record(a);
        auto u = h.undo(b);
        CHECK(u.has_value());
        CHECK(u->strokesAt(drawing(a), 0).empty());
        auto r = h.redo(*u);
        CHECK(r.has_value());
        CHECK(r->strokesAt(drawing(a), 0) == V{s1});
        History<Project> e;
        CHECK(!e.undo(a).has_value());
        CHECK(!e.canRedo());
        History<int> hi;
        hi.record(1);
        hi.undo(2);
        CHECK(hi.canRedo());
        hi.record(3);
        CHECK(!hi.canRedo());
    }
    {  // reprodução
        CHECK((nextPlaybackStep(0, 3, PlayMode::Once, 1) == PlaybackStep{1, 1, false}));
        CHECK(nextPlaybackStep(2, 3, PlayMode::Once, 1).finished);
        CHECK(nextPlaybackStep(2, 3, PlayMode::Loop, 1).index == 0);
        int idx = 0, dir = 1;
        std::vector<int> seen{0};
        for (int k = 0; k < 6; ++k) {
            auto s = nextPlaybackStep(idx, 3, PlayMode::PingPong, dir);
            idx = s.index; dir = s.direction; seen.push_back(idx);
        }
        CHECK((seen == std::vector<int>{0, 1, 2, 1, 0, 1, 2}));
    }
    {  // moveLayer: limita ao intervalo e devolve o mesmo quando nada muda
        Project p = Project::initial().addLayer(LayerKind::Drawing);
        const auto bottom = p.layers.front().id;
        Project up = p.moveLayer(bottom, 1);
        CHECK(up.layers[1].id == bottom);
        CHECK(p.moveLayer(bottom, -5).layers.front().id == bottom);
        CHECK(p.moveLayer(999, 1).layers.size() == 3);
        CHECK(p.moveLayer(bottom, 100).layers.back().id == bottom);
    }
    {  // setCurrentStrokes mantém o id do desenho e muda o serial; o resto continua igual
        Project p = Project::initial();
        const auto dl = drawing(p);
        p = p.addStroke(dl, s1).addStroke(dl, s2);
        const auto before = p.layer(dl)->drawingAt(p.currentFrameId());
        Project q = p.setCurrentStrokes(dl, {s2});
        const auto after = q.layer(dl)->drawingAt(q.currentFrameId());
        CHECK(after->id == before->id && after->serial != before->serial);
        CHECK(q.strokesAt(dl, 0) == V{s2});
        CHECK(p.layer(dl)->drawingAt(p.currentFrameId())->serial == before->serial);
        CHECK(p.setCurrentStrokes(999, {}).layers.size() == 2);
    }
    {  // insertFrameAt: cenário continua o vizinho; camadas de desenho recebem o conteúdo
        Project p = Project::initial();
        const auto sc = scenery(p), dl = drawing(p);
        p = p.addStroke(sc, s1).addFrameAfterCurrent().addFrameAfterCurrent();
        std::map<int64_t, std::vector<Stroke>> content{{dl, {s2}}};
        Project q = p.insertFrameAt(1, content);
        CHECK(q.frameCount() == 4 && q.currentFrame == 1);
        CHECK(q.strokesAt(sc, 1) == V{s1});
        CHECK(q.strokesAt(dl, 1) == V{s2});
        CHECK(q.strokesAt(dl, 0).empty() && q.strokesAt(dl, 2).empty());
        Project z = p.insertFrameAt(-4, {});
        CHECK(z.currentFrame == 0 && z.strokesAt(sc, 0) == V{s1});
    }
    {  // fichas: mesma ficha enquanto viva; liberta-se quando a última cópia desaparece
        TokenTable& tt = TokenTable::instance();
        tt.drain();
        Stroke a = S(0, 0, 0xFF000000u, 4);
        a.token = tt.acquire(0, 7001);
        Project p = Project::initial();
        const auto dl = drawing(p);
        Project q = p.addStroke(dl, a);
        {
            Stroke b = S(1, 1, 0xFF000000u, 4);
            b.token = tt.acquire(0, 7001);
            CHECK(b.token == a.token);
        }
        a.token.reset();
        CHECK(tt.drain().empty());          // o projeto ainda guarda a ficha
        const int64_t serialNow = q.layer(dl)->drawingAt(q.currentFrameId())->serial;
        q = Project::initial();             // deixa cair a versão com o traço
        p = Project::initial();
        auto rel = tt.drain();
        bool strokeReleased = false, serialReleased = false;
        for (auto& kv : rel) {
            if (kv.first == 0 && kv.second == 7001) strokeReleased = true;
            if (kv.first == 1 && kv.second == serialNow) serialReleased = true;
        }
        CHECK(strokeReleased && serialReleased);
        // recriada antes de esvaziar a fila: continua viva
        auto t1 = tt.acquire(0, 7002);
        t1.reset();
        auto t2 = tt.acquire(0, 7002);
        CHECK(tt.drain().empty());
        t2.reset();
        CHECK(tt.drain().size() == 1);
    }
    std::printf(g_fail ? "%d falhas\n" : "tudo certo\n", g_fail);
    return g_fail ? 1 : 0;
}
