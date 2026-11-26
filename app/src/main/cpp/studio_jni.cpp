// Ponte JNI do modelo do projeto (etapa 2 da migração): o Kotlin guarda só um número (handle) e pede ao C++ cada
// operação. Cada operação devolve um handle NOVO (o Project é um valor imutável, como no Kotlin) e o antigo continua
// válido até o Kotlin o libertar (nFree). O desfazer/refazer guarda handles, que partilham os desenhos por dentro.
#include <jni.h>

#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "studio_hold.hpp"
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

/** Quantos traços tem a camada no frame (índice de frame). */
FN(jint, nStrokeCount)(JNIEnv*, jclass, jlong h, jlong layerId, jint frame) {
    auto p = get(h);
    return p ? static_cast<jint>(p->strokesAt(layerId, frame).size()) : 0;
}

FN(jfloatArray, nStrokePoints)(JNIEnv* env, jclass, jlong h, jlong layerId, jint frame, jint index) {
    auto p = get(h);
    std::vector<studio::Stroke> s = p ? p->strokesAt(layerId, frame) : std::vector<studio::Stroke>{};
    const bool ok = index >= 0 && index < static_cast<jint>(s.size());
    const jsize n = ok ? static_cast<jsize>(s[static_cast<size_t>(index)].points.size() * 2) : 0;
    jfloatArray a = env->NewFloatArray(n);
    if (n > 0) {
        std::vector<jfloat> v;
        v.reserve(static_cast<size_t>(n));
        for (const studio::Pt& q : s[static_cast<size_t>(index)].points) { v.push_back(q.x); v.push_back(q.y); }
        env->SetFloatArrayRegion(a, 0, n, v.data());
    }
    return a;
}

/** [cor ARGB, largura, apagar, afunilamento, dureza, modo, id do preenchimento, id da imagem] como doubles. */
FN(jdoubleArray, nStrokeMeta)(JNIEnv* env, jclass, jlong h, jlong layerId, jint frame, jint index) {
    auto p = get(h);
    std::vector<studio::Stroke> s = p ? p->strokesAt(layerId, frame) : std::vector<studio::Stroke>{};
    jdoubleArray a = env->NewDoubleArray(8);
    if (index >= 0 && index < static_cast<jint>(s.size())) {
        const studio::Stroke& k = s[static_cast<size_t>(index)];
        jdouble v[8] = {static_cast<double>(k.colorArgb), k.width, k.erase ? 1.0 : 0.0, k.taper, k.hardness,
                        static_cast<double>(k.mode), static_cast<double>(k.fillBlob), static_cast<double>(k.imageBlob)};
        env->SetDoubleArrayRegion(a, 0, 8, v);
    }
    return a;
}

FN(jstring, nStrokeBrush)(JNIEnv* env, jclass, jlong h, jlong layerId, jint frame, jint index) {
    auto p = get(h);
    std::vector<studio::Stroke> s = p ? p->strokesAt(layerId, frame) : std::vector<studio::Stroke>{};
    const bool ok = index >= 0 && index < static_cast<jint>(s.size());
    return env->NewStringUTF(ok ? s[static_cast<size_t>(index)].brush.c_str() : "");
}

// ---- edições (devolvem um handle novo) ----
FN(jlong, nSelect)(JNIEnv*, jclass, jlong h, jint i) { return apply(h, [&](const Project& p) { return p.select(i); }); }

FN(jlong, nAddStroke)(JNIEnv* env, jclass, jlong h, jlong layerId, jfloatArray pts, jint color, jfloat width, jboolean erase,
                      jfloat taper, jfloat hardness, jstring brush, jint mode, jlong fillBlob, jlong imageBlob) {
    studio::Stroke s;
    const jsize n = pts ? env->GetArrayLength(pts) : 0;
    std::vector<jfloat> v(static_cast<size_t>(n));
    if (n > 0) env->GetFloatArrayRegion(pts, 0, n, v.data());
    for (jsize i = 0; i + 1 < n; i += 2) s.points.push_back({v[static_cast<size_t>(i)], v[static_cast<size_t>(i) + 1]});
    s.colorArgb = static_cast<uint32_t>(color);
    s.width = width;
    s.erase = erase != JNI_FALSE;
    s.taper = taper;
    s.hardness = hardness;
    s.brush = str(env, brush);
    s.mode = mode;
    s.fillBlob = fillBlob;
    s.imageBlob = imageBlob;
    return apply(h, [&](const Project& p) { return p.addStroke(layerId, s); });
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
