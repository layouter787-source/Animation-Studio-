// Ponte JNI do modelo do projeto (etapa 2 da migração): o Kotlin guarda só um número (handle) e pede ao C++ cada
// operação. Cada operação devolve um handle NOVO (o Project é um valor imutável, como no Kotlin) e o antigo continua
// válido até o Kotlin o libertar (nFree). O desfazer/refazer guarda handles, que partilham os desenhos por dentro.
#include <jni.h>

#include <cstdint>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "studio_fill.hpp"
#include "studio_hold.hpp"
#include "studio_ink.hpp"
#include "studio_project.hpp"

namespace {
using studio::Project;

std::mutex g_mutex;
std::unordered_map<jlong, std::shared_ptr<const Project>> g_projects;
jlong g_next = 1;

jlong store(Project p) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const jlong h = g_next++;
    g_projects[h] = std::make_shared<const Project>(std::move(p));
    return h;
}

std::shared_ptr<const Project> get(jlong h) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_projects.find(h);
    return it == g_projects.end() ? nullptr : it->second;
}

std::string str(JNIEnv* env, jstring s) {
    if (s == nullptr) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string r = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}

jlongArray toLongArray(JNIEnv* env, const std::vector<jlong>& v) {
    jlongArray a = env->NewLongArray(static_cast<jsize>(v.size()));
    if (!v.empty()) env->SetLongArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
    return a;
}

std::vector<jlong> fromLongArray(JNIEnv* env, jlongArray a) {
    std::vector<jlong> v;
    if (a == nullptr) return v;
    const jsize n = env->GetArrayLength(a);
    v.resize(static_cast<size_t>(n));
    if (n > 0) env->GetLongArrayRegion(a, 0, n, v.data());
    return v;
}

studio::Stroke strokeOf(jlong id) {
    studio::Stroke s;
    s.token = studio::TokenTable::instance().acquire(0, id);
    return s;
}

const studio::Layer* layerAt(const Project& p, jint i) {
    return i >= 0 && i < static_cast<jint>(p.layers.size()) ? &p.layers[static_cast<size_t>(i)] : nullptr;
}

/** Aplica [f] ao projeto do handle; handle inválido devolve 0. */
template <class F>
jlong apply(jlong h, F f) {
    auto p = get(h);
    return p ? store(f(*p)) : 0;
}
}  // namespace

#define FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_animationstudio_domain_NativeStudio_##name

FN(jlong, nInitial)(JNIEnv*, jclass) { return store(Project::initial()); }

FN(void, nFree)(JNIEnv*, jclass, jlong h) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_projects.erase(h);
}

FN(jint, nLiveCount)(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return static_cast<jint>(g_projects.size());
}

// ---- consultas ----
FN(jint, nFrameCount)(JNIEnv*, jclass, jlong h) { auto p = get(h); return p ? p->frameCount() : 0; }
FN(jint, nCurrentFrame)(JNIEnv*, jclass, jlong h) { auto p = get(h); return p ? p->currentFrame : 0; }
FN(jlong, nNextId)(JNIEnv*, jclass, jlong h) { auto p = get(h); return p ? p->nextId : 0; }
FN(jint, nLayerCount)(JNIEnv*, jclass, jlong h) { auto p = get(h); return p ? static_cast<jint>(p->layers.size()) : 0; }

FN(jlongArray, nFrameIds)(JNIEnv* env, jclass, jlong h) {
    auto p = get(h);
    const jsize n = p ? static_cast<jsize>(p->frameIds.size()) : 0;
    jlongArray a = env->NewLongArray(n);
    if (n > 0) {
        std::vector<jlong> v(p->frameIds.begin(), p->frameIds.end());
        env->SetLongArrayRegion(a, 0, n, v.data());
    }
    return a;
}

/** [id, tipo (0 desenho, 1 cenário), opacidade %, visível (0/1)] da camada [i]. */
FN(jlongArray, nLayerInfo)(JNIEnv* env, jclass, jlong h, jint i) {
    auto p = get(h);
    const studio::Layer* l = p ? layerAt(*p, i) : nullptr;
    jlongArray a = env->NewLongArray(4);
    if (l) {
        jlong v[4] = {l->id, l->kind == studio::LayerKind::Scenery ? 1 : 0, l->opacityPercent, l->visible ? 1 : 0};
        env->SetLongArrayRegion(a, 0, 4, v);
    }
    return a;
}

FN(jstring, nLayerName)(JNIEnv* env, jclass, jlong h, jint i) {
    auto p = get(h);
    const studio::Layer* l = p ? layerAt(*p, i) : nullptr;
    return env->NewStringUTF(l ? l->name.c_str() : "");
}

/** Ids (fichas) dos traços da camada no frame (índice de frame). */
FN(jlongArray, nStrokeIds)(JNIEnv* env, jclass, jlong h, jlong layerId, jint frame) {
    auto p = get(h);
    std::vector<studio::Stroke> s = p ? p->strokesAt(layerId, frame) : std::vector<studio::Stroke>{};
    std::vector<jlong> v;
    for (const studio::Stroke& k : s) v.push_back(k.token ? k.token->id : -1);
    return toLongArray(env, v);
}

/** Todos os desenhos da camada [i], em sequência: [id do desenho, serial, n, n ids de traços]. */
FN(jlongArray, nLayerDrawings)(JNIEnv* env, jclass, jlong h, jint i) {
    auto p = get(h);
    const studio::Layer* l = p ? layerAt(*p, i) : nullptr;
    std::vector<jlong> v;
    if (l) {
        for (const auto& kv : l->drawings) {
            const studio::Drawing& d = *kv.second;
            v.push_back(d.id);
            v.push_back(d.serial);
            v.push_back(static_cast<jlong>(d.strokes.size()));
            for (const studio::Stroke& k : d.strokes) v.push_back(k.token ? k.token->id : -1);
        }
    }
    return toLongArray(env, v);
}

/** Exposição da camada [i]: pares [id do frame, id do desenho]. */
FN(jlongArray, nLayerCels)(JNIEnv* env, jclass, jlong h, jint i) {
    auto p = get(h);
    const studio::Layer* l = p ? layerAt(*p, i) : nullptr;
    std::vector<jlong> v;
    if (l) for (const auto& kv : l->cels) { v.push_back(kv.first); v.push_back(kv.second); }
    return toLongArray(env, v);
}

/** Fichas libertadas desde a última chamada: pares [tipo (0 traço, 1 versão de desenho), id]. */
FN(jlongArray, nDrainReleased)(JNIEnv* env, jclass) {
    std::vector<jlong> v;
    for (const auto& kv : studio::TokenTable::instance().drain()) { v.push_back(kv.first); v.push_back(kv.second); }
    return toLongArray(env, v);
}

// ---- edições (devolvem um handle novo) ----
FN(jlong, nSelect)(JNIEnv*, jclass, jlong h, jint i) { return apply(h, [&](const Project& p) { return p.select(i); }); }

FN(jlong, nAddStroke)(JNIEnv*, jclass, jlong h, jlong layerId, jlong strokeId) {
    const studio::Stroke s = strokeOf(strokeId);
    return apply(h, [&](const Project& p) { return p.addStroke(layerId, s); });
}

FN(jlong, nSetCurrentStrokes)(JNIEnv* env, jclass, jlong h, jlong layerId, jlongArray ids) {
    std::vector<studio::Stroke> v;
    for (jlong id : fromLongArray(env, ids)) v.push_back(strokeOf(id));
    return apply(h, [&](const Project& p) { return p.setCurrentStrokes(layerId, v); });
}

/** Conteúdo por camada: camadas [layerIds], com [counts] traços cada, ids em [strokeIds] seguidos. */
FN(jlong, nInsertFrameAt)(JNIEnv* env, jclass, jlong h, jint position, jlongArray layerIds, jintArray counts, jlongArray strokeIds) {
    const std::vector<jlong> lids = fromLongArray(env, layerIds), sids = fromLongArray(env, strokeIds);
    std::vector<jint> cnt(static_cast<size_t>(counts ? env->GetArrayLength(counts) : 0));
    if (!cnt.empty()) env->GetIntArrayRegion(counts, 0, static_cast<jsize>(cnt.size()), cnt.data());
    std::map<int64_t, std::vector<studio::Stroke>> content;
    size_t k = 0;
    for (size_t i = 0; i < lids.size() && i < cnt.size(); ++i) {
        std::vector<studio::Stroke>& list = content[lids[i]];
        for (jint j = 0; j < cnt[i] && k < sids.size(); ++j) list.push_back(strokeOf(sids[k++]));
    }
    return apply(h, [&](const Project& p) { return p.insertFrameAt(position, content); });
}

FN(jlong, nMoveLayer)(JNIEnv*, jclass, jlong h, jlong id, jint delta) {
    return apply(h, [&](const Project& p) { return p.moveLayer(id, delta); });
}

/**
 * Monta um projeto a partir de peças (abrir ficheiro). [layerMeta]: tipo, opacidade, visível por camada;
 * [drawings]: [índice da camada, id do desenho, n, n ids de traços]*; [cels]: [índice da camada, id do frame, id do desenho]*.
 */
FN(jlong, nBuild)(JNIEnv* env, jclass, jlongArray frameIds, jint current, jlong nextId, jlongArray layerIds, jlongArray layerMeta,
                  jobjectArray names, jlongArray drawings, jlongArray cels) {
    const std::vector<jlong> fids = fromLongArray(env, frameIds), lids = fromLongArray(env, layerIds), meta = fromLongArray(env, layerMeta);
    const std::vector<jlong> dr = fromLongArray(env, drawings), ce = fromLongArray(env, cels);
    std::vector<studio::Layer> layers(lids.size());
    for (size_t i = 0; i < lids.size(); ++i) {
        studio::Layer& l = layers[i];
        l.id = lids[i];
        l.kind = meta.size() > 3 * i && meta[3 * i] == 1 ? studio::LayerKind::Scenery : studio::LayerKind::Drawing;
        l.opacityPercent = meta.size() > 3 * i + 1 ? static_cast<int>(meta[3 * i + 1]) : 100;
        l.visible = meta.size() > 3 * i + 2 ? meta[3 * i + 2] != 0 : true;
        if (names != nullptr && static_cast<jsize>(i) < env->GetArrayLength(names)) {
            jstring js = static_cast<jstring>(env->GetObjectArrayElement(names, static_cast<jsize>(i)));
            l.name = str(env, js);
            if (js) env->DeleteLocalRef(js);
        }
    }
    for (size_t k = 0; k + 2 < dr.size();) {
        const size_t li = static_cast<size_t>(dr[k]);
        auto d = std::make_shared<studio::Drawing>();
        d->id = dr[k + 1];
        const size_t n = static_cast<size_t>(dr[k + 2]);
        for (size_t j = 0; j < n && k + 3 + j < dr.size(); ++j) d->strokes.push_back(strokeOf(dr[k + 3 + j]));
        d->touch();
        if (li < layers.size()) layers[li].drawings[d->id] = d;
        k += 3 + n;
    }
    for (size_t k = 0; k + 2 < ce.size(); k += 3) {
        const size_t li = static_cast<size_t>(ce[k]);
        if (li < layers.size()) layers[li].cels[ce[k + 1]] = ce[k + 2];
    }
    return store(Project::assemble(std::vector<int64_t>(fids.begin(), fids.end()), std::move(layers), current, nextId));
}

FN(jlong, nAddFrame)(JNIEnv*, jclass, jlong h) { return apply(h, [](const Project& p) { return p.addFrameAfterCurrent(); }); }
FN(jlong, nDuplicateFrame)(JNIEnv*, jclass, jlong h) { return apply(h, [](const Project& p) { return p.duplicateCurrentFrame(); }); }
FN(jlong, nDeleteFrame)(JNIEnv*, jclass, jlong h) { return apply(h, [](const Project& p) { return p.deleteCurrentFrame(); }); }
FN(jlong, nSeparateScenery)(JNIEnv*, jclass, jlong h, jlong layerId) {
    return apply(h, [&](const Project& p) { return p.separateSceneryFromCurrent(layerId); });
}

FN(jlong, nAddLayer)(JNIEnv*, jclass, jlong h, jint kind) {
    return apply(h, [&](const Project& p) { return p.addLayer(kind == 1 ? studio::LayerKind::Scenery : studio::LayerKind::Drawing); });
}
FN(jlong, nDuplicateLayer)(JNIEnv*, jclass, jlong h, jlong id) { return apply(h, [&](const Project& p) { return p.duplicateLayer(id); }); }
FN(jlong, nRenameLayer)(JNIEnv* env, jclass, jlong h, jlong id, jstring name) {
    const std::string n = str(env, name);
    return apply(h, [&](const Project& p) { return p.renameLayer(id, n); });
}
FN(jlong, nSetLayerOpacity)(JNIEnv*, jclass, jlong h, jlong id, jint pct) {
    return apply(h, [&](const Project& p) { return p.setLayerOpacity(id, pct); });
}
FN(jlong, nToggleLayerVisible)(JNIEnv*, jclass, jlong h, jlong id) {
    return apply(h, [&](const Project& p) { return p.toggleLayerVisible(id); });
}
FN(jlong, nDeleteLayer)(JNIEnv*, jclass, jlong h, jlong id) { return apply(h, [&](const Project& p) { return p.deleteLayer(id); }); }

// ---- exposição ----
FN(jlong, nExtendHold)(JNIEnv*, jclass, jlong h, jint index, jint count) {
    return apply(h, [&](const Project& p) { return studio::extendHold(p, index, count); });
}
FN(jlong, nShrinkHold)(JNIEnv*, jclass, jlong h, jint index, jint count) {
    return apply(h, [&](const Project& p) { return studio::shrinkHold(p, index, count); });
}
FN(jint, nHoldStart)(JNIEnv*, jclass, jlong h, jint index) { auto p = get(h); return p ? studio::holdStart(*p, index) : 0; }
FN(jint, nHoldEnd)(JNIEnv*, jclass, jlong h, jint index) { auto p = get(h); return p ? studio::holdEnd(*p, index) : 0; }
FN(jboolean, nSameExposure)(JNIEnv*, jclass, jlong h, jint a, jint b) {
    auto p = get(h);
    return p && studio::sameExposure(*p, a, b) ? JNI_TRUE : JNI_FALSE;
}

// ---- reprodução: [índice, direção, terminou (0/1)] ----
FN(jintArray, nNextPlaybackStep)(JNIEnv* env, jclass, jint index, jint count, jint mode, jint direction) {
    const studio::PlayMode m = mode == 0 ? studio::PlayMode::Once : mode == 1 ? studio::PlayMode::Loop : studio::PlayMode::PingPong;
    const studio::PlaybackStep s = studio::nextPlaybackStep(index, count, m, direction);
    jint v[3] = {s.index, s.direction, s.finished ? 1 : 0};
    jintArray a = env->NewIntArray(3);
    env->SetIntArrayRegion(a, 0, 3, v);
    return a;
}

// ---- balde de tinta: máscara (w*h bytes, 1 = preencher) ou nulo se não há onde preencher ----
FN(jbyteArray, nComputeFill)(JNIEnv* env, jclass, jintArray argb, jint w, jint h, jint sx, jint sy, jint inkAlpha, jint colorTol,
                             jint gapCells, jint growCap) {
    if (argb == nullptr || w <= 0 || h <= 0 || env->GetArrayLength(argb) < w * h) return nullptr;
    std::vector<jint> px(static_cast<size_t>(w) * h);
    env->GetIntArrayRegion(argb, 0, w * h, px.data());
    studio::FillParams P;
    P.inkAlpha = inkAlpha;
    P.colorTolerance = colorTol;
    P.userGapCells = gapCells;
    P.growCap = growCap;
    const std::vector<uint8_t> m = studio::computeFill(reinterpret_cast<const uint32_t*>(px.data()), w, h, sx, sy, P);
    if (m.empty()) return nullptr;
    jbyteArray out = env->NewByteArray(static_cast<jsize>(m.size()));
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(m.size()), reinterpret_cast<const jbyte*>(m.data()));
    return out;
}

// ---- traço de tinta: contorno com pontas por velocidade, e a corda ----
/**
 * Polígonos do traço (ver studio_ink.hpp): [n, x0, y0, ...]* ou vazio. [xy]: pares x,y; [pr]: pressão; [tm]: ms.
 * [params]: largura, fração mínima, pressão, velocidade, ponta inicial, ponta final, tamanho das pontas, ms da ponta, alisamento, escala.
 */
FN(jfloatArray, nInkOutline)(JNIEnv* env, jclass, jfloatArray xy, jfloatArray pr, jfloatArray tm, jfloatArray params) {
    const jsize n = xy ? env->GetArrayLength(xy) / 2 : 0;
    std::vector<jfloat> a(static_cast<size_t>(n) * 2), p(static_cast<size_t>(n), 1.f), t(static_cast<size_t>(n), 0.f), q(10, 0.f);
    if (n > 0) env->GetFloatArrayRegion(xy, 0, n * 2, a.data());
    if (pr && env->GetArrayLength(pr) >= n && n > 0) env->GetFloatArrayRegion(pr, 0, n, p.data());
    if (tm && env->GetArrayLength(tm) >= n && n > 0) env->GetFloatArrayRegion(tm, 0, n, t.data());
    if (params && env->GetArrayLength(params) >= 10) env->GetFloatArrayRegion(params, 0, 10, q.data());
    std::vector<studio::ink::Sample> s(static_cast<size_t>(n));
    for (jsize i = 0; i < n; ++i) s[static_cast<size_t>(i)] = {a[2 * i], a[2 * i + 1], p[i], t[i]};
    studio::ink::Params P;
    P.width = q[0]; P.minWidthFraction = q[1]; P.pressureWidth = q[2]; P.speedWidth = q[3];
    P.startTaper = q[4]; P.endTaper = q[5]; P.taperSize = q[6]; P.taperMs = q[7]; P.smooth = q[8]; P.scale = q[9];
    const std::vector<float> o = studio::ink::outline(s, P);
    jfloatArray r = env->NewFloatArray(static_cast<jsize>(o.size()));
    if (!o.empty()) env->SetFloatArrayRegion(r, 0, static_cast<jsize>(o.size()), o.data());
    return r;
}

/** Corda: [state] = [x, y, direção x, direção y]; atualiza-o no lugar. */
FN(void, nRopeStart)(JNIEnv* env, jclass, jfloatArray state, jfloat x, jfloat y) {
    float st[4];
    studio::ink::rope::start(st, x, y);
    env->SetFloatArrayRegion(state, 0, 4, st);
}
FN(void, nRopeNext)(JNIEnv* env, jclass, jfloatArray state, jfloat x, jfloat y, jfloat ropePx) {
    float st[4];
    env->GetFloatArrayRegion(state, 0, 4, st);
    studio::ink::rope::next(st, x, y, ropePx);
    env->SetFloatArrayRegion(state, 0, 4, st);
}
FN(jfloatArray, nRopeFinish)(JNIEnv* env, jclass, jfloatArray state, jfloat x, jfloat y, jfloat ropePx) {
    float st[4];
    env->GetFloatArrayRegion(state, 0, 4, st);
    const std::vector<float> o = studio::ink::rope::finish(st, x, y, ropePx);
    env->SetFloatArrayRegion(state, 0, 4, st);
    jfloatArray r = env->NewFloatArray(static_cast<jsize>(o.size()));
    if (!o.empty()) env->SetFloatArrayRegion(r, 0, static_cast<jsize>(o.size()), o.data());
    return r;
}
