// Modelo do projeto em C++ (etapa 1 da migração): frames, camadas, desenhos, exposição partilhada (cenário),
// desfazer/refazer e passo de reprodução. Mesmas regras do Project.kt / Timeline.kt, escritas de novo em C++.
//
// Valor imutável: cada operação devolve um Project novo. Os desenhos ficam em shared_ptr<const>, por isso copiar um
// Project e guardar instantâneos para o desfazer é barato (partilha estrutural, como na versão Kotlin).
// Só C++ padrão; sem dependências.
#pragma once
#include <algorithm>
#include <cstdint>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include "studio_tokens.hpp"

namespace studio {

struct Pt {
    float x = 0, y = 0;
    bool operator==(const Pt& o) const { return x == o.x && y == o.y; }
};

/** Elemento de um desenho. Preenchimento e imagem ficam referenciados por id de blob (o conteúdo vive no motor). */
struct Stroke {
    std::vector<Pt> points;
    uint32_t colorArgb = 0xFF000000u;
    float width = 4.f;
    bool erase = false;
    float taper = 0.6f;
    float hardness = 1.f;
    std::string brush;       // id da predefinição; vazio = traço contínuo
    int mode = 0;            // 0 normal, 1 só dentro do desenho, 2 por trás
    int64_t fillBlob = -1;   // -1 = sem preenchimento
    int64_t imageBlob = -1;  // -1 = sem imagem
    std::shared_ptr<const Token> token;  // o conteúdo real do traço vive no Kotlin; esta ficha segura-o enquanto houver cópias
    bool operator==(const Stroke& o) const {
        return token == o.token && points == o.points && colorArgb == o.colorArgb && width == o.width && erase == o.erase &&
               taper == o.taper && hardness == o.hardness && brush == o.brush && mode == o.mode &&
               fillBlob == o.fillBlob && imageBlob == o.imageBlob;
    }
};

/** Um desenho (conjunto de traços). Pode ser referenciado por vários frames. */
struct Drawing {
    int64_t id = 0;
    std::vector<Stroke> strokes;
    int64_t serial = 0;                           // identidade desta versão do conteúdo (muda sempre que o conteúdo muda)
    std::shared_ptr<const Token> serialToken;     // liberta o serial quando ninguém mais tem esta versão

    /** Nova versão: o Kotlin guarda a lista de traços por serial e mantém a mesma lista enquanto nada muda. */
    void touch() {
        serial = TokenTable::instance().nextSerial();
        serialToken = TokenTable::instance().acquire(1, serial);
    }
};
using DrawingPtr = std::shared_ptr<const Drawing>;

enum class LayerKind { Drawing, Scenery };

struct Layer {
    int64_t id = 0;
    std::string name;
    LayerKind kind = LayerKind::Drawing;
    int opacityPercent = 100;
    bool visible = true;
    std::map<int64_t, DrawingPtr> drawings;  // id do desenho -> desenho
    std::map<int64_t, int64_t> cels;         // id do frame -> id do desenho

    DrawingPtr drawingAt(int64_t frameId) const {
        auto c = cels.find(frameId);
        if (c == cels.end()) return nullptr;
        auto d = drawings.find(c->second);
        return d == drawings.end() ? nullptr : d->second;
    }
};

class Project {
public:
    std::vector<int64_t> frameIds;
    std::vector<Layer> layers;
    int currentFrame = 0;
    int64_t nextId = 0;

    int frameCount() const { return static_cast<int>(frameIds.size()); }
    int64_t currentFrameId() const { return frameIds[static_cast<size_t>(currentFrame)]; }

    const Layer* layer(int64_t id) const {
        for (const Layer& l : layers) if (l.id == id) return &l;
        return nullptr;
    }

    std::vector<Stroke> strokesAt(int64_t layerId, int frameIndex) const {
        const Layer* l = layer(layerId);
        if (l == nullptr || frameIndex < 0 || frameIndex >= frameCount()) return {};
        DrawingPtr d = l->drawingAt(frameIds[static_cast<size_t>(frameIndex)]);
        return d ? d->strokes : std::vector<Stroke>{};
    }

    /** Um frame, um cenário (por baixo) e uma camada de desenho (por cima). */
    static Project initial() {
        Project p;
        p.frameIds = {0};
        Layer sc;
        sc.id = 1; sc.name = "Cenário 1"; sc.kind = LayerKind::Scenery;
        Layer dl;
        dl.id = 2; dl.name = "Camada 1"; dl.kind = LayerKind::Drawing;
        p.layers = {sc, dl};
        p.currentFrame = 0;
        p.nextId = 3;
        return p;
    }

    Project select(int index) const {
        Project p = *this;
        p.currentFrame = std::min(std::max(index, 0), frameCount() - 1);
        return p;
    }

    /** Acrescenta um traço no frame atual. Num cenário, o primeiro desenho cobre todos os frames sem desenho. */
    Project addStroke(int64_t layerId, const Stroke& stroke) const {
        Project p = *this;
        Layer* l = p.mut(layerId);
        if (l == nullptr) return p;
        const int64_t fid = currentFrameId();
        DrawingPtr existing = l->drawingAt(fid);
        if (existing) {
            auto d = std::make_shared<Drawing>(*existing);
            d->strokes.push_back(stroke);
            d->touch();
            l->drawings[d->id] = d;
            return p;
        }
        auto fresh = std::make_shared<Drawing>();
        fresh->id = p.nextId;
        fresh->strokes.push_back(stroke);
        fresh->touch();
        p.nextId += 1;
        l->drawings[fresh->id] = fresh;
        if (l->kind == LayerKind::Scenery) {
            for (int64_t f : frameIds) if (l->cels.find(f) == l->cels.end()) l->cels[f] = fresh->id;
        } else {
            l->cels[fid] = fresh->id;
        }
        return p;
    }

    // ---------- frames ----------

    /** Novo frame após o atual: camadas de desenho em branco; cenários continuam o desenho do frame atual. */
    Project addFrameAfterCurrent() const {
        Project p = *this;
        const int64_t newId = nextId;
        const int64_t cur = currentFrameId();
        for (Layer& l : p.layers) {
            if (l.kind != LayerKind::Scenery) continue;
            auto c = l.cels.find(cur);
            if (c != l.cels.end()) l.cels[newId] = c->second;
        }
        p.frameIds.insert(p.frameIds.begin() + currentFrame + 1, newId);
        p.currentFrame = currentFrame + 1;
        p.nextId = nextId + 1;
        return p;
    }

    /** Duplica o frame atual: desenhos copiados de forma independente; cenários partilhados. */
    Project duplicateCurrentFrame() const {
        Project p = *this;
        const int64_t newId = nextId;
        int64_t counter = nextId + 1;
        const int64_t cur = currentFrameId();
        for (Layer& l : p.layers) {
            DrawingPtr src = l.drawingAt(cur);
            if (!src) continue;
            if (l.kind == LayerKind::Scenery) {
                l.cels[newId] = src->id;
            } else {
                auto dup = std::make_shared<Drawing>(*src);
                dup->id = counter++;
                dup->touch();
                l.drawings[dup->id] = dup;
                l.cels[newId] = dup->id;
            }
        }
        p.frameIds.insert(p.frameIds.begin() + currentFrame + 1, newId);
        p.currentFrame = currentFrame + 1;
        p.nextId = counter;
        return p;
    }

    Project deleteCurrentFrame() const {
        if (frameIds.size() == 1) return *this;
        Project p = *this;
        const int64_t fid = currentFrameId();
        p.frameIds.erase(p.frameIds.begin() + currentFrame);
        for (Layer& l : p.layers) {
            l.cels.erase(fid);
            std::map<int64_t, DrawingPtr> kept;
            for (const auto& kv : l.cels) {
                auto d = l.drawings.find(kv.second);
                if (d != l.drawings.end()) kept[kv.second] = d->second;
            }
            l.drawings = std::move(kept);
        }
        p.currentFrame = std::min(currentFrame, p.frameCount() - 1);
        return p;
    }

    /** O frame atual e os seguintes que partilhavam o desenho do cenário passam a usar uma cópia. */
    Project separateSceneryFromCurrent(int64_t layerId) const {
        const Layer* src = layer(layerId);
        if (src == nullptr || src->kind != LayerKind::Scenery) return *this;
        DrawingPtr old = src->drawingAt(currentFrameId());
        if (!old) return *this;
        Project p = *this;
        Layer* l = p.mut(layerId);
        auto dup = std::make_shared<Drawing>(*old);
        dup->id = p.nextId;
        dup->touch();
        p.nextId += 1;
        for (int i = currentFrame; i < frameCount(); ++i) {
            auto c = l->cels.find(frameIds[static_cast<size_t>(i)]);
            if (c != l->cels.end() && c->second == old->id) c->second = dup->id;
        }
        l->drawings[dup->id] = dup;
        return p;
    }

    // ---------- camadas ----------

    /** Camadas de desenho entram no topo; cenários entram abaixo de todas as camadas de desenho. */
    Project addLayer(LayerKind kind) const {
        Project p = *this;
        int n = 1;
        for (const Layer& l : layers) if (l.kind == kind) ++n;
        Layer nl;
        nl.id = nextId;
        nl.kind = kind;
        nl.name = (kind == LayerKind::Drawing ? "Camada " : "Cenário ") + std::to_string(n);
        size_t idx = p.layers.size();
        if (kind == LayerKind::Scenery) {
            for (size_t i = 0; i < p.layers.size(); ++i) {
                if (p.layers[i].kind == LayerKind::Drawing) { idx = i; break; }
            }
        }
        p.layers.insert(p.layers.begin() + static_cast<std::ptrdiff_t>(idx), nl);
        p.nextId = nextId + 1;
        return p;
    }

    /** Duplica a camada com todo o conteúdo, de forma independente, logo acima da original. */
    Project duplicateLayer(int64_t layerId) const {
        size_t i = 0;
        while (i < layers.size() && layers[i].id != layerId) ++i;
        if (i == layers.size()) return *this;
        const Layer& src = layers[i];
        Project p = *this;
        int64_t counter = nextId + 1;
        std::map<int64_t, int64_t> idMap;
        for (const auto& kv : src.drawings) idMap[kv.first] = counter++;
        Layer d;
        d.id = nextId;
        d.name = src.name + " cópia";
        d.kind = src.kind;
        d.opacityPercent = src.opacityPercent;
        d.visible = src.visible;
        for (const auto& kv : src.drawings) {
            auto nd = std::make_shared<Drawing>(*kv.second);
            nd->id = idMap[kv.first];
            nd->touch();
            d.drawings[nd->id] = nd;
        }
        for (const auto& kv : src.cels) d.cels[kv.first] = idMap[kv.second];
        p.layers.insert(p.layers.begin() + static_cast<std::ptrdiff_t>(i) + 1, d);
        p.nextId = counter;
        return p;
    }

    Project renameLayer(int64_t layerId, const std::string& name) const {
        const size_t a = name.find_first_not_of(" \t\r\n");
        if (a == std::string::npos) return *this;
        const size_t b = name.find_last_not_of(" \t\r\n");
        Project p = *this;
        if (Layer* l = p.mut(layerId)) l->name = name.substr(a, b - a + 1);
        return p;
    }

    Project setLayerOpacity(int64_t layerId, int percent) const {
        Project p = *this;
        if (Layer* l = p.mut(layerId)) l->opacityPercent = std::min(100, std::max(0, percent));
        return p;
    }

    Project toggleLayerVisible(int64_t layerId) const {
        Project p = *this;
        if (Layer* l = p.mut(layerId)) l->visible = !l->visible;
        return p;
    }

    Project deleteLayer(int64_t layerId) const {
        if (layers.size() == 1) return *this;
        Project p = *this;
        p.layers.erase(std::remove_if(p.layers.begin(), p.layers.end(), [&](const Layer& l) { return l.id == layerId; }),
                       p.layers.end());
        return p;
    }

    /** Move uma camada [delta] posições na pilha (positivo = para cima). */
    Project moveLayer(int64_t layerId, int delta) const {
        size_t i = 0;
        while (i < layers.size() && layers[i].id != layerId) ++i;
        if (i == layers.size()) return *this;
        const long last = static_cast<long>(layers.size()) - 1;
        const long j = std::min(std::max(static_cast<long>(i) + delta, 0L), last);
        if (j == static_cast<long>(i)) return *this;
        Project p = *this;
        Layer l = p.layers[i];
        p.layers.erase(p.layers.begin() + static_cast<std::ptrdiff_t>(i));
        p.layers.insert(p.layers.begin() + static_cast<std::ptrdiff_t>(j), std::move(l));
        return p;
    }

    /** Substitui os traços do desenho do frame atual de uma camada (laço, balde, apagar elementos). Mantém o id. */
    Project setCurrentStrokes(int64_t layerId, const std::vector<Stroke>& strokes) const {
        const Layer* src = layer(layerId);
        if (src == nullptr) return *this;
        DrawingPtr d = src->drawingAt(currentFrameId());
        if (!d) return *this;
        Project p = *this;
        Layer* l = p.mut(layerId);
        auto nd = std::make_shared<Drawing>(*d);
        nd->strokes = strokes;
        nd->touch();
        l->drawings[nd->id] = nd;
        return p;
    }

    /**
     * Insere um frame na posição [position] (0 = antes do primeiro, frameCount = depois do último) e seleciona-o.
     * Camadas de desenho recebem o conteúdo de [content] (cópia independente); cenários continuam o desenho do frame
     * vizinho anterior (ou do primeiro, se inserido no início).
     */
    Project insertFrameAt(int position, const std::map<int64_t, std::vector<Stroke>>& content) const {
        Project p = *this;
        const int pos = std::min(std::max(position, 0), frameCount());
        const int64_t newId = nextId;
        int64_t counter = nextId + 1;
        const int nb = std::min(std::max(pos - 1, 0), frameCount() - 1);
        const int64_t neighbor = frameIds[static_cast<size_t>(nb)];
        for (Layer& l : p.layers) {
            if (l.kind == LayerKind::Scenery) {
                auto c = l.cels.find(neighbor);
                if (c != l.cels.end()) l.cels[newId] = c->second;
            } else {
                auto it = content.find(l.id);
                if (it == content.end() || it->second.empty()) continue;
                auto d = std::make_shared<Drawing>();
                d->id = counter++;
                d->strokes = it->second;
                d->touch();
                l.drawings[d->id] = d;
                l.cels[newId] = d->id;
            }
        }
        p.frameIds.insert(p.frameIds.begin() + pos, newId);
        p.currentFrame = pos;
        p.nextId = counter;
        return p;
    }

    /** Projeto montado a partir de peças (leitura de ficheiros): sem validações além das de limites. */
    static Project assemble(std::vector<int64_t> frameIds, std::vector<Layer> layers, int currentFrame, int64_t nextId) {
        Project p;
        p.frameIds = std::move(frameIds);
        p.layers = std::move(layers);
        p.currentFrame = p.frameIds.empty() ? 0 : std::min(std::max(currentFrame, 0), static_cast<int>(p.frameIds.size()) - 1);
        p.nextId = nextId;
        return p;
    }

private:
    Layer* mut(int64_t id) {
        for (Layer& l : layers) if (l.id == id) return &l;
        return nullptr;
    }
};

// ---------- reprodução ----------

enum class PlayMode { Once, Loop, PingPong };
struct PlaybackStep {
    int index = 0, direction = 1;
    bool finished = false;
    bool operator==(const PlaybackStep& o) const { return index == o.index && direction == o.direction && finished == o.finished; }
};

/** Próximo frame da reprodução. Em PingPong os extremos não são repetidos. */
inline PlaybackStep nextPlaybackStep(int index, int count, PlayMode mode, int direction) {
    switch (mode) {
        case PlayMode::Once: {
            const int n = index + 1;
            return n >= count ? PlaybackStep{index, 1, true} : PlaybackStep{n, 1, false};
        }
        case PlayMode::Loop: return PlaybackStep{(index + 1) % count, 1, false};
        case PlayMode::PingPong: {
            int d = direction >= 0 ? 1 : -1;
            int n = index + d;
            if (n >= count) { d = -1; n = index - 1; }
            else if (n < 0) { d = 1; n = index + 1; }
            return PlaybackStep{std::min(std::max(n, 0), count - 1), d, false};
        }
    }
    return PlaybackStep{index, direction, true};
}

// ---------- desfazer / refazer ----------

/** Histórico por instantâneos (T deve ser barato de copiar: o Project partilha os desenhos). */
template <class T>
class History {
public:
    explicit History(size_t limit = 100) : limit_(limit) {}
    bool canUndo() const { return !undo_.empty(); }
    bool canRedo() const { return !redo_.empty(); }

    void record(const T& previous) {
        undo_.push_back(previous);
        if (undo_.size() > limit_) undo_.erase(undo_.begin(), undo_.end() - static_cast<std::ptrdiff_t>(limit_));
        redo_.clear();
    }
    std::optional<T> undo(const T& current) {
        if (undo_.empty()) return std::nullopt;
        T r = undo_.back();
        undo_.pop_back();
        redo_.push_back(current);
        return r;
    }
    std::optional<T> redo(const T& current) {
        if (redo_.empty()) return std::nullopt;
        T r = redo_.back();
        redo_.pop_back();
        undo_.push_back(current);
        return r;
    }

private:
    size_t limit_;
    std::vector<T> undo_, redo_;
};

}  // namespace studio
