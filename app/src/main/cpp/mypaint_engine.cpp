// Motor de pincéis: usa o libmypaint (ISC) para calcular a sequência de "dabs" de um traço.
//
// O libmypaint decide ONDE e COM QUE FORMA cada toque é feito (espaçamento por raio, dinâmicas de pressão e
// velocidade, jitter, ruído de rastreio, dureza, razão de aspeto, ângulo, variação de cor). Esta camada fornece
// uma MyPaintSurface que só REGISTA os toques; o desenho em si faz-se em Kotlin (Compose), no mesmo pipeline
// dos restantes pincéis. Não há estado global: cada chamada cria o seu pincel, por isso é segura entre threads,
// e o resultado é determinístico (o libmypaint inicializa o gerador aleatório com semente fixa).
//
// Limitações conhecidas (documentadas em docs/LIBMYPAINT.md): smudge (precisa de ler a tela), lock_alpha,
// colorize, posterize e "paint" (mistura de pigmento) não são aplicados, porque dependem dos píxeis da tela.

#include <cmath>
#include <cstddef>
#include <string>
#include <vector>

extern "C" {
#include "mypaint-brush.h"
#include "mypaint-brush-settings.h"
#include "mypaint-surface.h"
}

#include "mypaint_engine.h"

namespace {

constexpr size_t kMaxDabs = 60000;

struct DabSurface {
    MyPaintSurface base;
    std::vector<float>* out;
    size_t count;
};

int drawDab(MyPaintSurface* self, float x, float y, float radius,
            float r, float g, float b,
            float opaque, float hardness, float /*softness*/,
            float alphaEraser, float aspect, float angle,
            float /*lockAlpha*/, float /*colorize*/, float /*posterize*/, float /*posterizeNum*/,
            float /*paint*/) {
    DabSurface* s = reinterpret_cast<DabSurface*>(self);
    if (s->count >= kMaxDabs) return 0;
    if (!(radius > 0.0f) || !(opaque > 0.0f) || !std::isfinite(x) || !std::isfinite(y)) return 0;
    std::vector<float>& o = *s->out;
    o.push_back(x);
    o.push_back(y);
    o.push_back(radius);
    o.push_back(r);
    o.push_back(g);
    o.push_back(b);
    o.push_back(opaque);
    o.push_back(hardness);
    o.push_back(alphaEraser);
    o.push_back(aspect);
    o.push_back(angle);
    s->count++;
    return 1;
}

// Sem acesso à tela, não há cor para misturar: alfa 0 faz o smudge não ter efeito.
void getColor(MyPaintSurface*, float, float, float,
              float* r, float* g, float* b, float* a, float) {
    *r = 0.0f;
    *g = 0.0f;
    *b = 0.0f;
    *a = 0.0f;
}

void beginAtomic(MyPaintSurface*) {}
void endAtomic(MyPaintSurface*, MyPaintRectangles*) {}
void destroySurface(MyPaintSurface*) {}

}  // namespace

bool mpComputeDabs(const MpDabRequest& rq, std::vector<float>& out) {
    out.clear();
    if (rq.points.size() < 3 || rq.points.size() % 3 != 0) return false;
    if (rq.settingNames.size() != rq.baseValues.size()) return false;

    MyPaintBrush* brush = mypaint_brush_new();
    if (brush == nullptr) return false;

    for (size_t i = 0; i < rq.settingNames.size(); i++) {
        const int id = static_cast<int>(mypaint_brush_setting_from_cname(rq.settingNames[i].c_str()));
        if (id < 0 || id >= MYPAINT_BRUSH_SETTINGS_COUNT) continue;
        mypaint_brush_set_base_value(brush, static_cast<MyPaintBrushSetting>(id), rq.baseValues[i]);
    }
    for (const MpMapping& m : rq.mappings) {
        if (m.settingIndex < 0 || static_cast<size_t>(m.settingIndex) >= rq.settingNames.size()) continue;
        const int sid = static_cast<int>(mypaint_brush_setting_from_cname(rq.settingNames[m.settingIndex].c_str()));
        const int inp = static_cast<int>(mypaint_brush_input_from_cname(m.input.c_str()));
        if (sid < 0 || sid >= MYPAINT_BRUSH_SETTINGS_COUNT) continue;
        if (inp < 0 || inp >= MYPAINT_BRUSH_INPUTS_COUNT) continue;
        const int n = static_cast<int>(m.points.size() / 2);
        if (n < 2 || n > 64) continue;
        mypaint_brush_set_mapping_n(brush, static_cast<MyPaintBrushSetting>(sid), static_cast<MyPaintBrushInput>(inp), n);
        for (int k = 0; k < n; k++) {
            mypaint_brush_set_mapping_point(brush, static_cast<MyPaintBrushSetting>(sid), static_cast<MyPaintBrushInput>(inp),
                                            k, m.points[2 * k], m.points[2 * k + 1]);
        }
    }

    DabSurface surf;
    mypaint_surface_init(&surf.base);
    surf.base.draw_dab = drawDab;
    surf.base.get_color = getColor;
    surf.base.begin_atomic = beginAtomic;
    surf.base.end_atomic = endAtomic;
    surf.base.destroy = destroySurface;
    surf.out = &out;
    surf.count = 0;

    mypaint_brush_reset(brush);
    mypaint_brush_new_stroke(brush);

    const size_t np = rq.points.size() / 3;
    const double dt = rq.dtime > 0.0f ? static_cast<double>(rq.dtime) : 0.008;
    // Primeiro evento com pressão 0 só posiciona o pincel (como faz o MyPaint).
    mypaint_brush_stroke_to(brush, &surf.base, rq.points[0], rq.points[1], 0.0f, 0.0f, 0.0f, 10.0, 1.0f, 0.0f, 0.0f, 0);
    for (size_t i = 0; i < np; i++) {
        const float x = rq.points[3 * i];
        const float y = rq.points[3 * i + 1];
        const float p = rq.points[3 * i + 2];
        mypaint_brush_stroke_to(brush, &surf.base, x, y, p, 0.0f, 0.0f, dt, 1.0f, 0.0f, 0.0f, 0);
    }
    // Último evento com pressão 0: fecha o traço no ponto final.
    mypaint_brush_stroke_to(brush, &surf.base, rq.points[3 * (np - 1)], rq.points[3 * (np - 1) + 1], 0.0f, 0.0f, 0.0f, dt, 1.0f, 0.0f, 0.0f, 0);

    mypaint_brush_unref(brush);
    return true;
}

#ifndef MP_NO_JNI

#include <jni.h>

namespace {

std::string toString(JNIEnv* env, jstring s) {
    if (s == nullptr) return std::string();
    const char* c = env->GetStringUTFChars(s, nullptr);
    if (c == nullptr) return std::string();
    std::string r(c);
    env->ReleaseStringUTFChars(s, c);
    return r;
}

std::vector<float> toFloats(JNIEnv* env, jfloatArray a) {
    std::vector<float> v;
    if (a == nullptr) return v;
    const jsize n = env->GetArrayLength(a);
    v.resize(static_cast<size_t>(n));
    if (n > 0) env->GetFloatArrayRegion(a, 0, n, v.data());
    return v;
}

}  // namespace

// Kotlin: MyPaintNative.computeDabs(names, base, mapSetting, mapInput, mapPoints, points, dtime): FloatArray?
// Devolve 11 floats por toque: x, y, raio, r, g, b, opaco, dureza, alpha_eraser, aspeto, ângulo (graus).
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_animationstudio_ui_MyPaintNative_computeDabs(JNIEnv* env, jclass /*clazz*/,
                                                       jobjectArray jNames, jfloatArray jBase,
                                                       jintArray jMapSetting, jobjectArray jMapInput,
                                                       jobjectArray jMapPoints, jfloatArray jPoints, jfloat dtime) {
    try {
        MpDabRequest rq;
        const jsize ns = jNames == nullptr ? 0 : env->GetArrayLength(jNames);
        for (jsize i = 0; i < ns; i++) {
            jstring js = static_cast<jstring>(env->GetObjectArrayElement(jNames, i));
            rq.settingNames.push_back(toString(env, js));
            if (js != nullptr) env->DeleteLocalRef(js);
        }
        rq.baseValues = toFloats(env, jBase);
        rq.points = toFloats(env, jPoints);
        rq.dtime = dtime;

        const jsize nm = jMapSetting == nullptr ? 0 : env->GetArrayLength(jMapSetting);
        std::vector<jint> mapSetting(static_cast<size_t>(nm));
        if (nm > 0) env->GetIntArrayRegion(jMapSetting, 0, nm, mapSetting.data());
        for (jsize i = 0; i < nm; i++) {
            MpMapping m;
            m.settingIndex = mapSetting[static_cast<size_t>(i)];
            jstring jin = static_cast<jstring>(env->GetObjectArrayElement(jMapInput, i));
            m.input = toString(env, jin);
            if (jin != nullptr) env->DeleteLocalRef(jin);
            jfloatArray jp = static_cast<jfloatArray>(env->GetObjectArrayElement(jMapPoints, i));
            m.points = toFloats(env, jp);
            if (jp != nullptr) env->DeleteLocalRef(jp);
            rq.mappings.push_back(std::move(m));
        }

        std::vector<float> out;
        if (!mpComputeDabs(rq, out)) return nullptr;
        jfloatArray res = env->NewFloatArray(static_cast<jsize>(out.size()));
        if (res == nullptr) return nullptr;
        if (!out.empty()) env->SetFloatArrayRegion(res, 0, static_cast<jsize>(out.size()), out.data());
        return res;
    } catch (...) {
        return nullptr;
    }
}

#endif  // MP_NO_JNI
