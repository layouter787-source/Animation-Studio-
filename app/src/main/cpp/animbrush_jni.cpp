// Ponte JNI para o AnimBrush (biblioteca de pincéis em C++17, ~500 pincéis).
//
// Só compila o AnimBrush se a pasta app/src/main/cpp/animbrush/ existir (ver CMakeLists.txt e docs/ANIMBRUSH.md);
// sem ela, estas funções existem na mesma e dizem que não há AnimBrush, e a app funciona sem esses pincéis.
//
// O motor emite "toques" para uma ISurface: aqui usa-se a RasterSurface de referência (CPU, float premultiplicado) sobre
// a caixa do traço, e devolve-se o resultado como píxeis ARGB (não premultiplicados) para o Kotlin criar a imagem.
#include <jni.h>

#include <cstdint>
#include <exception>
#include <string>
#include <vector>

#ifdef ANIMBRUSH_ENABLED
#include "animbrush/animbrush.hpp"
#endif

namespace {

#ifdef ANIMBRUSH_ENABLED
std::string toStd(JNIEnv* env, jstring s) {
    if (s == nullptr) return std::string();
    const char* c = env->GetStringUTFChars(s, nullptr);
    if (c == nullptr) return std::string();
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}
#endif

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_animationstudio_ui_AnimBrushNative_nativeAvailable(JNIEnv*, jobject) {
#ifdef ANIMBRUSH_ENABLED
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

// Regista os pincéis e carrega as texturas (stamps.abpack) a partir da memória; [pack] pode ser null
// (as máscaras procedurais continuam disponíveis).
JNIEXPORT jboolean JNICALL
Java_com_animationstudio_ui_AnimBrushNative_nativeInit(JNIEnv* env, jobject, jbyteArray pack) {
#ifdef ANIMBRUSH_ENABLED
    try {
        ab::BrushLibrary::instance();
        if (pack != nullptr) {
            const jsize n = env->GetArrayLength(pack);
            if (n > 0) {
                std::vector<uint8_t> buf(static_cast<size_t>(n));
                env->GetByteArrayRegion(pack, 0, n, reinterpret_cast<jbyte*>(buf.data()));
                ab::StampBank::instance().loadPackFromMemory(buf.data(), buf.size());
            }
        }
        return ab::BrushLibrary::instance().size() > 0 ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
#else
    (void)env;
    (void)pack;
    return JNI_FALSE;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_animationstudio_ui_AnimBrushNative_nativeCatalogJson(JNIEnv* env, jobject) {
#ifdef ANIMBRUSH_ENABLED
    try {
        const std::string json = ab::BrushLibrary::instance().catalogJson();
        return env->NewStringUTF(json.c_str());
    } catch (...) {
        return env->NewStringUTF("{}");
    }
#else
    return env->NewStringUTF("{}");
#endif
}

// Desenha um traço numa superfície de [w]x[h] píxeis cuja origem é ([ox],[oy]) na folha.
// xs/ys/pressures/dts têm um valor por ponto (dts = segundos desde o ponto anterior). A cor é reta (não premultiplicada),
// [radius] é o RAIO em px, [opacity] o fluxo global. Devolve w*h píxeis ARGB, ou null se o pincel não existe ou falhar.
JNIEXPORT jintArray JNICALL
Java_com_animationstudio_ui_AnimBrushNative_nativeRender(
    JNIEnv* env, jobject, jstring brushId, jfloatArray xs, jfloatArray ys, jfloatArray pressures, jfloatArray dts,
    jint w, jint h, jfloat ox, jfloat oy, jfloat r, jfloat g, jfloat b, jfloat radius, jfloat opacity, jint seed,
    jint frame, jfloat timeSec) {
#ifdef ANIMBRUSH_ENABLED
    try {
        if (w <= 0 || h <= 0 || xs == nullptr || ys == nullptr || pressures == nullptr || dts == nullptr) return nullptr;
        const jsize n = env->GetArrayLength(xs);
        if (n <= 0 || env->GetArrayLength(ys) != n || env->GetArrayLength(pressures) != n || env->GetArrayLength(dts) != n) {
            return nullptr;
        }
        std::vector<float> px(static_cast<size_t>(n)), py(static_cast<size_t>(n)), pr(static_cast<size_t>(n)), dt(static_cast<size_t>(n));
        env->GetFloatArrayRegion(xs, 0, n, px.data());
        env->GetFloatArrayRegion(ys, 0, n, py.data());
        env->GetFloatArrayRegion(pressures, 0, n, pr.data());
        env->GetFloatArrayRegion(dts, 0, n, dt.data());

        auto brush = ab::BrushLibrary::instance().create(toStd(env, brushId));
        if (!brush) return nullptr;

        ab::RasterSurface surface(w, h);
        ab::StrokeParams sp;
        sp.color = ab::Color{r, g, b, 1.f};
        sp.size = radius;
        sp.opacity = opacity;
        sp.seed = static_cast<uint32_t>(seed);
        sp.frame = frame;
        sp.time = timeSec;

        auto sample = [&](int i) {
            ab::InputSample s;
            s.x = px[static_cast<size_t>(i)] - ox;
            s.y = py[static_cast<size_t>(i)] - oy;
            s.pressure = pr[static_cast<size_t>(i)];
            s.dt = dt[static_cast<size_t>(i)] > 0.f ? dt[static_cast<size_t>(i)] : 1.f / 120.f;
            return s;
        };

        brush->begin(surface, sp, sample(0));
        for (jsize i = 1; i < n; ++i) brush->move(surface, sample(static_cast<int>(i)));
        // Um toque sem movimento: deixa o pincel "assentar" (aerógrafo, fogo, gotejar…).
        if (n == 1) brush->hold(surface, 0.25f);
        brush->end(surface);

        const std::vector<uint8_t> rgba = surface.toRGBA8(ab::Color{0.f, 0.f, 0.f, 0.f});
        const size_t count = static_cast<size_t>(w) * static_cast<size_t>(h);
        std::vector<jint> out(count);
        for (size_t i = 0; i < count; ++i) {
            const uint32_t R = rgba[i * 4], G = rgba[i * 4 + 1], B = rgba[i * 4 + 2], A = rgba[i * 4 + 3];
            out[i] = static_cast<jint>((A << 24) | (R << 16) | (G << 8) | B);
        }
        jintArray result = env->NewIntArray(static_cast<jsize>(count));
        if (result == nullptr) return nullptr;
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(count), out.data());
        return result;
    } catch (...) {
        return nullptr;
    }
#else
    (void)env; (void)brushId; (void)xs; (void)ys; (void)pressures; (void)dts; (void)w; (void)h; (void)ox; (void)oy;
    (void)r; (void)g; (void)b; (void)radius; (void)opacity; (void)seed; (void)frame; (void)timeSec;
    return nullptr;
#endif
}

}  // extern "C"
