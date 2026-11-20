#include <jni.h>

#include <cstring>
#include <string>
#include <vector>

#include "animbrush_core.h"

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_animationstudio_ui_AnimBrushNative_nativeAvailable(JNIEnv*, jobject) {
    return abridge::available() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_animationstudio_ui_AnimBrushNative_nativeInit(JNIEnv* env, jobject, jbyteArray pack) {
    if (pack == nullptr) return abridge::init(nullptr, 0) ? JNI_TRUE : JNI_FALSE;
    const jsize n = env->GetArrayLength(pack);
    std::vector<uint8_t> data(static_cast<size_t>(n));
    env->GetByteArrayRegion(pack, 0, n, reinterpret_cast<jbyte*>(data.data()));
    return abridge::init(data.data(), data.size()) ? JNI_TRUE : JNI_FALSE;
}

// Bytes UTF-8 (NewStringUTF só aceita "UTF-8 modificado"; assim não há problema com caracteres fora do BMP).
JNIEXPORT jbyteArray JNICALL Java_com_animationstudio_ui_AnimBrushNative_nativeCatalog(JNIEnv* env, jobject) {
    const std::string json = abridge::catalog();
    jbyteArray out = env->NewByteArray(static_cast<jsize>(json.size()));
    if (out != nullptr) env->SetByteArrayRegion(out, 0, static_cast<jsize>(json.size()), reinterpret_cast<const jbyte*>(json.data()));
    return out;
}

/**
 * Devolve null (nada para desenhar ou pincel desconhecido) ou um vetor: [largura, altura, bits(x), bits(y), bits(escala),
 * píxeis ARGB...]. x, y e escala são floats guardados como bits.
 */
JNIEXPORT jintArray JNICALL Java_com_animationstudio_ui_AnimBrushNative_nativeRender(
    JNIEnv* env, jobject, jstring id, jfloatArray pts, jfloat radius, jfloat r, jfloat g, jfloat b, jint seed, jboolean eraser) {
    if (id == nullptr || pts == nullptr) return nullptr;
    const jsize total = env->GetArrayLength(pts);
    const int count = static_cast<int>(total / 3);
    if (count < 1) return nullptr;
    std::vector<float> p(static_cast<size_t>(count) * 3);
    env->GetFloatArrayRegion(pts, 0, count * 3, p.data());
    const char* cid = env->GetStringUTFChars(id, nullptr);
    if (cid == nullptr) return nullptr;
    abridge::Image img;
    const bool ok = abridge::render(cid, p.data(), count, radius, r, g, b, static_cast<uint32_t>(seed), eraser == JNI_TRUE, img);
    env->ReleaseStringUTFChars(id, cid);
    if (!ok || img.w <= 0 || img.h <= 0) return nullptr;

    const jsize n = static_cast<jsize>(5 + img.argb.size());
    jintArray out = env->NewIntArray(n);
    if (out == nullptr) return nullptr;
    int32_t head[5];
    head[0] = img.w;
    head[1] = img.h;
    std::memcpy(&head[2], &img.x, sizeof(float));
    std::memcpy(&head[3], &img.y, sizeof(float));
    std::memcpy(&head[4], &img.scale, sizeof(float));
    env->SetIntArrayRegion(out, 0, 5, reinterpret_cast<const jint*>(head));
    env->SetIntArrayRegion(out, 5, static_cast<jsize>(img.argb.size()), reinterpret_cast<const jint*>(img.argb.data()));
    return out;
}

}  // extern "C"
