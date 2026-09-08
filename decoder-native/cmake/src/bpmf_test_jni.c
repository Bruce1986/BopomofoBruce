/*
 * TEST-ONLY JNI bridge for `libbpmf.so`.
 *
 * DEVPLAN W1-A explicitly excludes "JNI binding" (that's W2-A's `:decoder`
 * module: implements the `ZhuyinDecoder` Kotlin interface, thread-safety
 * wrapper, personal dictionary, DI). But W1-A's own acceptance criteria
 * requires a connectedAndroidTest that calls bpmf_init()/bpmf_input() on a
 * real device, which is impossible from Kotlin without *some* JNI glue.
 *
 * This file is that minimal glue and nothing more: four pass-through
 * functions bound to a test-only class
 * (androidTest/kotlin/.../testbridge/BpmfTestBridge.kt), named distinctly
 * (`nativeTest*` under a `testbridge` package) so it can't be mistaken for,
 * or collide with, W2-A's real production JNI binding. It is compiled into
 * the same libbpmf.so as the public bpmf_* C API for build simplicity —
 * W2-A is free to delete this file once `:decoder` has its own real
 * binding and its own connectedAndroidTest coverage.
 *
 * Exposure: cmake/CMakeLists.txt only adds this translation unit to the
 * `bpmf` target when -DBPMF_BUILD_TEST_BRIDGE=ON is set (debug variant
 * only — see build.gradle.kts), so these four
 * Java_..._BpmfTestBridge_nativeTest* symbols are absent from release .so
 * output — verified with `nm -D` (see devlog A5/A10).
 *
 * `nativeTestFree` treats its jlong argument as a raw pointer and calls
 * free() on it, so keeping it out of release builds matters — but NOT for
 * the reason a prior version of this comment gave ("avoid exposing an
 * arbitrary-address free() primitive to anything that can dlsym() the
 * .so"). That framing was wrong on its own terms: before
 * cmake/CMakeLists.txt gained a --version-script (see R1 in the 20260811
 * devlog entry), the release .so already exported chewing_free(void*) —
 * the vendored libchewing C API's own arbitrary-pointer free() — plus
 * chewing_delete/chewing_Terminate/chewing_set_logger and ~130 other
 * chewing_* functions regardless of this gate; the gate was never actually
 * the thing standing between a dlsym()-capable attacker and a free()
 * primitive on a release build. The version script now hides all of those
 * (release .so exports only bpmf_commit/bpmf_free/bpmf_init/bpmf_input),
 * so this gate keeping nativeTestFree() out of release IS what keeps
 * release libbpmf.so's dynsym table down to exactly bpmf.h's 4 functions —
 * that value (attack surface stays minimal and matches the documented
 * public API, not "prevents the only free() primitive") is why it's worth
 * keeping.
 */

#include "bpmf.h"

#include <jni.h>
#include <stdlib.h>
#include <string.h>

JNIEXPORT jlong JNICALL
Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestInit(
    JNIEnv* env, jclass clazz, jstring data_path) {
    (void)clazz;
    if (data_path == NULL) {
        return 0;
    }
    const char* path = (*env)->GetStringUTFChars(env, data_path, NULL);
    if (path == NULL) {
        /* OOM converting the jstring; GetStringUTFChars already threw OutOfMemoryError. */
        return 0;
    }
    void* handle = bpmf_init(path);
    (*env)->ReleaseStringUTFChars(env, data_path, path);
    return (jlong)(intptr_t)handle;
}

JNIEXPORT jobjectArray JNICALL
Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestInput(
    JNIEnv* env, jclass clazz, jlong handle, jstring zhuyin) {
    (void)clazz;
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    if (string_class == NULL) {
        /* FindClass has already thrown (NoClassDefFoundError / OOM). Calling
         * further JNI functions with a pending exception is undefined
         * behaviour, and every remaining path in this function needs
         * string_class, so hand control straight back to the JVM. */
        return NULL;
    }
    if (zhuyin == NULL) {
        return (*env)->NewObjectArray(env, 0, string_class, NULL);
    }
    const char* zhuyin_utf8 = (*env)->GetStringUTFChars(env, zhuyin, NULL);
    if (zhuyin_utf8 == NULL) {
        /* OOM converting the jstring; GetStringUTFChars already threw OutOfMemoryError. */
        return (*env)->NewObjectArray(env, 0, string_class, NULL);
    }

    char* joined = NULL;
    size_t count = bpmf_input((void*)(intptr_t)handle, zhuyin_utf8, &joined);
    (*env)->ReleaseStringUTFChars(env, zhuyin, zhuyin_utf8);

    jobjectArray result = (*env)->NewObjectArray(env, (jsize)count, string_class, NULL);
    if (result == NULL) {
        /* Allocation failed and NewObjectArray already threw OutOfMemoryError.
         * Filling it below would mean calling SetObjectArrayElement on a NULL
         * array with an exception pending. */
        return NULL;
    }

    /*
     * bpmf.h's contract guarantees *candidates_out (`joined` here) is never
     * NULL as long as the &joined pointer we passed isn't NULL itself — see
     * bpmf_wrapper.c's bpmf_input(), which always points it at either a
     * heap-allocated string or the static empty-string sentinel. The
     * `joined != NULL` check is therefore defense-in-depth, not a
     * workaround for a real NULL path.
     */
    if (count > 0 && joined != NULL) {
        char* cursor = joined;
        for (jsize i = 0; i < (jsize)count; i++) {
            char* newline = strchr(cursor, '\n');
            size_t segment_len = newline != NULL ? (size_t)(newline - cursor) : strlen(cursor);
            char* segment = (char*)malloc(segment_len + 1);
            if (segment != NULL) {
                memcpy(segment, cursor, segment_len);
                segment[segment_len] = '\0';
                jstring jsegment = (*env)->NewStringUTF(env, segment);
                (*env)->SetObjectArrayElement(env, result, i, jsegment);
                (*env)->DeleteLocalRef(env, jsegment);
                free(segment);
            }
            cursor = (newline != NULL) ? newline + 1 : cursor + segment_len;
        }
    }
    /* `joined` is owned by the bpmf handle (see bpmf.h ownership contract); do not free() it. */
    return result;
}

JNIEXPORT void JNICALL
Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestCommit(
    JNIEnv* env, jclass clazz, jlong handle, jint index) {
    (void)env;
    (void)clazz;
    bpmf_commit((void*)(intptr_t)handle, (size_t)index);
}

JNIEXPORT void JNICALL
Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestFree(
    JNIEnv* env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    bpmf_free((void*)(intptr_t)handle);
}
