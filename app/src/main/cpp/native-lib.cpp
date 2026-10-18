#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_animationstudio_MainActivity_nativeEngineStatus(JNIEnv* env, jobject /* thiz */) {
    const std::string status = "C++ animation engine initialized";
    return env->NewStringUTF(status.c_str());
}
