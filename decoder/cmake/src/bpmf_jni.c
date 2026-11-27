/*
 * :decoder 的 JNI glue（Kotlin 端 JniBpmfNative）。不連結 libbpmf.so，JNI_OnLoad 以 dlopen/dlsym 取 bpmf.h
 * 的四個函式（指標型別取自 bpmf.h），缺符號就回 JNI_ERR，System.loadLibrary 當場丟 UnsatisfiedLinkError。
 * 為何這樣放：docs/devlog/W2-A1-decoder-jni-20261120.md。
 */

#include "bpmf.h"

#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>
#include <string.h>

static __typeof__(bpmf_init)* p_init;
static __typeof__(bpmf_input)* p_input;
static __typeof__(bpmf_commit)* p_commit;
static __typeof__(bpmf_free)* p_free;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    void* lib = dlopen("libbpmf.so", RTLD_NOW); /* 刻意不 dlclose：指標要活到 process 結束。 */
    if (lib == NULL) {
        return JNI_ERR;
    }
    p_init = (__typeof__(p_init))dlsym(lib, "bpmf_init");
    p_input = (__typeof__(p_input))dlsym(lib, "bpmf_input");
    p_commit = (__typeof__(p_commit))dlsym(lib, "bpmf_commit");
    p_free = (__typeof__(p_free))dlsym(lib, "bpmf_free");
    if (p_init == NULL || p_input == NULL || p_commit == NULL || p_free == NULL) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

JNIEXPORT jlong JNICALL
Java_com_bopomofobruce_decoder_JniBpmfNative_init(JNIEnv* env, jobject thiz, jstring data_path) {
    (void)thiz;
    if (data_path == NULL) {
        return 0;
    }
    const char* path = (*env)->GetStringUTFChars(env, data_path, NULL);
    if (path == NULL) {
        return 0; /* OutOfMemoryError pending：不再呼叫其他 JNI 函式。 */
    }
    void* handle = p_init(path);
    (*env)->ReleaseStringUTFChars(env, data_path, path);
    return (jlong)(intptr_t)handle;
}

/*
 * 回傳 bpmf_input() 結果的原始 bytes（標準 UTF-8），由 Kotlin 解碼；NewStringUTF 吃 modified UTF-8，BMP 以外會解錯。
 * 輸入刻意用 GetStringUTFChars：U+0000（C0 80）與 surrogate 編碼都會被 bpmf_wrapper.c 拒絕，不會在 NUL 處被截斷。
 */
JNIEXPORT jbyteArray JNICALL Java_com_bopomofobruce_decoder_JniBpmfNative_input(
    JNIEnv* env, jobject thiz, jlong handle, jstring zhuyin) {
    (void)thiz;
    char* joined = NULL;
    size_t count = 0;
    if (zhuyin != NULL) {
        const char* utf = (*env)->GetStringUTFChars(env, zhuyin, NULL);
        if (utf == NULL) {
            return NULL; /* OutOfMemoryError pending */
        }
        count = p_input((void*)(intptr_t)handle, utf, &joined);
        (*env)->ReleaseStringUTFChars(env, zhuyin, utf);
    }
    /* joined 屬於 handle（bpmf.h ownership contract）：只讀、不 free。 */
    jsize len = (count > 0 && joined != NULL) ? (jsize)strlen(joined) : 0;
    jbyteArray out = (*env)->NewByteArray(env, len);
    if (out != NULL && len > 0) {
        (*env)->SetByteArrayRegion(env, out, 0, len, (const jbyte*)joined);
    }
    return out; /* NULL 時 OutOfMemoryError 已 pending */
}

JNIEXPORT void JNICALL Java_com_bopomofobruce_decoder_JniBpmfNative_commit(
    JNIEnv* env, jobject thiz, jlong handle, jint index) {
    (void)env;
    (void)thiz;
    if (index >= 0) { /* bpmf_commit 也會拒絕，但不讓負數經 size_t 轉型繞回來。 */
        p_commit((void*)(intptr_t)handle, (size_t)index);
    }
}

JNIEXPORT void JNICALL
Java_com_bopomofobruce_decoder_JniBpmfNative_free(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env;
    (void)thiz;
    p_free((void*)(intptr_t)handle);
}
